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

    public int get(int tileLimit, int nextToJoiner) {
	int workCount = partitioner.getWorkEstimate().getWork(filter);
	// return decreased work if we're fissable
	if (tilesForFission(tileLimit, nextToJoiner)>1 && isFissable) {
	    return workCount / tilesForFission(tileLimit, nextToJoiner) + DynamicProgPartitioner.FISSION_OVERHEAD;
	} else {
	    return workCount;
	}
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
	Utils.assert(str instanceof SIRFilter);
	this.filter = (SIRFilter)str;
    }

    /**
     * Add this to the map and return.
     */
    public StreamTransform traceback(LinkedList partitions, PartitionRecord curPartition, int tileLimit, int nextToJoiner) {
	// do fission if we can
	int tff = tilesForFission(tileLimit, nextToJoiner);
	if (tff>1 && isFissable) {
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
	    return new FissionTransform(tilesForFission(tileLimit, nextToJoiner));
	} else {
	    curPartition.add(filter, partitioner.getWorkEstimate().getWork(filter));
	    return new IdentityTransform();
	}
    }
}
