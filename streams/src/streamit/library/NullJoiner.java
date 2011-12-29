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

package streamit.library;

public class NullJoiner extends Joiner
{
    @Override
	public void work ()
    {
        // a null joiner should never have its work function called
        ERROR ("work function called in a NullJoiner");  
    }
    
    @Override
	public int [] getWeights ()
    {
        // null joiners do not distribute any weights
        throw new UnsupportedOperationException();
    }
    
    @Override
	public int getProduction () { return 0; }

    @Override
	public String toString() {
        return "roundrobin(0)";
    }
}
