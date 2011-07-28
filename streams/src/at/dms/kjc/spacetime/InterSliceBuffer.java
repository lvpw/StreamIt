package at.dms.kjc.spacetime;

import at.dms.util.Utils;
import java.util.Vector;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import at.dms.kjc.*;
import at.dms.kjc.slir.WorkNode;
import at.dms.kjc.slir.InterFilterEdge;
import at.dms.kjc.slir.OutputNode;

/**
 * This class represents a buffer between two traces. The rotating register abstraction 
 * is implemented by have rotating buffers, so we can actually have many physical buffers 
 * for each buffer.  
 * 
 * @author mgordon
 *
 */
public class InterSliceBuffer extends OffChipBuffer {
    // the edge
    protected InterFilterEdge edge;
   
    /** 
     * A map of StreamingDrams to the number of InterTraceBuffers
     * mapped to it.  StreamingDram->Integer
     */
    protected static HashMap<StreamingDram, Integer> dramsToBuffers;
    
    
    protected InterSliceBuffer(InterFilterEdge edge) {
        super(edge.getSrc(), edge.getDest());
        this.edge = edge;
        calculateSize();
    }

    public static InterSliceBuffer getBuffer(InterFilterEdge edge) {
        if (!bufferStore.containsKey(edge)) {
            System.out.println("Creating Inter Buffer from " + edge.getSrc() + " to " + edge.getDest());
            bufferStore.put(edge, new InterSliceBuffer(edge));
        }
        return (InterSliceBuffer) bufferStore.get(edge);
    }

   
    /**
     * @return True of this buffer is not used because the output intrattracebuffer
     * of the source trace performs its function.
     */
    public boolean redundant() {
        return unnecessary((OutputNode) theEdge.getSrc());
    }

    public OffChipBuffer getNonRedundant() {
        if (redundant()) {
            return IntraSliceBuffer.getBuffer(
                                              (WorkNode) theEdge.getSrc().getPrevious(),
                                              (OutputNode) theEdge.getSrc()).getNonRedundant();
        }
        return this;
    }

    protected void calculateSize() {
        // max of the buffer size in the various stages...
        int maxItems = Math.max(Util.initBufferSize(edge), Util.steadyBufferSize(edge));
        
        sizeSteady = (Address.ZERO.add(maxItems)).add32Byte(0);
    }

    public InterFilterEdge getEdge() {
        return edge;
    }

    /**
     * @param dram
     * @return The number of intertracebuffer's mapped to <pre>dram</pre>.
     * Used because each dram can at handle at most 
     * StreamingDram.STREAMING_QUEUE_SIZE number of reads and writes.
     */
    public int getNumInterTraceBuffers(StreamingDram dram) {
        assert dramsToBuffers.containsKey(dram);
        return dramsToBuffers.get(dram).intValue();
    }
    
}
