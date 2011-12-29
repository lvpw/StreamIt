/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.frontend.nodes;

/**
 * A unary expression.  This is a child expression with a modifier;
 * it could be "!a" or "-b" or "c++".
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: ExprUnary.java,v 1.6 2006-01-25 17:04:25 thies Exp $
 */
public class ExprUnary extends Expression
{
    // Operators:
    public static final int UNOP_NOT = 1;
    public static final int UNOP_NEG = 2;
    public static final int UNOP_PREINC = 3;
    public static final int UNOP_POSTINC = 4;
    public static final int UNOP_PREDEC = 5;
    public static final int UNOP_POSTDEC = 6;
    public static final int UNOP_COMPLEMENT = 7;

    private int op;
    private Expression expr;
    
    /** Creates a new ExprUnary applying the specified operator to the
     * specified expression. */
    public ExprUnary(FEContext context, int op, Expression expr)
    {
        super(context);
        this.op = op;
        this.expr = expr;
    }
    
    /** Returns the operator of this. */
    public int getOp() { return op; }
    
    /** Returns the expression this modifies. */
    public Expression getExpr() { return expr; }
    
    /** Accept a front-end visitor. */
    @Override
	public Object accept(FEVisitor v)
    {
        return v.visitExprUnary(this);
    }

    @Override
	public boolean equals(Object other)
    {
        if (!(other instanceof ExprUnary))
            return false;
        ExprUnary eu = (ExprUnary)other;
        if (op != eu.getOp())
            return false;
        if (!(expr.equals(eu.getExpr())))
            return false;
        return true;
    }
    
    @Override
	public int hashCode()
    {
        return new Integer(op).hashCode() ^ expr.hashCode();
    }
    
    @Override
	public String toString()
    {
        String preOp = "", postOp = "";
        switch(op)
            {
            case UNOP_NOT: preOp = "!"; break;
            case UNOP_NEG: preOp = "-"; break;
            case UNOP_PREINC: preOp = "++"; break;
            case UNOP_POSTINC: postOp = "++"; break;
            case UNOP_PREDEC: preOp = "--"; break;
            case UNOP_POSTDEC: postOp = "--"; break;
            case UNOP_COMPLEMENT: preOp = "~"; break;

            default: preOp = "?(" + op + ")"; break;
            }
        return preOp + "(" + expr.toString() + ")" + postOp;
    }
}
