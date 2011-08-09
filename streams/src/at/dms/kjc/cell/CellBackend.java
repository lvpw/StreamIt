package at.dms.kjc.cell;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import at.dms.kjc.JInterfaceDeclaration;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.backendSupport.CommonPasses;
import at.dms.kjc.backendSupport.DumpSlicesAndChannels;
import at.dms.kjc.backendSupport.Layout;
import at.dms.kjc.backendSupport.MultiLevelSplitsJoins;
import at.dms.kjc.backendSupport.SpaceTimeScheduleAndSSG;
import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.sir.SIRGlobal;
import at.dms.kjc.sir.SIRHelper;
import at.dms.kjc.sir.SIRInterfaceTable;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.SIRStructure;
import at.dms.kjc.slir.*;
import at.dms.kjc.vanillaSlice.EmitStandaloneCode;

public class CellBackend {
    /** holds pointer to BackEndFactory instance during back end portion of this compiler. */
    public static CellBackendFactory backEndBits = null;

    /**
     * Top level method for Cell backend, called via reflection from {@link at.dms.kjc.StreaMITMain}.
     * @param str               SIRStream from {@link at.dms.kjc.Kopi2SIR}
     * @param interfaces        JInterfaceDeclaration[] from {@link at.dms.kjc.Kopi2SIR}
     * @param interfaceTables   SIRInterfaceTable[] from  {@link at.dms.kjc.Kopi2SIR}
     * @param structs           SIRStructure[] from  {@link at.dms.kjc.Kopi2SIR}
     * @param helpers           SIRHelper[] from {@link at.dms.kjc.Kopi2SIR}
     * @param global            SIRGlobal from  {@link at.dms.kjc.Kopi2SIR}
     */
    public static void run(SIRStream str,
            JInterfaceDeclaration[] interfaces,
            SIRInterfaceTable[] interfaceTables,
            SIRStructure[]structs,
            SIRHelper[] helpers,
            SIRGlobal global) {
        
        int numCores = KjcOptions.cell;
        
        System.out.println("Entering Cell backend. Compiling to " + numCores + " SPUs.");
        
        // The usual optimizations and transformation to slice graph
        CommonPasses commonPasses = new CommonPasses();
        // perform standard optimizations.
        StreamGraph streamGraph = commonPasses.run(str, interfaces, interfaceTables, structs, helpers, global, numCores);
        
        assert streamGraph.getSSGs().size() == 1 : "CellBackend does not yet support dynamic graphs";
        
        for ( StaticSubGraph ssg : streamGraph.getSSGs()) {
        	runSSG(ssg, commonPasses, numCores, structs);
        }		
	  }
    
    public static void runSSG(StaticSubGraph ssg, CommonPasses commonPasses, int numCores, SIRStructure[]structs ) {     
    	
        
        
        new MultiLevelSplitsJoins(ssg, MAX_TAPES/2).doit();
        ssg.dumpGraph("traces-after-multi.dot");
        
        // perform some standard cleanup on the slice graph.
        ssg = commonPasses.simplifySlices(ssg);
        
        // Set schedules for initialization, prime-pump (if KjcOptions.spacetime), and steady state.
        SpaceTimeScheduleAndSSG schedule = commonPasses.scheduleSlices(ssg);

        // create a collection of (very uninformative) processor descriptions.
        CellChip cellChip = new CellChip(numCores);

        // assign SliceNodes to processors
        Layout<CellPU> layout;
        layout = new CellNoSWPipeLayout(schedule, cellChip);
        layout.runLayout();
        
        // create other info needed to convert Slice graphs to Kopi code + Channels
        CellBackendFactory cellBackEndBits  = new CellBackendFactory(cellChip);
        backEndBits = cellBackEndBits;
        backEndBits.setLayout(layout);
        
        CellComputeCodeStore ppuCS = cellBackEndBits.getPPU().getComputeCode();
        if (KjcOptions.celldyn) {

        }
        else {
            ppuCS.addSPUInit(schedule);
            ppuCS.addCallBackFunction();
            ppuCS.initSpulib();
            ppuCS.addDataAddressField();
            ppuCS.setupInputBufferAddress();
            ppuCS.setupOutputBufferAddress();
            ppuCS.setupDataAddress();
        }
        // now convert to Kopi code plus channels.  (Javac gives error if folowing two lines are combined)
        CellBackendScaffold top_call = backEndBits.getBackEndMain();
        top_call.run(schedule, backEndBits);
        
        for (SPU spu : cellBackEndBits.getSPUs()) {
            for (CellComputeCodeStore cs : spu.getInitComputeCodeStores())
                cs.addInitFunctions();
        }
        
        ppuCS.printStats();
        
        int k=0;
        for (LinkedList<Integer> group : scheduleLayout) {
            System.out.print("group " + k + ": ");
            for (int i : group) {
                System.out.print(i + " ");
            }
            System.out.println();
            k++;
        }
        
        if (KjcOptions.celldyn) {
            ppuCS.dynamic();
        }
        
        // Dump graphical representation
        DumpSlicesAndChannels.dumpGraph("slicesAndChannels.dot", ssg, backEndBits);
        
        /*
         * Emit code to structs.h
         */
        String outputFileName = "structs.h";
        try {
            CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter(outputFileName, false)));
            // write out C code
            EmitStandaloneCode.emitTypedefs(structs,backEndBits,p);
            p.close();
        } catch (IOException e) {
            throw new AssertionError("I/O error on " + outputFileName + ": " + e);
        }
        
        /*
         * Emit Makefile
         */
        outputFileName = "Makefile";
        try {
            CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter(outputFileName, false)));
            // write out C code
    
            EmitCellCode codeEmitter = new EmitCellCode(cellBackEndBits);
            codeEmitter.generateMakefile(p);
            p.close();
        } catch (IOException e) {
            throw new AssertionError("I/O error on " + outputFileName + ": " + e);
        }
        
        CellPU ppu = cellBackEndBits.getComputeNode(0);
        outputFileName = "strppu.c";
        try {
            CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter(outputFileName, false)));
            // write out C code
    
            EmitCellCode codeEmitter = new EmitCellCode(cellBackEndBits);
            codeEmitter.generatePPUCHeader(p);
            codeEmitter.emitCodeForComputeNode(ppu, p);
            codeEmitter.generateMain(p);
            p.close();
        } catch (IOException e) {
            throw new AssertionError("I/O error on " + outputFileName + ": " + e);
        }
        
        /*
         * Emit code to strN.c
         */
        for (int n = 1; n < cellBackEndBits.getComputeNodes().size(); n++) {
            CellPU nodeN = cellBackEndBits.getComputeNode(n);
            ArrayList<CellComputeCodeStore> codestores = nodeN.getComputeCodeStores();
            ArrayList<CellComputeCodeStore> initCodeStores = nodeN.getInitComputeCodeStores();
            for (int c = 0; c < nodeN.getNumComputeCodeStores(); c++) {
                CellComputeCodeStore cs = codestores.get(c);
                CellComputeCodeStore initcs = initCodeStores.get(c);
                outputFileName = "str" + filterIdMap.get(cs.getSliceNode()) + ".c";
                try {
                    CodegenPrintWriter p = new CodegenPrintWriter(new BufferedWriter(new FileWriter(outputFileName, false)));
                    //CodegenPrintWriter initp = new CodegenPrintWriter(new BufferedWriter(new FileWriter(initOutputFileName, false)));
                    // write out C code

                    EmitCellCode codeEmitter = new EmitCellCode(cellBackEndBits);
                    codeEmitter.generateSPUCHeader(p, initcs.getSliceNode(), true);
                    codeEmitter.emitCodeForComputeStore(initcs, nodeN, p);
                    p.println("#include \"endfilter.h\"");
                    p.println();
                    p.println();
                    p.println();
                    codeEmitter.generateSPUCHeader(p, cs.getSliceNode(), false);
                    codeEmitter.emitCodeForComputeStore(cs, nodeN, p);
                    p.println("#include \"endfilter.h\"");
                    p.close();
                } catch (IOException e) {
                    throw new AssertionError("I/O error on " + outputFileName + ": " + e);
                }
            }
        }
        
        System.exit(0);
    }

    public static int numfilters = 0;
    public static int numchannels = 0;
    
    public static final int numspus = KjcOptions.cell;
    public static final int MAX_TAPES = 15;
    public static final int ITERS_PER_BATCH = 100;
    
    /**
     * InputSliceNode -> List of input channel IDs
     */
    public static final HashMap<InputNode,LinkedList<Integer>> inputChannelMap = 
        new HashMap<InputNode,LinkedList<Integer>>();
    
    /**
     * OutputSliceNode -> List of output channel IDs
     */
    public static final HashMap<OutputNode,LinkedList<Integer>> outputChannelMap =
        new HashMap<OutputNode,LinkedList<Integer>>();
    
    public static final HashMap<Integer,InternalFilterNode> SPUassignment = 
        new HashMap<Integer,InternalFilterNode>();
    
    /**
     * Encapsulates the schedule and layout of the program. The first dimension
     * specifies the order of firings. The second dimension specifies the group
     * of filters that are fired in parallel. The integer specifies the ID of
     * the filter to be fired.
     */
    public static final LinkedList<LinkedList<Integer>> scheduleLayout =
        new LinkedList<LinkedList<Integer>>();
    
    public static final HashMap<InterFilterEdge,Integer> channelIdMap = 
        new HashMap<InterFilterEdge,Integer>();
    
    public static final ArrayList<InterFilterEdge> channels = 
        new ArrayList<InterFilterEdge>();
    
    /**
     * List of all the filters in the graph (including RR splitters and joiners 
     * as separate filters)
     */
    public static final ArrayList<InternalFilterNode> filters = 
        new ArrayList<InternalFilterNode>();
    
    public static final HashMap<InternalFilterNode,Integer> filterIdMap = 
        new HashMap<InternalFilterNode,Integer>();
    
    public static final HashMap<OutputNode,Integer> duplicateSplitters = 
        new HashMap<OutputNode,Integer>();
    
    /**
     * InputSliceNode -> ID of the artificial channel connecting it to the 
     * FilterSliceNode
     */
    public static final HashMap<InputNode,Integer> artificialJoinerChannels =
        new HashMap<InputNode,Integer>();
    
    /**
     * OutputSliceNode -> ID of the artificial channel connecting the FilterSliceNode
     * to it
     */
    public static final HashMap<OutputNode,Integer> artificialRRSplitterChannels = 
        new HashMap<OutputNode,Integer>();

    /**
     * Set of IDs of channels that already have input data ready
     */
    public static final HashSet<Integer> readyInputs = new HashSet<Integer>();
    
    public static LinkedList<Integer> getLastScheduleGroup() {
        if (scheduleLayout.size() == 0) {
            LinkedList<Integer> newGroup = new LinkedList<Integer>();
            scheduleLayout.add(newGroup);
            return newGroup;
        }
        else return scheduleLayout.getLast();
    }
    
    public static void addReadyInputsForCompleted(LinkedList<Integer> completedIds) {
        for (int i : completedIds) {
            InternalFilterNode sliceNode = filters.get(i);
            if (sliceNode.isInputSlice()) {
                InputNode inputNode = sliceNode.getAsInput();
                if (inputNode.isJoiner(SchedulingPhase.STEADY)) {
                    // mark artificial channel to filterslicenode as ready
                    int artificialId = artificialJoinerChannels.get(inputNode);
                    readyInputs.add(artificialId);
                }
            } else if (sliceNode.isFilterSlice()) {
                WorkNode filterNode = sliceNode.getAsFilter();
                OutputNode outputNode = filterNode.getNext().getAsOutput();
                if (outputNode.isRRSplitter(SchedulingPhase.STEADY)) {
                    // mark artificial channel to outputslicenode as ready 
                    int artificialId = artificialRRSplitterChannels.get(outputNode);
                    readyInputs.add(artificialId);
                } else {
                    // Mark all outputs as ready
                    LinkedList<Integer> outputIds = outputChannelMap.get(outputNode);
                    readyInputs.addAll(outputIds);
                }
            } else {
                OutputNode outputNode = sliceNode.getAsOutput();
                if (outputNode.isRRSplitter(SchedulingPhase.STEADY)) {
                    // Mark all outputs as ready
                    LinkedList<Integer> outputIds = outputChannelMap.get(outputNode);
                    readyInputs.addAll(outputIds);
                }
            }
        }
    }
    
    public static InterFilterEdge getEdgeBetween(OutputNode src, InputNode dest) {
        for (InterFilterEdge e : src.getDestSequence(SchedulingPhase.STEADY)) {
            if (e.getDest() == dest)
                return e;
        }
        return null;
    }
}
