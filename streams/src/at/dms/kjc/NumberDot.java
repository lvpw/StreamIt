package at.dms.kjc;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRStream;

/**
 * This class does the front-end processing to turn a Kopi compilation
 * unit into StreamIt classes, and then prints the class graph as a
 * dot file.
 */
public class NumberDot extends StreamItDot
{

    public NumberDot(PrintStream outputStream) {
        super(outputStream);
    }

    /**
     * Prints dot graph of <pre>str</pre> to System.out
     */
    public static void printGraph(SIRStream str) {
        str.accept(new NumberDot(System.out));
    }

    /**
     * Prints dot graph of <pre>str</pre> to <pre>filename</pre>
     */
    public static void printGraph(SIRStream str, String filename) {
        try {
            FileOutputStream out = new FileOutputStream(filename);
            NumberDot dot = new NumberDot(new PrintStream(out));
            dot.print("digraph streamit {\n");
            str.accept(dot);
            dot.print("}\n");
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
	public Object visitFilter(SIRFilter self,
                              JFieldDeclaration[] fields,
                              JMethodDeclaration[] methods,
                              JMethodDeclaration init,
                              JMethodDeclaration work,
                              CType inputType, CType outputType)
    {
        return new NamePair(makeLabelledNode(pruneIdent(self.getIdent()) + " (" + self.getNumber() + ")"));
    }

    /**
     * Cuts off anything after _ to prune compiler stuff.
     */
    private String pruneIdent(String ident) {
        int index = ident.indexOf("_");
        if (index>0) {
            return ident.substring(0, index);
        }
        return ident;
    }

    /**
     * Prints out the subgraph cluser line that is needed in to make clusters. This method is overridden to make colored
     * pipelines and splitjoins in LinearDot.
     **/
    @Override
	public String getClusterString(SIRStream self) {
        String qualified = self.getIdent()+"";
        int i = qualified.lastIndexOf(".");
        if (i>0) {
            qualified = qualified.substring(i+4).toLowerCase();
        }
        return "subgraph cluster_" + getName() + " {\n label=\"" + pruneIdent(qualified) + " (" + self.getNumber() + ")" + "\";\n";
    }

}
