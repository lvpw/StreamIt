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

import java.util.List;

/**
 * A variable-weight round-robin splitter or joiner.  This has a list
 * of expressions, each of which is a number of items to take or give
 * to a given tape.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: SJWeightedRR.java,v 1.4 2006-09-25 13:54:54 dimock Exp $
 */
public class SJWeightedRR extends SplitterJoiner
{
    private List<Expression> weights;
    
    /** Creates a new round-robin splitter or joiner with the specified
     * weights. */
    public SJWeightedRR(FEContext context, List<Expression> weights)
    {
        super(context);
        this.weights = weights;
    }

    /** Returns the list of round-robin weights. */
    public List<Expression> getWeights()
    {
        return weights;
    }
    
    /** Accept a front-end visitor. */
    @Override
	public Object accept(FEVisitor v)
    {
        return v.visitSJWeightedRR(this);
    }
}
