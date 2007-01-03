/**
 * 
 */
package at.dms.kjc.spacetime;

import java.util.*;

import at.dms.kjc.common.CommonUtils;
import at.dms.kjc.slicegraph.DataFlowOrder;
import at.dms.kjc.slicegraph.Slice;
/**
 * This class operates on the SpaceTimeSchedule and generates the preloop
 * schedule for the partitioned stream graph.  It will create a pre loop schedule
 * so that each trace can execute without respect to data-flow constraints in the 
 * steady-state (software pipelining).
 * 
 * @author mgordon
 *
 */
public class GeneratePrimePumpSchedule {
    private SpaceTimeSchedule spaceTimeSchedule;
    //the execution count for each trace during the calculation of the schedule
    private HashMap<Slice, Integer> exeCounts;
    
    
   
    public GeneratePrimePumpSchedule(SpaceTimeSchedule sts) {
        spaceTimeSchedule = sts;
        exeCounts = new HashMap<Slice, Integer>();
    }
    
    /**
     * Create the preloop schedule and place it in the SpaceTimeSchedule.
     */
    public void schedule() {
        LinkedList<LinkedList<Slice>> preLoopSchedule = new LinkedList<LinkedList<Slice>>();
        if (SpaceTimeBackend.NO_SWPIPELINE) {
            spaceTimeSchedule.setPrimePumpSchedule(preLoopSchedule);
            return;
        }
        
        LinkedList dataFlowTraversal = DataFlowOrder.getTraversal(spaceTimeSchedule.getPartitioner().getSliceGraph());
              
        //keep adding traces to the schedule until all traces can fire 
        while (!canEverythingFire(dataFlowTraversal)) {
            CommonUtils.println_debugging("Pre-loop Scheduling Step...");
            //the traces that are firing in the current step...
            LinkedList<Slice> currentStep = new LinkedList<Slice>();
           
            Iterator it = dataFlowTraversal.iterator();
            while (it.hasNext()) {
                Slice slice = (Slice)it.next();
                //if the trace can fire, then fire it in this init step
                if (canFire(slice)) {
                    currentStep.add(slice);
                    CommonUtils.println_debugging("  Adding " + slice);
                }
            }
            recordFired(currentStep);
            preLoopSchedule.add(currentStep);
        } 
       
        spaceTimeSchedule.setPrimePumpSchedule(preLoopSchedule);
    }

    /**
     * Record that the trace has fired at this time in the calculation of the 
     * preloop schedule.
     * @param trace
     */
    private void recordFired(LinkedList<Slice> fired) {
        Iterator<Slice> it = fired.iterator();
        while (it.hasNext()) {
            Slice slice = it.next();
            if (exeCounts.containsKey(slice)) {
                exeCounts.put(slice, new Integer(exeCounts.get(slice).intValue() + 1));
            }
            else {
                exeCounts.put(slice, new Integer(1));
            }
        }
    }
    
    /**
     * Get the number of times (the iteration number) that the trace has
     * executed at this point in the schedule.
     * @param slice
     * @return The execution count (iteration number)
     */
    private int getExeCount(Slice slice) {
        if (exeCounts.containsKey(slice))
            return exeCounts.get(slice).intValue();
        else
            return 0;
    }
    
    /**
     * Return true if the trace can fire currently in the preloop schedule meaning
     * all of its dependencies are satisfied
     * @param slice
     * @return True if the trace can fire.
     */
    private boolean canFire(Slice slice) {
        if (!shouldFire(slice))
            return false;
                   
        Slice[] depends = slice.getDependencies();
        
        int myExeCount = getExeCount(slice);
        
        //check each of the depends to make sure that they have fired at least
        //one more time than me.
        for (int i = 0; i < depends.length; i++) {
            //file input nodes can always fire
            if (spaceTimeSchedule.getPartitioner().isIO(depends[i]))
                continue;
            
            int dependsExeCount = getExeCount(depends[i]);
            assert !(myExeCount > dependsExeCount);
            if (myExeCount == dependsExeCount)
                return false;
        }
        return true;
    }
    
    /**
     * @return True if all the traces in the stream graph can fire, end of pre-loop schedule.
     */
    private boolean canEverythingFire(LinkedList dataFlowTraversal) {
        Iterator it = dataFlowTraversal.iterator();
        while (it.hasNext()) {
            Slice slice = (Slice)it.next();
            if (!shouldFire(slice))
                continue;
            if (!canFire(slice))
                return false;
        }
        return true;
    }
    
    
    /**
     * We only need to schedule io traces that split or join.  Otherwise
     * their function is folded in to the neighboring trace.
     * 
     * @param slice The trace
     * @return should this be counted as a trace that needs to fire.
     */
    private boolean shouldFire(Slice slice) {
        if (!spaceTimeSchedule.getPartitioner().isIO(slice))
            return true;
        if (slice.getHead().getNextFilter().isFileOutput()) {
            if (slice.getHead().oneInput())
                return false;
            else 
                return true;
        }
        else {
            if (slice.getTail().oneOutput())
                return false;
            else
                return true;
        }
    }
}
