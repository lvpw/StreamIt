package at.dms.kjc.sir.lowering.partition.cache;

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

import at.dms.kjc.cluster.DataEstimate;
import at.dms.kjc.cluster.CodeEstimate;

class CConfigFilter extends CConfig {
    /**
     * The filter corresponding to this.
     */

    private FusionInfo fusion_info;
    private SIRFilter filter;

    public CConfigFilter(SIRFilter filter, CachePartitioner partitioner) {
	super(partitioner);
	this.filter = filter;
	
	//estimate work, code size and data size
	int work_estimate = partitioner.getWorkEstimate().getWork(filter);
	int code_size = getICodeSize();
	int data_size = DataEstimate.estimateDWS(filter);
	int input = DataEstimate.getTypeSize(filter.getInputType());
	int output = DataEstimate.getTypeSize(filter.getInputType());
	fusion_info = new FusionInfo(work_estimate, work_estimate, code_size, data_size, filter.getPopInt(), filter.getPeekInt(), filter.getPushInt(), input, output);	
    }

    public boolean getPeek() {
	return filter.getPeekInt() > filter.getPopInt();
    }


    public int numberOfTiles() {
	return 1;
    }

    public FusionInfo getFusionInfo() {
	return fusion_info;
    }

    public CCost get(int tileLimit) {
	return getFusionInfo().getCost();
    }

    /**
     * Returns the estimate of instruction code size that we should
     * use for this filter.
     */
    private int getICodeSize() {
	int iCodeSize;
	iCodeSize = partitioner.getWorkEstimate().getICodeSize(filter);
	// if estimate is above threshold, count it as being
	// exactly at threshold, so that we don't propagate up
	// decisions that are based on an exceeded icode size.
	// That is, we only want to constrain the filter NOT to
	// fuse with anyone else.  We don't want all containers of
	// this filter to have a high cost just because this guy
	// exceeded the icode limit.
	if (iCodeSize>partitioner.getCodeCacheSize()) {
	    iCodeSize = partitioner.getCodeCacheSize();
	}
	return iCodeSize;
    }

    public SIRStream getStream() {
	return filter;
    }    


    /**
     * Add this to the map and return.
     */
    public SIRStream traceback(LinkedList partitions, PartitionRecord curPartition, int tileLimit, SIRStream str) {
	curPartition.add(filter, partitioner.getWorkEstimate().getWork(filter));
	return filter;
    }

    public void printArray(int numTiles) {
	System.err.println("Filter cost");
    }

}
