package at.dms.kjc.sir.lowering.partition.cache;

import java.util.LinkedList;

import at.dms.kjc.cluster.DataEstimate;
import at.dms.kjc.sir.SIRFileReader;
import at.dms.kjc.sir.SIRFileWriter;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.partition.PartitionRecord;

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
        long work_estimate = partitioner.getWorkEstimate().getWork(filter);
        int code_size = getICodeSize();
        int data_size = DataEstimate.estimateDWS(filter);
        int input = DataEstimate.getTypeSize(filter.getInputType());
        int output = DataEstimate.getTypeSize(filter.getInputType());

	int penalty = 0;

	if (filter instanceof SIRFileReader ||
	    filter instanceof SIRFileWriter) {
	    penalty += 100;
	}
	
        fusion_info = new FusionInfo(work_estimate+penalty, work_estimate, code_size, data_size, filter.getPopInt(), filter.getPeekInt(), filter.getPushInt(), input, output);  
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
    public SIRStream traceback(LinkedList<PartitionRecord> partitions, PartitionRecord curPartition, int tileLimit, SIRStream str) {
        curPartition.add(filter, partitioner.getWorkEstimate().getWork(filter));
        return filter;
    }

    public void printArray(int numTiles) {
        System.err.println("Filter cost");
    }

}
