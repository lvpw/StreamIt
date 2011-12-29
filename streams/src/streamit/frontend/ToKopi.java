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

package streamit.frontend;
import java.util.List;

import streamit.frontend.nodes.MakeBodiesBlocks;
import streamit.frontend.nodes.Program;
import streamit.frontend.nodes.TempVarGen;
import streamit.frontend.passes.CreateInitFunctions;
import streamit.frontend.passes.DisambiguateUnaries;
import streamit.frontend.passes.FindFreeVariables;
import streamit.frontend.passes.NoRefTypes;
import streamit.frontend.passes.RenameBitVars;
import streamit.frontend.passes.SeparateInitializers;
import streamit.frontend.passes.TrimDumbDeadCode;
import streamit.frontend.tojava.ComplexToStruct;
import streamit.frontend.tojava.DoComplexProp;
import streamit.frontend.tojava.InsertInitConstructors;
import streamit.frontend.tojava.MoveStreamParameters;
import streamit.frontend.tojava.NameAnonymousFunctions;
import streamit.frontend.tojava.TranslateEnqueue;

/**
 * Read StreamIt programs and run them through the main compiler.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;,
 *          David Ziegler &lt;dziegler@cag.lcs.mit.edu&gt;
 * @version $Id: ToKopi.java,v 1.13 2006-09-25 13:54:53 dimock Exp $
 */
public class ToKopi
{
    public void printUsage()
    {
        System.err.println(
                           "streamit.frontend.ToKopi: StreamIt compiler\n" +
                           "Usage: java streamit.frontend.ToKopi in.str ...\n" +
                           "\n" +
                           "Options:\n" +
                           "  --help         Print this message\n" +
                           "\n");
    }

    private boolean printHelp = false;
    private String outputFile = null;
    private List<String> inputFiles = new java.util.ArrayList<String>();

    public void doOptions(String[] args)
    {
        for (int i = 0; i < args.length; i++)
            {
                if (args[i].equals("--help"))
                    printHelp = true;
                else if (args[i].equals("--"))
                    {
                        // Add all of the remaining args as input files.
                        for (i++; i < args.length; i++)
                            inputFiles.add(args[i]);
                    }
                else if (args[i].equals("--output"))
                    outputFile = args[++i];
                else
                    // Maybe check for unrecognized options.
                    inputFiles.add(args[i]);
            }
    }

    public static Program lowerIRToJava(Program prog)
    {
        /* What's the right order for these?  Clearly generic
         * things like MakeBodiesBlocks need to happen first.
         * I don't think there's actually a problem running
         * MoveStreamParameters after DoComplexProp, since
         * this introduces only straight assignments which the
         * Java front-end can handle.  OTOH,
         * MoveStreamParameters introduces references to
         * "this", which doesn't exist. */
        TempVarGen varGen = new TempVarGen();
        prog = (Program)prog.accept(new CreateInitFunctions());
        prog = (Program)prog.accept(new MakeBodiesBlocks());
        prog = (Program)prog.accept(new SeparateInitializers());
        prog = (Program)prog.accept(new DisambiguateUnaries(varGen));
        prog = (Program)prog.accept(new NoRefTypes());
        prog = (Program)prog.accept(new RenameBitVars());
        prog = (Program)prog.accept(new FindFreeVariables());
        prog = (Program)prog.accept(new DoComplexProp(varGen));
        prog = (Program)prog.accept(new ComplexToStruct());
        prog = (Program)prog.accept(new SeparateInitializers());
        prog = (Program)prog.accept(new TranslateEnqueue());
        prog = (Program)prog.accept(new InsertInitConstructors(varGen, false));
        prog = (Program)prog.accept(new MoveStreamParameters());
        prog = (Program)prog.accept(new NameAnonymousFunctions());
        prog = (Program)prog.accept(new TrimDumbDeadCode());
        return prog;
    }

    //    public void run(String[] args)
    //   {
    //        doOptions(args);
    //        if (printHelp)
    //        {
    //            printUsage();
    //            return;
    //        }
    //        
    //        Program prog = null;
    ////        Writer outWriter;
    //
    //        try
    //        {
    //            prog = ToJava.parseFiles(inputFiles);
    //        }
    //        catch (java.io.IOException e) {e.printStackTrace(System.err);}
    //        catch (antlr.RecognitionException e) {e.printStackTrace(System.err);}
    //        catch (antlr.TokenStreamException e) {e.printStackTrace(System.err);}
    //
    //        if (prog == null)
    //        {
    //            System.err.println("Compilation didn't generate a parse tree.");
    //            return;
    //        }
    //
    //        prog = lowerIRToJava(prog);
    //
    //        System.out.println("/*");
    //        SIRStream s = (SIRStream) prog.accept(new FEIRToSIR());
    //        Flattener.flatten(s, new JInterfaceDeclaration[0],
    //                          new SIRInterfaceTable[0], new SIRStructure[0], null);
    //    }
    
    //    public static void main(String[] args)
    //    {
    //        new ToKopi().run(args);
    //    }

}
