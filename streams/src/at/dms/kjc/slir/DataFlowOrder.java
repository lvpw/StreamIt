package at.dms.kjc.slir;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * This class generates a data flow schedule of the slice graph (but does not handle feedbackloops).
 * More specifically, in the traversal, all ancestors of a node are 
 * guaranteed to appear before the node.
 * 
 * @author mgordon 
 */
public class DataFlowOrder {
    
    /**
     * Generate a list of slices in data-flow order for the steady state
     * <p>
     * TODO: need to add markers for feedbackloops in original graph.
     * Order would be: (1) fake node with just a preWork to push enqueued.
     * (2) The body in order.  (3) The loop in order.
     * (one (bad) model would be {@link at.dms.kjc.cluster.DiscoverSchedule}.)
     * </p>
     * @param topSlices The slice forest.
     * @return A LinkedList of slices in data-flow order
     */
    public static LinkedList<Filter> getTraversal(Filter[] topSlices) {
        LinkedList<Filter> schedule = new LinkedList<Filter>();
        HashSet<Filter> visited = new HashSet<Filter>();
        LinkedList<Filter> queue = new LinkedList<Filter>();
        for (int i = 0; i < topSlices.length; i++) {
            queue.add(topSlices[i]);
            while (!queue.isEmpty()) {
                Filter slice = queue.removeFirst();
                if (!visited.contains(slice)) {
                    visited.add(slice);                                                         
                    for (InterFilterEdge destEdge : slice.getOutputNode().getDestSet(SchedulingPhase.STEADY)) {
                        Filter current = destEdge.getDest().getParent();
                        if (!visited.contains(current)) {
                            // only add if all sources has been visited
                            boolean addMe = true;
                            for (InterFilterEdge oneSource : current.getInputNode().getSourceSet(SchedulingPhase.STEADY)) {
                                if (!visited.contains(oneSource.getSrc().getParent())) {
                                    addMe = false;
                                    break;
                                }
                            }
                            if (addMe)
                                queue.add(current);
                        }
                    }
                    schedule.add(slice);
                }
            }
        }

        return schedule;
    }
}
