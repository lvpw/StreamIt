package at.dms.kjc.raw;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.Iterator;
import java.util.Collection;
import at.dms.util.Utils;

/** 
 * This class keeps the counters for weights of the splitter/joiners
 * and performs the test to check whether the simulation is finished 
 */
public class SimulationCounter {
    
    private HashMap arcCountsIncoming;
    private HashMap arcCountsOutgoing;
    
    private HashMap bufferCount;
    private HashSet fired;

    public SimulationCounter() {
	arcCountsIncoming = new HashMap();
	arcCountsOutgoing = new HashMap();
	bufferCount = new HashMap();
	fired = new HashSet();
    }

    public boolean hasFired(FlatNode node) {
	return fired.contains(node);
    }
    
    public void setFired(FlatNode node) {
	fired.add(node);
    }

    public int getBufferCount(FlatNode node) {
	if (!bufferCount.containsKey(node))
	    bufferCount.put(node, new Integer(0));
	return ((Integer)bufferCount.get(node)).intValue();
    }
    
    public void decrementBufferCount(FlatNode node, int val) {
	if (!bufferCount.containsKey(node))
	    Utils.fail("Cannot decrement a unseen buffer");
	if (val > getBufferCount(node))
	    Utils.fail("Cannot decrement a buffer more than it holds");
	bufferCount.put(node, 
			new Integer(getBufferCount(node) - val));
    }

    public void incrementBufferCount(FlatNode node) {
	if (!bufferCount.containsKey(node))
	    bufferCount.put(node, new Integer(0));
	bufferCount.put(node, 
			new Integer (getBufferCount(node) + 1));
    }

    public int getArcCountIncoming(FlatNode node, int way) {
	/* Create counters in the hashmap if this node has not
	   been visited already 
	*/
	if (!arcCountsIncoming.containsKey(node)) {
	    int[] nodeCounters = new int[node.inputs];
	    for (int i = 0; i < node.inputs; i++) {
		nodeCounters[i] = node.incomingWeights[i];
	    }
	    arcCountsIncoming.put(node, nodeCounters);
	}
	//Get the counter and return the count for the given way
	int[] currentArcCounts = (int[])arcCountsIncoming.get(node);
	return currentArcCounts[way];
    }
    
    public void decrementArcCountIncoming(FlatNode node, int way) 
    {
	int[] currentArcCounts = (int[])arcCountsIncoming.get(node);
	if (currentArcCounts[way] > 0)
	    currentArcCounts[way]--;
	else 
	    System.err.println("Trying to decrement a way with a zero count.");
	
	arcCountsIncoming.put(node, currentArcCounts);
    }
    
    public void resetArcCountIncoming(FlatNode node, int way) 
    {
	int[] currentArcCounts = (int[])arcCountsIncoming.get(node);
	if (currentArcCounts[way] == 0)
	    currentArcCounts[way] = node.incomingWeights[way];
	else
	    System.err.println("Trying to reset a non-zero counter.");
	arcCountsIncoming.put(node, currentArcCounts);
    }

    

    public int getArcCountOutgoing(FlatNode node, int way) {
	/* Create counters in the hashmap if this node has not
	   been visited already 
	*/
	if (!arcCountsOutgoing.containsKey(node)) {
	    int[] nodeCounters = new int[node.ways];
	    for (int i = 0; i < node.ways; i++) {
		nodeCounters[i] = node.weights[i];
	    }
	    arcCountsOutgoing.put(node, nodeCounters);
	}
	//Get the counter and return the count for the given way
	int[] currentArcCounts = (int[])arcCountsOutgoing.get(node);
	return currentArcCounts[way];
    }
    
    public void decrementArcCountOutgoing(FlatNode node, int way) 
    {
	int[] currentArcCounts = (int[])arcCountsOutgoing.get(node);
	if (currentArcCounts[way] > 0)
	    currentArcCounts[way]--;
	else 
	    System.err.println("Trying to decrement a way with a zero count.");
	
	arcCountsOutgoing.put(node, currentArcCounts);
    }
    
    public void resetArcCountOutgoing(FlatNode node, int way) 
    {
	int[] currentArcCounts = (int[])arcCountsOutgoing.get(node);
	if (currentArcCounts[way] == 0)
	    currentArcCounts[way] = node.weights[way];
	else
	    System.err.println("Trying to reset a non-zero counter.");
	arcCountsOutgoing.put(node, currentArcCounts);
    }

        
}

