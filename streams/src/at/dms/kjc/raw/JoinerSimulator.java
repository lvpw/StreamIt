package at.dms.kjc.raw;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRJoinType;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIRSplitter;

public class JoinerSimulator 
{
    //hash set indexed by flatnode to schedule
    public static HashMap<FlatNode, JoinerScheduleNode> schedules;
    //hash map indexed by Flatnode to a hashset of all
    //the buffer names for a node
    public static HashMap<FlatNode, HashSet> buffers;
    
    //the current flatnode we are working on
    private static FlatNode current;
    
    public static void createJoinerSchedules(FlatNode top) 
    {
        schedules = new HashMap<FlatNode, JoinerScheduleNode>();
        buffers = new HashMap<FlatNode, HashSet>();
        
        Iterator<FlatNode> joiners = Layout.getJoiners().iterator();
        while (joiners.hasNext()) {
            FlatNode node = joiners.next();
            current = node;
            buffers.put(current, new HashSet());
            buildJoinerSchedule(node);
        }
    }
    
    private static void buildJoinerSchedule(FlatNode node) 
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
    

    

    private static void simulateDataItem(FlatNode node, 
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
            buffers.get(current).add(buf);
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
