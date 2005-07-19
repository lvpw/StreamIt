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

import streamit.frontend.nodes.*;

import java.util.Iterator;
import java.util.List;

import java.util.ArrayList;

/**
 * Insert I/O rate declarations into filters' init functions.  For
 * programs destined for the Java library, this also inserts the phase
 * declarations into the init function.  For programs headed for the
 * StreamIt compiler, phase construction happens implicitly with calls
 * inserted in <code>NodesToJava</code>.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: InsertIODecls.java,v 1.10 2005-07-19 18:09:12 madrake Exp $
 */
public class InsertIODecls extends InitMunger
{
    private boolean libraryFormat;
    
    /**
     * Creates a new pass object.  If <code>library</code> is true,
     * create code suitable for the Java library.  This affects the
     * output of phased filters; a filter's work function must either
     * declare I/O rates or consist entirely of phase calls for
     * library-form output.
     *
     * @param library  Generate code for the Java library
     */
    public InsertIODecls(boolean library)
    {
        libraryFormat = library;
    }

    /**
     * Given a list of functions, find a work function.  If the list
     * contains an anonymous work function, return that; otherwise,
     * return the first work function, or null if there are no work
     * functions at all.
     *
     * @param fns   List of functions to search
     * @return      Primary work function in fns
     */
    public static Function findWork(List fns, boolean init)
    {
        Function work = null;
        for (Iterator iter = fns.iterator(); iter.hasNext(); )
        {
            Function fn = (Function)iter.next();
            int cls = init ? Function.FUNC_PREWORK : Function.FUNC_WORK;
            if (fn.getCls() == cls)
            {
                if (fn.getName() == null)
                    work = fn;
                if (work == null)
                    work = fn;
            }
        }
        return work;
    }

    public Object visitStreamSpec(StreamSpec spec)
    {
        spec = (StreamSpec)super.visitStreamSpec(spec);

        // Only visit filters.
        if (spec.getType() != StreamSpec.STREAM_FILTER)
            return spec;

        if (libraryFormat)
            return ssLibrary(spec);
        else
            return ssCompiler(spec);
    }
    
    /** Do the rewrite work for the library flow. */
    private Object ssLibrary(StreamSpec spec)
    {
        List fns = new ArrayList(spec.getFuncs());
        StreamType st = spec.getStreamType();
        List newStmts = new ArrayList();
        newStmts.add(new StmtSetTypes(spec.getContext(), st));
        translateWork(spec, (FuncWork)findWork(fns, true), true, newStmts);
        translateWork(spec, (FuncWork)findWork(fns, false), false, newStmts);
        fns = replaceInitWithPrepended(spec.getContext(), fns, newStmts);
        
        return new StreamSpec(spec.getContext(), spec.getType(),
                              spec.getStreamType(), spec.getName(),
                              spec.getParams(), spec.getVars(), fns);
    }

    private void translateWork(StreamSpec spec, FuncWork work,
                               boolean init, List newStmts)
    {
        // Do nothing if we didn't actually find the function.
        if (work == null)
            return;

        final List phaseList = new ArrayList(); 
        final StreamSpec specfinal = spec;
        {
            StmtBlock body = (StmtBlock) work.getBody();
            body.accept(new FEReplacer() {
                    public Object visitExprFunCall(ExprFunCall exp) {
                        Function aFunction = specfinal.getFuncNamed(exp.getName());
                        if (aFunction != null && aFunction.getCls() == Function.FUNC_PHASE) {
                            phaseList.add(aFunction);
                        }
                        return exp;
                    }
                });    
        }

        // We need to add phases.  Is this a phased filter?
        if (phaseList.size() > 0)
        {
            // This will ignore control flow in the work function which may cause
            // problems outside of the library.
            for (Iterator iter = phaseList.iterator(); iter.hasNext();) {
                newStmts.add(new StmtAddPhase(work.getContext(), init, (FuncWork) iter.next())); 
            }
        }
        else
            // Add the work function as the only phase.
            newStmts.add(new StmtAddPhase(work.getContext(), init, work));

    }

    /** Do the rewrite work for the compiler flow. */
    private Object ssCompiler(StreamSpec spec)
    {
        List fns = new ArrayList(spec.getFuncs());
        // Assert that this class cast works.
        FuncWork work = (FuncWork)findWork(fns, false);
        StreamType st = spec.getStreamType();
        List newStmts = new ArrayList();
        // This is a phased filter iff the work function has
        // all null I/O rates.  In that case, use the setIOTypes
        // call to set the types.
        if (work.getPopRate() == null && work.getPushRate() == null)
            newStmts.add(new StmtSetTypes(work.getContext(), st));
        else
        {
            if (!(st.getIn() instanceof TypePrimitive) ||
                ((TypePrimitive)st.getIn()).getType() !=
                TypePrimitive.TYPE_VOID)
            {
                newStmts.add(new StmtIODecl(work.getContext(), "input",
                                            st.getIn(), work.getPopRate(),
                                            work.getPeekRate()));
            }
            if (!(st.getOut() instanceof TypePrimitive) ||
                ((TypePrimitive)st.getOut()).getType() !=
                TypePrimitive.TYPE_VOID)
            {
                newStmts.add(new StmtIODecl(work.getContext(), "output",
                                            st.getOut(), work.getPushRate()));
            }
        }
        
        if (newStmts.isEmpty())
            return spec;
        
        fns = replaceInitWithPrepended(spec.getContext(), fns, newStmts);
        
        return new StreamSpec(spec.getContext(), spec.getType(),
                              spec.getStreamType(), spec.getName(),
                              spec.getParams(), spec.getVars(), fns);
    }
}
