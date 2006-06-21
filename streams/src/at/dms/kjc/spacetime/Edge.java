package at.dms.kjc.spacetime;

import at.dms.kjc.*;

/**
 *  This class represents an edge in the partitioned stream graph between slices (traces).
 *  But it actually connectes <pre>OutputTraceNodes</pre> to <pre>InputTraceNodes</pre>.
 * 
 * @author mgordon
 *
 */
public class Edge {
    private OutputTraceNode src;

    private InputTraceNode dest;

    private CType type;

    public Edge(OutputTraceNode src, InputTraceNode dest) {
        assert src != null : "Source Null!";
        assert dest != null : "Dest Null!";
        this.src = src;
        this.dest = dest;
        type = null;
    }

    public Edge(OutputTraceNode src) {
        this.src = src;
    }

    public Edge(InputTraceNode dest) {
        this.dest = dest;
    }

    public CType getType() {
        if (type != null)
            return type;
        assert src.getPrevFilter().getFilter().getOutputType() == dest
            .getNextFilter().getFilter().getInputType() : "Error calculating type";
        type = src.getPrevFilter().getFilter().getOutputType();
        return type;
    }

    public OutputTraceNode getSrc() {
        return src;
    }

    public InputTraceNode getDest() {
        return dest;
    }

    public void setSrc(OutputTraceNode src) {
        this.src = src;
    }

    public void setDest(InputTraceNode dest) {
        this.dest = dest;
    }

    public String toString() {
        return src + "->" + dest + "(" + hashCode() + ")";
    }

    /**
     * The number of items that traverse this edge in the initialization
     * stage.
     * 
     * @return The number of items that traverse this edge in the initialization
     * stage. 
     */
    public int initItems() {
        int itemsReceived, itemsSent;

        // calculate the items the input trace receives
        FilterInfo next = FilterInfo.getFilterInfo((FilterTraceNode) dest
                                                   .getNext());
        
        itemsSent = (int) ((double) next.initItemsReceived() * dest.ratio(this));
        //System.out.println(next.initItemsReceived()  + " * " + dest.ratio(this));
        
        // calculate the items the output trace sends
        FilterInfo prev = FilterInfo.getFilterInfo((FilterTraceNode) src
                                                   .getPrevious());
        itemsReceived = (int) ((double) prev.initItemsSent() * src.ratio(this));

        /*if (itemsSent != itemsReceived) {
            System.out.println("*** Init: Items received != Items Sent!");
            System.out.println(prev + " -> " + next);
            System.out.println("Mult: " + prev.getMult(true, false) + " " +  
                    next.getMult(true, false));
            System.out.println("Push: " + prev.prePush + " " + prev.push);
            System.out.println("Pop: " + next.pop);
            System.out.println("Init items Sent * Ratio: " + prev.initItemsSent() + " * " +
                    src.ratio(this));
            System.out.println("Items Received: " + next.initItemsReceived(true));
            System.out.println("Ratio received: " + dest.ratio(this));
            
        }*/
        
        // see if they are different
        assert (itemsSent == itemsReceived) : "Calculating init stage: items received != items send on buffer: "
            + src + " (" + itemsSent + ") -> (" + itemsReceived + ") "+ dest;

        return itemsSent;
    }

    /**
     * @return The amount of items (not counting typesize) that flows 
     * over this edge in the steady state.
     */
    public int steadyItems() {
        int itemsReceived, itemsSent;

        // calculate the items the input trace receives
        FilterInfo next = FilterInfo.getFilterInfo(dest.getNextFilter());
        itemsSent = (int) ((next.steadyMult * next.pop) * ((double) dest
                                                           .getWeight(this) / dest.totalWeights()));

        // calculate the items the output trace sends
        FilterInfo prev = FilterInfo.getFilterInfo((FilterTraceNode) src
                                                   .getPrevious());
        itemsReceived = (int) ((prev.steadyMult * prev.push) * ((double) src
                                                                .getWeight(this) / src.totalWeights()));

        assert (itemsSent == itemsReceived) : "Calculating steady state: items received != items send on buffer "
            + itemsSent + " " + itemsReceived + " " + prev + " " + next;

        return itemsSent;
    }

   /**
    * The number of items sent over this link in one call of the link in the prime
    * pump stage, the link might be used many times in the prime pump stage conceptually 
    * using the rotating buffers.
    * 
    * @return ...
    */
    public int primePumpItems() {
        return (int) ((double) FilterInfo.getFilterInfo(src.getPrevFilter())
                      .totalItemsSent(false, true) * src.ratio(this));
    }

}
