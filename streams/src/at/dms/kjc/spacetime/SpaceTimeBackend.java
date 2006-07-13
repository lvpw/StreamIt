package at.dms.kjc.spacetime;

import at.dms.kjc.sir.*;
import at.dms.kjc.*;
import at.dms.kjc.common.ConvertLonelyPops;
import at.dms.kjc.flatgraph2.*;
import java.util.LinkedList;
import java.util.ListIterator;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.stats.StatisticsGathering;
import at.dms.kjc.sir.linear.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.partition.*;
import at.dms.kjc.sir.lowering.fusion.*;
import at.dms.kjc.sir.lowering.fission.*;
import at.dms.kjc.lir.*;
import java.util.*;
import at.dms.util.SIRPrinter;
//import at.dms.kjc.sir.SIRToStreamIt;

/**
 * The entry to the space time backend for raw.
 */
public class SpaceTimeBackend {
    /** don't generate the work function code for a filter,
     * instead produce debugging code.
     */
    public static boolean FILTER_DEBUG_MODE = false;
    /** should we not software pipeline the steady state */
    public static boolean NO_SWPIPELINE = KjcOptions.noswpipe;
    public static boolean FISSION = true;

    public static SIRStructure[] structures;

    private static RawChip rawChip;
  
    public static double COMP_COMM_RATIO;
    
    public static void run(SIRStream str,
                           JInterfaceDeclaration[] interfaces,
                           SIRInterfaceTable[] interfaceTables,
                           SIRStructure[]structs,
                           SIRHelper[] helpers,
                           SIRGlobal global) {
        structures = structs;
    
        //first of all enable altcodegen by default
        //KjcOptions.altcodegen = true;

        int rawRows = -1;
        int rawColumns = -1;

        //set number of columns/rows
        rawRows = KjcOptions.raw;
        if(KjcOptions.rawcol>-1)
            rawColumns = KjcOptions.rawcol;
        else
            rawColumns = KjcOptions.raw;

        //create the RawChip
        rawChip = new RawChip(rawColumns, rawRows);

        // propagate constants and unroll loop
        System.out.println("Running Constant Prop and Unroll...");
        Set theStatics = new HashSet();
        if (global != null) theStatics.add(global);
        Map associatedGlobals = StaticsProp.propagate(str,theStatics);  
        ConstantProp.propagateAndUnroll(str, true);
        System.out.println("Done Constant Prop and Unroll...");

        // convert round(x) to floor(0.5+x) to avoid obscure errors
        RoundToFloor.doit(str);
        // add initPath functions
        EnqueueToInitPath.doInitPath(str);

        // construct stream hierarchy from SIRInitStatements
        ConstructSIRTree.doit(str);

        // VarDecl Raise to move array assignments up

        new VarDeclRaiser().raiseVars(str);

        // do constant propagation on fields
            System.out.println("Running Constant Field Propagation...");
            FieldProp.doPropagate(str);
            System.out.println("Done Constant Field Propagation...");
 
        // expand array initializers loaded from a file
        ArrayInitExpander.doit(str);
        
        if (str instanceof SIRContainer)
            ((SIRContainer)str).reclaimChildren();
        
        if (KjcOptions.dup > 1) {
            (new DuplicateBottleneck()).duplicate(str, rawChip);
        }
        
        Lifter.liftAggressiveSync(str);

        if (KjcOptions.fission > 1) {
            str = Flattener.doLinearAnalysis(str);
            str = Flattener.doStateSpaceAnalysis(str);

            System.out.println("Running Vertical Fission...");
            FissionReplacer.doit(str, KjcOptions.fission);
            Lifter.lift(str);
            System.out.println("Done Vertical Fission...");
        }

        // run user-defined transformations if enabled
        if (KjcOptions.optfile != null) {
            System.err.println("Running User-Defined Transformations...");
            str = ManualPartition.doit(str);
            System.err.println("Done User-Defined Transformations...");
            RemoveMultiPops.doit(str);
        }
    
        if (KjcOptions.sjtopipe) {
            SJToPipe.doit(str);
        }

        StreamItDot.printGraph(str, "before-partition.dot");

        // str = Partitioner.doit(str, 32);

        // VarDecl Raise to move array assignments up
        new VarDeclRaiser().raiseVars(str);

        // VarDecl Raise to move peek index up so
        // constant prop propagates the peek buffer index
        new VarDeclRaiser().raiseVars(str);

        // this must be run now, other pass rely on it...
        RenameAll.renameOverAllFilters(str);

        // SIRPrinter printer1 = new SIRPrinter();
        // IterFactory.createFactory().createIter(str).accept(printer1);
        // printer1.close();

        // Linear Analysis
        LinearAnalyzer lfa = null;
        if (KjcOptions.linearanalysis || KjcOptions.linearpartition) {
            System.out.print("Running linear analysis...");
            lfa = LinearAnalyzer.findLinearFilters(str, KjcOptions.debug, true);
            System.out.println("Done");
            LinearDot.printGraph(str, "linear.dot", lfa);
            LinearDotSimple.printGraph(str, "linear-simple.dot", lfa, null);
            // IterFactory.createFactory().createIter(str).accept(new
            // LinearPreprocessor(lfa));

            // if we are supposed to transform the graph
            // by replacing work functions with their linear forms, do so now
            if (KjcOptions.linearreplacement) {
                System.err.print("Running linear replacement...");
                LinearDirectReplacer.doReplace(lfa, str);
                System.err.println("done.");
                // print out the stream graph after linear replacement
                LinearDot.printGraph(str, "linear-replace.dot", lfa);
            }
        }

        // make sure SIRPopExpression's only pop one element
        // code generation doesn't handle generating multiple pops
        // from a single SIRPopExpression
        RemoveMultiPops.doit(str);

        // We require that no FileReader directly precede a splitter and
        // no joiner directly precede a FileWriter.
	//        System.err.println("Before SafeFileReaderWriterPositions"); 
	//        SIRToStreamIt.run(str,new JInterfaceDeclaration[]{}, new SIRInterfaceTable[]{}, new SIRStructure[]{});
        SafeFileReaderWriterPositions.doit(str);
	//        System.err.println("After SafeFileReaderWriterPositions");
	//        SIRToStreamIt.run(str,new JInterfaceDeclaration[]{}, new SIRInterfaceTable[]{}, new SIRStructure[]{});
        
        //lonely pops are converted into a statement with only a register read
        //then they are optimized out by gcc, so convert lonely pops (pops unnested in
        //a larger expression) into an assignment of the pop to a dummy variable
        new ConvertLonelyPops().convertGraph(str);
                
        // make sure that push expressions do not contains pop expressions
        // because if they do and we use the gdn, we will generate the header 
        // for the pop expression before the push expression and that may cause 
        // deadlock...
        at.dms.kjc.common.SeparatePushPop.doit(str);
        
       
        
        // get the execution counts from the scheduler
        HashMap[] executionCounts = SIRScheduler.getExecutionCounts(str);
        
        
        double CCRatio = CompCommRatio.ratio(str, WorkEstimate.getWorkEstimate(str), 
                executionCounts[1]);
        System.out.println("Comp/Comm Ratio of SIR graph: " + 
                CCRatio);
       
        new CalculateParams(str, CCRatio).doit();
      
        //Util.printExecutionCount(executionCounts[0]);
        //Util.printExecutionCount(executionCounts[1]);
        
        // flatten the graph by running (super?) synch removal
        FlattenGraph.flattenGraph(str, lfa, executionCounts);
        UnflatFilter[] topNodes = FlattenGraph.getTopLevelNodes();
        println("Top Nodes:");
        for (int i = 0; i < topNodes.length; i++)
            println(topNodes[i].toString());

        Trace[] traces = null;
        Trace[] traceGraph = null; 
        
        // get the work estimation
        WorkEstimate work = WorkEstimate.getWorkEstimate(str);
        SimplePartitioner partitioner = new SimplePartitioner(topNodes,
                                                              executionCounts, lfa, work, rawChip);
        traceGraph = partitioner.partition();
        System.out.println("Traces: " + traceGraph.length);
        partitioner.dumpGraph("traces.dot");

        //We have to create multilevel splits and/or joins if their width
        //is greater than the number of memories of the chip...
        new MultiLevelSplitsJoins(partitioner, rawChip).doit();
        partitioner.dumpGraph("traces_after_multi.dot");
        
        /*
         * System.gc(); System.out.println("MEM:
         * "+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));
         */
        StreaMITMain.clearParams();
        FlattenGraph.clear();
        AutoCloner.clear();
        SIRContainer.destroy();
        UnflatEdge.clear();
        str = null;
        interfaces = null;
        interfaceTables = null;
        structs = null;
        structures = null;
        lfa = null;
        executionCounts = null;
        topNodes = null;
        System.gc();

        // ----------------------- This Is The Line -----------------------
        // No Structure, No SIRStreams, Old Stuff Restricted Past This Point
        // Violators Will Be Garbage Collected

        
        //COMP_COMM_RATIO = CompCommRatio.ratio(partitioner);
        
        System.out.println("Multiplying Steady-State...");
        MultiplySteadyState.doit(partitioner.getTraceGraph());
     
        //we can now use filter infos, everything is set
        FilterInfo.canUse();
        //create the space/time schedule object to be filled in by the passes 
        SpaceTimeSchedule spaceTimeSchedule = new SpaceTimeSchedule(partitioner, rawChip);
        //check to see if we need to add any buffering before splitters or joiners
        //for correct execution of the init stage and steady state
        AddBuffering.doit(spaceTimeSchedule);
        
        //create the layout for each stage of the execution using simulated annealing
        //or manual
        Layout layout = null;
        if (KjcOptions.manuallayout) {
            layout = new ManualTraceLayout(spaceTimeSchedule);
        } else {
            layout = new AnnealedLayout(spaceTimeSchedule);
        }
        layout.run();
                
      
        
        System.out.println("Space/Time Scheduling Steady-State...");
        GenerateSteadyStateSchedule spaceTimeScheduler = 
            new GenerateSteadyStateSchedule(spaceTimeSchedule, layout);
        spaceTimeScheduler.schedule();
  
                
        //calculate preloop and initialization code
        System.out.println("Creating Initialization Schedule...");
        spaceTimeSchedule.setInitSchedule(DataFlowOrder.getTraversal(spaceTimeSchedule.partitioner.getTraceGraph()));
        
        System.out.println("Creating Pre-Loop Schedule...");
        GeneratePrimePumpSchedule preLoopSched = new GeneratePrimePumpSchedule(spaceTimeSchedule);
        preLoopSched.schedule();
        
       
        
        //System.out.println("Assigning Buffers to DRAMs...");
        //new BufferDRAMAssignment().run(spaceTimeSchedule);
        //ManualDRAMPortAssignment.run(spaceTimeSchedule);
        
        //set the rotation lengths of the buffers
        OffChipBuffer.setRotationLengths(spaceTimeSchedule);
        
        //communicate the addresses for the off-chip buffers && set up
        // the rotating buffers based on the preloopschedule for software pipelining
//        if (!KjcOptions.magicdram) {
            CommunicateAddrs.doit(rawChip, spaceTimeSchedule);
//        }
 
        //dump some dot graphs!
        TraceDotGraph.dumpGraph(spaceTimeSchedule, spaceTimeSchedule.getInitSchedule(), 
                                "initTraces.dot", layout, true);
        TraceDotGraph.dumpGraph(spaceTimeSchedule, spaceTimeSchedule.getSchedule(), 
                "steadyTraces.dot", layout, true);
        TraceDotGraph.dumpGraph(spaceTimeSchedule, spaceTimeSchedule.getSchedule(), 
                "steadyTraces.nolabel.dot", layout, true, false);
        
        //dump the POV representation of the schedule
        (new POVRAYScheduleRep(spaceTimeSchedule, layout, "schedule.pov")).create(); 
        
        //create the raw execution code and switch code for the initialization
        // phase and the primepump stage and the steady state
        System.out.println("Creating Raw Code...");
        Rawify.run(spaceTimeSchedule, rawChip, layout); 
        
        // generate the switch code assembly files...
        GenerateSwitchCode.run(rawChip);
        // generate the compute code from the SIR
        GenerateComputeCode.run(rawChip);
        // generate the magic dram code if enabled
//        if (KjcOptions.magicdram) {
//            MagicDram.GenerateCode(rawChip);
//        }
        //dump the layout
        LayoutDot.dumpLayout(spaceTimeSchedule, rawChip, "layout.dot");
        
        Makefile.generate(rawChip);
        
        // generate the bc file depending on if we have number gathering enabled
        if (KjcOptions.numbers > 0)
            BCFile.generate(spaceTimeSchedule, rawChip, 
                    NumberGathering.doit(rawChip, partitioner.io));
        else
            BCFile.generate(spaceTimeSchedule, rawChip, null);
        
        System.exit(1);
        
        
        /*
          System.out.println("Scheduling Traces...");
          SimpleScheduler scheduler = new SimpleScheduler(partitioner, rawChip);
          scheduler.schedule();
        
          System.out.println("Calculating Prime Pump Schedule...");
          SchedulePrimePump.doit(scheduler);
          System.out.println("Finished Calculating Prime Pump Schedule.");

          assert !KjcOptions.magicdram : "Magic DRAM support is not working";

          // we can now use filter infos, everything is set
          FilterInfo.canUse();
          System.out.println("Dumping preDRAMsteady.dot...");

          TraceDotGraph.dumpGraph(scheduler.getSchedule(), partitioner.io,
          "preDRAMsteady.dot", false, rawChip, partitioner);
          System.out.println("Assigning Buffers to DRAMs...");
          // assign the buffers not assigned by Jasp to drams
          BufferDRAMAssignment.run(scheduler.getSchedule(), rawChip,
          partitioner.io);
          // communicate the addresses for the off-chip buffers
          if (!KjcOptions.magicdram) {
          // so right now, this pass does not communicate addresses
          // but it generates the declarations of the buffers
          // on the corresponding tile.
          CommunicateAddrs.doit(rawChip);
          }
          TraceDotGraph.dumpGraph(scheduler.getInitSchedule(), partitioner.io,
          "inittraces.dot", true, rawChip, partitioner);
          TraceDotGraph.dumpGraph(scheduler.getSchedule(), partitioner.io,
          "steadyforrest.dot", true, rawChip, partitioner);
    
       
        */
    }
    
    public static RawChip getRawChip() {
        return rawChip;
    }
    
    public static void println(String s) {
        if (KjcOptions.debug)
            System.out.println(s);
    }
}

// /*

// //software pipeline
// //
// traceForrest=Schedule2Dependencies.findDependencies(spSched,traces,rawRows,rawColumns);
// //SoftwarePipeline.pipeline(spSched,traces,io);
// //for(int i=0;i<traces.length;i++)
// traces[i].doneDependencies();
// System.err.println("TopNodes in Forest: "+traceForrest.length);
// traceForrest=PruneTopTraces.prune(traceForrest);
// System.err.println("TopNodes in Forest: "+traceForrest.length);

// //traceForrest[0] = traces[0];
// /*if(false&&REAL) {
// //System.out.println("TracesGraph: "+traceGraph.length);
// //for(int i=0;i<traceGraph.length;i++)
// //System.out.println(traceGraph[i]);
// traces=traceGraph;
// int index=0;
// traceForrest[0]=traceGraph[0];
// Trace realTrace=traceGraph[0];
// while(((FilterTraceNode)realTrace.getHead().getNext()).isPredefined())
// realTrace=traceGraph[++index];
// TraceNode node=realTrace.getHead();
// FilterTraceNode currentNode=null;
// if(node instanceof InputTraceNode)
// currentNode=(FilterTraceNode)node.getNext();
// else
// currentNode=(FilterTraceNode)node;
// currentNode.setXY(0,0);
// System.out.println("SETTING: "+currentNode+" (0,0)");
// int curX=1;
// int curY=0;
// int forward=1;
// int downward=1;
// //ArrayList traceList=new ArrayList();
// //traceList.add(new Trace(currentNode));
// TraceNode nextNode=currentNode.getNext();
// while(nextNode!=null&&nextNode instanceof FilterTraceNode) {
// currentNode=(FilterTraceNode)nextNode;
// System.out.println("SETTING: "+nextNode+" ("+curX+","+curY+")");
// currentNode.setXY(curX,curY);
// if(curX>=rawColumns-1&&forward>0) {
// forward=-1;
// curY+=downward;
// } else if(curX<=0&&forward<0) {
// forward=1;
// if(curY==0)
// downward=1;
// if(curY==rawRows-1)
// downward=-1;
// if((curY==0)||(curY==rawRows-1)) {
// } else
// curY+=downward;
// } else
// curX+=forward;
// nextNode=currentNode.getNext();
// }
// //traces=new Trace[traceList.size()];
// //traceList.toArray(traces);
// for(int i=1;i<traces.length;i++) {
// traces[i-1].setEdges(new Trace[]{traces[i]});
// traces[i].setDepends(new Trace[]{traces[i-1]});
// }
// //System.out.println(traceList);
// } else */

// if(false&&REAL) {
// len=traceGraph.length;
// newLen=len;
// for(int i=0;i<len;i++)
// if(((FilterTraceNode)traceGraph[i].getHead().getNext()).isPredefined())
// newLen--;
// traces=new Trace[newLen];
// io=new Trace[len-newLen];
// idx=0;
// idx2=0;
// for(int i=0;i<len;i++) {
// Trace trace=traceGraph[i];
// if(!((FilterTraceNode)trace.getHead().getNext()).isPredefined())
// traces[idx++]=trace;
// else
// io[idx2++]=trace;
// }
// System.out.println("Traces: "+traces.length);
// for(int i=0;i<traces.length;i++)
// System.out.println(traces[i]);
// SpaceTimeSchedule sched=TestLayout.layout(traces,rawRows,rawColumns);
// traceForrest=Schedule2Dependencies.findDependencies(sched,traces,rawRows,rawColumns);
// SoftwarePipeline.pipeline(sched,traces,io);
// for(int i=0;i<traces.length;i++)
// traces[i].doneDependencies();
// System.err.println("TopNodes in Forest: "+traceForrest.length);
// traceForrest=PruneTopTraces.prune(traceForrest);
// System.err.println("TopNodes in Forest: "+traceForrest.length);
// }
// */
