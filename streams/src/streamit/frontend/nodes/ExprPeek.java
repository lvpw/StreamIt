/*
 * ExprPeek.java: a StreamIt peek expression
 * David Maze <dmaze@cag.lcs.mit.edu>
 * $Id: ExprPeek.java,v 1.2 2002-08-20 20:04:28 dmaze Exp $
 */

package streamit.frontend.nodes;

/**
 * A StreamIt peek expression.  This returns a single item off of the current
 * filter's input tape, without removing it; its type is the input type of
 * the filter.  This expression has a single child expression indicating
 * which item off the tape is to be read.
 */
public class ExprPeek extends Expression
{
    private Expression expr;
    
    /** Creates a new pop expression. */
    public ExprPeek(FEContext context, Expression expr)
    {
        super(context);
        this.expr = expr;
    }

    /** Returns the position on the tape to be read. */
    public Expression getExpr()
    {
        return expr;
    }
    
    /** Accept a front-end visitor. */
    public Object accept(FEVisitor v)
    {
        return v.visitExprPeek(this);
    }
}
