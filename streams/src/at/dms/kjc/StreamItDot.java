package at.dms.kjc;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Iterator;

import at.dms.kjc.sir.AttributeStreamVisitor;
import at.dms.kjc.sir.SIRFeedbackLoop;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRJoinType;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRPhasedFilter;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.SIRSplitType;
import at.dms.kjc.sir.SIRSplitter;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.SIRStructure;

/**
 * This class does the front-end processing to turn a Kopi compilation
 * unit into StreamIt classes, and then prints the class graph as a
 * dot file.
 */
public class StreamItDot implements AttributeStreamVisitor
{
    private PrintStream outputStream;

    /**
     * Inner class to represent dot graph components.
     */
    public class NamePair
    {
        /** Name of the first node in an object. */
        String first;
        /** Name of the last node in an object. */
        String last;
        /** Create a new NamePair with both fields null. */
        public NamePair() { first = null; last = null; }
        /** Create a new NamePair with both fields the same. */
        public NamePair(String s) { first = s; last = s; }
        /** Create a new NamePair with two different names. */
        public NamePair(String f, String l) { first = f; last = l; }
    }
    
    private int lastNode;

    public StreamItDot(PrintStream outputStream) {
        lastNode = 0;
        this.outputStream = outputStream;
    }

    /**
     * Prints out a dot graph for the program being compiled.
     */
    public void compile(JCompilationUnit[] app) 
    {
        Kopi2SIR k2s = new Kopi2SIR(app);
        SIRStream stream = null;
        for (int i = 0; i < app.length; i++)
            {
                SIRStream top = (SIRStream)app[i].accept(k2s);
                if (top != null)
                    stream = top;
            }
        
        if (stream == null)
            {
                System.err.println("No top-level stream defined!");
                System.exit(-1);
            }

        // Use the visitor.
        print("digraph streamit {\n");
        stream.accept(this);
        print("}\n");
    }

    /**
     * Prints dot graph of <pre>str</pre> to System.out
     */
    public static void printGraph(SIRStream str) {
        str.accept(new StreamItDot(System.out));
    }

    /**
     * Prints dot graph of <pre>str</pre> to <pre>filename</pre>
     */
    public static void printGraph(SIRStream str, String filename) {
        try {
            FileOutputStream out = new FileOutputStream(filename);
            StreamItDot dot = new StreamItDot(new PrintStream(out));
            dot.print("digraph streamit {\n");
            str.accept(dot);
            dot.print("}\n");
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void print(String f) 
    {
        outputStream.print(f);
    }

    void printEdge(String from, String to)
    {
        if (from == null || to == null)
            return;
        print(from + " -> " + to + "\n");
    }

    String makeEmptyNode()
    {
        String name = getName();
        print(name + " [ style=invis ]\n");
        return name;
    }

    public String makeLabelledNode(String label)
    {
        String name = getName();
        if (label == null) label = name;
        print(name + " [ label=\"" + label + "\" ]\n");
        return name;
    }

    String makeLabelledInvisNode(String label)
    {
        String name = getName();
        if (label == null) label = name;
        print(name + " [ label=\"" + label + "\" ]\n");
        return name;
    }

    public String getName()
    {
        lastNode++;
        return "node" + lastNode;
    }

    /* visit a structure */
    @Override
	public Object visitStructure(SIRStructure self,
                                 JFieldDeclaration[] fields) 
    {
        return new NamePair(makeLabelledInvisNode(self.getIdent()));
    }

    /**
     * Returns the rate information for a filter.
     */
    protected static String makeFilterLabel(SIRPhasedFilter self) {
        String label = "";
        try {
            JMethodDeclaration[] phases = self.getPhases();
            for (int i = 0; i < phases.length; i++)
                {
                    label += "\\npush" + (phases.length>1 ? ""+i : "") + "=" + phases[i].getPushString();
                    label += "\\npop" + (phases.length>1 ? ""+i : "")  + "=" + phases[i].getPopString();
                    label += "\\npeek" + (phases.length>1 ? ""+i : "")  + " =" + phases[i].getPeekString();
                }
            phases = self.getInitPhases();
            for (int i = 0; i < phases.length; i++)
                {
                    label += "\\ninitPush" + (phases.length>1 ? ""+i : "") + "=" + phases[i].getPushString();
                    label += "\\ninitPop" + (phases.length>1 ? ""+i : "") + "=" + phases[i].getPopString();
                    label += "\\ninitPeek" + (phases.length>1 ? ""+i : "") + " =" + phases[i].getPeekString();
                }
        } catch (Exception e) {
            // if constants not resolved for the ints, will get an exception
        }
        return label;
    }

    /* visit a filter */
    @Override
	public Object visitFilter(SIRFilter self,
                              JFieldDeclaration[] fields,
                              JMethodDeclaration[] methods,
                              JMethodDeclaration init,
                              JMethodDeclaration work,
                              CType inputType, CType outputType)
    {
        return new NamePair(makeLabelledNode(self.getName() + makeFilterLabel(self)));
    }

    /* visit a phased filter */
    @Override
	public Object visitPhasedFilter(SIRPhasedFilter self,
                                    JFieldDeclaration[] fields,
                                    JMethodDeclaration[] methods,
                                    JMethodDeclaration init,
                                    JMethodDeclaration work,
                                    JMethodDeclaration[] initPhases,
                                    JMethodDeclaration[] phases,
                                    CType inputType, CType outputType)
    {
        NamePair pair = new NamePair();
        
        // Print this within a subgraph.
        print(getClusterString(self));
        
        // Walk through each of the phases.
        if (initPhases != null)
            for (int i = 0; i < initPhases.length; i++)
                {
                    NamePair p2 = processWorkFunction(initPhases[i]);
                    printEdge(pair.last, p2.first);
                    // Update the known edges.
                    if (pair.first == null)
                        pair.first = p2.first;
                    pair.last = p2.last;
                }
        
        if (phases != null)
            for (int i = 0; i < phases.length; i++)
                {
                    NamePair p2 = processWorkFunction(phases[i]);
                    printEdge(pair.last, p2.first);
                    // Update the known edges.
                    if (pair.first == null)
                        pair.first = p2.first;
                    pair.last = p2.last;
                }
        
        print("}\n");
        return pair;
    }
    
    /* visit a splitter */
    @Override
	public Object visitSplitter(SIRSplitter self,
                                SIRSplitType type,
                                JExpression[] expWeights)
    {
        String label = type.toString();
        // try to add weights to label
        try {
            int[] weights = self.getWeights();
            label += "(";
            for (int i=0; i<weights.length; i++) {
                label += weights[i];
                if (i!=weights.length-1) {
                    label+=",";
                }
            }
            label += ")";
        } catch (Exception e) {}
        // Create an empty node and return it.
        return new NamePair(makeLabelledInvisNode(label));
    }
    
    /* visit a joiner */
    @Override
	public Object visitJoiner(SIRJoiner self,
                              SIRJoinType type,
                              JExpression[] expWeights)
    {
        String label = type.toString();
        // try to add weights to label
        try {
            int[] weights = self.getWeights();
            label += "(";
            for (int i=0; i<weights.length; i++) {
                label += weights[i];
                if (i!=weights.length-1) {
                    label+=",";
                }
            }
            label += ")";
        } catch (Exception e) {}
        return new NamePair(makeLabelledInvisNode(label));
    }
    
    /* process a work function */
    public NamePair processWorkFunction(JMethodDeclaration work) {
        String label = work.getName();
        try {
            label += "\\npush=" + work.getPushString();
            label += "\\npop=" + work.getPopString();
            label += "\\npeek=" + work.getPeekString();
        } catch (Exception e) {
            // if constants not resolved for the ints, will get an exception
        }
    
        return new NamePair(makeLabelledNode(label));
    }

    /* pre-visit a pipeline */
    @Override
	public Object visitPipeline(SIRPipeline self,
                                JFieldDeclaration[] fields,
                                JMethodDeclaration[] methods,
                                JMethodDeclaration init)
    {
        NamePair pair = new NamePair();
        
        // Print this within a subgraph.
        print(getClusterString(self));
        //print("subgraph cluster_" + getName() + " {\n label=\"" + self.getIdent() + "\";\n");
        
        // Walk through each of the elements in the pipeline.
        Iterator iter = self.getChildren().iterator();
        while (iter.hasNext())
            {
                SIROperator oper = (SIROperator)iter.next();
                NamePair p2 = (NamePair)oper.accept(this);
                printEdge(pair.last, p2.first);
                // Update the known edges.
                if (pair.first == null)
                    pair.first = p2.first;
                pair.last = p2.last;
            }

        print("}\n");
        return pair;
    }
       
    /* pre-visit a splitjoin */
    @Override
	public Object visitSplitJoin(SIRSplitJoin self,
                                 JFieldDeclaration[] fields,
                                 JMethodDeclaration[] methods,
                                 JMethodDeclaration init,
                                 SIRSplitter splitter,
                                 SIRJoiner joiner)
    {
        NamePair pair = new NamePair();
        
        // Create a subgraph again...
        print(getClusterString(self));
        //print("subgraph cluster_" + getName() + " {\n label=\"" + self.getIdent() + "\";\n");

        // Visit the splitter and joiner to get their node names...
        NamePair np;
        np = (NamePair)splitter.accept(this);
        pair.first = np.first;
        np = (NamePair)joiner.accept(this);
        pair.last = np.last;

        // ...and walk through the body.
        Iterator<SIRStream> iter = self.getParallelStreams().iterator();
        while (iter.hasNext()) {
            SIROperator oper = iter.next();
            np = (NamePair)oper.accept(this);
            printEdge(pair.first, np.first);
            printEdge(np.last, pair.last);
        }

        print("}\n");
        return pair;
    }

    /* pre-visit a feedbackloop */
    @Override
	public Object visitFeedbackLoop(SIRFeedbackLoop self,
                                    JFieldDeclaration[] fields,
                                    JMethodDeclaration[] methods,
                                    JMethodDeclaration init,
                                    JMethodDeclaration initPath)
    {
        NamePair np;
        
        // Create a subgraph again...
        print(getClusterString(self));
        //print("subgraph cluster_" + getName() + " {\n label=\"" + self.getIdent() + "\";\n");

        // Visit the splitter and joiner.
        np = (NamePair)self.getJoiner().accept(this);
        String joinName = np.first;
        np = (NamePair)self.getSplitter().accept(this);
        String splitName = np.first;

        // Visit the body and the loop part.
        np = (NamePair)self.getBody().accept(this);
        printEdge(joinName, np.first);
        printEdge(np.last, splitName);
        np = (NamePair)self.getLoop().accept(this);
        printEdge(splitName, np.first);
        printEdge(np.last, joinName);

        print("}\n");
        return new NamePair(joinName, splitName);
    }

    /**
     * Prints out the subgraph cluser line that is needed in to make clusters. This method is overridden to make colored
     * pipelines and splitjoins in LinearDot.
     **/
    public String getClusterString(SIRStream self) {
        return "subgraph cluster_" + getName() + " {\n label=\"" + self.getName() + "\";\n";
    }


}
