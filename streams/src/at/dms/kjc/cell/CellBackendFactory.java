package at.dms.kjc.cell;

import java.util.LinkedList;

import at.dms.kjc.backendSupport.BackEndFactory;
import at.dms.kjc.backendSupport.BackEndScaffold;
import at.dms.kjc.backendSupport.Channel;
import at.dms.kjc.backendSupport.CodeStoreHelper;
import at.dms.kjc.backendSupport.CodeStoreHelperJoiner;
import at.dms.kjc.backendSupport.CodeStoreHelperSimple;
import at.dms.kjc.backendSupport.CodeStoreHelperSplitter;
import at.dms.kjc.backendSupport.Layout;
import at.dms.kjc.slir.Edge;
import at.dms.kjc.slir.FilterSliceNode;
import at.dms.kjc.slir.InputSliceNode;
import at.dms.kjc.slir.OutputSliceNode;
import at.dms.kjc.slir.SchedulingPhase;
import at.dms.kjc.slir.Filter;
import at.dms.kjc.slir.SliceNode;

public class CellBackendFactory 
    extends BackEndFactory<CellChip, CellPU, CellComputeCodeStore, Integer> {

    private Layout<CellPU> layout;
    private CellChip cellChip;
    private GetOrMakeCellChannel channelMaker = new GetOrMakeCellChannel(this);
    
    /**
     * Constructor if creating UniBackEndFactory before layout
     * Creates <b>numProcessors</b> processors.
     * @param numProcessors  number of processors to create.
     */
    public CellBackendFactory(Integer numProcessors) {
       this(new CellChip(numProcessors));
    }
    
    /**
     * Constructor if creating UniBackEndFactory after layout.
     * Call it with same collection of processors used in Layout.
     * @param processors  the existing collection of processors.
     */
    public CellBackendFactory(CellChip processors) {
        if (processors == null) {
            processors = new CellChip(1);
        }
        this.cellChip = processors;
    }
    
    private CellBackendScaffold scaffolding = null;
    
    @Override
    public CellBackendScaffold getBackEndMain() {
        if (scaffolding == null) {
            scaffolding = new CellBackendScaffold();
        }
        return scaffolding;
    }
    
    public void setLayout(Layout<CellPU> layout) {
        this.layout = layout;
    }
    
    public Layout<CellPU> getLayout() {
        return this.layout;
    }

    @Override
    public Channel getChannel(Edge e) {
        return channelMaker.getOrMakeChannel(e);
    }

    @Override
    public Channel getChannel(SliceNode src, SliceNode dst) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CodeStoreHelper getCodeStoreHelper(SliceNode node) {
        if (node instanceof FilterSliceNode) {
            // simply do appropriate wrapping of calls...
            return new CodeStoreHelperSimple((FilterSliceNode)node,this);
        } else if (node instanceof InputSliceNode) {
            // CodeStoreHelper that does not expect a work function.
            // Can we combine with above?
            return new CodeStoreHelperJoiner((InputSliceNode)node, this);
        } else {
            return new CodeStoreHelperSplitter((OutputSliceNode)node,this);
        }
    }

    @Override
    public CellComputeCodeStore getComputeCodeStore(CellPU parent) {
        return parent.getComputeCode();
    }

    @Override
    public CellPU getComputeNode(Integer specifier) {
        return cellChip.getNthComputeNode(specifier);
    }

    @Override
    public CellChip getComputeNodes() {
        return cellChip;
    }
    
    public PPU getPPU() {
        return cellChip.getPPU();
    }
    
    public LinkedList<SPU> getSPUs() {
        return cellChip.getSPUs();
    }
    
    public int getCellPUNumForFilter(FilterSliceNode filterNode) {
        return getCellPUNum(getLayout().getComputeNode(filterNode));
    }
    
    public int getCellPUNum(CellPU cpu) {
        for (int i=0; i<cellChip.size(); i++) {
            if (cellChip.getNthComputeNode(i) == cpu)
                return i;
        }
        return -1;
    }

    @Override
    public void processFilterSliceNode(FilterSliceNode filter,
            SchedulingPhase whichPhase, CellChip computeNodes) {
        new CellProcessFilterSliceNode(filter, whichPhase, this).processFilterSliceNode();
    }

    @Override
    public void processFilterSlices(Filter slice, SchedulingPhase whichPhase,
            CellChip computeNodes) {
        throw new AssertionError("processFilterSlices called, back end should be calling processFilterSlice(singular)");
    }

    @Override
    public void processInputSliceNode(InputSliceNode input,
            SchedulingPhase whichPhase, CellChip computeNodes) {
         new CellProcessInputSliceNode(input, whichPhase, this).processInputSliceNode();
    }

    @Override
    public void processOutputSliceNode(OutputSliceNode output,
            SchedulingPhase whichPhase, CellChip computeNodes) {
        new CellProcessOutputSliceNode(output, whichPhase, this).processOutputSliceNode();
    }

}
