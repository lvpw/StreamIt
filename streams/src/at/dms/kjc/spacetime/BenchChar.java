/**
 * 
 */
package at.dms.kjc.spacetime;

import java.util.*;

import at.dms.kjc.backendSupport.FilterInfo;
import at.dms.kjc.sir.*;
import at.dms.kjc.slir.InterFilterEdge;
import at.dms.kjc.slir.SchedulingPhase;
import at.dms.kjc.slir.Filter;
import at.dms.kjc.slir.SliceNode;

/**
 * Calculate and print some characteristics of the application.
 * 
 * @author mgordon
 *
 */
public class BenchChar {

    private static int joins;
    private static int splits;

    private static int peekingFilters;
    private static int bufferedFilters;
    private static int shortestPath;
    private static int longestPath;
    
    /**
     * Calculate and print some characteristics of the application.
     * Must be run after filterinfos are ready to use.
     * 
     * @param spaceTime
     */
    public static void doit(SpaceTimeSchedule spaceTime, SIRStream str) {
        
        System.out.println("---- Characteristics ----");
        //get the number of splits, joins, peekinng filters
        
        findNodes(spaceTime.getSlicer().getSliceGraph());
        calcPaths(spaceTime.getSlicer().getSliceGraph());
        System.out.println("SplitJoins: " + findSplitJoins(str));
        System.out.println("Splits: " + splits);
        System.out.println("Joins: " + joins);
        System.out.println("Peeking Filters: " + peekingFilters);
        System.out.println("Buffered Filters: " + bufferedFilters);
        System.out.println("ShortestPath S to S: " + shortestPath);
        System.out.println("Longest Path S to S: " + longestPath);
        System.out.println("-------------------------");
    }
    
    private static int findSplitJoins(SIRStream str) {
        if (str instanceof SIRFeedbackLoop) {
            SIRFeedbackLoop fl = (SIRFeedbackLoop) str;
            int sum = 0;
            sum = findSplitJoins(fl.getBody());
            sum += findSplitJoins(fl.getLoop());
            return sum;
        }
        if (str instanceof SIRPipeline) {
            SIRPipeline pl = (SIRPipeline) str;
            Iterator iter = pl.getChildren().iterator();
            int sum = 0;
            while (iter.hasNext()) {
                SIRStream child = (SIRStream) iter.next();
                sum += findSplitJoins(child);
            }
            return sum;
        }
        if (str instanceof SIRSplitJoin) {
            SIRSplitJoin sj = (SIRSplitJoin) str;
            Iterator<SIRStream> iter = sj.getParallelStreams().iterator();
            int sum = 0;
            while (iter.hasNext()) {
                SIRStream child = iter.next();
                sum += findSplitJoins(child);
            }
            return sum + 1;
        }
        
        return 0;        
    }
    
    private static void findNodes(Filter[] slices) {
        joins = 0;
        splits = 0;
        peekingFilters = 0;
        bufferedFilters = 0;
        for (int i = 0; i < slices.length; i++) {
            if (!slices[i].getHead().oneInput() && 
                    !slices[i].getHead().noInputs())
                joins++;
            if (!slices[i].getTail().oneOutput() &&
                    !slices[i].getTail().noOutputs())
                splits++;
            SliceNode node = slices[i].getHead().getNext();
            while (node.isFilterSlice()) {
                FilterInfo fi = FilterInfo.getFilterInfo(node.getAsFilter());
                if (fi.peek > fi.pop)
                    peekingFilters++;
                if (!DirectCommunication.testDC(fi))
                    bufferedFilters++;
                node = node.getNext();
            }
        }
    }
    
    private static void calcPaths(Filter[] slices) {
        //find all the top slices, or sources
        LinkedList<Filter> topSlices = new LinkedList<Filter>();
        for (int i = 0; i < slices.length; i++) {
            if (slices[i].getHead().noInputs())
                topSlices.add(slices[i]);
        }
        
        int longestPath = 0;
        Filter longestSource = null;
        //find the source that has the longs path to a sink
        for (int i = 0; i < topSlices.size(); i++) {
            int longestPathSliceToSink = longestPathToSink(topSlices.get(i));
            if (longestPathSliceToSink > longestPath) {
                longestPath = longestPathSliceToSink;
                longestSource = topSlices.get(i);
            }
        }
        assert longestSource != null;
        
        BenchChar.longestPath = longestPath;
        //now find the shortest path from longest source to the longest sink
        BenchChar.shortestPath = shortestPathToSink(longestSource);
    }
    
    private static int longestPathToSink(Filter slice) {
        if (slice.getTail().noOutputs())
            return 0;
        Iterator<InterFilterEdge> edges = slice.getTail().getDestSet(SchedulingPhase.STEADY).iterator();
        int maxPath = 0;
        while (edges.hasNext()) {
            InterFilterEdge edge = edges.next();
            int pathLenToSink = longestPathToSink(edge.getDest().getParent());
            if (pathLenToSink > maxPath)
                maxPath = pathLenToSink;
        }
        return maxPath + 1;
    }
    
    private static int shortestPathToSink(Filter slice) {
        if (slice.getTail().noOutputs())
            return 0;
        Iterator<InterFilterEdge> edges = slice.getTail().getDestSet(SchedulingPhase.STEADY).iterator();
        int minPath = Integer.MAX_VALUE;
        while (edges.hasNext()) {
            InterFilterEdge edge = edges.next();
            int pathLenToSink = shortestPathToSink(edge.getDest().getParent());
            if (pathLenToSink < minPath)
                minPath = pathLenToSink;
        }
        return minPath + 1;
    }
}
