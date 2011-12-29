package at.dms.kjc.sir.lowering.partition;

import java.util.Iterator;
import java.util.TreeSet;

import at.dms.kjc.sir.SIRContainer;
import at.dms.kjc.sir.SIRFeedbackLoop;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.fusion.FusePipe;
import at.dms.kjc.sir.lowering.fusion.FuseSplit;
import at.dms.util.Utils;

/**
 * Represents a fusion of children in a stream graph.
 */

public final class FusionTransform extends StreamTransform {
    /**
     * Integers denoting the sorted positions of the partitions in
     * this.  That is, if 'partitions' holds &lt;3, 1, 0, 7&gt; then
     * children 0-1, 2-3, and 4-7 should each be fused (for a total of
     * 3 partitions).  Note that it includes the endpoints.
     */
    private TreeSet<Integer> partitions;

    public FusionTransform() {
        this.partitions = new TreeSet<Integer>();
    }

    /**
     * Perform the transform on <str> and return new stream.
     */
    @Override
	public SIRStream doMyTransform(SIRStream str) {
        // make sure we have a container
        assert (str instanceof SIRContainer): "Expected container as target of fusion, but got: " + str;
        // if we have as many partitions as children, then we're done
        if (((SIRContainer)str).size()==partitions.size()-1) {
            return str;
        }
        // call fusion
        SIRStream result = str;
        PartitionGroup childPart = calcPartitionArray();
        if (str instanceof SIRPipeline) {
            FusePipe.fuse((SIRPipeline)str, childPart);
        } else if (str instanceof SIRSplitJoin) {
            result = FuseSplit.fuse((SIRSplitJoin)str, childPart);
            // if we were supposed to fuse the whole thing, make sure
            // we got something different
            assert (childPart.size()>1 || str!=result): "Failed to fuse splitjoin: " + result;
            // if we got a pipeline back, that means we used old fusion,
            // and we should fuse the pipe again
            if (childPart.size()==1) { 
                if (result instanceof SIRPipeline) {
                    // if the whole thing is a pipeline
                    FusePipe.fuse((SIRPipeline)result);
                }
            } else {
                // if we might have component pipelines
                for (int i=0; i<childPart.size(); i++) {
                    if (childPart.get(i)>1 && ((SIRSplitJoin)result).get(i) instanceof SIRPipeline) {
                        FusePipe.fuse((SIRPipeline)((SIRSplitJoin)result).get(i));
                    }
                }
            }
        } else if (str instanceof SIRFeedbackLoop) {
            Utils.fail("FeedbackLoop fusion not supported");
        } else {
            Utils.fail("Unexpected stream type " + str.getClass() + ": " + str);
        }
        return result;
    }

    /**
     * Add a partition AFTER child 'i' to the list, where 'i' is
     * 1-indexed.  (So to fuse all children, add partitions at 0 and
     * size()).
     */
    public void addPartition(int i) {
        this.partitions.add(new Integer(i));
    }

    /**
     * Transforms this.partitions into an array that is suitable for
     * the fusion passes.
     */
    private PartitionGroup calcPartitionArray() {
        assert (partitions.size()>=2) : "Require >= 2 partitions in fusion.";
        int[] result = new int[partitions.size()-1];
        Iterator<Integer> it = partitions.iterator();
        int last = it.next().intValue();
        for (int i=0; it.hasNext(); i++) {
            int next = it.next().intValue();
            result[i] = next - last;
            last = next;
        }
        return PartitionGroup.createFromArray(result);
    }

    @Override
	public String toString() {
        return "Fusion transform, #" + id + " (Will fuse into " + (partitions.size()-1) + " components)";
    }

}
