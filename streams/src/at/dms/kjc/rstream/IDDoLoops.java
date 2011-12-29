package at.dms.kjc.rstream;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;

import at.dms.kjc.Constants;
import at.dms.kjc.JAddExpression;
import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JBinaryExpression;
import at.dms.kjc.JBlock;
import at.dms.kjc.JCompoundAssignmentExpression;
import at.dms.kjc.JEmptyStatement;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionListStatement;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JForStatement;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JLocalVariable;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JPostfixExpression;
import at.dms.kjc.JPrefixExpression;
import at.dms.kjc.JRelationalExpression;
import at.dms.kjc.JStatement;
import at.dms.kjc.JVariableDeclarationStatement;
import at.dms.kjc.JVariableDefinition;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.util.Utils;

/**
 * This pass identifies java-style for loops that can be converted to 
 * fortran-style do loops. It should be run right before code generation
 * so that no other pass alters the for loops that are recognized and thus
 * invalidates the classification.
 *
 * @author Michael Gordon
 */

public class IDDoLoops extends SLIRReplacingVisitor implements FlatVisitor, Constants
{
    private int forLevel = 0;
    private HashMap<JLocalVariable, HashSet> varUses;
    private HashMap<JForStatement, DoLoopInformation> loops;
    private HashSet<JLocalVariable> inductionVars;

    /**
     * The entry point of this class, given a stream *top* and 
     * everything downstream of it, classify the for loop in each method of
     * each filter as to whether they can be converted to do loops.
     *
     * @param top The top level of the application
     */
    public static void doit(FlatNode top)
    {
        IDDoLoops doLoops = new IDDoLoops();
        top.accept(doLoops, null, true);
    }    

    public static void doit(SIRFilter filter) 
    {
        IDDoLoops doLoops = new IDDoLoops();
        doLoops.visitFilter(filter);
    }

    /**
     * Visit a flat node and iterate over all the methods 
     * if this is a filter flat node and check for 
     * do loops.
     *
     * @param node current flat node we are visiting
     *
     */
    @Override
	public void visitNode(FlatNode node) 
    {
        if (node.isFilter()) {
            visitFilter((SIRFilter)node.contents);
        }
    }
    
    private void visitFilter(SIRFilter filter) 
    {
        JMethodDeclaration[] methods = filter.getMethods();
        for (int i = 0; i < methods.length; i++) {
            varUses = UseDefInfo.getUsesMap(methods[i]);
            //iterate over the statements
            JBlock block = methods[i].getBody();
            for (int j = 0; j < block.getStatementArray().length; j++) {
                block.setStatement(j,
                                   (JStatement)block.getStatementArray()[j].accept(this));
                assert this.forLevel == 0;
            }
        
            //now go back thru the statements and delete the var defs
            //for the induction variables, but only if we are generating do loops
            if (KjcOptions.doloops) {
                JStatement[] statements = methods[i].getBody().getStatementArray();
                for (int k = 0; k < statements.length; k++) {
                    if (!(statements[k] instanceof JEmptyStatement || 
                          statements[k] instanceof JVariableDeclarationStatement))
                        break;
            
                    if (statements[k] instanceof JVariableDeclarationStatement) {
                        JVariableDeclarationStatement varDecl = 
                            (JVariableDeclarationStatement) statements[k];
                        Vector<JVariableDefinition> newVars = new Vector<JVariableDefinition>();
                        for (int j = 0; j < varDecl.getVars().length; j++) {
                            if (!inductionVars.contains(varDecl.getVars()[j]))
                                newVars.add(varDecl.getVars()[j]);
                        }
                        varDecl.setVars(newVars.toArray(new JVariableDefinition[0]));
                    }
                }
            }
        }
    }
    
    private IDDoLoops() 
    {
        forLevel = 0;
        loops = new HashMap<JForStatement, DoLoopInformation>();
        inductionVars = new HashSet<JLocalVariable>();
    }
    
    
    /**
     * See comments in method.
     */
    @Override
	public Object visitForStatement(JForStatement self,
                                    JStatement init,
                                    JExpression cond,
                                    JStatement incr,
                                    JStatement body) {
        boolean passed = false;
        JLocalVariable induction = null;
        //put the do loop information in this class...
        DoLoopInformation info = new DoLoopInformation();
        //we are inside a for loop
        forLevel++;
    
        //  System.out.println("----------------");
        if (init != null) {
            JExpression initExp = getExpression(init);
            //JAssignment Expression 
        
            //get the induction variable, simple calculation
            getInductionVariable(initExp, info);
            //check for method calls
            if (info.init != null && CheckForMethodCalls.check(info.init))
                info.init = null;
            //System.out.println("Induction Var: " + info.induction);
            //System.out.println("init exp: " + info.init);
            if (info.init == null)
                System.out.println("Couldn't resolve init in for loop");
        }
        if (cond != null && info.induction != null && info.init != null) {
            //get the condition statement and put it in the correct format
            getDLCondExpression(Utils.passThruParens(cond), info);
            //check for method calls in condition
            if (info.cond != null && CheckForMethodCalls.check(info.cond))
                info.cond = null;
            //      System.out.println("cond exp: " + info.cond);
            //cond.accept(this);
            if (info.cond == null)
                System.out.println("Couldn't resolve cond in for loop");        
        }
        //check the increment, only if there is one and we have passed 
        //everything above
        if (incr != null && info.induction != null && info.init != null &&
            info.cond != null) {
            //get the increment expression, and put it in the right format
            getDLIncrExpression(getExpression(incr), info);
            //check for method call in increment
            if (info.incr != null && CheckForMethodCalls.check(info.incr))
                info.incr = null;
            //      System.out.println("incr exp: " + info.incr);
            //incr.accept(this);
            if (info.incr == null)
                System.out.println("Couldn't resolve incr in for loop");
        }

        //check that we found everything as expected...
        if (info.init != null && info.induction != null && info.cond != null &&
            info.incr != null) {
            //the induction variable or any variables accessed in the cond or
            //increment are accessed in the body, and check for function calls
            //if their are fields or structures that included in this list of
            //variables
            if (IDDoLoopsCheckBody.check(info, body)) {
                //check that the induction variable is only used in the body of
                //the loop and no where outside the loop
                if (scopeOfInduction(self, info)) {
                    //everything passed add it to the hashmap
                    //System.out.println("Identified Do loop...");
                    loops.put(self, info);
                    inductionVars.add(info.induction);
                    passed = true;
                } else {
                    System.out.println("Induction used outside for loop");
                }
        
            } else {
                System.out.println("Induction variable assigned in for loop (or other weirdness in body)");
            }
        }
    
        //check for nested for loops that can be converted...
        JStatement newBody = (JStatement)body.accept(this);
        if (newBody!=null && newBody!=body) {
            self.setBody(newBody);
        }
    
        //leaving for loop
        forLevel--;
        assert forLevel >= 0;
    
        if (passed) {
            return new JDoLoopStatement(info.induction,
                                        info.init,
                                        info.cond,
                                        info.incr, newBody, true);
        }
        else {
            System.out.println("For Loop could not be converted to do loop");
            self.setBody(newBody);
            return self;
        }
    }
    
    /**
     * Check to see that the scope of the induction var is 
     * limited to the for loop, i.e. it is not used outside 
     * of the loop.
     *
     * @param jfor The for statement
     * @param doInfo The do loop information, used for to get induction variable
     *
     * @return True if all the uses or def of the induction variable 
     * are in the body of the for loop, false otherwise.
     */

    public boolean scopeOfInduction(JForStatement jfor, 
                                    DoLoopInformation doInfo) 
    {
        //check the scope of the induction variable...
        Iterator allInductionUses =
            varUses.get(doInfo.induction).iterator();
    
        //add all the uses in the for loop
        HashSet<Object> usesInForLoop = UseDefInfo.getForUses(jfor);
    
        while (allInductionUses.hasNext()) {
            Object use = allInductionUses.next();
            if (!usesInForLoop.contains(use)) {
                //System.out.println("Couldn't find " + use + " in loop.");
                return false;
            }
        
        }
        return true;
    }

    /**
     * Calculate the increment expression for the do loop and 
     * put it in the right format, so just return *exp* where
     * ind-var += *exp*.
     *
     * @param incrExp The orginal for loop increment expression
     * @param info The information on this do loop, place the 
     *  do loop increment expression in here.
     *
     */

    private void getDLIncrExpression(JExpression incrExp, 
                                     DoLoopInformation info)
    {
        if (Utils.passThruParens(incrExp) instanceof JBinaryExpression) {
            if (Utils.passThruParens(incrExp) instanceof JCompoundAssignmentExpression) {
                //compound assignment expression of the form left (op=) right 
                JCompoundAssignmentExpression comp = 
                    (JCompoundAssignmentExpression)Utils.passThruParens(incrExp);
                //make sure left is the induction variable
                if (Utils.passThruParens(comp.getLeft()) instanceof JLocalVariableExpression &&
                    ((JLocalVariableExpression)Utils.passThruParens(comp.getLeft())).
                    getVariable().equals(info.induction)) {
                    //return right if plus
                    if (comp.getOperation() == OPE_PLUS) {
                        info.incr = Utils.passThruParens(comp.getRight());
                    } /*else if (comp.getOperation() == OPE_MINUS) {
                        info.incr = new JExpressionStatement(null, 
                        new JUnaryMinusExpression(null, comp.getRight()),
                        null);
                        }*/
                }
            } //if an assignment expression of the form i = i + x or i = x + i, i is induction
            else if (Utils.passThruParens(incrExp) instanceof JAssignmentExpression &&
                     (Utils.passThruParens(((JAssignmentExpression)Utils.passThruParens(incrExp)).getLeft()) 
                      instanceof JLocalVariableExpression &&
                      ((JLocalVariableExpression)Utils.passThruParens(((JAssignmentExpression)Utils.
                                                                      passThruParens(incrExp)).getLeft())).
                      getVariable().equals(info.induction))) {
                //normal assignment expression left = right
                JAssignmentExpression ass = (JAssignmentExpression)Utils.passThruParens(incrExp);
                //only handle plus and minus 
                if (Utils.passThruParens(ass.getRight()) instanceof JAddExpression) {
                    //Util.passThruParens(ass.getRight()) instanceof JMinusExpression) {
                    JBinaryExpression bin = (JBinaryExpression)Utils.passThruParens(ass.getRight());
                    //if left of binary is an access to the induction variable
                    if (Utils.passThruParens(bin.getLeft()) instanceof JLocalVariableExpression &&
                        ((JLocalVariableExpression)Utils.passThruParens(bin.getLeft())).
                        getVariable().equals(info.induction)) {
                        //if plus return the right,
                        if (Utils.passThruParens(ass.getRight()) instanceof JAddExpression)
                            info.incr = Utils.passThruParens(bin.getRight());
                        /*if (ass.getRight() instanceof JMinusExpression)
                          info.incr = new JExpressionStatement(null,
                          new JUnaryMinusExpression(null, bin.getRight()),
                          null);*/
                    }
                    //analogue of above...
                    if (Utils.passThruParens(bin.getRight()) instanceof JLocalVariableExpression &&
                        ((JLocalVariableExpression)Utils.passThruParens(bin.getRight())).
                        getVariable().equals(info.induction)) {
                        if (Utils.passThruParens(ass.getRight()) instanceof JAddExpression)
                            info.incr = Utils.passThruParens(bin.getLeft());
                        /*if (Util.passThruParens(ass.getRight()) instanceof JMinusExpression)
                          info.incr = new JExpressionStatement(null, 
                          new JUnaryMinusExpression
                          (null, Util.passThruParens(bin.getLeft())),
                          null);*/
                    }
                }
            }
        }
        else if (Utils.passThruParens(incrExp) instanceof JPrefixExpression) {  //prefix op expr 
            JPrefixExpression pre = (JPrefixExpression)Utils.passThruParens(incrExp);
            //check that we assigning the induction variable
            if (Utils.passThruParens(pre.getExpr()) instanceof JLocalVariableExpression &&
                ((JLocalVariableExpression)Utils.passThruParens(pre.getExpr())).
                getVariable().equals(info.induction)) {
                if (pre.getOper() == OPE_PREINC) {
                    info.incr = new JIntLiteral(1);
                } /*else {
                    info.incr = new JExpressionStatement(null, new JIntLiteral(-1), null);
                    }   */  
            }
        }
        else if (Utils.passThruParens(incrExp) instanceof JPostfixExpression) { //postfix expr op
            JPostfixExpression post = (JPostfixExpression)Utils.passThruParens(incrExp);
            //check that we assigning the induction variable
            if (Utils.passThruParens(post.getExpr()) instanceof JLocalVariableExpression &&
                ((JLocalVariableExpression)Utils.passThruParens(post.getExpr())).
                getVariable().equals(info.induction)) {
                if (post.getOper() == OPE_POSTINC) {
                    info.incr = new JIntLiteral(1);
                } /*else {
                    info.incr = new JExpressionStatement(null, new JIntLiteral(-1), null);
                    }   */  
            }
        }
    }
    
    /**
     * Give the condition expression of the for loop, return the 
     * conditional expression for in do loop form. So just *expr*
     * in *induction_var* <= *expr* 
     *
     * @param condExp The condition expression of the for loop
     * @param info The do loop information as calculated so far
     */
    private void getDLCondExpression(JExpression condExp,
                                     DoLoopInformation info) 
    {
        //only handle binary relational expressions
        if (Utils.passThruParens(condExp) instanceof JRelationalExpression) {
            JRelationalExpression cond = (JRelationalExpression)Utils.passThruParens(condExp);
        
            //make sure that lhs is an access to the induction variable 
            if (Utils.passThruParens(cond.getLeft()) instanceof JLocalVariableExpression &&
                ((JLocalVariableExpression)Utils.passThruParens(cond.getLeft())).
                getVariable().equals(info.induction)) {
        
                //rhs is of type int
                switch (cond.getOper()) {
                case OPE_LT:
                    info.cond = cond.getRight();
                    break;
                case OPE_LE:
                    if (Utils.passThruParens(cond.getRight()) instanceof JIntLiteral) {
                        JIntLiteral right = (JIntLiteral)Utils.passThruParens(cond.getRight());
                        info.cond = new JIntLiteral(right.intValue() + 1);
                    }
                    else
                        info.cond = new JAddExpression(null, cond.getRight(),
                                                       new JIntLiteral(1));
            
                    break;
                    //don't handle > or >=, it is not supported yet
                    /*case OPE_GT:
                      info.cond = cond.getRight();
                      break;
                      case OPE_GE:
                      info.cond = new JAddExpression(null, cond.getRight(),
                      new JIntLiteral(1));
                      break;
                    */
                default:
                    return;
                }
            }
        }
    }
    
    /**
     * Given the initialization expression of a for loop, 
     * generate the initialization expression of the do loop,
     * if possible and place in info
     *
     * @param initExp the init expression of a for loop
     * @param info The empty do loop information
     *
     */
    private void getInductionVariable(JExpression initExp, 
                                      DoLoopInformation info) 
    {
        //make sure it is an assignment expression 
        //remember that all var defs have been lifted...
        if (Utils.passThruParens(initExp) instanceof JAssignmentExpression) {
            JAssignmentExpression ass = (JAssignmentExpression)Utils.passThruParens(initExp);
        
            //check that we are dealing with integers 
            if (!(ass.getLeft().getType().isOrdinal() && 
                  ass.getRight().getType().isOrdinal()))
                return;
        
            //check that the left is a variable expression
            if (Utils.passThruParens(ass.getLeft()) instanceof JLocalVariableExpression) {
                //set the induction variable
                info.induction = 
                    ((JLocalVariableExpression)Utils.passThruParens(ass.getLeft())).getVariable();
            }
            else 
                return;
        
            //set the initialization statement of the for loop...
            info.init = ass.getRight();
        }
    }
    

    /**
     * Given a statement, return the expression that this statement is 
     * composed of, if not an expression statement return null.
     *
     *
     * @param orig The statement
     *
     *
     * @return null if *orig* does not contain an expression or
     * the expression if it does.
     */
    public static JExpression getExpression(JStatement orig)
    {
        if (orig instanceof JExpressionListStatement) {
            JExpressionListStatement els = (JExpressionListStatement)orig;
            if (els.getExpressions().length == 1)
                return Utils.passThruParens(els.getExpression(0));
            else
                return null;
        }
        else if (orig instanceof JExpressionStatement) {
            return Utils.passThruParens(((JExpressionStatement)orig).getExpression());
        }
        else 
            return null;
    }
}
