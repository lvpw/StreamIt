package at.dms.kjc.sir.lowering.partition.linear;

import java.util.*;
import java.io.*;

import at.dms.kjc.*;
import at.dms.util.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.linear.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.fusion.*;
import at.dms.kjc.sir.lowering.fission.*;
import at.dms.kjc.sir.lowering.partition.*;

public class LinearPartitioner {
    /**
     * Whether or not we're trying to cut splitjoins horizontally and
     * vertically (otherwise we just consider each child pipeline).
     */
    static final boolean ENABLE_TWO_DIMENSIONAL_CUTS = false;

    /**
     * Debugging.
     */
    static final boolean DEBUG = false;

    /**
     * Relative cost of an add to a multiply (for estimating cost of
     * linear nodes).
     */
    public static final int MULTIPLY_OVERHEAD = 3;

    /**
     * Different configurations to look for.
     */
    public static final int COLLAPSE_NONE = 0;
    public static final int COLLAPSE_ANY = 1;
    public static final int COLLAPSE_LINEAR = 2;
    public static final int COLLAPSE_FREQ = 3;

    /**
     * String names for collapse values
     */
    public static final String COLLAPSE_STRING(int collapse) {
	switch(collapse) {
	case COLLAPSE_NONE: return "NONE";
	case COLLAPSE_ANY: return "ANY";
	case COLLAPSE_LINEAR: return "LINEAR";
	case COLLAPSE_FREQ: return "FREQ";
	default: return "UNKNOWN_COLLAPSE_TYPE: " + collapse;
	}
    }

    /**
     * Map from stream structures to LDPConfig's.
     */
    private final HashMap configMap;

    /**
     * The linear analyzer for this.
     */
    private final LinearAnalyzer lfa;
    /**
     * Stream we're partitioning.
     */
    private final SIRStream str;
    /**
     * Execution counts for <str> (given original factoring of containers).
     */
    private HashMap[] counts;
    
    public LinearPartitioner(SIRStream str, LinearAnalyzer lfa) {
	this.str = str;
	this.lfa = lfa;
	this.configMap = new HashMap();
    }

    /**
     * This is the toplevel call for doing partitioning.
     */
    public SIRStream toplevel() {
	// lift before and after
	Lifter.lift(str);

	// debug setup
	long start = System.currentTimeMillis();

	// calculate partitions
	StreamTransform st = calcPartitions();
	if (DEBUG) { st.printHierarchy(); }

	// debug output
	System.err.println("Linear partitioner took " + 
			   (System.currentTimeMillis()-start)/1000 + " secs to calculate partitions.");

	// perform partitioning transformations
	SIRStream result = st.doTransform(str);

	// lift before and after
	Lifter.lift(result);

	return result;
    }

    /**
     * Returns a stream transform that will perform the partitioning
     * for <str>.
     */
    private StreamTransform calcPartitions() {
	// build stream config
	LDPConfig topConfig = buildStreamConfig();
	// set execution counts here, because we want to account for
	// identities that we added to the stream
	this.counts = SIRScheduler.getExecutionCounts(str);
	// build up tables.
	int savings = topConfig.get(COLLAPSE_ANY);
	if (DEBUG) { System.err.println("Expected savings from linear transforms (ops / steady state): " + savings); }
	StreamTransform result = topConfig.traceback(COLLAPSE_ANY);
	return result;
    }

    /**
     * Builds up mapping from stream to array in this. Returns a
     * config for the toplevel stream.
     */
    private LDPConfig buildStreamConfig() {
	return (LDPConfig)str.accept(new ConfigBuilder());
    }

    public LDPConfig getConfig(SIRStream str) {
	return (LDPConfig) configMap.get(str);
    }

    public LinearAnalyzer getLinearAnalyzer() {
	return this.lfa;
    }

    /**
     * Returns the pre-computed execution counts for the stream that
     * we're partitioning.
     */
    public HashMap[] getExecutionCounts() {
	return counts;
    }

    /**
     * Returns a LDPConfig for <str>
     */
    private LDPConfig createConfig(SIRStream str) {
	if (str instanceof SIRFilter) {
	    return new LDPConfigFilter((SIRFilter)str, this);
	} else if (str instanceof SIRPipeline) {
	    return new LDPConfigPipeline((SIRPipeline)str, this);
	} else if (str instanceof SIRSplitJoin) {
	    return new LDPConfigSplitJoin((SIRSplitJoin)str, this);
	} else {
	    Utils.assert(str instanceof SIRFeedbackLoop, "Unexpected stream type: " + str);
	    return new LDPConfigFeedbackLoop((SIRFeedbackLoop)str, this);
	}
    }

    class ConfigBuilder extends EmptyAttributeStreamVisitor {

	public Object visitSplitJoin(SIRSplitJoin self,
				     JFieldDeclaration[] fields,
				     JMethodDeclaration[] methods,
				     JMethodDeclaration init,
				     SIRSplitter splitter,
				     SIRJoiner joiner) {
	    // we require rectangular splitjoins, so if this is not
	    // rectangular, make it so
	    if (!self.isRectangular()) {
		self.makeRectangular();
	    }

	    // shouldn't have 0-sized SJ's
	    Utils.assert(self.size()!=0, "Didn't expect SJ with no children.");
	    for (int i=0; i<self.size(); i++) {
		self.get(i).accept(this);
	    }
	    return makeConfig(self);
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

	private LDPConfig makeConfig(SIRStream self) {
	    LDPConfig config = createConfig(self);
	    configMap.put(self, config);
	    return config;
	}
    }

}

