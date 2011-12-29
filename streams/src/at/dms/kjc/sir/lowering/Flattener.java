package at.dms.kjc.sir.lowering;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import at.dms.kjc.JClassDeclaration;
import at.dms.kjc.JInterfaceDeclaration;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.StreamItDot;
import at.dms.kjc.common.ConvertLocalsToFields;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.iterator.SIRIterator;
import at.dms.kjc.iterator.SIRPhasedFilterIter;
import at.dms.kjc.lir.LIRToC;
import at.dms.kjc.sir.EmptyStreamVisitor;
import at.dms.kjc.sir.SIRContainer;
import at.dms.kjc.sir.SIRDynamicRateManager;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRGlobal;
import at.dms.kjc.sir.SIRHelper;
import at.dms.kjc.sir.SIRInterfaceTable;
import at.dms.kjc.sir.SIRPhasedFilter;
import at.dms.kjc.sir.SIRPortal;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.SIRStructure;
import at.dms.kjc.sir.linear.LinearAnalyzer;
import at.dms.kjc.sir.linear.LinearAtlasReplacer;
import at.dms.kjc.sir.linear.LinearDiagonalReplacer;
import at.dms.kjc.sir.linear.LinearDirectReplacer;
import at.dms.kjc.sir.linear.LinearDot;
import at.dms.kjc.sir.linear.LinearDotSimple;
import at.dms.kjc.sir.linear.LinearIndirectReplacer;
import at.dms.kjc.sir.linear.LinearRedundancyAnalyzer;
import at.dms.kjc.sir.linear.LinearRedundancyReplacer;
import at.dms.kjc.sir.linear.frequency.FrequencyReplacer;
import at.dms.kjc.sir.lowering.fission.FissionReplacer;
import at.dms.kjc.sir.lowering.fusion.FuseAll;
import at.dms.kjc.sir.lowering.fusion.Lifter;
import at.dms.kjc.sir.lowering.partition.ManualPartition;
import at.dms.kjc.sir.lowering.partition.SJToPipe;
import at.dms.kjc.sir.lowering.partition.linear.LinearPartitioner;
import at.dms.kjc.sir.stats.StatisticsGathering;
import at.dms.util.Utils;

/**
 * This is the main class for decomposing the high SIR into
 * lower-level function calls for the uniprocessor backend.
 */
public class Flattener {
    /**
     * Flattens <str> into a low IR representation, given that <interfaces>
     * are all the top-level interfaces declared in the program and 
     * <interfaceTables> represents the mapping from interfaces to methods
     * that implement a given interface in a given class.
     */
    public static void run(SIRStream str,
                           JInterfaceDeclaration[] interfaces,
                           SIRInterfaceTable[] interfaceTables,
                           SIRStructure[] structs,
                           SIRHelper[] helpers,
                           SIRGlobal global) {

        // move field initializations into init function
        FieldInitMover.moveStreamInitialAssignments(str, 
                                                    FieldInitMover.COPY_ARRAY_INITIALIZERS);

        // propagate constants and unroll loops
        System.err.print("Running Constant Prop and Unroll... ");
        Set<SIRGlobal> theStatics = new HashSet<SIRGlobal>();
        if (global != null) theStatics.add(global);
        Map associatedGlobals = StaticsProp.propagate(str,theStatics);
        ConstantProp.propagateAndUnroll(str,true);
        System.err.println("done.");

        // convert round(x) to floor(0.5+x) to avoid obscure errors
        RoundToFloor.doit(str);

        // Add initPath functions 
        EnqueueToInitPath.doInitPath(str);

        // Convert Peeks to Pops
        if (KjcOptions.poptopeek) {
            System.err.print("Converting pop to peek... ");
            PopToPeek.removeAllPops(str);
            ConstantProp.propagateAndUnroll(str);
            System.err.println("done.");
        }
    
        /* DEBUGGING PRINTING *
           SIRPrinter printer1 = new SIRPrinter("ir1.txt");
           IterFactory.createFactory().createIter(str).accept(printer1);
           printer1.close();
        */

        // construct stream hierarchy from SIRInitStatements
        ConstructSIRTree.doit(str);

        if (hasDynamicRates(str)) {
            SIRDynamicRateManager.pushConstantPolicy(1000000);
            //      System.err.println("Failure: Dynamic rates are not yet supported in the uniprocessor backend.");
            //      System.exit(1);
        }

        if (SIRPortal.findMessageStatements(str)) {
            Utils.fail("Teleport messaging is not supported in the old uniprocessor backend.");
        }

        lowerFilterContents(str, true);

        Lifter.liftAggressiveSync(str);
        // dump the original graph to a dot format
        StreamItDot.printGraph(str, "canonical-graph.dot");
        StreamItDot.printGraph(str, "before-partition.dot");

        // gather application-characterization statistics
        if (KjcOptions.stats) {
            StatisticsGathering.doit(str);
        }

        if (KjcOptions.fusion) {
            System.err.print("Running FuseAll... ");
            str = FuseAll.fuse(str);
            Lifter.lift(str);
            System.err.println("done.");
        }

        if (KjcOptions.fission>1) {
            System.err.print("Running vertical fission... ");
            FissionReplacer.doit(str, KjcOptions.fission);
            Lifter.lift(str);
            System.err.println("done.");
        }

        // debug output
        //SIRToStreamIt.run(str, interfaces, interfaceTables, structs);

        System.err.print("Raising variable declarations... ");
        new VarDeclRaiser().raiseVars(str);
        System.err.println("done.");

        // move field initializations into init function
        System.err.print("Moving initial assignments... ");
        // ignore array initializers this time since we already moved
        // them copied them over once, don't need to move again
        FieldInitMover.moveStreamInitialAssignments(str,
                                                    FieldInitMover.IGNORE_ARRAY_INITIALIZERS);
        System.err.println("done.");

        if (KjcOptions.sjtopipe) {
            SJToPipe.doit(str);
        }

        /*
          SIRFilter toDuplicate = ((SIRFilter)
          ((SIRPipeline)
          ((SIRPipeline)str).get(1)).get(0));
          System.err.println("Trying to duplicate " + toDuplicate);
          StatelessDuplicate.doit(toDuplicate, 2);
        */

        str = doLinearAnalysis(str);

        str = doStateSpaceAnalysis(str);

        MarkFilterBoundaries.doit(str);

        if (KjcOptions.optfile != null) {
            System.err.println("Running User-Defined Transformations...");
            str = ManualPartition.doit(str);
            System.err.println("done.");
        }

        // dump the partitioned graph to a dot format
        StreamItDot.printGraph(str, "after-partition.dot");

        // if we have don't have a container, wrap it in a pipeline
        // for the sake of SIRScheduler.
        if (!(str instanceof SIRContainer)) {
            str = SIRContainer.makeWrapper(str);
        }

        // make single structure
        SIRIterator iter = IterFactory.createFactory().createIter(str);
        System.err.print("Structuring... ");
        JClassDeclaration flatClass = Structurer.structure(iter,
                                                           interfaces,
                                                           interfaceTables,
                                                           structs);
        System.err.println("done.");

        if (KjcOptions.localstoglobals) {
            ConvertLocalsToFields.doit(str);
        }

        // optionally print a version of the source code that we're
        // sending to the scheduler
        if (KjcOptions.print_partitioned_source) {
            new streamit.scheduler2.print.PrintProgram().printProgram(IterFactory.createFactory().createIter(str));
        }

        // build schedule as set of higher-level work functions
        System.err.print("Scheduling... ");
        SIRSchedule schedule = SIRScheduler.buildWorkFunctions((SIRContainer)str, flatClass);
        System.err.println("done.");
        // add LIR hooks to init and work functions
        System.err.print("Annotating IR for uniprocessor... ");
        LowerInitFunctions.lower(iter, schedule);
        LowerWorkFunctions.lower(iter);
        System.err.println("done.");

        /* DEBUGGING PRINTING
           System.out.println("----------- AFTER FLATTENER ------------------");
           IRPrinter printer = new IRPrinter();
           flatClass.accept(printer);
           printer.close();
        */


        System.err.println("Generating code...");
        LIRToC.generateCode(flatClass);
        //System.err.println("done.");
    }

    public static boolean hasDynamicRates(SIRStream str) {
        final boolean[] result = { false };
        // look for dynamic expressions in peek or pop rate
        // declarations of all filters and phased filters in graph.
        IterFactory.createFactory().createIter(str).accept(new EmptyStreamVisitor() {
                public void visitFilter(SIRFilter self,
                                        SIRFilterIter iter) {
                    // look for dynamic expressions
                    if (self.getPush().isDynamic() ||
                        self.getPop().isDynamic() ||
                        self.getPeek().isDynamic()) {
                        result[0] = true;
                    }
                }
        
                /* visit a phased filter */
                public void visitPhasedFilter(SIRPhasedFilter self,
                                              SIRPhasedFilterIter iter) {
                    // check init phases
                    JMethodDeclaration[] init = self.getInitPhases();
                    for (int i=0; i<init.length; i++) {
                        if (init[i].getPush().isDynamic() ||
                            init[i].getPop().isDynamic() ||
                            init[i].getPeek().isDynamic()) {
                            result[0] = true;
                        }
                    }
                    // check work phases
                    JMethodDeclaration[] work = self.getPhases();
                    for (int i=0; i<work.length; i++) {
                        if (work[i].getPush().isDynamic() ||
                            work[i].getPop().isDynamic() ||
                            work[i].getPeek().isDynamic()) {
                            result[0] = true;
                        }
                    }
                }
            });
        return result[0];
    }
    
    /**
     * Lowers the contents of filters <str> as is appropriate along
     * the main compilation path.  Also has the effect of doing
     * constant propagation and unrolling (with appropriate setup and
     * cleanup) for all filters in str.  Doesn't unroll any loops that
     * haven't already been unrolled.
     */
    public static void lowerFilterContents(SIRStream str, boolean printStatus) {
        //Raise NewArray's up to top
        if (printStatus) { System.err.print("Raising variable declarations... "); }
        new VarDeclRaiser().raiseVars(str);
        if (printStatus) { System.err.println("done."); }
    
        /* aal -- changed so that the default action is to do field prop.
         * turn off field prop using --nofieldprop or -L */
        // do constant propagation on fields
            if (printStatus) { System.err.print("Propagating constant fields... "); }
            FieldProp.doPropagate(str);
            if (printStatus) { System.err.println("done."); }

        // expand array initializers loaded from a file
        ArrayInitExpander.doit(str);

        /* DEBUGGING PRINTING
           System.out.println("--------- AFTER CONSTANT PROP / FUSION --------");
           printer1 = new SIRPrinter();
           IterFactory.createFactory().createIter(str).accept(printer1);
           printer1.close();
        */
    
            //Flatten Blocks
            if (printStatus) { System.err.print("Flattening blocks... "); }
            new BlockFlattener().flattenBlocks(str);
            if (printStatus) { System.err.println("done."); }
            //Analyze Branches
            //System.err.print("Analyzing branches... ");
            //new BranchAnalyzer().analyzeBranches(str);
            //System.err.println("done.");
        //Destroys arrays into local variables if possible
        //System.err.print("Destroying arrays... ");
        //new ArrayDestroyer().destroyArrays(str);
        //System.err.println("done.");
        //Raise variables to the top of their block
        if (printStatus) { System.err.print("Raising variables... "); }
        new VarDeclRaiser().raiseVars(str);
        if (printStatus) { System.err.println("done."); }
    }

    // the linear analyzer last calculated via a call to
    // doLinearAnalysis.  Should really be returned as a second return
    // value from doLinearAnalysis, but that would require changes in
    // several places...
    public static LinearAnalyzer lfa = null;
    /**
     * Returns new value of <str>.  Also stores the linear filter
     * analyzer used, if any, in the lfa variable.
     */
    public static SIRStream doLinearAnalysis(SIRStream str) {

        // if someone wants to run any of the linear tools/optimizations
        // we need to run linear analysis first to extract the information
        // we are working with.
        if (!KjcOptions.statespace && 
            (KjcOptions.linearanalysis ||
             KjcOptions.linearreplacement ||
             KjcOptions.linearreplacement2 ||
             KjcOptions.linearreplacement3 ||
             KjcOptions.atlas ||
             KjcOptions.linearpartition ||
             KjcOptions.frequencyreplacement ||
             KjcOptions.redundantreplacement)) {

            // run the linear analysis and stores the information garnered in the lfa
            System.err.println("Running linear analysis... ");
            // only refactor linear children if we're NOT doing the linear partitioner
            lfa = LinearAnalyzer.findLinearFilters(str,
                                                   KjcOptions.debug,
                                                   !KjcOptions.linearpartition);
            System.err.println("done with linear analysis.");

            // now, print out the graph using the LinearPrinter which colors the graph
            // nodes based on their linearity.
            LinearDot.printGraph(str, "linear.dot", lfa);
            LinearDotSimple.printGraph(str, "linear-simple.dot", lfa, null);

            // if we are doing linear partitioning, it will take care
            // of linear and frequency replacement automatically
            if (KjcOptions.linearpartition) {
                System.err.println("Running linear partitioner...");
                str = new LinearPartitioner(str, lfa).toplevel();
            } else { 
                // and finally, if we want to run frequency analysis
                // 0 means stupid implementation, 1 means nice implemenation
                if (KjcOptions.frequencyreplacement) {
                    System.err.print("Running frequency replacement...");
                    FrequencyReplacer.doReplace(lfa, str);
                    System.err.println("done.");
                    LinearDot.printGraph(str, ("linear-frequency.dot"), lfa);
                }

                // otherwise, test for linear and frequency
                // replacement separately...

                // if we are supposed to transform the graph
                // by replacing work functions with their linear forms, do so now 
                if (KjcOptions.linearreplacement) {
                    System.err.print("Running linear replacement... ");
                    LinearDirectReplacer.doReplace(lfa, str);
                    System.err.println("done.");
                    // print out the stream graph after linear replacement
                    LinearDot.printGraph(str, "linear-replace.dot", lfa);
                }

                // if we are supposed to transform the graph
                // by replacing work functions with their linear forms (using indirection)
                if (KjcOptions.linearreplacement2) {
                    System.err.print("Running indirect linear replacement... ");
                    LinearIndirectReplacer.doReplace(lfa, str);
                    System.err.println("done.");
                    // print out the stream graph after linear replacement
                    LinearDot.printGraph(str, "linear-indirect-replace.dot", lfa);
                }

                // if we are supposed to transform the graph
                // by replacing work functions with diagonal matrix multiplies
                if (KjcOptions.linearreplacement3) {
                    System.err.print("Running diagonal linear replacement... ");
                    LinearDiagonalReplacer.doReplace(lfa, str);
                    System.err.println("done.");
                    // print out the stream graph after linear replacement
                    LinearDot.printGraph(str, "linear-diagonal-replace.dot", lfa);
                }

                // if we are supposed to transform the graph
                // by replacing work functions with diagonal matrix multiplies
                if (KjcOptions.atlas) {
                    System.err.print("Running ATLAS linear replacement... ");
                    LinearAtlasReplacer.doReplace(lfa, str);
                    System.err.println("done.");
                    // print out the stream graph after linear replacement
                    LinearDot.printGraph(str, "linear-atlas-replace.dot", lfa);
                }

            }

            if (KjcOptions.redundantreplacement) {
                System.err.print("Running redundancy analysis... ");        
                // now, run a redundancy analysis pass and print the results
                LinearRedundancyAnalyzer lra = new LinearRedundancyAnalyzer(lfa);
                System.err.println("done.");
                // print dot graph for redundancy information
                LinearDot.printGraph(str, "linear-redundant.dot", lfa, lra);
        
                // do the redundancy replacement
                System.err.print("Running anti-redundant replacement...");
                LinearRedundancyReplacer.doReplace(lfa, lra, str);
                System.err.println("done.");
                // print out the stream graph after linear redundant replacement
                LinearDot.printGraph(str, "linear-redundant-replace.dot", lfa);
            }

            Lifter.liftAggressiveSync(str);
            StreamItDot.printGraph(str, "after-linear.dot");
        }
        return str;
    }
    
    /**
     * Returns new value of <str>.
     */
    public static SIRStream doStateSpaceAnalysis(SIRStream str) {

        // if someone wants to run any of the linear tools/optimizations
        // we need to run linear analysis first to extract the information
        // we are working with.
        if (KjcOptions.statespace) {
            // catch unsupported options
            if (KjcOptions.linearpartition ||
                KjcOptions.linearreplacement2 ||
                KjcOptions.linearreplacement3 ||
                KjcOptions.atlas ||
                KjcOptions.linearpartition ||
                KjcOptions.frequencyreplacement ||
                KjcOptions.redundantreplacement) {
                throw new RuntimeException("Option is currently unsupported with state-space analysis.");
            }

            // run the linear analysis and stores the information garnered in the lfa
            System.err.println("Running linear state-space analysis... ");
            // only refactor linear children if we're NOT doing the linear partitioner
            at.dms.kjc.sir.statespace.LinearAnalyzer lfa = at.dms.kjc.sir.statespace.LinearAnalyzer.findLinearFilters(str,
                                                                                                                      KjcOptions.debug,
                                                                                                                      !KjcOptions.linearpartition);
            System.err.println("done with linear state-space analysis.");

            // now, print out the graph using the LinearPrinter which colors the graph
            // nodes based on their linearity.
            at.dms.kjc.sir.statespace.LinearDot.printGraph(str, "linear.dot", lfa);
            at.dms.kjc.sir.statespace.LinearDotSimple.printGraph(str, "linear-simple.dot", lfa, null);

            // if we are supposed to transform the graph
            // by replacing work functions with their linear forms, do so now 
            if (KjcOptions.linearreplacement) {
                System.err.print("Running state-space linear replacement... ");
                at.dms.kjc.sir.statespace.LinearDirectReplacer.doReplace(lfa, str);
                System.err.println("done.");
                // print out the stream graph after linear replacement
                at.dms.kjc.sir.statespace.LinearDot.printGraph(str, "linear-replace.dot", lfa);
            }

            Lifter.liftAggressiveSync(str);
            StreamItDot.printGraph(str, "after-linear.dot");
        }
        return str;
    }
}
