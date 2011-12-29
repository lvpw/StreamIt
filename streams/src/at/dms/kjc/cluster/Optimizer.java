
package at.dms.kjc.cluster;

//import at.dms.kjc.common.*;
//import at.dms.kjc.flatgraph.FlatNode;
//import at.dms.kjc.flatgraph.FlatVisitor;
import java.util.Hashtable;

import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFeedbackLoopIter;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.iterator.SIRPhasedFilterIter;
import at.dms.kjc.iterator.SIRPipelineIter;
import at.dms.kjc.iterator.SIRSplitJoinIter;
import at.dms.kjc.sir.SIRFeedbackLoop;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRPhasedFilter;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.StreamVisitor;
import at.dms.kjc.sir.lowering.BlockFlattener;
import at.dms.kjc.sir.lowering.DeadCodeElimination;
import at.dms.kjc.sir.lowering.Propagator;
import at.dms.kjc.sir.lowering.Unroller;
import at.dms.kjc.sir.lowering.VarDeclRaiser;

/**
 * A StreamVisitor that perfoms a sequence of optimizations.
 * 
 * <p>Currently only acts on non-phase filters.  All other constructs
 * are ignored.</p>
 * <p>For filters, it always flattens blocks, raises variable declarations
 * and eliminates dead code.
 * 
 * If the <code>nofieldprop</code> flag does not preclude propagation,
 * filters are also optimized by alternately unrolling and propagating until
 * the unroller reaches the limit of what it will unroll.</p>
 * 
 * <p>The name Optimizer may be a misnomer since this is nowhere near the 
 * complete suite of standard optimizations.</p>
 * @see Unroller
 * @see Propagator
 * @see BlockFlattener
 * @see VarDeclRaiser
 * @see DeadCodeElimination
 */
class Optimizer implements StreamVisitor {

    public Optimizer() {}

    public static void optimize(SIRStream str) {
        Optimizer est = new Optimizer();
        if (ClusterBackend.debugging)
            System.out.print("Optimizing filters...");
        IterFactory.createFactory().createIter(str).accept(est);
        if (ClusterBackend.debugging)
            System.out.println("done.");
    }

    public void visitFilter(SIRFilter filter,
                            SIRFilterIter iter) { 

        //unroll all methods
        //System.out.println("Optimizing "+filter+"...");

        for (int i = 0; i < filter.getMethods().length; i++) {
            JMethodDeclaration method=filter.getMethods()[i];
                Unroller unroller;
                do {
                    do {
                        unroller = new Unroller(new Hashtable());
                        method.accept(unroller);
                    } while(unroller.hasUnrolled());
            
                    method.accept(new Propagator(new Hashtable()));
                    unroller = new Unroller(new Hashtable());
                    method.accept(unroller);

                } while(unroller.hasUnrolled());

                method.accept(new BlockFlattener());
                method.accept(new Propagator(new Hashtable()));
               
            filter.getMethods()[i].accept(new VarDeclRaiser());
        }
           
        DeadCodeElimination.doit(filter);
    }

    public void visitPhasedFilter(SIRPhasedFilter self,
                                  SIRPhasedFilterIter iter) {
        // This is a stub; it'll get filled in once we figure out how phased
        // filters should actually work.
    }
    
    /**
     * PRE-VISITS 
     */
        
    /* pre-visit a pipeline */
    public void preVisitPipeline(SIRPipeline self, SIRPipelineIter iter) {}
    
    /* pre-visit a splitjoin */
    public void preVisitSplitJoin(SIRSplitJoin self, SIRSplitJoinIter iter) {}
    
    /* pre-visit a feedbackloop */
    public void preVisitFeedbackLoop(SIRFeedbackLoop self, SIRFeedbackLoopIter iter) {}
    
    /**
     * POST-VISITS 
     */
        
    /* post-visit a pipeline */
    public void postVisitPipeline(SIRPipeline self, SIRPipelineIter iter) {}
   
    /* post-visit a splitjoin */
    public void postVisitSplitJoin(SIRSplitJoin self, SIRSplitJoinIter iter) {}

    /* post-visit a feedbackloop */
    public void postVisitFeedbackLoop(SIRFeedbackLoop self, SIRFeedbackLoopIter iter) {}


}
