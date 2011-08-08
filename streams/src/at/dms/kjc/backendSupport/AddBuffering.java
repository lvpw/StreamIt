/**
 * 
 */
package at.dms.kjc.backendSupport;

import java.util.*;
import at.dms.kjc.backendSupport.WorkNodeInfo;
import at.dms.kjc.slir.*;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRIdentity;
import at.dms.kjc.sir.lowering.RenameAll;
import at.dms.kjc.*;

/**
 * <p>After synch removal and conversion from the stream graph to the slice
 * graph, input slice nodes (joiners) or output slice nodes (splitter) may
 * not execute an integral number of times.</p>
 * <p>For an input slice node, each incoming edge may not have the same multiplicity 
 * and/or for an output slice node, the upstream filter may push a number of nodes 
 * that does not divide evenly by the total weights of the edges of the output node.</p> 
 * <p>The SpaceTime compiler assumes that there are no items buffered at input slice
 * nodes or output slice nodes, so to execute an integral number of times. To 
 * force this, this pass adds buffered at a slice by adding an identity filter to the 
 * end of a slice.  It will balance the incoming edges of an input slice node, and/or
 * make sure the output slice node executes an integral number of times.<p>
 * 
 * @author mgordon
 *
 */
public class AddBuffering {
    private StaticSubGraph ssg;
    private HashSet<Filter> editedSlices;
    private boolean limitTiles;
    private int totalTiles;
    
    /**
     * Fix the input slice node and output slice node multiplicity to be
     * integral in the init stage.
     *  
     * @param spaceTime The space time schedule.
     */
    public static void doit(StaticSubGraph spaceTime, boolean limitTiles, int totalTiles) {
        System.out.println("Equalizing splits and joins by buffering...");
        new AddBuffering(spaceTime,limitTiles,totalTiles).doitInternal();
    }
    
    private AddBuffering(StaticSubGraph st, boolean limit, int tt) {
        this.ssg = st;
        this.limitTiles = limit;
        this.totalTiles = tt;
        editedSlices = new HashSet<Filter>();
    }
    
    private void doitInternal() {
        checkOutputNodes();
        checkInputNodes();
    }
    
    /**
     * Iterate over the output slice nodes of the slice graph and 
     * see if any off them don't execute an integral number of times 
     * based on the items produced by the upstream filter.  If non-integer,
     * then create an id filter to buffer items to make it integral.
     */
    private void checkOutputNodes() {
        for (int t = 0; t < ssg.getFilterGraph().length; t++) {
            Filter slice = ssg.getFilterGraph()[t];
            //check all the outgoing edges to see if they are balanced in the init
            //and fix them if we have to
            fixOutputNode(slice.getOutputNode());
        }
        //don't forget to reset the filter infos after each iteration
        WorkNodeInfo.reset();
    }
    
    /**
     * Make sure that output slice node performs an integral number of 
     * iterations in the initialization stage.
     * 
     * @param output The output slice node
     */
    private void fixOutputNode(OutputNode output) {
        WorkNode filterNode = output.getPrevFilter();
        WorkNodeContent filter = filterNode.getFilter();
        
        //do nothing if nothing is pushed...
        if (filter.initItemsPushed() == 0) 
            return;
        
        if (filter.initItemsPushed() % output.totalWeights(SchedulingPhase.INIT) != 0) {
            //if the number of items of produced by the filter does not
            //divide equal the total weights of the output slice node
            //we have to buffer to items by adding an upstream id filter
            int itemsToBuffer = filter.initItemsPushed() % output.totalWeights(SchedulingPhase.INIT);
            int itemsIDShouldPass = filter.initItemsPushed() - itemsToBuffer;
            Filter slice = output.getParent();
            System.out.println(" * Adding buffering after " + slice.getOutputNode().getPrevious() + 
                    " to equalize output, pass: " + itemsIDShouldPass + " buffer: " + itemsToBuffer);
            //System.out.println("   " + filter.initItemsPushed() + " % " + 
            //        output.totalWeights() + " != 0");
            addIDToSlice(slice, itemsIDShouldPass);
            
            //remember that we have edited this slice...
            editedSlices.add(slice);
        }
    }
    
    
    /**
     * Make sure that all edges of an input slice node executes an equal number
     * of times.  Do this for all input slice nodes. 
     *
     */
    private void checkInputNodes() {
        //check all the edges of the slice graph to see if they are 
        //balanced
        boolean change = false;
        do {
            //System.out.println("Iteration " + i++);
            change = false;
            for (int t = 0; t < ssg.getFilterGraph().length; t++) {
                Filter slice = ssg.getFilterGraph()[t];
                //check all the outgoing edges to see if they are balanced in the init
                //and fix them if we have to
                boolean currentChange 
                    = fixInputNodeBuffering(slice.getInputNode());
                if (currentChange)
                    change = true;
            }
            //don't forget to reset the filter infos after each iteration
            WorkNodeInfo.reset();
            
        } while (change);
    }
    
    /**
     * 
     * 
     * @param input The input slice node to fix, if needed.
     * 
     * @return true if buffering was added to the correct this input
     * slice node.
     */
    private boolean fixInputNodeBuffering(InputNode input) {
        
        Iterator<InterFilterEdge> edges = input.getSourceSet(SchedulingPhase.INIT).iterator();
        HashMap<InterFilterEdge, Double> mults = new HashMap<InterFilterEdge, Double>();
        double minMult = Double.MAX_VALUE;

        //System.out.println("***** " + input + " " + input.getNext());
        
        if (!edges.hasNext())
            return false;
        
        //find the edge that has the smallest multiplicity in the
        //initialization stage
        while(edges.hasNext()) {
            InterFilterEdge edge = edges.next();
            
            double mult = 
                (initItemsPushed(edge) / ((double)input.getWeight(edge, SchedulingPhase.INIT)));
            
            //System.out.print(edge + " mult: " + mult + ": ");
            //System.out.println(initItemsPushed(edge) + " / " + input.getWeight(edge));
            
            //remember the mult of this edge
            
            //System.out.println(" * Mult for " + edge + " = " + mult +
            //        " = " + initItemsPushed(edge) + " / " + 
            //        input.getWeight(edge));
            
            mults.put(edge, new Double(mult));
            //remember the min, it will be the new multiplicity for all the edges
            if (mult < minMult)
                minMult = mult;
        }
       
        //make the target minimum multiplicity an integral number 
        minMult = Math.floor(minMult);
       
        //now add buffering so that the input slice node receives exactly the
        //right amount of data from each input to perform the minimum number
        //of iterations
              
        //re-iterate over the edges and if they don't have init mult
        //minMult then add buffering by adding an upstream identity 
        //filter
        boolean changes = false;
        
        edges = input.getSourceSet(SchedulingPhase.INIT).iterator();
        while (edges.hasNext()) {
            InterFilterEdge edge = edges.next();
            
            double myMult = mults.get(edge).doubleValue();
            //if the mult is not equal to the target mult, we must buffer
            if (myMult != minMult) {
                System.out.println(" * For " + input + " change " + edge + " from "  + 
                        mults.get(edge).doubleValue() + " to " + minMult);
                
                changes = true;
                Filter slice = edge.getSrc().getParent();
                //System.out.println(" * Adding output buffering to " + slice);
                //make sure that we have not already added buffering to this 
                //slice, if we try to fix it will mess up the already fixed input
                //slice node...
                assert !editedSlices.contains(slice) : 
                    "Trying to add different buffering amounts to " + slice;
                //the number of items that we should let pass in the init
                //stage for the upstream filter, the rest that it produces in the
                //init are buffered...
                //System.out.println("   Now pass " + itemsToPass(slice, edge, minMult));
                int itemsToPass = itemsToPass(slice, edge, minMult);
                if (legalToAddBufferingInSlice(slice, itemsToPass)) { 
                    addIDToSlice(slice, itemsToPass);
                    editedSlices.add(slice);
                }
                else {
                    //since we cannot legally add the buffering inside the upstream slice
                    //add a new buffering slice that will buffer all the items that
                    //are produced greater than the new mult
                    addNewBufferingSlice(slice, edge, 
                            (int)(((double)minMult) * input.getWeight(edge, SchedulingPhase.INIT)));
                }
            }
        }
        
        return changes;
    }
    
    /**
     * 
     * @param slice The downstream slice
     * @param incoming
     * @param itemsToPassInit
     */
    private void addNewBufferingSlice(Filter upSlice, InterFilterEdge edge, int itemsToPassInit) {
        System.out.println("Adding new buffering slice at edge: " + edge);
        CType type = edge.getType(); 
        
        //the downstream slice
        Filter downSlice = edge.getDest().getParent();
        
        //the new input of the new slice
        InputNode newInput = new InputNode(new int[]{1});
        //create the identity filter for the new slice
        SIRFilter identity = new SIRIdentity(type);
        RenameAll.renameAllFilters(identity);
        
        //create the identity filter node...
        WorkNode filter = 
            new WorkNode(new WorkNodeContent(identity));
       
        //create the new output slice node
        OutputNode newOutput = new OutputNode(new int[]{1});
        //set the intra-slice connections
        newInput.setNext(filter);
        filter.setPrevious(newInput);
        filter.setNext(newOutput);
        newOutput.setPrevious(filter);
        
        //the new slice
        Filter bufferingSlice = new Filter(newInput);
        bufferingSlice.finish();
        
        //create the new edge that will exist between the new slice and the
        //downstream slice
        InterFilterEdge newEdge = new InterFilterEdge(newOutput, edge.getDest());
        
        //now install the edge at the input of the downstream slice instead 
        //of the old edge
        downSlice.getInputNode().replaceEdge(edge, newEdge, SchedulingPhase.INIT);
        
        //reset the dest of the existing edge to be the new buffering slice
        edge.setDest(newInput);
                
        //set the sources and dests of the new input and new output
        newInput.setSources(new InterFilterEdge[]{edge});
        newOutput.setDests(new InterFilterEdge[][]{{newEdge}});
        
        System.out.println("   with new input: " + newInput);
        System.out.println("   with new output: " + newOutput);
        
        //set the mults of the new identity
        filter.getFilter().setInitMult(itemsToPassInit);
        WorkNodeContent prev = upSlice.getOutputNode().getPrevFilter().getFilter();
        
        //calc the number of steady items
        int steadyItems = (int) 
             (((double)(prev.getSteadyMult() * prev.getPushInt())) *  
                edge.getSrc().ratio(edge, SchedulingPhase.INIT));
        
        System.out.println("   with initMult: " + itemsToPassInit + 
                ", steadyMult: " + steadyItems);
        
        filter.getFilter().setSteadyMult(steadyItems);
       
    }
    
    /**
     * Return true if we can legally add the buffering required for slice to the
     * inside of the slice by adding a id filter to the end of the slice.
     * 
     * This checks if there are less than num_tiles filters in the slice and if
     * the buffering required will screw up any of the filters in the init stage.
     * 
     * @param slice The slice
     * @param initItemsSent The new number of items sent to the output slice node 
     * in the init stage.
     * @return True if we can add the required buffering to the inside of the slice.
     */
    private boolean legalToAddBufferingInSlice(Filter slice, int initItemsSent) {
        if (KjcOptions.greedysched || KjcOptions.noswpipe)
            return false;
        if (limitTiles && 1 >= totalTiles)
            return false;
        
        OutputNode output = slice.getOutputNode();
        
        Iterator<InterFilterEdge> edges = output.getDestSet(SchedulingPhase.INIT).iterator(); 
        while (edges.hasNext()) {
            InterFilterEdge edge = edges.next();
            WorkNodeInfo downstream = WorkNodeInfo.getFilterInfo(edge.getDest().getNextFilter());
            
            int itemsRecOnEdge = (int) (((double)initItemsSent) *
                    output.ratio(edge, SchedulingPhase.INIT));
            int itemsNeededOnEdge = (int) (((double)downstream.initItemsNeeded) * 
                    edge.getDest().ratio(edge, SchedulingPhase.INIT));
            
            if (itemsRecOnEdge < itemsNeededOnEdge) {
                System.out.println("Cannot add buffering inside slice: " + edge);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Calculate the number of items that should be passed on to the 
     * output slice node by the new ID filter, meaning the remainder that 
     * are produced by the filter are buffered by the ID.
     * 
     * @param slice The slice we are buffering.
     * @param edge The edge that requires the buffering.
     * @param inputMult The multiplicity that is now required for the edge.
     * 
     * @return The number of items that the ID must pass to the output
     * slice node.
     */
    private int itemsToPass(Filter slice, InterFilterEdge edge, double inputMult) {
        assert slice == edge.getSrc().getParent();
        OutputNode output = edge.getSrc();
        InputNode input = edge.getDest();
        
        //the number of items that should now flow over this edge 
        //in the init stage
        double edgeItems = (((double)inputMult) * ((double)input.getWeight(edge, SchedulingPhase.INIT)));

        //System.out.println("  Edge Items = " + edgeItems + " = " + 
        //        inputMult +  " * " +  
        //                input.getWeight(edge));
        
        //System.out.println("  Items to pass = " + edgeItems + " * " +
        //        ((double)output.totalWeights() / 
        //                ((double)output.getWeight(edge))));
        
        //this is the number of items that need to be passed to the
        //output slice node inorder for edgeItems to flow on edge
        return (int)(((double)output.totalWeights(SchedulingPhase.INIT) / 
                ((double)output.getWeight(edge, SchedulingPhase.INIT))) *
                ((double)edgeItems));
    }
    
    /**
     * Create an identity filter and add it to the end of slice.  The
     * new identity filter will have an init multiplicity of 
     * initMult and a steady-state multiplicity equal to the number 
     * of items its upstream filter produces in the steady-state.
     *   
     * @param slice The slice to add to.
     * @param initMult The init multiplicity of the new id filter.
     */
    private void addIDToSlice(Filter slice, int initMult) {
        WorkNode oldLast = slice.getOutputNode().getPrevFilter();

// removed check: can always convert to SimpleSlices
// and check would fail for UniProcessor every time...
       
        //create the identity
        SIRFilter identity = new SIRIdentity(oldLast.getFilter().getOutputType());
        RenameAll.renameAllFilters(identity);
        
        //create the identity filter node...
        WorkNode filter = 
            new WorkNode(new WorkNodeContent(identity));
        
        //set the multiplicities
        filter.getFilter().setInitMult(initMult);
        filter.getFilter().setSteadyMult(oldLast.getFilter().getSteadyMult() *
                oldLast.getFilter().getPushInt());
        
        //connect and re-finish slice
        oldLast.setNext(filter);
        filter.setPrevious(oldLast);
        filter.setNext(slice.getOutputNode());
        slice.getOutputNode().setPrevious(filter);
        slice.finish();
        
    }
    
    /**
     * The number of items pushed onto this edge in the initialization
     * stage.
     * 
     * @param edge The edge.
     * @return The number of items pushed onto this edge in the initialization
     * stage.
     */
    private double initItemsPushed(InterFilterEdge edge) {
        return ((double)edge.getSrc().getPrevFilter().getFilter().initItemsPushed()) *
        edge.getSrc().ratio(edge, SchedulingPhase.INIT);
    }
}
