/*
 * ExprPop.java: a StreamIt pop expression
 * David Maze <dmaze@cag.lcs.mit.edu>
 * $Id: ExprPop.java,v 1.2 2002-08-20 20:04:28 dmaze Exp $
 */

package streamit.frontend.nodes;

/**
 * A StreamIt pop expression.  This pops a single item off of the current
 * filter's input tape; its type is the input type of the filter.  This
 * expression has no internal state.
 */
public class ExprPop extends Expression
{
    /** Creates a new pop expression. */
    public ExprPop(FEContext context)
    {
        super(context);
    }
    
    /** Accept a front-end visitor. */
    public Object accept(FEVisitor v)
    {
        return v.visitExprPop(this);
    }
}
