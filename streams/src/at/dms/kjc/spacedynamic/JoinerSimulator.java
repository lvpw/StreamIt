package at.dms.kjc.spacedynamic;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.util.Utils;
import java.util.HashSet;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Vector;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Iterator;

public class JoinerSimulator 
{
    //hash set indexed by flatnode to schedule
    public static HashMap schedules;
    //hash map indexed by Flatnode to a hashset of all
    //the buffer names for a node
    public HashMap buffers;
    
    //the current flatnode we are working on
    private FlatNode current;

    private StreamGraph streamGraph;

    public JoinerSimulator(StreamGraph streamGraph) 
    {
        this.streamGraph = streamGraph;
        schedules = new HashMap();
        buffers = new HashMap();
    }
    
    public void createJoinerSchedules() 
    {
        Iterator joiners = streamGraph.getLayout().getJoiners().iterator();
        while (joiners.hasNext()) {
            FlatNode node = (FlatNode)joiners.next();
            current = node;
            buffers.put(current, new HashSet());
            buildJoinerSchedule(node);
        }
    }
    
    private void buildJoinerSchedule(FlatNode node) 
    {
        JoinerCounter counters = new JoinerCounter();
        JoinerScheduleNode first = new JoinerScheduleNode();
        JoinerScheduleNode current, temp;
        current = first;

        //see if joiner has no inputs
        if (node.inputs == 0)
            return;
        do {
            simulateDataItem(node, current, counters, "");
            if (counters.checkAllZero())
                break;
        
            temp = new JoinerScheduleNode();
            current.next = temp;
            current = temp;
        }while(true);

        //Loop the schedule
        current.next = first;
    
        schedules.put(node, first);
    }
    

    

    private void simulateDataItem(FlatNode node, 
                                  JoinerScheduleNode schedNode,
                                  JoinerCounter counters,
                                  String buf) 
    {
        /*if (node.contents instanceof SIRSplitter ||
          node.contents instanceof SIRIdentity) {
          simulateDataItem(node.incoming[0], schedNode, counters, 
          "0" + buf);
          } else */
        if (node.contents instanceof SIRFilter || node.contents instanceof SIRSplitter) {
            //fill in the joiner schedule node
            schedNode.type = JoinerScheduleNode.RECEIVE;
            schedNode.buffer = buf;
            //add the buffer name to the buffer list for this node
            ((HashSet)buffers.get(current)).add(buf);
            return;
        }
        //else if (node.contents instanceof SIRSplitter) {
        //just pass thru splitters they only have one upstream connection
        //   simulateDataItem(node.incoming[0], schedNode,
        //           counters, buf);
        //}
        else if (node.contents instanceof SIRJoiner) {
            //here is the meat
            SIRJoiner joiner = (SIRJoiner)node.contents;
            //if Joiner send the item out to all arcs
            if (joiner.getType() == SIRJoinType.COMBINE) {
                throw new RuntimeException("COMBINE");
            }
            else {
                //weighted round robin
                for (int i = 0; i < node.inputs; i++) {
                    //System.out.println(i + " " + counters.getCount(node, i) +
                    //             node.incoming[i].contents.getName()));
                    if (counters.getCount(node, i) > 0) {
                        counters.decrementCount(node, i);
                        simulateDataItem(node.incoming[i], schedNode,
                                         counters, i + buf);
                        return;
                    }
                }
                //none were greater than zero, reset all counters
                //and send to the first non zero
                for (int i = 0; i < node.inputs; i++) {
                    counters.resetCount(node, i);
                }
                for (int i = 0; i < node.inputs; i++) {
                    if (counters.getCount(node, i) > 0) {
                        counters.decrementCount(node, i);
                        simulateDataItem(node.incoming[i], schedNode,
                                         counters, i + buf);
                        return;
                    }
                }
            }
        
        }
        else {
            throw new RuntimeException("SimulateDataItem");
        }
    
    }
}
