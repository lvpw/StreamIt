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

class DPConfigFilter extends DPConfig {
    /**
     * The filter corresponding to this.
     */
    private SIRFilter filter;
    /**
     * Whether or not <filter> is stateless.
     */
    private boolean isFissable;
    
    public DPConfigFilter(SIRFilter filter, DynamicProgPartitioner partitioner) {
        super(partitioner);
        this.filter = filter;
        this.isFissable = StatelessDuplicate.isFissable(filter);
    }

    public DPCost get(int tileLimit, int nextToJoiner) {
        // consider ourselves to be next to a joiner if we don't care about joiners
        if (!partitioner.joinersNeedTiles()) {
            nextToJoiner = 1;
        }

        int workCount = partitioner.getWorkEstimate().getWork(filter);
        // return decreased work if we're fissable
        int cost;
        if (tilesForFission(tileLimit, nextToJoiner)>1 && isFissable 
            // don't currently support fission on cluster backend
            // because it requires mix of transforming and no
            // transforming -- wouldn't be too hard to add.
            && KjcOptions.cluster==-1) {
            cost = workCount / tilesForFission(tileLimit, nextToJoiner) + DynamicProgPartitioner.FISSION_OVERHEAD;
        } else {
            cost = workCount;
        }
        return new DPCost(cost, cost, getICodeSize());
    }

    /**
     * Returns the estimate of instruction code size that we should
     * use for this filter.
     */
    private int getICodeSize() {
        int iCodeSize;
        if (partitioner.limitICode()) {
            iCodeSize = partitioner.getWorkEstimate().getICodeSize(filter);
            // if estimate is above threshold, count it as being
            // exactly at threshold, so that we don't propagate up
            // decisions that are based on an exceeded icode size.
            // That is, we only want to constrain the filter NOT to
            // fuse with anyone else.  We don't want all containers of
            // this filter to have a high cost just because this guy
            // exceeded the icode limit.
            if (iCodeSize>partitioner.ICODE_THRESHOLD) {
                iCodeSize = partitioner.ICODE_THRESHOLD;
            }
        } else {
            iCodeSize = 0;
        }
        return iCodeSize;
    }

    // see how many tiles we can devote to fissed filters;
    // depends on if we need a separate tile for a joiner.
    private int tilesForFission(int tileLimit, int nextToJoiner) {
        return Math.min(DynamicProgPartitioner.MAX_FISSION_FACTOR, nextToJoiner==1 ? tileLimit : tileLimit - 1);
    }

    public SIRStream getStream() {
        return filter;
    }

    /**
     * Requires <str> is a filter.
     */
    protected void setStream(SIRStream str) {
        assert str instanceof SIRFilter;
        this.filter = (SIRFilter)str;
    }

    /**
     * Add this to the map and return.
     */
    public SIRStream traceback(LinkedList partitions, PartitionRecord curPartition, int tileLimit, int nextToJoiner, SIRStream str) {
        if (DynamicProgPartitioner.pruningOnTraceback && partitioner.getWorkEstimate().getWork(filter)<partitioner.getBottleneck()) {
            // don't fiss if doesn't exceed bottleneck
            curPartition.add(filter, partitioner.getWorkEstimate().getWork(filter));
            return filter;
        }

        // consider ourselves to be next to a joiner if we don't care about joiners
        if (!partitioner.joinersNeedTiles()) {
            nextToJoiner = 1;
        }

        // do fission if we can
        int tff = tilesForFission(tileLimit, nextToJoiner);
        if (tff>1 && isFissable
            // don't currently support fission on cluster backend
            // because it requires mix of transforming and no
            // transforming -- wouldn't be too hard to add.
            && KjcOptions.cluster==-1) {
            // record fission tiles
            for (int i=0; i<tff; i++) {
                curPartition.add(filter, 
                                 partitioner.getWorkEstimate().getWork(filter) / tff + 
                                 DynamicProgPartitioner.FISSION_OVERHEAD);
                // only draw new partitions BETWEEN the elements
                // of the fissed splitjoin -- not at the end
                if (i!=tff-1) {
                    partitions.add(curPartition);
                    curPartition = new PartitionRecord();
                }
            }
            // record joiner tile if we need one
            if (tff<tileLimit) {
                curPartition = new PartitionRecord();
                // just record the filter as the contents since
                // it's responsible for this joiner node
                curPartition.add(SIRJoiner.createUniformRR(filter.getParent(), new JIntLiteral(1)), 0);
                partitions.add(curPartition);
            }
            if (!DynamicProgPartitioner.transformOnTraceback) {
                return filter;
            } else {
                return StatelessDuplicate.doit(filter, tilesForFission(tileLimit, nextToJoiner));
            }
        } else {
            curPartition.add(filter, partitioner.getWorkEstimate().getWork(filter));
            return filter;
        }
    }
}
