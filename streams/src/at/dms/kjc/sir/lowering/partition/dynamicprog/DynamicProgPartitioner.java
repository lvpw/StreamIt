package at.dms.kjc.sir.lowering.partition.dynamicprog;

import java.util.*;
import java.io.*;

import at.dms.kjc.*;
import at.dms.util.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.fusion.*;
import at.dms.kjc.sir.lowering.fission.*;
import at.dms.kjc.sir.lowering.partition.*;

public class DynamicProgPartitioner extends ListPartitioner {
    /**
     * The overhead of work estimated for each fissed node.
     */
    static final int FISSION_OVERHEAD = 1;
    /**
     * The maximum amount to fiss (before network takes over).
     */
    static final int MAX_FISSION_FACTOR = 3;
    /**
     * The overhead of work estimated for horizontal fission of
     * containers (could calculate more precisely; only doing
     * horizontal filter fusion precisely for now.)
     */
    static final int HORIZONTAL_CONTAINER_OVERHEAD = 0; //100;
    /**
     * The factor by which a filter's input or output rate should be
     * multiplied to estimate the horizontal fusion overhead.
     */
    static final int HORIZONTAL_FILTER_OVERHEAD_FACTOR = 0; //5;
    /**
     * Whether or not we're sharing configurations in symmetrical
     * splitjoins.  Sharing doesn't work with 2-D partitioning, but
     * keeping as a variable for documentation purposes in case we
     * want to revert or experiment.
     */
    private static final boolean SHARING_CONFIGS = false;

    /**
     * Map from stream structures to DPConfig's.
     */
    private HashMap configMap;
    /**
     * The set of splitjoins whose children are structurally
     * equivalent (and have equal amounts of work.)
     */
    private HashSet uniformSJ;

    public DynamicProgPartitioner(SIRStream str, WorkEstimate work, int numTiles) {
	super(str, work, numTiles);
	this.configMap = new HashMap();
	this.uniformSJ = new HashSet();
    }

    /**
     * Collect scaling statistics for all partitions 1...<maxTiles>.
     */
    public static void saveScalingStatistics(SIRStream str, WorkEstimate work, int maxTiles) {
	PartitionUtil.setupScalingStatistics();
	for (int i=1; i<maxTiles; i++) {
	    LinkedList partitions = new LinkedList();
	    new DynamicProgPartitioner(str, work, i).calcPartitions(partitions);
	    PartitionUtil.doScalingStatistics(partitions, i);
	}
	PartitionUtil.stopScalingStatistics();
    }
    
    /**
     * This is the toplevel call for doing partitioning.
     */
    public SIRStream toplevel() {
	// debug setup
	long start = System.currentTimeMillis();
	LinkedList partitions = new LinkedList();

	// calculate partitions
	StreamTransform st = calcPartitions(partitions);
	if (KjcOptions.debug) { st.printHierarchy(); }

	// debug output
	PartitionUtil.printTileWork(partitions, numTiles);
	PartitionDot.printPartitionGraph(str, "partitions.dot", PartitionRecord.asMap(partitions));
	Utils.assert(partitions.size()<=numTiles, "Assigned " + partitions.size() + " tiles, but we only have " + numTiles);
	System.out.println("Dynamic programming partitioner took " + 
			   (System.currentTimeMillis()-start)/1000 + " secs to calculate partitions.");

	// perform partitioning transformations
	return st.doTransform(str);
    }

    /**
     * Returns a stream transform that will perform the partitioning
     * for <str>.  <partitions> must be a non-null linked list; it is
     * cleared and then filled with PartitionRecords representing the
     * partitions.
     */
    private StreamTransform calcPartitions(LinkedList partitions) {
	// build stream config
	System.out.println("  Building stream config... ");
	DPConfig topConfig = buildStreamConfig();
	if (DPConfigContainer.aliases>0) {
	    System.out.println("  Aliasing " + DPConfigContainer.aliases + " entries of memo table.");
	}
	// rebuild our work estimate, since we might have introduced
	// identity nodes to make things rectangular
	work = WorkEstimate.getWorkEstimate(str);
	// build up tables.
	System.out.println("  Calculating partition info...");
	int bottleneck = topConfig.get(numTiles, 0);
	int tilesUsed = numTiles;
	// decrease the number of tiles to the fewest that we need for
	// a given bottleneck.  This is in an attempt to decrease
	// synchronization and improve utilization.
	/*
	while (tilesUsed>1 && bottleneck==topConfig.get(tilesUsed-1, 0)) {
	    tilesUsed--;
	}
	if (tilesUsed<numTiles) {
	    System.err.println("Decreased tile usage from " + numTiles + " to " + tilesUsed + " without increasing bottleneck.");
	}
	*/
	
	if (KjcOptions.debug && topConfig instanceof DPConfigContainer) {
	    ((DPConfigContainer)topConfig).printArray();
	}

	// expand config stubs that were shared for symmetry optimizations
	if (SHARING_CONFIGS) {
	    expandSharedConfigs();
	}
	
	// build up list of partitions 
	partitions.clear();
	PartitionRecord curPartition = new PartitionRecord();
	partitions.add(curPartition);

	System.out.println("  Tracing back...");
	StreamTransform result = topConfig.traceback(partitions, curPartition, tilesUsed, 0);

	/* This is no longer the case because of estimating fusion overhead.
	Utils.assert(bottleneck==PartitionUtil.getMaxWork(partitions),
		     "bottleneck=" + bottleneck + " but PartitionUtil.getMaxWork(partitions)=" + PartitionUtil.getMaxWork(partitions));
	*/
	return result;
    }

    /**
     * Builds up mapping from stream to array in this, also
     * identifying the uniform splitjoins.  Returns a config for the
     * toplevel stream.
     */
    private DPConfig buildStreamConfig() {
	RefactorSplitJoin.addDeepRectangularSyncPoints(str);
	StreamItDot.printGraph(str, "dp-partition-input.dot");
	return (DPConfig)str.accept(new ConfigBuilder());
    }

    /**
     * Expands shared config records into separate records so that the
     * traceback can give a full schedule.
     */
    private void expandSharedConfigs() {
	// these are config mappings that were once shared, but we
	// have expanded to be unshared
	HashMap unshared = new HashMap();
	// this is the working set under consideration -- contains
	// some shared and some non-shared items
	HashMap potentialShares = configMap;
	do {
	    // less shared is our first-level fix of shares we find in
	    // potential shares.  They might still have some sharing.
	    HashMap lessShared = new HashMap();
	    for (Iterator it = potentialShares.keySet().iterator(); it.hasNext(); ) {
		SIRStream str = (SIRStream)it.next();
		DPConfig config = (DPConfig)potentialShares.get(str);
		SIRStream configStr = config.getStream();
		// if <config> represents something other than <str>, then
		// replace it with an identical config that wraps <str>
		if (str!=configStr) {
		    unshared.put(str, config.copyWithStream(str));
		    // also need to take care of children of <str>.  Do
		    // this by associating them with the children of
		    // <configStr> and putting them back in the mix; will
		    // iterate 'til nothing is left.
		    if (str instanceof SIRContainer) {
			SIRContainer cont = (SIRContainer)str;
			for (int i=0; i<cont.size(); i++) {
			    lessShared.put(cont.get(i), configMap.get(((SIRContainer)configStr).get(i)));
			}
		    }
		}
	    }
	    potentialShares = lessShared;
	} while (!(potentialShares.isEmpty()));
	// add all from <unshared> to <configMap> (don't do above to
	// avoid modifying what we're iterating over)
	configMap.putAll(unshared);
    }

    public DPConfig getConfig(SIRStream str) {
	return (DPConfig) configMap.get(str);
    }

    /**
     * Returns a DPConfig for <str>
     */
    private DPConfig createConfig(SIRStream str) {
	if (str instanceof SIRFilter) {
	    return new DPConfigFilter((SIRFilter)str, this);
	} else if (str instanceof SIRPipeline) {
	    return new DPConfigPipeline((SIRPipeline)str, this);
	} else if (str instanceof SIRSplitJoin) {
	    return new DPConfigSplitJoin((SIRSplitJoin)str, this);
	} else {
	    Utils.assert(str instanceof SIRFeedbackLoop, "Unexpected stream type: " + str);
	    return new DPConfigFeedbackLoop((SIRFeedbackLoop)str, this);
	}
    }

    class ConfigBuilder extends EmptyAttributeStreamVisitor {

	public Object visitSplitJoin(SIRSplitJoin self,
				     JFieldDeclaration[] fields,
				     JMethodDeclaration[] methods,
				     JMethodDeclaration init,
				     SIRSplitter splitter,
				     SIRJoiner joiner) {
	    // shouldn't have 0-sized SJ's
	    Utils.assert(self.size()!=0, "Didn't expect SJ with no children.");
	    super.visitSplitJoin(self, fields, methods, init, splitter, joiner);
	    // if parent is a pipeline, don't need a config for this splitjoin
	    if (self.getParent() instanceof SIRPipeline) {
		return self;
	    } else {
		return makeConfig(self);
	    }
	}

	public Object visitPipeline(SIRPipeline self,
				    JFieldDeclaration[] fields,
				    JMethodDeclaration[] methods,
				    JMethodDeclaration init) {
	    super.visitPipeline(self, fields, methods, init);
	    return makeConfig(self);
	}

	/* pre-visit a feedbackloop */
	public Object visitFeedbackLoop(SIRFeedbackLoop self,
					JFieldDeclaration[] fields,
					JMethodDeclaration[] methods,
					JMethodDeclaration init,
					JMethodDeclaration initPath) {
	    super.visitFeedbackLoop(self, fields, methods, init, initPath);
	    return makeConfig(self);
	}

	public Object visitFilter(SIRFilter self,
				  JFieldDeclaration[] fields,
				  JMethodDeclaration[] methods,
				  JMethodDeclaration init,
				  JMethodDeclaration work,
				  CType inputType, CType outputType) {
	    super.visitFilter(self, fields, methods, init, work, inputType, outputType);
	    return makeConfig(self);
	}

	private DPConfig makeConfig(SIRStream self) {
	    DPConfig config = createConfig(self);
	    configMap.put(self, config);
	    return config;
	}
    }
}

