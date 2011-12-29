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

/*
 * DestroyedClass.java
 *
 * Created on May 31, 2001, 4:50 PM
 * Classes that inherit from this class are supposed to have finalizers
 * called on their instances (in reverse order of descent).
 * 
 * Unfortunately: (1) finalization is not reliable.
 * (2) The only time to finalize to close FileWriters (which is one of the
 * few real uses of this class) is at the end of the program, but
 * System.runFinalizersAtExit(true) will cause the finalizes to be run
 * after some JVMs have already closed the files -- causing FileWriters to
 * throw exceptions.  Any other time is likely not to close a FileWriter.
 */

package streamit.misc;

import java.lang.reflect.Method;

public class DestroyedClass extends Misc
{
    private boolean Destroyed = false;
    private static Class DestroyedClass;


    // The class initializer initializes thisClass
    // to the appropriate value
    static {
        try
            {
                DestroyedClass = Class.forName ("streamit.misc.DestroyedClass");
            }
        catch (ClassNotFoundException error)
            {
                // This REALLY should not happen
                // just assert
                assert false;
            }
    }

    // The finalizer checks that the class has already been Destroyed,
    // and if not, it Destroys it
    @Override
	final protected void finalize ()
    {
        if (!Destroyed) Destroy ();
        Destroyed = true;
    }

    // DELETE member functions will be used
    // to provide the actual destructors
    public void DELETE () { }

    void Destroy ()
    {
        // make sure the object hasn't been Destroyed yet
        assert !Destroyed;
        Destroyed = true;

        Class objectClass = this.getClass ();
        assert objectClass != null;

        for ( ; objectClass != DestroyedClass ; objectClass = objectClass.getSuperclass ())
            {
                Method deleteMethod = null;

                try
                    {
                        deleteMethod = objectClass.getDeclaredMethod ("DELETE", (Class[])null);
                        assert deleteMethod != null;

                        deleteMethod.invoke (this, (Object[])null);
                    }
                catch (NoSuchMethodException error)
                    {
                        // do nothing, this isn't really an error
                        // just an annoying Java-ism
                    }

                // I hope I can just catch the rest of the exceptions here...
                catch (Throwable error)
                    {
                        // This REALLY shouldn't happen
                        // just assert
                        assert false;
                    }
            }
    }
}
