package at.dms.kjc.spacetime;

import java.util.ListIterator;
import at.dms.kjc.sir.*;
import at.dms.kjc.*;
import at.dms.kjc.spacetime.switchIR.*;

/** This class will rawify the SIR code and it creates the 
 * switch code.  It does not rawify the switch code in place. 
**/
public class Rawify
{
    public static void run(ListIterator traces, RawChip rawChip,
			   boolean init) 
    {
	//iterate over the traces in the given order and generate the 
	//switch code, the tile code, and the off chip stuff for 
	//each TraceNode
	Trace trace;
	
	while (traces.hasNext()) {
	    trace = (Trace)traces.next();
	    //iterate over the TraceNodes
	    TraceNode traceNode = trace.getHead();
	    while (traceNode != null) {
		//do the appropiate code generation
		if (traceNode.isFilterTrace()) {
		    RawTile tile = rawChip.getTile(((FilterTraceNode)traceNode).getX(), 
						   ((FilterTraceNode)traceNode).getY());
		    //create the filter info class
		    FilterInfo filterInfo = FilterInfo.getFilterInfo((FilterTraceNode)traceNode);
		    //switch code for the trace
		    //generate switchcode based on the presence of buffering		    
		    if (filterInfo.isDirect()) 
			createSwitchCodeDirect((FilterTraceNode)traceNode, 
				     trace, filterInfo, init, tile, rawChip);
		    else
			createSwitchCodeBuffered((FilterTraceNode)traceNode, 
				     trace, filterInfo, init, tile, rawChip);
		    //generate the compute code for the trace and place it in
		    //the tile
		    if (init)
			tile.getComputeCode().addTraceInit(filterInfo);
		    else
			tile.getComputeCode().addTraceSteady(filterInfo);
		}
		
		//get the next tracenode
		traceNode = traceNode.getNext();
	    }
	}
	
	//generate code need in between init and steady
	if (init) 
	    EndInitialization(rawChip);
	else 
	    EndSteadyState(rawChip);
    }

    private static void createSwitchCodeDirect(FilterTraceNode node, Trace parent, 
						 FilterInfo filterInfo,
						 boolean init, RawTile tile,
						 RawChip rawChip) 
    {
	//get the multiplicity based on the init variable
	int mult = (init) ? node.getInitMult() : node.getSteadyMult();
		
	for (int i = 0; i < mult; i++) {
	    //append the receive code
	    if (node.getPrevious() != null && node.getPrevious().isFilterTrace()) 
		createReceiveCode(i, node, parent, filterInfo, init, tile, rawChip);
	    //append the send code
	    if (node.getNext() != null && node.getNext().isFilterTrace()) 
		createSendCode(i, node, parent, filterInfo, init, tile, rawChip);
	}
	//don't have to worry about remaining
    }
    

    private static void createSwitchCodeBuffered(FilterTraceNode node, Trace parent, 
						 FilterInfo filterInfo,
						 boolean init, RawTile tile,
						 RawChip rawChip) 
    {
	//get the multiplicity based on the init variable
	int mult = (init) ? node.getInitMult() : node.getSteadyMult();
	
	for (int i = 0; i < mult; i++) {
	    //append the receive code
	    if (node.getPrevious() != null && node.getPrevious().isFilterTrace()) 
		createReceiveCode(i, node, parent, filterInfo, init, tile, rawChip);
	    //append the send code
	    if (node.getNext() != null && node.getNext().isFilterTrace()) 
		createSendCode(i, node, parent, filterInfo, init, tile, rawChip);
	}
	
	//now we must take care of the remaining items on the input tape 
	//after the initialization phase if the upstream filter produces more than
	//we consume in init
	if (init) {
	    if (node.getPrevious() != null &&
		node.getPrevious().isFilterTrace()) {		
		if (filterInfo.remaining > 0) {
		    for (int i = 0; 
			 i < filterInfo.remaining * Util.getTypeSize(node.getFilter().getInputType()); 
			 i++) {
			RouteIns ins = new RouteIns(tile);
			//add the route from the source tile to this
			//tile's compute processor
			ins.addRoute(rawChip.getTile(((FilterTraceNode)node.getPrevious()).getX(), 
						     ((FilterTraceNode)node.getPrevious()).getY()),
				     tile);
			tile.getSwitchCode().appendIns(ins, init);
		    }   
		}
	    }
	}
    }
    
    private static void createReceiveCode(int iteration, FilterTraceNode node, Trace parent, 
				   FilterInfo filterInfo, boolean init, RawTile tile,
				   RawChip rawChip) 
    {
	//if this is the init and it is the first time executing
	//and a twostage filter, use initpop and multiply this
	//by the size of the type it is receiving
	int itemsReceiving = itemsNeededToFire(filterInfo, iteration, init) *
	    Util.getTypeSize(node.getFilter().getInputType());

	
	for (int j = 0; j < itemsReceiving; j++) {
	    RouteIns ins = new RouteIns(tile);
	    //add the route from the source tile to this
	    //tile's compute processor
	    ins.addRoute(rawChip.getTile(((FilterTraceNode)node.getPrevious()).getX(), 
					 ((FilterTraceNode)node.getPrevious()).getY()),
			 tile);
	    tile.getSwitchCode().appendIns(ins, init);
	}
    }

    private static void createSendCode(int iteration, FilterTraceNode node, Trace parent, 
				   FilterInfo filterInfo, boolean init, RawTile tile,
				   RawChip rawChip) 
    {
	//get the items needed to fire and multiply it by the type 
	//size
	int items = itemsFiring(filterInfo, iteration, init) * 
	    Util.getTypeSize(node.getFilter().getOutputType());
	
	for (int j = 0; j < items; j++) {
	    RouteIns ins = new RouteIns(tile);
	    //add the route from this tile to the next trace node
	    ins.addRoute(tile, rawChip.getTile(((FilterTraceNode)node.getNext()).getX(), 
					       ((FilterTraceNode)node.getNext()).getY()));
	    //append the instruction
	    tile.getSwitchCode().appendIns(ins, init);
	}	
    }


    private static int itemsFiring(FilterInfo filterInfo, int exeCount, boolean init) 
    {
	int items = filterInfo.push;
	
	if (init && exeCount == 0 && (filterInfo.isTwoStage()))
	    items = filterInfo.prePush;
	
	return items;
    }
    

    private static int itemsNeededToFire(FilterInfo filterInfo, int exeCount, boolean init) 
    {
	int items = filterInfo.pop;
	
	//if we and this is the first execution we need either peek or initPeek
	if (init && exeCount == 0) {
	    if (filterInfo.isTwoStage())
		items = filterInfo.prePeek;
	    else
		items = filterInfo.peek;
	}
	
	return items;
    }
    

    private static void EndInitialization(RawChip rawChip) 
    {
    }
    
    private static void EndSteadyState(RawChip rawChip) 
    {
	
    }
}

