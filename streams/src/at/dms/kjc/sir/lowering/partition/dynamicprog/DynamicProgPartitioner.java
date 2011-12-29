package at.dms.kjc.sir.lowering.partition.dynamicprog;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import at.dms.kjc.CType;
import at.dms.kjc.JFieldDeclaration;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFeedbackLoopIter;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.iterator.SIRIterator;
import at.dms.kjc.sir.EmptyAttributeStreamVisitor;
import at.dms.kjc.sir.EmptyStreamVisitor;
import at.dms.kjc.sir.SIRContainer;
import at.dms.kjc.sir.SIRFeedbackLoop;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRIdentity;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.SIRSplitter;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.fusion.FuseAll;
import at.dms.kjc.sir.lowering.fusion.Lifter;
import at.dms.kjc.sir.lowering.partition.ListPartitioner;
import at.dms.kjc.sir.lowering.partition.PartitionDot;
import at.dms.kjc.sir.lowering.partition.PartitionRecord;
import at.dms.kjc.sir.lowering.partition.PartitionUtil;
import at.dms.kjc.sir.lowering.partition.Partitioner;
import at.dms.kjc.sir.lowering.partition.RefactorSplitJoin;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;

public class DynamicProgPartitioner extends ListPartitioner {
    /**
     * The overhead of work estimated for each fissed node.
     */
    static final int FISSION_OVERHEAD = 10;
    /**
     * The maximum amount to fiss (before network takes over).
     */
    static final int MAX_FISSION_FACTOR = 64;
    /**
     * The factor by which a filter's input or output rate should be
     * multiplied to estimate the horizontal fusion overhead.
     */
    static final int HORIZONTAL_FILTER_OVERHEAD_FACTOR = 30;
    /**
     * When estimating overhead of horizontal fusion, this is added if
     * the top or bottommost elements of child pipelines are also
     * containers, instead of filters.
     */
    static final int HORIZONTAL_CONTAINER_OVERHEAD = 30;
    /**
     * Threshold value of instruction code size before cost goes up
     * dramatically.
     */
    static final int ICODE_THRESHOLD = 16000;
    //static final int ICODE_THRESHOLD = 100;
    /**
     * Whether or not we're sharing configurations in symmetrical
     * splitjoins.  Sharing doesn't work with 2-D partitioning, but
     * keeping as a variable for documentation purposes in case we
     * want to revert or experiment.
     */
    private static final boolean SHARING_CONFIGS = false;
    /**
     * Whether or not we should collapse all identity nodes before
     * starting to partition.  This would be only for the sake of
     * getting a better work estimate for the collapsed nodes, as they
     * are likely to be collapsed anyway.
     */
    private static final boolean COLLAPSE_IDENTITIES = false;
    /**
     * Whether or not we're collapsing extra horizontal nodes when
     * they do not exceed the bottleneck.  This happens during
     * traceback.
     */
    static final boolean pruningOnTraceback = true;
    /**
     * Whether or not we collapse nodes as much as possible while
     * maintaining the optimal bottleneck.  Extra collapsing can
     * reduce synchronization overhead, but could also introduce a new
     * bottleneck if the work estimate was not accurate.
     *
     * Compared to pruningOnTraceback, this has the effect of
     * collapsing extra pipelines rather than just splitjoins.
     */
    private static final boolean MINIMIZE_TILE_USAGE = false;
    /**
     * Whether or not we're transforming the stream on traceback.  If
     * not, then we're just gathering the partition info for dot
     * output.
     */
    static boolean transformOnTraceback;
    /**
     * Bottleneck in current run.
     */
    private long bottleneck;
    
    /**
     * Map from stream structures to DPConfig's.
     */
    private HashMap<SIRStream, DPConfig> configMap;
    /**
     * The set of splitjoins whose children are structurally
     * equivalent (and have equal amounts of work.)
     */
    private HashSet uniformSJ;

    /**
     * Whether or not joiners need a tile.
     */
    private boolean joinersNeedTiles;
    /**
     * Whether or not there is a limit on instruction code size.
     */
    private boolean limitICode;
    /**
     * Whether we must fuse down to numTiles.  If this is false,
     * indicates that it is acceptable to return more tiles/threads if
     * it improves load balancing (e.g., for cluster backend.)
     */
    private boolean strict;
    /**
     * Set of filters that should not be fused horizontally (e.g.,
     * because they have a dynamic rate).
     */
    private HashSet noHorizFuse;
    
    /**
     * <pre>joinersNeedTiles</pre> indicates whether or not joiners in the graph
     * require a tile in the allocation.  If false, joiners will be
     * counted as free.  If true, a tile will be allocated to each
     * joiner, though adjacent joiners will be considered as collapsed
     * into one.
     *
     * <pre>limitICode</pre> indicates whether or not we are considering an
     * instruction code size limit, and factoring that into the work
     * function.
     *
     * <pre>strict</pre> indicates if we are absolutely required to
     * produce at most <numTiles> filters.  If it is false, then we
     * might produce more filters if the load balancing improves
     * (e.g., on a multicore, might benefit from more threads if
     * additional threads breakup the bottleneck.)
     */
    public DynamicProgPartitioner(SIRStream str, WorkEstimate work, int numTiles, boolean joinersNeedTiles, boolean limitICode, boolean strict, HashSet noHorizFuse) {
        super(str, work, numTiles);
        this.joinersNeedTiles = joinersNeedTiles;
        this.limitICode = limitICode;
        this.strict = strict;
        this.configMap = new HashMap<SIRStream, DPConfig>();
        this.uniformSJ = new HashSet();
        this.noHorizFuse = noHorizFuse;
    }
    /**
     * As above, without <b>noHorizFuse</b>.
     */
    public DynamicProgPartitioner(SIRStream str, WorkEstimate work, int numTiles, boolean joinersNeedTiles, boolean limitICode, boolean strict) {
        this(str, work, numTiles, joinersNeedTiles, limitICode, strict, new HashSet());
    }
    

    /**
     * Collect scaling statistics for all partitions 1...<pre>maxTiles</pre>,
     * assuming joiners need tiles.
     */
    public static void saveScalingStatistics(SIRStream str, WorkEstimate work, int maxTiles) {
        PartitionUtil.setupScalingStatistics();
        for (int i=1; i<maxTiles; i++) {
            LinkedList<PartitionRecord> partitions = new LinkedList<PartitionRecord>();
            new DynamicProgPartitioner(str, work, i, true, false, true).calcPartitions(partitions, false);
            PartitionUtil.doScalingStatistics(partitions, i);
        }
        PartitionUtil.stopScalingStatistics();
    }
    
    /**
     * This is the toplevel call for doing partitioning.  Returns the
     * partitioned stream.
     */
    public SIRStream toplevel() {
        LinkedList<PartitionRecord> partitions = new LinkedList<PartitionRecord>();
        return calcPartitions(partitions, true);
    }

    /**
     * The toplevel call for calculating partitions without fusing
     * anything in the stream.  Note that the stream might be
     * re-arranged if the partitioner couldn't undo the
     * canonicalization that it used; that's why a new stream is
     * returned.  The hashmap that is passed in is cleared and filled
     * with a mapping from SIROperator to String denoting list of
     * partition numbers that a given SIROperator is assigned to.
     * <br/> implicit parameter str from class constructor.
     * @param partitionMap The partition map to fill in.
     * @return the (possibly munged) stream
     */
    public SIRStream calcPartitions(Map<SIROperator, Integer> partitionMap) {
        LinkedList<PartitionRecord> partitions = new LinkedList<PartitionRecord>();
        SIRStream result = calcPartitions(partitions, false);

        partitionMap.clear();
        partitionMap.putAll(PartitionRecord.asIntegerMap(partitions));

        return result;
    }

    /**
     * Returns a stream transform that will perform the partitioning
     * for <pre>str</pre>.  <pre>partitions</pre> must be a non-null linked list; it is
     * cleared and then filled with PartitionRecords representing the
     * partitions.  If <pre>doTransform</pre> is true, then the result of
     * partitioning the stream is returned; otherwise the stream is
     * left alone and only <pre>partitions</pre> are filled up.
     */
    private SIRStream calcPartitions(LinkedList<PartitionRecord> partitions, boolean doTransform) {
        if (COLLAPSE_IDENTITIES) {
            // to deal with cases like matmul, fuse all identity's in the
            // stream
            str = fuseIdentities(str);
            // rebuild work estimate since we introduced new nodes
            work = WorkEstimate.getWorkEstimate(str);
        }

        // if we can expand number of tiles past <numTiles>, then
        // allocate space to calculate up to any number of tiles that
        // could be needed.  We will contract this back below.
        int origNumTiles = numTiles;
        if (!strict) {
            // conservative over-estimate of the maximum number of
            // tiles that could possibly lead to improved load
            // balancing for this program.  In the extreme case, we
            // would fiss <numTiles> filters, introducing a new filter
            // and possibly a new joiner for each one)
            int beneficialTiles = Partitioner.countTilesNeeded(str, joinersNeedTiles) + (2 + (joinersNeedTiles ? 1 : 0)) * numTiles;
            // explore thread counts up to the max of the number
            // requested and the number that might benefit us
            if (beneficialTiles > numTiles) {
                numTiles = beneficialTiles;
            }
        }

        // build stream config
        System.out.println("  Building stream config... ");
        DPConfig topConfig = buildStreamConfig();
        if (DPConfigContainer.aliases>0) {
            System.out.println("  Aliasing " + DPConfigContainer.aliases + " entries of memo table.");
        }
        // rebuild our work estimate, since we might have introduced
        // identity nodes to make things rectangular
        work = WorkEstimate.getWorkEstimate(str);

        // if we're limiting icode, start with 1 filter and work our
        // way up to as many filters are needed.
        if (limitICode) { numTiles = 0; }
        DPCost cost;
        do {
            if (limitICode) { numTiles++; }
            System.err.println("Trying " + numTiles + " tiles.");

            // build up tables.
            System.out.println("  Calculating partition info...");
            cost = topConfig.get(numTiles, 0);
            bottleneck = cost.getMaxCost();
            // Don't print this because it could be misleading -- the bottleneck in the
            // original graph has a different scaling factor than the bottleneck in the
            // partitioned graph, because fission could increase the steady state size.
            // The user gets equivalent information from the final work estimates.
            // System.err.println("  Partitioner thinks bottleneck is " + bottleneck);
            if (limitICode) {
                System.err.println("  Max iCode size: " + cost.getICodeSize());
            }
        } while (limitICode && cost.getICodeSize()>ICODE_THRESHOLD);
    
        int tilesUsed;
        if (!strict) {
            // if we have option of increasing number of tiles
            // (threads) to improve load balancing, then do that
            tilesUsed = increaseNumberOfTiles(origNumTiles, topConfig);
        } else {
            tilesUsed = numTiles;
            // decrease the number of tiles to the fewest that we need for
            // a given bottleneck.  This is in an attempt to decrease
            // synchronization and improve utilization.
            if (MINIMIZE_TILE_USAGE) {
                while (tilesUsed>1 && bottleneck==topConfig.get(tilesUsed-1, 0).getMaxCost()) {
                    tilesUsed--;
                }
                if (tilesUsed<numTiles) {
                    System.err.println("Decreased tile usage from " + numTiles + " to " + tilesUsed + " without increasing bottleneck.");
                }
            }
        }
        // reset bottleneck before traceback (e.g., fission checks it)
        bottleneck = topConfig.get(tilesUsed, 0).getMaxCost();

        if (KjcOptions.debug && topConfig instanceof DPConfigContainer) {
            ((DPConfigContainer)topConfig).printArray();
        }

        // expand config stubs that were shared for symmetry optimizations
        if (SHARING_CONFIGS) {
            expandSharedConfigs();
        }
    
        System.out.println("  Tracing back...");

        // build up list of partitions 
        partitions.clear();
        PartitionRecord curPartition = new PartitionRecord();
        partitions.add(curPartition);

        transformOnTraceback = doTransform;
        SIRStream result = topConfig.traceback(partitions, curPartition, tilesUsed, 0, str);

        // remove unnecessary identities
        Lifter.eliminateIdentities(result);

        // reclaim children here, since they might've been shuffled
        // around in the config process
        if (result instanceof SIRContainer) {
            ((SIRContainer)result).reclaimChildren();
        }
        // also set parent to null, since this should be toplevel stream
        result.setParent(null);

        // can only print if we didn't transform
        if (!doTransform) {
            Lifter.lift(result);
            PartitionDot.printPartitionGraph(result, "partitions.dot", PartitionRecord.asStringMap(partitions));
        }
    
        return result;
    }

    private SIRStream fuseIdentities(SIRStream str) {
        final HashSet<SIRStream> allIdentities = new HashSet<SIRStream>();
        IterFactory.createFactory().createIter(str).accept(new EmptyStreamVisitor() {
                public void postVisitStream(SIRStream self,
                                            SIRIterator iter) {
                    if (self instanceof SIRFilter) {
                        // for filters, add if an identity
                        if (self instanceof SIRIdentity) {
                            allIdentities.add(self);
                        }
                    } else if (self instanceof SIRContainer) {
                        // for containers, add if children are all identities
                        boolean all = true;
                        SIRContainer cont = (SIRContainer)self;
                        for (int i=0; i<cont.size(); i++) {
                            if (!allIdentities.contains(cont.get(i))) {
                                all = false;
                                break;
                            }
                        }
                        if (all) {
                            allIdentities.add(self);
                        }
                    }
                }
            });
        // now fuse top-most nodes that are in all-identities
        return fuseIdentitiesHelper(str, allIdentities);
    }



    /**
     * Increase # of threads as long as N'th biggest load is
     * increasing, and choose the MIN number of threads that yields
     * this watermark.  N represents the original number of threads
     * requested by the backend.  Return the total number of threads
     * that should be used by the program.
     */
    private int increaseNumberOfTiles(int N, DPConfig topConfig) {
        // current estimate of result
        int bestTiles = N;
        // current minimum load in top N partitions
        long bestLoad = Long.MIN_VALUE;

        for (int tiles=N; tiles<numTiles; tiles++) {
            // build up list of partitions 
            LinkedList<PartitionRecord> partitions = new LinkedList<PartitionRecord>();
            PartitionRecord curPartition = new PartitionRecord();
            partitions.add(curPartition);
            
            transformOnTraceback = false;
            // first make sure we've calculated this case
            topConfig.get(tiles, 0);
            // then traceback
            topConfig.traceback(partitions, curPartition, tiles, 0, str);

            // if we don't even have N partitions, keep looking
            if (partitions.size()<N) {
                continue;
            }

            // sort the partitions by work
            Collections.sort(partitions,
                             new Comparator<PartitionRecord>() {
                public int compare(PartitionRecord p1, PartitionRecord p2) {
                    if (p1.getWork() < p2.getWork()) return -1;
                    if (p1.getWork() > p2.getWork()) return 1;
                    return 0;
                }
            });

            // get N'th biggest load
            long nthLoad = partitions.get(partitions.size()-N).getWork();
            /* debug print
            System.err.println("\nWith " + tiles + " threads, bottleneck = " + nthLoad);
            for (int i=0; i<partitions.size(); i++) {
                PartitionRecord pr = partitions.get(partitions.size()-i-1);
                System.err.println("  work[" + (partitions.size()-i-1) + "] = " + pr.getWork());
                for (int j=0; j<pr.size(); j++) {
                    System.err.println("    " + pr.get(j));
                }
            }
            */

            if (nthLoad > bestLoad) {
                // if load is increasing, remember it
                bestLoad = nthLoad;
                bestTiles = tiles;
            } else if (nthLoad < bestLoad) {
                // if load is decreasing, we're done
                break;
            }
        }
        return bestTiles;
    }

    private SIRStream fuseIdentitiesHelper(SIRStream str, HashSet<SIRStream> allIdentities) {
        if (str instanceof SIRContainer) {
            // fuse it if it is all identities
            if (allIdentities.contains(str)) {
                return fuseall(str);
            } else {
                // otherwise recurse
                SIRContainer cont = (SIRContainer)str;
                for (int i=0; i<cont.size(); i++) {
                    cont.set(i, fuseIdentitiesHelper(cont.get(i), allIdentities));
                }
                return cont;
            }
        } else {
            // if not a container, just return the node
            return str;
        }
    }

    /**
     * Builds up mapping from stream to array in this, also
     * identifying the uniform splitjoins.  Returns a config for the
     * toplevel stream.
     */
    private DPConfig buildStreamConfig() {
        // make canonical representation
        str = RefactorSplitJoin.addDeepRectangularSyncPoints(str);
        if (str instanceof SIRContainer) {
            // fix any sloppiness about parent pointers from addDeepRectangularSyncPoints
            ((SIRContainer)str).reclaimChildren();
        }
        // dump to graph
        //StreamItDot.printGraph(str, "dp-partition-input.dot");
        return (DPConfig)str.accept(new ConfigBuilder());
    }

    /**
     * Expands shared config records into separate records so that the
     * traceback can give a full schedule.
     */
    private void expandSharedConfigs() {
        // these are config mappings that were once shared, but we
        // have expanded to be unshared
        HashMap<SIRStream, DPConfig> unshared = new HashMap<SIRStream, DPConfig>();
        // this is the working set under consideration -- contains
        // some shared and some non-shared items
        HashMap<SIRStream, DPConfig> potentialShares = configMap;
        do {
            // less shared is our first-level fix of shares we find in
            // potential shares.  They might still have some sharing.
            HashMap<SIRStream, DPConfig> lessShared = new HashMap<SIRStream, DPConfig>();
            for (Iterator<SIRStream> it = potentialShares.keySet().iterator(); it.hasNext(); ) {
                SIRStream str = it.next();
                DPConfig config = potentialShares.get(str);
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

    /**
     * Whether or not joiners in this need a tile.
     */
    public boolean joinersNeedTiles() {
        return this.joinersNeedTiles;
    }

    /**
     * Whether or not to limit icode of fused parts.
     */
    public boolean limitICode() {
        return this.limitICode;
    }

    /**
     * Set of filters that should not be fused horizontally.
     */
    public HashSet getNoHorizFuse() {
        return this.noHorizFuse;
    }
    
    public long getBottleneck() {
        return this.bottleneck;
    }

    public DPConfig getConfig(SIRStream str) {
        return configMap.get(str);
    }

    /**
     * Returns a DPConfig for <pre>str</pre>
     */
    private DPConfig createConfig(SIRStream str) {
        if (str instanceof SIRFilter) {
            return new DPConfigFilter((SIRFilter)str, this);
        } else if (str instanceof SIRPipeline) {
            return new DPConfigPipeline((SIRPipeline)str, this);
        } else if (str instanceof SIRSplitJoin) {
            return new DPConfigSplitJoin((SIRSplitJoin)str, this);
        } else {
            assert str instanceof SIRFeedbackLoop:
                "Unexpected stream type: " + str;
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
            assert self.size()!=0: "Didn't expect SJ with no children.";
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
    
    /** 
     * Check limitations on fusion
     * @param str                               candidate for fusion
     * @param unfusableDueToSSGConstruction     if [0] == true, can not call fusaAll because of unfusable filters at dynamic rate boundaries.
     * @param unfusableDueToFeedback            if [0] == true, can not fuse strictly because of presence of feedback loop
     */
    private void fusionChecker (SIRStream str, final boolean[] unfusableDueToSSGConstruction, final boolean[] unfusableDueToFeedback) {
        SIRIterator streamiter = IterFactory.createFactory().createIter(str);
        streamiter.
        accept(new EmptyStreamVisitor() {
            @Override 
            public void preVisitFeedbackLoop(SIRFeedbackLoop loop,
                    SIRFeedbackLoopIter iter) {
                super.preVisitFeedbackLoop(loop, iter);
                unfusableDueToFeedback[0] = true;
            }
            
            @Override
            public void visitFilter(SIRFilter filter,
                    SIRFilterIter iter) {
                if (getNoHorizFuse().contains(filter)) {
                    SIRStream parent = filter.getParent();
                    SIRSplitJoin closestSplitJoin = null;
                    while (parent != null) {
                        if (parent instanceof SIRSplitJoin) {
                            closestSplitJoin = (SIRSplitJoin)parent;
                            break;
                        }
                        parent = parent.getParent();
                    }
                    if (closestSplitJoin != null 
                            && ((closestSplitJoin.getSplitter().getSumOfWeights() == 0
                                    && closestSplitJoin.getSplitter().getWays() > 1)
                                || (closestSplitJoin.getJoiner().getSumOfWeights() == 0
                                    &&  closestSplitJoin.getJoiner().getWays() > 1))) {
                        // this is the actual criterion that a filter in a SSG is not fusable.
                        // should be in at.dms.kjc.flatgraph.something...
                        unfusableDueToSSGConstruction[0] = true;
                    }
                }
            }
        });
    }

    public SIRStream fuseall (SIRStream str) {
        final boolean unfusableDueToSSGConstruction[] = {false};
        final boolean unfusableDueToFeedback[] = {false};
        fusionChecker(str,unfusableDueToSSGConstruction,unfusableDueToFeedback);

        // for now, don't even try fusing around unfusable filters.
        if (unfusableDueToSSGConstruction[0]) {
            System.err.println(this.getClass().getName() + ": Unfusable stream " + str + " due to SSG construction");
            return str;
        } else {
            boolean strictFusion = ! unfusableDueToFeedback[0];
            // Fuseall wants a SIRContainer, so wrap in case we have a SIRFilter.
            SIRPipeline wrapper = SIRContainer.makeWrapper(str);
            wrapper.reclaimChildren();
            // Fuseall returns a pipeline, but it may have only one element.
            SIRPipeline wrapper2 = FuseAll.fuse(str, strictFusion);
            Lifter.eliminatePipe(wrapper2); // fusion's intorduced pipeline.
            Lifter.lift(wrapper);  // clean up graph structure (if any left)
            if (wrapper.size() != 1 || ! (wrapper.get(0) instanceof SIRFilter)) {
                // didn't actually fuse all -- print warning.
                System.err.println(this.getClass().getName() + ": Unfusable stream " + str + " due to feedbackloop");
            }
            if (wrapper.size()==1) {
                // common case: will have fused down to 1 filter unwrap and return.
                Lifter.eliminatePipe(wrapper);
                return wrapper.get(0);
            } else {
                return wrapper;
            }
        }
    }
}

