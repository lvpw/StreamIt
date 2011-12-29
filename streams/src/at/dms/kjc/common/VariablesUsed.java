package at.dms.kjc.common;

import java.io.Serializable;
import java.util.HashSet;

import at.dms.kjc.JArrayAccessExpression;
import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JCompoundAssignmentExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JFieldAccessExpression;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.JPhylum;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.SLIREmptyVisitor;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.sir.SIRFilter;
import at.dms.util.Utils;

/**
 * This class will return a HashSet containing all the
 * variables (locals and fields) used (ignoring defines)
 *
 * @author Michael Gordon
 * 
 */

public class VariablesUsed extends SLIREmptyVisitor
{
    private HashSet<Serializable> vars;
    /** 
     * if this is true, and a variable is assigned to a complex expression 
     * (assiged to something other than a literal), count the variables as used
     * because the expression may have side-effects.
     **/
    private boolean countComplexAss;

    /**
     * Return a hashset of all the variables that are used in this tree, meaning 
     * all the variables that it is important to keep because they are not dead.
     *
     *
     * @param entry the root of the tree,
     * @param countComplexAssignments if this is true, and a variable is assigned
     * to a complex expression 
     * (assiged to something other than a literal), count the variables as used
     * because the expression may have side-effects.
     *
     * @return the hash set of JLocalVariables or Strings (for fields)
     */

    public static HashSet<Serializable> getVars(JPhylum entry, boolean countComplexAssignments) 
    {
        VariablesUsed used = new VariablesUsed(countComplexAssignments);
    
        entry.accept(used);
    
        return used.vars;
    }
    
    /**
     * Return a hashset of all the variables that are used in flatnode, meaning 
     * all the variables that it is important to keep because they are not dead.
     *
     * @param node The flatnode to visit
     * @param countComplexAssignments if this is true, and a variable is assigned
     * to a complex expression 
     * (assiged to something other than a literal), count the variables as used
     * because the expression may have side-effects.
     *
     * @return the hash set of JLocalVariables or Strings (for fields)
     */
    public static HashSet<Serializable> getVars(FlatNode node, boolean countComplexAssignments)  
    {
        if (node.isFilter()) {
            return getVars((SIRFilter)node.contents, countComplexAssignments);
        }
    
        return new HashSet<Serializable>();
    }
    
    public static HashSet<Serializable> getVars(SIRFilter filter, boolean countComplexAssignments)  
    {
        VariablesUsed used = new VariablesUsed(countComplexAssignments);
    
        for (int i = 0; i < filter.getMethods().length; i++) {
            filter.getMethods()[i].accept(used);
        }
        for (int i = 0; i < filter.getFields().length; i++) {
            filter.getFields()[i].accept(used);
        }
    
        return used.vars;
    }
    
    private VariablesUsed(boolean complexAss) 
    {
        vars = new HashSet<Serializable>();
        countComplexAss = complexAss;
    }
    


    public void visitFieldExpression(JFieldAccessExpression self,
                                     JExpression left,
                                     String ident) 
    {
        if (!(left instanceof JThisExpression)) {
            left.accept(this);
            return;
        }
        vars.add(ident);
    }

    public void visitLocalVariableExpression(JLocalVariableExpression self,
                                             String ident) 
    {
        vars.add(self.getVariable());
    }
    
    /**
     * prints an assignment expression
     */
    public void visitAssignmentExpression(JAssignmentExpression self,
                                          JExpression left,
                                          JExpression right) {
        //count a complex right expression as a use for the left
        if (countComplexAss) {
            //there are no side effects, so never
            //count it as a use for the left
            if (!HasSideEffects.hasSideEffects(right) &&
                !HasSideEffects.hasSideEffects(left))
                visitLValue(left);
            else  //otherwise a use for the left
                left.accept(this);
        }
        else //don't count a complex right expression as a use for the left
            visitLValue(left);

        right.accept(this);
    }
    

    /**
     * prints a compound expression
     */
    public void visitCompoundAssignmentExpression(JCompoundAssignmentExpression self,
                                                  int oper,
                                                  JExpression left,
                                                  JExpression right) {
    
        //count a complex right expression as a use for the left
        if (countComplexAss) {
            //there are no side effects, so never
            //count it as a use for the left
            if (!HasSideEffects.hasSideEffects(right) && 
                !HasSideEffects.hasSideEffects(left))
                visitLValue(left);
            else  //otherwise a use for the left
                left.accept(this);
        }
        else //don't count a complex right expression as a use for the left
            visitLValue(left);

        right.accept(this);
    }

    private void visitLValue(JExpression expr) 
    {
        //for an array access expression only record the 
        //accessors as being referenced...
        if (Utils.passThruParens(expr) instanceof JArrayAccessExpression) {
            ((JArrayAccessExpression)expr).getAccessor().accept(this);
            visitLValue(((JArrayAccessExpression)expr).getPrefix());
        }
    }    
}
