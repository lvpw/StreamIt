package at.dms.kjc.sir.lowering;

import at.dms.kjc.CType;
import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JBlock;
import at.dms.kjc.JEmptyStatement;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JReturnStatement;
import at.dms.kjc.JStatement;
import at.dms.kjc.JUnqualifiedInstanceCreation;
import at.dms.kjc.SLIREmptyVisitor;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.iterator.SIRFeedbackLoopIter;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.iterator.SIRIterator;
import at.dms.kjc.iterator.SIRPhasedFilterIter;
import at.dms.kjc.iterator.SIRPipelineIter;
import at.dms.kjc.iterator.SIRSplitJoinIter;
import at.dms.kjc.lir.LIRWorkEntry;
import at.dms.kjc.lir.LIRWorkExit;
import at.dms.kjc.sir.SIRFeedbackLoop;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRPeekExpression;
import at.dms.kjc.sir.SIRPhasedFilter;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRPopExpression;
import at.dms.kjc.sir.SIRPushExpression;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.StreamVisitor;

/**
 * This class adds LIR hooks to work functions.
 */
public class LowerWorkFunctions implements StreamVisitor 
{
    /**
     * Lowers the work functions in <str>.
     */
    public static void lower(SIRIterator iter)
    {
        iter.accept(new LowerWorkFunctions());
    }
    
    private void addEntryExit(JMethodDeclaration method)
    {
        // add the entry node before the variable declarations, since
        // the variable declarations might include initializations
        // that read from the input tape.
        method.addStatementFirst(new LIRWorkEntry
                                 (LoweringConstants.getStreamContext()));
        // append an exit node
        method.addStatement(new LIRWorkExit
                            (LoweringConstants.getStreamContext()));
        // prepend exit nodes before any return statements, too
        method.accept(new SLIRReplacingVisitor() {
                @Override
				public Object visitReturnStatement(JReturnStatement self,
                                                   JExpression expr)
                {
                    JStatement[] stmts = new JStatement[2];
                    stmts[0] =
                        new LIRWorkExit(LoweringConstants.getStreamContext());
                    stmts[1] = self;
                    return new JBlock(self.getTokenReference(), stmts, null);
                }
            });
    }

    private void removeStructureNew(JMethodDeclaration method)
    {
        method.accept(new SLIRReplacingVisitor() {
                @Override
				public Object visitExpressionStatement(JExpressionStatement self,
                                                       JExpression expr)
                {
                    if (!(expr instanceof JAssignmentExpression))
                        return self;
                    JAssignmentExpression assn = (JAssignmentExpression)expr;
                    JExpression right = assn.getRight();
                    if (right instanceof JUnqualifiedInstanceCreation)
                        return new JEmptyStatement(self.getTokenReference(),
                                                   self.getComments());
                    return self;
                }
            });
    }
    
    /**
     * PLAIN-VISITS 
     */
    
    /* visit a filter */
    @Override
	public void visitFilter(SIRFilter self,
                            SIRFilterIter iter) {
        doGeneralFilter(self);
    }

    /* visit a phased filter */
    @Override
	public void visitPhasedFilter(SIRPhasedFilter self,
                                  SIRPhasedFilterIter iter) {
        doGeneralFilter(self);
    }

    private void doGeneralFilter(SIRPhasedFilter self) {
        // only worry about actual SIRFilter's, not special cases like
        // FileReader's and FileWriter's
        if (!self.needsWork()) {
            return;
        }
        // dismantle arrays
        new ArrayDestroyer().destroyArrays(self);
        for (int i = 0; i < self.getMethods().length; i++)
            {
                self.getMethods()[i].accept(new VarDeclRaiser());
            }
        // current limitation: DeadCode only works for plain
        // SIRFilters, not SIRPhasedFilters
        if (self instanceof SIRFilter) {
            DeadCodeElimination.doit(self);
        }

        addEntryExits(self);
    }

    /**
     * add entry/exit nodes to any function directly doing I/O
     */
    private void addEntryExits(SIRPhasedFilter self) {
        // search for peek/pop statements instead of looking at
        // declared I/O rates because we don't want decls for
        // hierarchical functions.  Note: hierarchical functions still
        // cause problems (those that call pop() and also call a
        // phase.)
        for (int i=0; i<self.getMethods().length; i++) {
            JMethodDeclaration method = self.getMethods()[i];
            final boolean[] IO = { false };
            method.accept(new SLIREmptyVisitor() {
                    @Override
					public void visitPopExpression(SIRPopExpression self, CType tapeType) {
                        IO[0] = true;
                    }
                    @Override
					public void visitPeekExpression(SIRPeekExpression self, CType tapeType, JExpression arg) {
                        IO[0] = true;
                    }
                    @Override
					public void visitPushExpression(SIRPushExpression self, CType tapeType, JExpression arg) {
                        IO[0] = true;
                    }
                });
        
            if (IO[0]) {
                addEntryExit(method);
                removeStructureNew(method);
            }
        }
        // also remove structures from init functions
        removeStructureNew(self.getInit());
    }
    
    /**
     * PRE-VISITS 
     */
        
    /* pre-visit a pipeline */
    @Override
	public void preVisitPipeline(SIRPipeline self,
                                 SIRPipelineIter iter) {
        removeStructureNew(self.getInit());
    }
  
    /* pre-visit a splitjoin */
    @Override
	public void preVisitSplitJoin(SIRSplitJoin self,
                                  SIRSplitJoinIter iter) {
        removeStructureNew(self.getInit());
    }
  
    /* pre-visit a feedbackloop */
    @Override
	public void preVisitFeedbackLoop(SIRFeedbackLoop self,
                                     SIRFeedbackLoopIter iter) {
        removeStructureNew(self.getInit());
    }
  
    /**
     * POST-VISITS 
     */

    /* post-visit a pipeline */
    @Override
	public void postVisitPipeline(SIRPipeline self,
                                  SIRPipelineIter iter) { 
    }
  
    /* post-visit a splitjoin */
    @Override
	public void postVisitSplitJoin(SIRSplitJoin self,
                                   SIRSplitJoinIter iter) {
    }
  
    /* post-visit a feedbackloop */
    @Override
	public void postVisitFeedbackLoop(SIRFeedbackLoop self,
                                      SIRFeedbackLoopIter iter) {
    }
}
