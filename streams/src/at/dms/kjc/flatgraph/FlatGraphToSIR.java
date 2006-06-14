package at.dms.kjc.flatgraph;

import at.dms.kjc.sir.*;
import java.util.*;


/**
 * This class will convert the FlatGraph back to an SIR representation.  
 * It will create the necessary SIR containers to represent the flat graph.  
 * This assume that the Flat graph is amenable to this conversion.  For example, 
 * it will not work if filter nodes have multiple input/output.   
 * 
 * Note that this does not work in the presence of a feedbackloop; it does not 
 * work if there are backedges in the graph. 
 * 
 * @author mgordon
 */
public class FlatGraphToSIR extends at.dms.util.Utils
{
    /** The toplevel FlatNode of the flatgraph, the entry point */
    private FlatNode toplevel;
    /** The toplevel SIRPipeline for the SIR representation of the Flat graph */
    private SIRPipeline toplevelSIR;
    /** int used to name new containers when converting */
    private int id;
    /** unique int used to identity the toplevel pipeline*/
    private static int globalID;

    /**
     * Create a new FlatGraphToSIR with top as the entry-point
     * to the FlatGraph and construct an SIR graph from the flat graph.
     * 
     * Note: This will not work if there are back edges in the flat graph.
     * 
     * @param top The entry point to the Flat graph.
     */
    public FlatGraphToSIR(FlatNode top) 
    {
        toplevel = top;
        id = 0;
        toplevelSIR = new SIRPipeline("TopLevel" + globalID++);
        reSIR(toplevelSIR, toplevel, new HashSet());
    }

    /**
     * Return the toplevel pipeline for the constructed SIR graph.
     * 
     * @return the toplevel pipeline for the constructed SIR graph.
     */
    public SIRPipeline getTopLevelSIR() 
    {
        assert toplevelSIR != null;
       
        return toplevelSIR;
    }
    
    
    /**
     * This recursive function will convert the flat graph into an 
     * SIR graph, creating the necessary conatiners along the way.
     * 
     * Note: This will not work if there are back edges in the graph.
     * 
     * @param parent The current container.
     * @param current The node that we are about to work on.
     * @param visited The set of FlatNode we have already added to the SIR graph.
     */
    private void reSIR(SIRContainer parent, FlatNode current, HashSet visited) 
    {
        if (current.isFilter()) {
            SIRPipeline pipeline;

            if (!(parent instanceof SIRPipeline)) {
                pipeline = new SIRPipeline(parent, "Pipeline" + id++);
                pipeline.setInit(SIRStream.makeEmptyInit());
                parent.add(pipeline);
            }
            else 
                pipeline = (SIRPipeline)parent;
        
            //keep adding filters to this pipeline
            while (current.isFilter()) {
                //make sure we have not added this filter before
                assert !(visited.contains(current)) : "FlatGraph -> SIR does not support Feedbackloops";
                //record that we have added this filter
                visited.add(current);
                List params;
                //if its parent does not contain it, just pass null params...
                if (parent.indexOf(current.getFilter()) == -1)
                    params = new LinkedList(); 
                else
                    params = ((SIRFilter)current.contents).getParams();
        
        
                pipeline.add((SIRFilter)current.contents, params);
                ((SIRFilter)current.contents).setParent(parent);
                //nothing more to do here so just return
                if (current.ways == 0)
                    return;
                //assert that there is exactly one outgoing edge per filter if there is none
                assert current.ways == 1 && current.edges.length == 1;
                current = current.edges[0];
            }

        }
    
        if (current.isSplitter()) {
            SIRSplitJoin splitJoin = new SIRSplitJoin(parent, "SplitJoin" + id++);
            splitJoin.setInit(SIRStream.makeEmptyInit());
            //add this splitjoin to the parent!
            parent.add(splitJoin);
            //make sure we have not seen this splitter
            assert !(visited.contains(current)) : "FlatGraph -> SIR does not support Feedbackloops";
            //record that we have added this splitter
            visited.add(current);
            splitJoin.setSplitter((SIRSplitter)current.contents);
            //create a dummy joiner for this split join just in case it does not have one
            SIRJoiner joiner = SIRJoiner.create(splitJoin, SIRJoinType.NULL, 
                                                ((SIRSplitter)current.contents).getWays());
            splitJoin.setJoiner(joiner);

            for (int i = 0; i < current.edges.length; i++) {
                if (current.edges[i] != null) {
                    //wrap all parallel splitter edges in a pipeline
                    SIRPipeline pipeline = new SIRPipeline(splitJoin, "Pipeline" + id++);
                    pipeline.setInit(SIRStream.makeEmptyInit());
                    splitJoin.add(pipeline);
                    reSIR(pipeline, current.edges[i], visited);
                }
        
            }
            return;
        }
    
        if (current.isJoiner()){
        
            SIRContainer splitJoin = parent;
            while (!(splitJoin instanceof SIRSplitJoin)) {
                splitJoin = splitJoin.getParent();
            }
        
            assert splitJoin != null;
        
            if (!visited.contains(current)) {
                //make sure we have not seen this Joiner
                assert !(visited.contains(current)) : "FlatGraph -> SIR does not support Feedbackloops";
                //record that we have added this Joiner
                visited.add(current);
                //first time we are seeing this joiner so set it as the joiner
                //and visit the remainder of the graph
                ((SIRSplitJoin)splitJoin).setJoiner((SIRJoiner)current.contents);
                if (current.ways > 0) {
                    assert current.edges.length == 1 && current.ways == 1 && current.edges[0] != null;
                    reSIR(((SIRSplitJoin)splitJoin).getParent(), current.edges[0], visited);
                }
            }
        }    
    }
}

