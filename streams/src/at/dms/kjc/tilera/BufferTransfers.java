package at.dms.kjc.tilera;

import java.util.LinkedList;
import java.util.List;

import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JStatement;
import at.dms.kjc.slir.InputNode;
import at.dms.kjc.slir.InterFilterEdge;
import at.dms.kjc.slir.OutputNode;
import at.dms.kjc.slir.SchedulingPhase;

public abstract class BufferTransfers {
    /** the output buffer that these dma commands uses as its source */
    protected RotatingBuffer parent;
    /** the block of ilib_wait calls, one for each dma command generated, separated for steady 
     * because we have concurrency, for init they are in commandsInit*/
    protected List<JStatement> waitCallsSteady;
    /** the dma commands block */
    protected List<JStatement> commandsSteady;
    /** the dma commands block */
    protected List<JStatement> commandsInit;
    /** the output slice node */
    protected OutputNode output;
    /** any declarations that are needed */
    protected List<JStatement> decls;
    
    public BufferTransfers(RotatingBuffer buf) {
        parent = buf;
        waitCallsSteady= new LinkedList<JStatement>();
        commandsSteady = new LinkedList<JStatement>();
        commandsInit = new LinkedList<JStatement>();
        decls = new LinkedList<JStatement>();
        //if this is a shared input buffer (one we are using for output), then 
        //the output buffer we are implementing here is the upstream output buffer
        //on the same tile
        if (buf instanceof InputRotatingBuffer) {
            output = ((InputRotatingBuffer)buf).getLocalSrcFilter().getParent().getOutputNode();
        }
        else
            output = parent.filterNode.getParent().getOutputNode();
    }
    
    /**
     * Return the list of DMA commands that will transfer the items from the
     * output buffer to to appropriate input buffer(s)
     * 
     * @return the dma commands
     */
    public List<JStatement> transferCommands(SchedulingPhase which) {
        if (which == SchedulingPhase.INIT)
            return commandsInit;
        
        return commandsSteady;
    }
    
    /**
     * Return declarations of variables needed by the dma commands 
     * @return declarations of variables needed by the dma commands 
     */
    public List<JStatement> decls() {
        return decls;
    }
    
    /**
     * Return the ilib_wait statements that wait for the dma commands to complete
     * 
     * @return the wait statements
     */
    public List<JStatement> waitCallsSteady() {
        return waitCallsSteady;    
    }
    
    public abstract JStatement zeroOutHead(SchedulingPhase phase);
    
    public abstract JMethodDeclaration pushMethod();
    
    /**
     * Do some checks to make sure we will generate correct code for this distribution pattern.
     */
    protected void checkSimple(SchedulingPhase phase) {
        assert output.singleAppearance();
        for (int w = 0; w < output.getWeights(phase).length; w++) {
            for (InterFilterEdge edge : output.getDests(phase)[w]) {
                InputNode input = edge.getDest();
                //assert that we don't have a single edge appear more than once for the input slice node
                assert input.singleAppearance();
                
                int inWeight = input.getWeight(edge, phase);
                assert inWeight == output.getWeights(phase)[w];
            }
        }
    }
}
