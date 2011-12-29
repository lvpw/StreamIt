package at.dms.kjc.raw;

import java.util.HashMap;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIRSplitter;
import at.dms.kjc.sir.SIRStream;
import at.dms.util.Utils;

public class BlockExecutionCounts implements FlatVisitor 
{
    private static HashMap<FlatNode, Integer> blockCounts;

    public static int getBlockCount(FlatNode node) 
    {
        if (blockCounts == null)
            Utils.fail("Block Execution Count not calculated");
        return blockCounts.get(node).intValue();
    }

    public static void calcBlockCounts(FlatNode top) 
    {
        BlockExecutionCounts bec = new BlockExecutionCounts();
        top.accept(bec, null, true);
    }
    
    public BlockExecutionCounts () 
    {
        blockCounts = new HashMap<FlatNode, Integer>();
    }
    
    public void visitNode(FlatNode node) 
    {
        if (node.contents instanceof SIRJoiner ||
            node.contents instanceof SIRSplitter) {
            blockCounts.put(node, new Integer(1));
            return;
        } 
        if (((SIRStream)node.contents).insideFeedbackLoop()) {
            blockCounts.put(node, new Integer(1));
            return;
        }
    
        SIRFilter filter = (SIRFilter)node.contents;

        //block count of sources are 1
    
        //must be a multiple!!!!!!
        if (filter.getPopInt() == 0) {
            blockCounts.put(node, new Integer(1));
        }
        else {
            //blockCounts.put(node, new Integer(1));
            blockCounts.put(node, 
                            new Integer(RawBackend.
                                         steadyExecutionCounts.get(node).intValue()));
        }
        return;
    }
}

