/*
 * FEVisitor.java: visit a tree of front-end nodes
 * David Maze <dmaze@cag.lcs.mit.edu>
 * $Id: FEVisitor.java,v 1.8 2003-01-10 18:22:01 dmaze Exp $
 */

package streamit.frontend.nodes;

/**
 * A FEVisitor implements part of the "visitor" design pattern for
 * StreamIt front-end nodes.  The pattern basically exchanges type
 * structures for function calls, so a different function in the visitor
 * is called depending on the run-time type of the object being visited.
 * Calling visitor methods returns some value, the type of which
 * depends on the semantics of the visitor in question.  In general,
 * you will create a visitor object, and then pass it to the accept()
 * method of the object in question.
 */
public interface FEVisitor
{
    public Object visitExprArray(ExprArray exp);
    public Object visitExprBinary(ExprBinary exp);
    public Object visitExprComplex(ExprComplex exp);
    public Object visitExprConstChar(ExprConstChar exp);
    public Object visitExprConstFloat(ExprConstFloat exp);
    public Object visitExprConstInt(ExprConstInt exp);
    public Object visitExprConstStr(ExprConstStr exp);
    public Object visitExprField(ExprField exp);
    public Object visitExprFunCall(ExprFunCall exp);
    public Object visitExprPeek(ExprPeek exp);
    public Object visitExprPop(ExprPop exp);
    public Object visitExprTernary(ExprTernary exp);
    public Object visitExprUnary(ExprUnary exp);
    public Object visitExprVar(ExprVar exp);
    public Object visitFunction(Function func);
    public Object visitFuncWork(FuncWork func);
    public Object visitProgram(Program prog);
    public Object visitSCAnon(SCAnon creator);
    public Object visitSCSimple(SCSimple creator);
    public Object visitSJDuplicate(SJDuplicate sj);
    public Object visitSJRoundRobin(SJRoundRobin sj);
    public Object visitSJWeightedRR(SJWeightedRR sj);
    public Object visitStmtAdd(StmtAdd stmt);
    public Object visitStmtAssign(StmtAssign stmt);
    public Object visitStmtBlock(StmtBlock stmt);
    public Object visitStmtBody(StmtBody stmt);
    public Object visitStmtBreak(StmtBreak stmt);
    public Object visitStmtContinue(StmtContinue stmt);
    public Object visitStmtDoWhile(StmtDoWhile stmt);
    public Object visitStmtEnqueue(StmtEnqueue stmt);
    public Object visitStmtExpr(StmtExpr stmt);
    public Object visitStmtFor(StmtFor stmt);
    public Object visitStmtIfThen(StmtIfThen stmt);
    public Object visitStmtJoin(StmtJoin stmt);
    public Object visitStmtLoop(StmtLoop stmt);
    public Object visitStmtPhase(StmtPhase stmt);
    public Object visitStmtPush(StmtPush stmt);
    public Object visitStmtReturn(StmtReturn stmt);
    public Object visitStmtSplit(StmtSplit stmt);
    public Object visitStmtVarDecl(StmtVarDecl stmt);
    public Object visitStmtWhile(StmtWhile stmt);
    public Object visitStreamSpec(StreamSpec spec);
    public Object visitStreamType(StreamType type);
    public Object visitOther(FENode node);
}
