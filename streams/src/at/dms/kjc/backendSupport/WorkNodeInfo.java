package at.dms.kjc.backendSupport;

//import at.dms.kjc.sir.*;
//import at.dms.util.Utils;
//import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;

import at.dms.kjc.slir.WorkNodeContent;
import at.dms.kjc.slir.WorkNode;
import at.dms.kjc.slir.InputNode;
import at.dms.kjc.slir.InterFilterEdge;
import at.dms.kjc.slir.SchedulingPhase;

/**
 * A class to hold all the various information for a filter.  Be careful, 
 * because this class caches the data of the FilterContent, so if the underlying 
 * FilterContent changes, then you have to reset() the FilterInfos.
 * 
 */
public class WorkNodeInfo {
    /** peeked amount in pre-work of two-stage filter */
    public int prePeek;
    /** popped amount in pre-work in two-stage filter */
    public int prePop;
    /** pushed amount in pre-work in two-stage filter */
    public int prePush;
    /** number of items remaining in buffer between steady states */
    public int copyDown;
    /** during init, the number of items that we receive from upstream, but we don't need
     * during init
     */
    public int remaining;
    public int bottomPeek;
    /** multiplicity of calls to work function from init stage */
    public int initMult;
    /** multiplicity of calls to work function in steady state */
    public int steadyMult;
    /** number of items pushed by a call to the work function */
    public int push;
    /** number of items popped by a call to the work function */
    public int pop;
    /** peek depth of work function, (includes pops?) */
    public int peek;

    public int initItemsNeeded;
    
    private boolean linear;
    /** The FilterNode with this info. */
    public WorkNode sliceNode;
    /** The FilterContent with this info. */
    public WorkNodeContent filter;

    /** HashMap of all the filter infos FilterSliceNode -> FilterInfo */
    private static HashMap<WorkNode, WorkNodeInfo> filterInfos;

    // true if everything is set and we can use this class
    // because once a filter info is created you cannot
    // change the underlying filter...
    private static boolean canuse;

    static {
        filterInfos = new HashMap<WorkNode, WorkNodeInfo>();
        canuse = false;
    }

    /** 
     * Call this when it is safe to use filter infos, meaning all the 
     * information that they collect has been calculated so the information
     * can be presented.
     */
    public static void canUse() {
        canuse = true;
    }

    /**
     * Force the filter info to be recalculated.
     */
    public static void reset() {
        filterInfos = new HashMap<WorkNode, WorkNodeInfo>();
    }
    
    /** Return a stored FilterInfo or calculate a new one as needed. */
    public static WorkNodeInfo getFilterInfo(WorkNode sliceNode) {
        assert canuse;
        if (!filterInfos.containsKey(sliceNode)) {
            WorkNodeInfo info = new WorkNodeInfo(sliceNode);
            filterInfos.put(sliceNode, info);
            return info;
        } else
            return filterInfos.get(sliceNode);
    }

    private WorkNodeInfo(WorkNode sliceNode) {
        filter = sliceNode.getFilter();
        this.sliceNode = sliceNode;
        this.steadyMult = filter.getSteadyMult();
        this.initMult = filter.getInitMult();
        // multiply the primepump number by the
        // steady state multiplicity to get the true
        // primepump multiplicity
        prePeek = 0;
        prePush = 0;
        prePop = 0;
        linear = filter.isLinear();
        if (linear) {
            peek = filter.getArray().length;
            push = 1;
            pop = filter.getPopCount();
            calculateRemaining();
        } else if (sliceNode.isFileInput()) {
            push = 1;
            pop = 0;
            peek = 0;
            calculateRemaining();
        } else if (sliceNode.isFileOutput()) {
            push = 0;
            pop = 1;
            peek = 0;
            calculateRemaining();
        } else {
            push = filter.getPushInt();
            pop = filter.getPopInt();
            peek = filter.getPeekInt();
            if (isTwoStage()) {
                prePeek = filter.getPreworkPeek();
                prePush = filter.getPreworkPush();
                prePop = filter.getPreworkPop();
            }
            calculateRemaining();
        }
    }
    
    /** @return true if two stage filter */
    public boolean isTwoStage() {
        return filter.isTwoStage();
    }

    private int calculateRemaining() {
        // the number of times this filter fires in the initialization
        // schedule
        int initFire = initMult;

        // if this is not a twostage, fake it by adding to initFire,
        // so we always think the preWork is called
        // if (!(filter instanceof SIRTwoStageFilter))
        if (!filter.isTwoStage())
            initFire++;

        // see mgordon's masters thesis for an explanation of this calculation
        if (initFire - 1 > 0) {
            bottomPeek = Math.max(0, peek - (prePeek - prePop));
        } else
            bottomPeek = 0;

        // don't call initItemsReceived() here it
        // may cause an infinite loop because it creates filter infos
        int initItemsRec = 0;
        if (sliceNode.getPrevious().isFilterSlice()) {
            WorkNodeContent filterC = ((WorkNode) sliceNode.getPrevious())
                .getFilter();
            initItemsRec = filterC.getPushInt() * filterC.getInitMult();
            if (filterC.isTwoStage()) {
                initItemsRec -= filterC.getPushInt();
                initItemsRec += filterC.getPreworkPush();
            }
        } else { // previous is an input slice
            InputNode in = (InputNode) sliceNode.getPrevious();

            // add all the upstream filters items that reach this filter
            for (int i = 0; i < in.getWeights(SchedulingPhase.INIT).length; i++) {
                InterFilterEdge incoming = in.getSources(SchedulingPhase.INIT)[i];
                WorkNodeContent filterC = ((WorkNode) incoming.getSrc()
                                         .getPrevious()).getFilter();
                // calculate the init items sent by the upstream filter
                int upstreamInitItems = 0;
                upstreamInitItems = filterC.getPushInt()
                    * filterC.getInitMult();
                if (filterC.isTwoStage()) {
                    upstreamInitItems -= filterC.getPushInt();
                    upstreamInitItems += filterC.getPreworkPush();
                }
                /*
                 * System.out.println("Upstream: " + filterC);
                 System.out.println("push: " + filterC.getPushInt() + 
                 " init Mult: " + filterC.getInitMult());
                 System.out.println("Upstream: " + upstreamInitItems + " Ratio: " + 
                 incoming.getSrc().ratio(incoming));
                 */                
                initItemsRec += (int) (((double) upstreamInitItems) * incoming
                                       .getSrc().ratio(incoming, SchedulingPhase.INIT));
            }
        }
        
        initItemsNeeded = (prePeek + bottomPeek + Math.max((initFire - 2), 0) * pop);
        assert initItemsRec >= initItemsNeeded : initItemsRec + " " + initItemsNeeded;
        
        int initItemsPopped = (prePop + ((initFire - 1) * pop));
        
        remaining = initItemsRec
            - initItemsNeeded;
        //the number of items that remain on the input buffer after the init stage
        copyDown = initItemsRec - initItemsPopped;
        //System.out.println(sliceNode + " " + copyDown);
        assert remaining >= 0 : filter.getName()
            + ": Error calculating remaining " + initItemsRec + " < "
            + initItemsNeeded;
        return remaining;
    }

    /** @return true if filter calculates a linear combination of its inputs */
    public boolean isLinear() {
        return linear;
    }

    /** Does this filter require a receive buffer during code
     * generation?
     * 
     * @return  true if (two-stage) filter never peeks
     */
    public boolean noBuffer() {
        if (peek == 0 && prePeek == 0)
            return true;
        return false;
    }

    /**
     * Can we use a simple (non-circular, non-copy-down) 
     * receive buffer for this filter?
     * @return true if (two-stage) peeks == pops and remaining == 0, 
     *  except false if {@link #noBuffer()} is true.
     */
    public boolean isSimple() {
        if (noBuffer())
            return false;

        if (peek == pop && remaining == 0 && (prePop == prePeek))
            return true;
        return false;
    }

    /** @return the number of items produced in the init stage */
    public int initItemsSent() {
        int items = push * initMult;
        if (isTwoStage()) {
            items -= push;
            items += prePush;
        }
        return items;
    }

    /**
     * Return the number of items received in the init stage including
     * the remaining items on the tape that are not consumed in the
     * schedule.
     * 
     * @return The number of items received in the init stage.
     */
    public int initItemsReceived() {
        return initItemsReceived(false);
    }
    
    /**
     * Return the number of items received in the init stage including
     * the remaining items on the tape that are not consumed in the
     * schedule.
     * 
     * @param debug if true, print debug info.
     * 
     * @return The number of items received in the init stage.
     */
    public int initItemsReceived(boolean debug) {
        // the number of items produced by the upstream filter in
        // initialization
        int upStreamItems = 0;

        if (debug)
            System.out.println("*****  Init items received " + this + " *****");
        
        if (sliceNode.getPrevious().isFilterSlice()) {
            upStreamItems = 
                WorkNodeInfo.getFilterInfo(
                        (WorkNode) sliceNode.getPrevious()).initItemsSent();
            if (debug)
                System.out.println(" Upstream filter sends: " + upStreamItems);
        } else { // previous is an input slice
            InputNode in = (InputNode) sliceNode.getPrevious();
            if (debug)
                System.out.println(" Upstream input node:");
            // add all the upstream filters items that reach this filter
            Iterator<InterFilterEdge> edges = in.getSourceSet(SchedulingPhase.INIT).iterator();
            while (edges.hasNext()) {
                InterFilterEdge incoming = edges.next();
                upStreamItems += 
                    (int) 
                    ((double)WorkNodeInfo.getFilterInfo((WorkNode)incoming.getSrc().getPrevious())
                            .initItemsSent() * incoming.getSrc().ratio(incoming, SchedulingPhase.INIT));
                if (debug) {
                    System.out.println("   " + incoming + ": sends " + 
                            WorkNodeInfo.getFilterInfo((WorkNode)incoming.getSrc().getPrevious())
                            .initItemsSent() + ", at ratio " + incoming.getSrc().ratio(incoming, SchedulingPhase.INIT) + " = " +
                            (int) 
                            ((double)WorkNodeInfo.getFilterInfo((WorkNode)incoming.getSrc().getPrevious())
                                    .initItemsSent() * incoming.getSrc().ratio(incoming, SchedulingPhase.INIT)));
                }
                            //((double) incoming.getSrc()
                            //        .getWeight(incoming) / incoming.getSrc().totalWeights()));
                // upStreamItems +=
                // (int)(FilterInfo.getFilterInfo(previous[i]).initItemsSent() *
                // ((double)out.getWeight(in) / out.totalWeights()));
            }
        }
        if (debug)
            System.out.println("*****");
        return upStreamItems;
    }


    /**
     * get the total number of items received during the execution of the stage
     * we are in (based on <b>whichPhase</b>.  For PRIMEPUMP, this is just
     * for one firing of the parent slice in the primepump stage, the slice may fire
     * many times in the prime pump schedule to fill the rotating buffers.
     * 
     * @param whichPhase scheduling phase: initialization, primePump or steady state.
     * @return total number of items received
     */
    public int totalItemsReceived(SchedulingPhase whichPhase) {
        int items = 0;
        // get the number of items received
        switch (whichPhase) {
        case INIT:
            items = initItemsReceived();
            break;
        case PRIMEPUMP:
        case STEADY:
            items = steadyMult * pop;
            break;
        }

        return items;
    }

    /**
     * get the total number of itmes sent during the execution of the stage
     * we are in (based on <b>whichPhase</b>.  For PRIMEPUMP, this is just
     * for one firing of the parent slice in the primepump stage, the slice may fire
     * many times in the prime pump schedule to fill the rotating buffers.
     * 
     * @param whichPhase  scheduling phase: initialization, primePump or steady state.
     * @return total number of itmes sent
     */
    public int totalItemsSent(SchedulingPhase whichPhase) {
        int items = 0;
        switch (whichPhase) {
        case INIT:
            items = initItemsSent();
            break;
        case PRIMEPUMP:
        case STEADY:
            items = steadyMult * push;
            break;
        }
        return items;
    }

    /**
     * @param whichPhase  scheduling phase: initialization, primePump or steady state.
     * @return The multiplicity of the filter in the given stage, return
     * the steady mult for the primepump stage.
     */
    public int getMult(SchedulingPhase whichPhase) {
        switch (whichPhase) {
            case INIT:
                return initMult;
            case PRIMEPUMP:
            case STEADY:
                return steadyMult;
            default:                     // required coverage 
                throw new AssertionError();
        }
        
    }
    
    /**
     * Return the number of items this filter pops in during the scheduling phase
     * 
     * @param whichPhase The scheduling phase
     * @return the number of items pop'ed
     */
    public int totalItemsPopped(SchedulingPhase whichPhase) {
        if (whichPhase == SchedulingPhase.INIT) {
            int items = (initMult * pop);
            if (isTwoStage()) {
                items -= pop;
                items += prePop;
            }
            return items;
        }
        else
            return steadyMult * pop;
        
    }
    
    /**
     * @param exeCount The iteration we are querying. 
     * @param init Init stage?
     * @return The number of items this filter will produce.
     */
    public int itemsFiring(int exeCount, boolean init) {
        int items = push;

        if (init && exeCount == 0 && isTwoStage())
            items = prePush;

        return items;
    }

    /**
     * @param exeCount The current execution we are querying of the filter.
     * @param init Is this the init stage?
     * @return The number of items needed for fire this filter in the given stage at 
     * at the given iteration.
     */
    public int itemsNeededToFire(int exeCount, boolean init) {
        int items = pop;

        // if we and this is the first execution we need either peek or initPeek
        if (init && exeCount == 0) {
            if (isTwoStage())
                items = prePeek;
            else
                items = peek;
        }

        return items;
    }

    /** A printable representation.  Not committed to format. */
    public String toString() {
        return sliceNode.toString();
    }

    /*
     * Not needed now, but needed for magic crap public FilterSliceNode[]
     * getNextFilters() { FilterSliceNode[] ret;
     * 
     * if (sliceNode.getNext() == null) return new FilterSliceNode[0]; else if
     * (sliceNode.getNext().isFilterSlice()) { ret = new FilterSliceNode[1];
     * ret[0] = (FilterSliceNode)sliceNode.getNext(); } else { //output slice
     * node HashSet set = new HashSet(); OutputSliceNode output =
     * (OutputSliceNode)sliceNode.getNext(); for (int i = 0; i <
     * output.getDests().length; i++) for (int j = 0; j <
     * output.getDests()[i].length; j++)
     * set.add(output.getDests()[i][j].getNext()); ret =
     * (FilterSliceNode[])set.toArray(new FilterSliceNode[0]); } return ret; }
     * 
     * 
     * //for the filter slice node, get all upstream filter slice nodes, //going
     * thru input and output slice nodes public FilterSliceNode[]
     * getPreviousFilters() { FilterSliceNode[] ret;
     * 
     * if (sliceNode.getPrevious() == null) return new FilterSliceNode[0];
     * 
     * if (sliceNode.getPrevious().isFilterSlice()) { ret = new
     * FilterSliceNode[1]; ret[0] = (FilterSliceNode)sliceNode.getPrevious(); }
     * else { //input slice node InputSliceNode input =
     * (InputSliceNode)sliceNode.getPrevious();
     * 
     * //here we assume each slice has at least one filter slice node ret = new
     * FilterSliceNode[input.getSources().length]; for (int i = 0; i <
     * ret.length; i++) { ret[i] =
     * (FilterSliceNode)input.getSources()[i].getSrc().getPrevious(); } } return
     * ret; }
     */
}
