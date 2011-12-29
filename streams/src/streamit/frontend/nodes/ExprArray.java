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
 * An array-element reference.  This is an expression like
 * <code>a[n]</code>.  There is a base expression (the "a") and an
 * offset expresion (the "n").
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: ExprArray.java,v 1.6 2006-12-08 21:58:39 dimock Exp $
 */
public class ExprArray extends Expression
{
    private Expression base, offset;
    
    /** Creates a new ExprArray with the specified base and offset. */
    public ExprArray(FEContext context, Expression base, Expression offset)
    {
        super(context);
        this.base = base;
        this.offset = offset;
    }
    
    /** Returns the base expression of this. */
    public Expression getBase() { return base; }

    /** @return the component expression of this. 
     * (Throws ClassCastException if passed anything other than a variable or an array)*/
    public ExprVar getComponent() {
        Expression comp = this;
        while (comp instanceof ExprArray)
            comp = ((ExprArray) comp).getBase();
        return (ExprVar) comp;
    }

    /** Returns the offset expression of this. */
    public Expression getOffset() { return offset; }
    
    /** Accept a front-end visitor. */
    @Override
	public Object accept(FEVisitor v)
    {
        return v.visitExprArray(this);
    }

    /**
     * Determine if this expression can be assigned to.  Array
     * elements can always be assigned to.
     *
     * @return always true
     */
    @Override
	public boolean isLValue()
    {
        return true;
    }

    @Override
	public String toString()
    {
        return base + "[" + offset + "]";
    }
    
    @Override
	public int hashCode()
    {
        return base.hashCode() ^ offset.hashCode();
    }
    
    @Override
	public boolean equals(Object o)
    {
        if (!(o instanceof ExprArray))
            return false;
        ExprArray ao = (ExprArray)o;
        return (ao.base.equals(base) && ao.offset.equals(offset));
    }
}
