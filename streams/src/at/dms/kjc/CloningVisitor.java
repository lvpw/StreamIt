package at.dms.kjc;

import java.util.HashSet;

import at.dms.compiler.JavaStyleComment;
import at.dms.kjc.iterator.IterFactory;
import at.dms.kjc.iterator.SIRFeedbackLoopIter;
import at.dms.kjc.iterator.SIRFilterIter;
import at.dms.kjc.iterator.SIRPhasedFilterIter;
import at.dms.kjc.iterator.SIRPipelineIter;
import at.dms.kjc.iterator.SIRSplitJoinIter;
import at.dms.kjc.sir.SIRFeedbackLoop;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRInitStatement;
import at.dms.kjc.sir.SIRPhasedFilter;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.StreamVisitor;

/**
 * This descends through a stream hierarchy and identifies local
 * variables and stream structures that SHOULD be cloned (since their
 * definition is within the hierarchy).
 */
public class CloningVisitor extends SLIREmptyVisitor implements StreamVisitor {

    /**
     * A list of things that *should* be cloned.  Currently the
     * following types should not be cloned unless they are an
     * element of this list:
     *   - SIRContainer
     *   - JLocalVariable
     */
    private HashSet<DeepCloneable> toBeCloned;

    public CloningVisitor() {
        this.toBeCloned = new HashSet<DeepCloneable>();
    }
    
    /**
     * Used by deepCopy(int offset,JBlock oldObj) above
     */
    public void visitBlockStatement(int offset,
                                    JBlock self,
                                    JavaStyleComment[] comments) {
        for(;offset<self.size();offset++) {
            self.getStatement(offset).accept(this);
        }
    }

    /**
     * Return the list of what should be cloned.
     */
    public HashSet<DeepCloneable> getToBeCloned() {
        return toBeCloned;
    }

    /**
     * Right now the super doesn't visit the variable in a jlocal var,
     * but make sure we don't, either.
     */
    public void visitLocalVariableExpression(JLocalVariableExpression self,
                                             String ident) {
    }

    /**
     * Visits a variable decl.
     */
    public void visitVariableDefinition(JVariableDefinition self,
                                        int modifiers,
                                        CType type,
                                        String ident,
                                        JExpression expr) {
        super.visitVariableDefinition(self, modifiers, type, ident, expr);
        // record that we should clone this, since we reached it
        toBeCloned.add(self);
    }
    
    /**
     * visits a formal param.
     */
    public void visitFormalParameters(JFormalParameter self,
                                      boolean isFinal,
                                      CType type,
                                      String ident) {
        super.visitFormalParameters(self, isFinal, type, ident);
        // record that we should clone this, since we reached it
        toBeCloned.add(self);
    }

    /**
     * Visits an init statement (recurses into the target stream)
     */
    public void visitInitStatement(SIRInitStatement self,
                                   SIRStream target) {
        super.visitInitStatement(self, target);
        // also recurse into the stream target
        IterFactory.createFactory().createIter(target).accept(this);
    }

    /**
     * PLAIN-VISITS 
     */

    /**
     * For visiting all fields and methods of SIRStreams.
     */
    private void visitStream(SIRStream stream) {
        // visit the methods
        JMethodDeclaration[] methods = stream.getMethods();
        if (methods != null) {
            for (int i=0; i<methods.length; i++) {
                methods[i].accept(this);
            }
        }
        // visit the fields
        JFieldDeclaration[] fields = stream.getFields();
        for (int i=0; i<fields.length; i++) {
            fields[i].accept(this);
        }
    }
        
    /* visit a filter */
    public void visitFilter(SIRFilter self,
                            SIRFilterIter iter) {
        // visit node
        visitStream(self);
    }

    /* visit a phased filter */
    public void visitPhasedFilter(SIRPhasedFilter self,
                                  SIRPhasedFilterIter iter) {
        visitStream(self);
    }
  
    /**
     * PRE-VISITS 
     */
        
    /* pre-visit a pipeline */
    public void preVisitPipeline(SIRPipeline self,
                                 SIRPipelineIter iter) {
        // record this container as one that should be cloned
        toBeCloned.add(self);
        // visit node
        visitStream(self);
    }

    /* pre-visit a splitjoin */
    public void preVisitSplitJoin(SIRSplitJoin self,
                                  SIRSplitJoinIter iter) {
        // record this container as one that should be cloned
        toBeCloned.add(self);
        // visit node
        visitStream(self);
    }

    /* pre-visit a feedbackloop */
    public void preVisitFeedbackLoop(SIRFeedbackLoop self,
                                     SIRFeedbackLoopIter iter) {
        // record this container as one that should be cloned
        toBeCloned.add(self);
        // visit node
        visitStream(self);
    }

    /**
     * POST-VISITS 
     */
        
    /* post-visit a pipeline -- do nothing, visit on way down */
    public void postVisitPipeline(SIRPipeline self,
                                  SIRPipelineIter iter) {
    }

    /* post-visit a splitjoin -- do nothing, visit on way down */
    public void postVisitSplitJoin(SIRSplitJoin self,
                                   SIRSplitJoinIter iter) {
    }

    /* post-visit a feedbackloop -- do nothing, visit on way down */
    public void postVisitFeedbackLoop(SIRFeedbackLoop self,
                                      SIRFeedbackLoopIter iter) {
    }
}
