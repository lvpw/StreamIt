/**
 * 
 */
package at.dms.kjc.backendSupport;

import java.util.*;

import at.dms.kjc.common.*;
import at.dms.kjc.slir.*;

/**
 * @author mgordon
 *
 */
public class NoSWPipeLayout<T extends ComputeNode, Ts extends ComputeNodesI> extends SimulatedAnnealing implements Layout<T> {
    
    protected SIRSlicer slicer;
    protected Ts chip;
    protected LinkedList<Filter> scheduleOrder;
    protected LinkedList<InternalFilterNode> assignedFilters;
    protected Random rand;
    
    /** from assignment when done with simulated annealing */
    private HashMap<InternalFilterNode, T> layout;
    
    public NoSWPipeLayout(SpaceTimeScheduleAndSlicer spaceTime, Ts chip) {
        this.chip = chip;
        this.slicer = (SIRSlicer)spaceTime.getSlicer();
        scheduleOrder = 
            DataFlowOrder.getTraversal(spaceTime.getSlicer().getSliceGraph());
        assignedFilters = new LinkedList<InternalFilterNode>();
        rand = new Random(17);
    }
    
    /**
     * only valid after run();
     */
    public T getComputeNode(InternalFilterNode node) {
        return layout.get(node);
    }
    
    /**
     * only valid after run()
     */
    public void setComputeNode(InternalFilterNode node, T tile) {
        layout.put(node, tile);
    }
    
    /** 
     * Use this function to reassign the assignment to <newAssign>, update
     * anything that needs to be updated on a new assignment.
     * Callable only during {@link at.dms.kjc.common.SimulatedAnnealing Simulated Annealing}.
     * @param newAssign
     */
    public void setAssignment(HashMap newAssign) {
        this.assignment = newAssign;
    }
    
    /**
     * Called by perturbConfiguration() to perturb the configuration.
     * perturbConfiguration() decides if we should keep the new assignment.
     * The assignment should contain only {@link at.dms.kjc.slicegraphFilterSliceNode FilterSliceNode}s when this is called.
     */
    public void swapAssignment() {
        WorkNode filter1 = (WorkNode)assignedFilters.get(rand.nextInt(assignedFilters.size()));
        assignment.put(filter1, chip.getNthComputeNode(rand.nextInt(chip.size())));
    }
    
    /**
     * Random initial assignment.
     */
    public void initialPlacement() {
        Iterator<Filter> slices = scheduleOrder.iterator();
        int tile = 0;
        while (slices.hasNext()) {
            
          Filter slice = slices.next();
          //System.out.println(trace.getHead().getNextFilter());
          //if (spaceTime.partitioner.isIO(trace))
          //    continue;
       
          //System.out.println("init assiging " + trace.getHead().getNextFilter() + " to " + tile);
          assignment.put(slice.getInputNode().getNextFilter(), chip.getNthComputeNode(tile++));
          assignedFilters.add(slice.getInputNode().getNextFilter());
          tile = tile % chip.size();
          
        }
    }
    
    
    
    /**
     * The placement cost (energy) of the configuration.
     * 
     * @param debug Might want to do some debugging...
     * @return placement cost
     */
    public double placementCost(boolean debug) {
        double tileCosts[] = new double[chip.size()];
        
        Iterator<Filter> slices = scheduleOrder.iterator();
        HashMap<WorkNode, Double> endTime = new HashMap<WorkNode, Double>();
        while (slices.hasNext()) {
            Filter slice = slices.next();
            //System.err.println(slice.toString());
            T tile = (T)assignment.get(slice.getInputNode().getNextFilter());
            double traceWork = slicer.getSliceBNWork(slice); 
            double startTime = 0;
            //now find the start time
            
            //find the max end times of all the traces that this trace depends on
            double maxDepStartTime = 0;
            InputNode input = slice.getInputNode();
            Iterator<InterFilterEdge> inEdges = input.getSourceSet(SchedulingPhase.STEADY).iterator();
            while (inEdges.hasNext()) {
                InterFilterEdge edge = inEdges.next();
                if (slicer.isIO(edge.getSrc().getParent()))
                    continue;
                WorkNode upStream = edge.getSrc().getPrevFilter();
                
                ComputeNode upTile = (T)assignment.get(upStream);
                assert endTime.containsKey(upStream); // TODO: assertion fails on fedback loop.
                if (endTime.get(upStream).doubleValue() > maxDepStartTime)
                    maxDepStartTime = endTime.get(upStream).doubleValue();
            }
            
            startTime = Math.max(maxDepStartTime, tileCosts[tile.getUniqueId()]);
            
            //add the start time to the trace work (one filter)!
            tileCosts[tile.getUniqueId()] = startTime + traceWork;
            endTime.put(slice.getInputNode().getNextFilter(), tileCosts[tile.getUniqueId()]);
        }
        
        
        double max = -1;
        for (int i = 0; i < tileCosts.length; i++) {
            if (tileCosts[i] > max) 
                max = tileCosts[i];
        }
        
        return max;
     }
    
    /** 
     * Perform any initialization that has to be done before
     * simulated annealing. This does not include the initial placement. 
     *
     */
    public void initialize() {
        
    }
    

    /**
     * Decide if we should keep a configuration that has a 
     * cost that is EQUAL to the current minimum of the search.  
     * By default, don't keep it. Override if you want other behavior.
     * 
     * @return Should we set the min config to this config (they have the same
     * cost). 
     */
    protected boolean keepNewEqualMin() {
        return false;
    }
        
    public void runLayout() { 
        //initialPlacement();
        
        // set up assignemts for filters in assignment
        simAnnealAssign(1, 2000);

        // set assignments for all SliceNodes in layout
        Set<Map.Entry> entries = assignment.entrySet();
        layout = new HashMap<InternalFilterNode, T>();
        for (Map.Entry<InternalFilterNode, T> snode_cnode : entries) {
            T cnode = snode_cnode.getValue();
            InternalFilterNode snode = snode_cnode.getKey();
            
            if (snode instanceof WorkNode) {
                setComputeNode(snode,cnode);
                if (snode.getPrevious() instanceof InputNode) {
                    setComputeNode(snode.getPrevious(),cnode);
                }
                if (snode.getNext() instanceof OutputNode) {
                    setComputeNode(snode.getNext(),cnode);
                }
            }
        }
    }
}