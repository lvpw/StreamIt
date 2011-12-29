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
 * A complex-valued expression.  This has two child expressions, which
 * are the real and imaginary parts.  Either child may be null, which
 * corresponds to a value of zero for that part.  (So a pure-real value
 * represented as an ExprComplex would have a null imaginary part.)  This
 * is intended to be used to construct simple expressions from the parse
 * tree, and then combine them into more complicated expressions with
 * fully-expanded real and imaginary parts.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: ExprComplex.java,v 1.6 2003-10-09 19:50:59 dmaze Exp $
 */
public class ExprComplex extends Expression
{
    private Expression real, imag;
    
    /**
     * Create a new ExprComplex with the specified real and imaginary
     * parts.  Either of real or imag may be null, for a purely real
     * or imaginary complex number.
     *
     * @param context  file and line number this expression corresponds to
     * @param real     real part of the complex expression, or null
     * @param imag     imaginary part of the complex expression or null
     */
    public ExprComplex(FEContext context, Expression real, Expression imag)
    {
        super(context);
        this.real = real;
        this.imag = imag;
    }
    
    /**
     * Returns the real part of this.  May return null if this is a
     * purely imaginary expression.
     *
     * @return the real part of the expression, or null
     */
    public Expression getReal() { return real; }  

    /**
     * Returns a non-null expression for the real part of this.  If
     * this is a purely imaginary expression, returns an expression
     * corresponding to zero.
     *
     * @return the real part of the expression
     */
    public Expression getRealExpr()
    {
        if (real != null)
            return real;
        return new ExprConstFloat(this.getContext(), 0.0f);
    }

    /**
     * Returns the imaginary part of this.  May return null if this is
     * a purely real expression.
     *
     * @return the imaginary part of the expression, or null
     */
    public Expression getImag() { return imag; }

    /**
     * Returns a non-null expression for the imaginary part of this.
     * If this is a purely real expression, returns an expression
     * corresponding to zero.
     *
     * @return the imaginary part of the expression
     */
    public Expression getImagExpr()
    {
        if (imag != null)
            return imag;
        return new ExprConstFloat(this.getContext(), 0.0f);
    }

    /** Accept a front-end visitor. */
    @Override
	public Object accept(FEVisitor v)
    {
        return v.visitExprComplex(this);
    }

    @Override
	public String toString()
    {
        return "((" + real + ")+(" + imag + ")i)";
    }
    
    @Override
	public boolean equals(Object other)
    {
        if (!(other instanceof ExprComplex))
            return false;
        ExprComplex that = (ExprComplex)other;
        if (!(this.real.equals(that.real)))
            return false;
        if (!(this.imag.equals(that.imag)))
            return false;
        return true;
    }
    
    @Override
	public int hashCode()
    {
        return real.hashCode() ^ imag.hashCode();
    }
}
