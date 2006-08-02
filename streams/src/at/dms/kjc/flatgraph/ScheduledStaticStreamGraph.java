package at.dms.kjc.flatgraph;

import java.util.*;
//import at.dms.kjc.flatgraph.*;
import at.dms.util.Utils;
import at.dms.kjc.sir.lowering.SIRScheduler;
import at.dms.kjc.sir.lowering.partition.PartitionDot;
import at.dms.kjc.sir.*;
/**
 * A StaticStreamGraph represents a subgraph of the application's StreamGraph
 * where communication within the SSG is over static rate channels. The
 * input/output (if either exists) of an SSG is dynamic, but the sources and
 * sinks have their input/output rates zeroed, repectively.
 *
 * This extension allows scheduling of a StaticStreamGraph
 * 
 * 
 */
public class ScheduledStaticStreamGraph extends StaticStreamGraph {
    // given a flatnode map to the execution count for desired stage
    protected HashMap<FlatNode,Integer> initExecutionCounts;

    protected HashMap<FlatNode,Integer> steadyExecutionCounts;

    // stores the multiplicities as returned by the scheduler...
    private HashMap<SIROperator,int[]>[] executionCounts;

    /**
     * create a static stream graph with realTop as the first node.
     */
     public ScheduledStaticStreamGraph(StreamGraph sg, FlatNode realTop) {
         super(sg,realTop);
     }

     /**
      * Given the current toplevel flatnode, create the SIR graph, also
      * regenerating the flatgraph *
      */
     public void createSIRGraph() {
         //int suffix = Double.valueOf(Math.random() * 1000).intValue();
         (new DumpGraph()).dumpGraph(topLevel, Utils
                                     .makeDotFileName("beforeFGtoSIR"/* + suffix*/, topLevelSIR),
                                     initExecutionCounts, steadyExecutionCounts);

         setTopLevelSIR((new FlatGraphToSIR(topLevel)).getTopLevelSIR());

         (new DumpGraph()).dumpGraph(topLevel, Utils
                 .makeDotFileName("afterFGtoSIR"/* + suffix*/, topLevelSIR),
                 initExecutionCounts, steadyExecutionCounts);
     }
     /**
      * call the scheduler on the toplevel SIR node and create the execution
      * counts *
      */
     public void scheduleAndCreateMults() {
         // get the multiplicities from the scheduler
         executionCounts = SIRScheduler.getExecutionCounts(topLevelSIR);
         PartitionDot.printScheduleGraph(topLevelSIR, Utils
                                         .makeDotFileName("schedule", topLevelSIR), executionCounts);

         // create the multiplicity maps
         createExecutionCounts();
         // print the flat graph
         dumpFlatGraph();
     }

     /** dump a dot rep of the flat graph * */
     protected void dumpFlatGraph() {
         // dump the flatgraph of the application, must be called after
         // createExecutionCounts
         (new DumpGraph()).dumpGraph(graphFlattener.top, Utils
                                     .makeDotFileName("flatgraph", topLevelSIR),
                                     initExecutionCounts, steadyExecutionCounts);
     }


     /**
      * given the multiplicities created by the scheduler, put them into a format
      * that is more easily used
      */
     protected void createExecutionCounts() {

         // make fresh hashmaps for results
         HashMap[] result = { initExecutionCounts = new HashMap<FlatNode,Integer>(),
                              steadyExecutionCounts = new HashMap<FlatNode,Integer>() };

         // then filter the results to wrap every filter in a flatnode,
         // and ignore splitters
         for (int i = 0; i < 2; i++) {
             for (Iterator it = executionCounts[i].keySet().iterator(); it
                      .hasNext();) {
                 SIROperator obj = (SIROperator) it.next();
                 int val = ((int[]) executionCounts[i].get(obj))[0];
                 // System.err.println("execution count for " + obj + ": " +
                 // val);
                 /*
                  * This bug doesn't show up in the new version of FM Radio - but
                  * leaving the comment here in case we need to special case any
                  * other scheduler bugsx.
                  * 
                  * if (val==25) { System.err.println("Warning: catching
                  * scheduler bug with special-value " + "overwrite in
                  * SpaceDynamicBackend"); val=26; } if ((i == 0) &&
                  * (obj.getName().startsWith("Fused__StepSource") ||
                  * obj.getName().startsWith("Fused_FilterBank"))) val++;
                  */
                 if (graphFlattener.getFlatNode(obj) != null)
                     ((HashMap<FlatNode,Integer>)result[i]).put(graphFlattener.getFlatNode(obj), new Integer(
                                                                                val));
             }
         }

         // Schedule the new Identities and Splitters introduced by
         // GraphFlattener
         for (int i = 0; i < GraphFlattener.needsToBeSched.size(); i++) {
             FlatNode node = (FlatNode) GraphFlattener.needsToBeSched.get(i);
             int initCount = -1;
             if (node.incoming.length > 0) {
                 if (initExecutionCounts.get(node.incoming[0]) != null)
                     initCount = ((Integer) initExecutionCounts
                                  .get(node.incoming[0])).intValue();
                 if ((initCount == -1)
                     && (executionCounts[0].get(node.incoming[0].contents) != null))
                     initCount = ((int[]) executionCounts[0]
                                  .get(node.incoming[0].contents))[0];
             }
             int steadyCount = -1;
             if (node.incoming.length > 0) {
                 if (steadyExecutionCounts.get(node.incoming[0]) != null)
                     steadyCount = ((Integer) steadyExecutionCounts
                                    .get(node.incoming[0])).intValue();
                 if ((steadyCount == -1)
                     && (executionCounts[1].get(node.incoming[0].contents) != null))
                     steadyCount = ((int[]) executionCounts[1]
                                    .get(node.incoming[0].contents))[0];
             }
             if (node.contents instanceof SIRIdentity) {
                 if (initCount >= 0)
                     initExecutionCounts.put(node, new Integer(initCount));
                 if (steadyCount >= 0)
                     steadyExecutionCounts.put(node, new Integer(steadyCount));
             } else if (node.contents instanceof SIRSplitter) {
                 // System.out.println("Splitter:"+node);
                 int[] weights = node.weights;
                 FlatNode[] edges = node.edges;
                 int sum = 0;
                 for (int j = 0; j < weights.length; j++)
                     sum += weights[j];
                 for (int j = 0; j < edges.length; j++) {
                     if (initCount >= 0)
                         initExecutionCounts.put(edges[j], new Integer(
                                                                       (initCount * weights[j]) / sum));
                     if (steadyCount >= 0)
                         steadyExecutionCounts.put(edges[j], new Integer(
                                                                         (steadyCount * weights[j]) / sum));
                 }
                 if (initCount >= 0)
                     initExecutionCounts.put(node, new Integer(initCount));
                 if (steadyCount >= 0)
                     steadyExecutionCounts.put(node, new Integer(steadyCount));
             } else if (node.contents instanceof SIRJoiner) {
                 //FlatNode oldNode = graphFlattener.getFlatNode(node.contents);
                 if (executionCounts[0].get(node.oldContents) != null)
                     initExecutionCounts.put(node, new Integer(((int[]) executionCounts[0]
                                                      .get(node.oldContents))[0]));
                 if (executionCounts[1].get(node.oldContents) != null)
                     steadyExecutionCounts.put(node, new Integer(((int[]) executionCounts[1]
                                                      .get(node.oldContents))[0]));
             }
         }
     }
     /** get the multiplicity map for the give stage * */
     public HashMap getExecutionCounts(boolean init) {
         return init ? initExecutionCounts : steadyExecutionCounts;
     }

     /**
      * get the multiplicity for <pre>node</pre> in the given stage, if <pre>init</pre> then init
      * stage *
      */
     public int getMult(FlatNode node, boolean init) {
         assert !(!init && !steadyExecutionCounts.containsKey(node)) : "Asking for steady mult for a filter that is not in the steady schedule "
             + node;

         Integer val = ((Integer) (init ? initExecutionCounts.get(node)
                                   : steadyExecutionCounts.get(node)));
         if (val == null)
             return 0;
         else
             return val.intValue();
     }
     /** accept a stream graph visitor * */
     public void accept(StreamGraphVisitor s, HashSet visited, boolean newHash) {
         if (newHash)
             visited = new HashSet();

         if (visited.contains(this))
             return;

         visited.add(this);
         s.visitStaticStreamGraph(this);

         Iterator nextsIt = nextSSGs.iterator();
         while (nextsIt.hasNext()) {
             StaticStreamGraph ssg = (StaticStreamGraph) nextsIt.next();
             ssg.accept(s, visited, false);
         }
     }
}
