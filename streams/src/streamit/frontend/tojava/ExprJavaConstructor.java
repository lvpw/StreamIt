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

package streamit.frontend.tojava;

import streamit.frontend.nodes.Expression;
import streamit.frontend.nodes.FEContext;
import streamit.frontend.nodes.FEVisitor;
import streamit.frontend.nodes.Type;

/**
 * A Java constructor expression.  This appears in this package
 * because it is only used for frontend to Java conversion; specifically,
 * it needs to appear at the front of init functions, before anything
 * can use variables that need to have constructors.  This is just
 * a Java 'new' expression for some single type.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: ExprJavaConstructor.java,v 1.3 2005-12-23 15:01:05 dimock Exp $
 */
public class ExprJavaConstructor extends Expression
{
    private Type type;
    
    public ExprJavaConstructor(FEContext context, Type type)
    {
        super(context);
        this.type = type;
    }
    
    public Type getType()
    {
        return type;
    }
    
    @Override
	public Object accept(FEVisitor v)
    {
        return v.visitOther(this);
    }
}

    
