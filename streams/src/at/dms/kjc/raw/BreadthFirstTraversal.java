package at.dms.kjc.raw;

import at.dms.kjc.flatgraph.FlatNode;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.Vector;

public class BreadthFirstTraversal 
{
    private static LinkedList<FlatNode> traversal;
    
    //returns a linked listed with a breadth first traversal 
    //of the stream graph starting at top
    public static LinkedList<FlatNode> getTraversal(FlatNode top) 
    {
        traversal = new LinkedList<FlatNode>();
        if (top == null)
            return traversal;
    
        HashSet<FlatNode> visited = new HashSet<FlatNode>();
        Vector<FlatNode> queue = new Vector<FlatNode>();
        FlatNode node;
    
        queue.add(top);
        while(!queue.isEmpty()) {
            node = queue.get(0);
            queue.remove(0);
        
            if (node == null)
                continue;
        
            //add the current node to the traversal
            traversal.add(node);

            //to keep the order of the nodes of a splitjoin in the correct order
            //(the order defined by the joiner) add to the queue in the reverse order
            for (int i = node.ways - 1; i >= 0; i--) {
                if (!visited.contains(node.edges[i])) {
                    queue.add(node.edges[i]); 
                    visited.add(node.edges[i]);
                }
            }
        } 
        return traversal;
    }
}
