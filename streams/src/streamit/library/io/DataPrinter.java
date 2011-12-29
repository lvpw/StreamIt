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

package streamit.library.io;

import streamit.library.Channel;
import streamit.library.Filter;

public class DataPrinter extends Filter
{
    Class fileType;

    public DataPrinter (Class type)
    {
        fileType = type;
    }

    public void init ()
    {
        inputChannel = new Channel (fileType, 1);
    }

    public void work ()
    {
        try
            {
                if (fileType == Character.TYPE)
                    {
                        System.out.print (inputChannel.popChar () + ", ");
                    } else
                        if (fileType == Float.TYPE)
                            {
                                System.out.print (inputChannel.popFloat () + ", ");
                            } else
                                if (fileType == Integer.TYPE)
                                    {
                                        System.out.print (inputChannel.popInt () + ", ");
                                    } else
                                        if (Class.forName ("java.io.Serializable").isAssignableFrom (fileType))
                                            {
                                                System.out.print (inputChannel.pop () + ", ");
                                            } else
                                                {
                                                    ERROR ("You must define a writer for your type here.\nIf you're trying to write an object, it should be a serializable object\n(and then you won't have to do anything special).");
                                                }
            }
        catch (Throwable e)
            {
                ERROR ("There was an error reading from a file");
            }
    }
}
