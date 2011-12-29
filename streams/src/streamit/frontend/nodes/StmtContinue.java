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
 * A simple continue statement.  This statement jumps to evaluating
 * the condition if the innermost loop is a (do/)while loop, or to the
 * increment statement if the innermost loop is a for loop.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: StmtContinue.java,v 1.4 2004-01-16 21:18:39 dmaze Exp $
 */
public class StmtContinue extends Statement
{
    /** Creates a new continue statement. */
    public StmtContinue(FEContext context)
    {
        super(context);
    }

    /** Accept a front-end visitor. */
    @Override
	public Object accept(FEVisitor v)
    {
        return v.visitStmtContinue(this);
    }

    @Override
	public boolean equals(Object other)
    {
        // No state; any two continue statements are equal.
        if (other instanceof StmtContinue)
            return true;
        return false;
    }

    @Override
	public int hashCode()
    {
        // No state, so...
        return 17;
    }
    
    @Override
	public String toString()
    {
        return "continue";
    }
}

