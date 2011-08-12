package at.dms.kjc.backendSupport;

import at.dms.kjc.common.CommonUtils;
import at.dms.kjc.slir.DataFlowOrder;
import at.dms.kjc.slir.SchedulingPhase;
import at.dms.kjc.slir.Filter;
import at.dms.kjc.KjcOptions;
import java.util.*;

/**
 * This class operates on the SpaceTimeSchedule and generates the preloop
 * schedule for the partitioned stream graph.  It will create a pre loop schedule
 * so that each trace can execute without respect to data-flow constraints in the 
 * steady-state (software pipelining).
 * 
 * @author mgordon
 *
 */
public class GeneratePrimePump {
    private BasicSpaceTimeSchedule spaceTimeSchedule;
    //the execution count for each trace during the calculation of the schedule
    private HashMap<Filter, Integer> exeCounts;
    
    
   
    public GeneratePrimePump(BasicSpaceTimeSchedule sts) {
        spaceTimeSchedule = sts;
        exeCounts = new HashMap<Filter, Integer>();
    }
    
    /**
     * Set the primepump schedule to be empty.
     */
    public void setEmptySchedule() {
        spaceTimeSchedule.setPrimePumpSchedule(new LinkedList<LinkedList<Filter>>());
    }
    
    /**
     * Create the preloop schedule and place it in the SpaceTimeSchedule.
     */
    public void schedule(Filter[] sliceGraph) {
        LinkedList<LinkedList<Filter>> preLoopSchedule = new LinkedList<LinkedList<Filter>>();
        if (! ( (KjcOptions.tilera > -1 || KjcOptions.smp > -1)) || KjcOptions.noswpipe) {
            spaceTimeSchedule.setPrimePumpSchedule(preLoopSchedule);
            return;
        }
        
        LinkedList<Filter> dataFlowTraversal = DataFlowOrder.getTraversal(sliceGraph);
              
        //keep adding traces to the schedule until all traces can fire 
        while (!canEverythingFire(dataFlowTraversal)) {
            CommonUtils.println_debugging("Pre-loop Scheduling Step...");
            //the traces that are firing in the current step...
            LinkedList<Filter> currentStep = new LinkedList<Filter>();
           
            LinkedList<Filter> canFireNow = new LinkedList<Filter>();
            for (Filter slice : dataFlowTraversal) {
                if (canFire(slice)) {
                    canFireNow.add(slice);
                }
            }
            
            /* XXX testing */   int maxsiz = dataFlowTraversal.size();
            /* XXX testing */   int usesiz = 0;
            for (Filter slice : canFireNow) {
                /* XXX testing */ assert usesiz < maxsiz;
                /* XXX testing */ usesiz++;
                //if the trace can fire, then fire it in this init step
                currentStep.add(slice);
                CommonUtils.println_debugging("  Adding " + slice);
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
    private void recordFired(LinkedList<Filter> fired) {
        Iterator<Filter> it = fired.iterator();
        while (it.hasNext()) {
            Filter slice = it.next();
            if (exeCounts.containsKey(slice)) {
                exeCounts.put(slice, exeCounts.get(slice) + 1);
            }
            else {
                exeCounts.put(slice, 1);
            }
        }
    }
    
    /**
     * Get the number of times (the iteration number) that the trace has
     * executed at this point in the schedule.
     * @param slice
     * @return The execution count (iteration number)
     */
    private int getExeCount(Filter slice) {
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
    private boolean canFire(Filter slice) {       
        Filter[] depends = slice.getDependencies(SchedulingPhase.STEADY);
        int myExeCount = getExeCount(slice);
        
        //check each of the depends to make sure that they have fired at least
        //one more time than me.
        for (int i = 0; i < depends.length; i++) {
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
    private boolean canEverythingFire(LinkedList<Filter> dataFlowTraversal) {
        for (Filter slice: dataFlowTraversal) {
            if (!canFire(slice))
                return false;
        }
        return true;
    }  
}
