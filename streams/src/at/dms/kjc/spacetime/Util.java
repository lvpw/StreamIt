package at.dms.kjc.spacetime;

import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.flatgraph2.*;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.util.Utils;
import java.util.List;
import java.util.Collection;
import at.dms.kjc.sir.lowering.*;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.HashMap;
import java.io.*;
import at.dms.kjc.spacetime.switchIR.*;

/** A class with useful functions that span classes * */
public class Util {
    public static String CSTOINTVAR = "__csto_integer__";

    public static String CSTOFPVAR = "__csto_float__";

    public static String CSTIFPVAR = "__csti_float__";

    public static String CSTIINTVAR = "__csti_integer__";

    public static String CGNOINTVAR = "__cgno_integer__";

    public static String CGNOFPVAR = "__cgno_float__";

    public static String CGNIFPVAR = "__cgni_float__";

    public static String CGNIINTVAR = "__cgni_integer__"; 

    // unique ID for each file reader/writer used to
    // generate var names...
    private static HashMap fileVarNames;

    private static int fileID = 0;

    static {
        fileVarNames = new HashMap();
    }

    public static int nextPow2(int i) {
        String str = Integer.toBinaryString(i);
        if (str.indexOf('1') == -1)
            return 0;
        int bit = str.length() - str.indexOf('1');
        int ret = (int) Math.pow(2, bit);
        if (ret == i * 2)
            return i;
        return ret;

    }

    /*
     * for a given CType return the size (number of elements that need to be
     * sent when routing).
     */
    public static int getTypeSize(CType type) {

        if (!(type.isArrayType() || type.isClassType()))
            return 1;
        else if (type.isArrayType()) {
            int elements = 1;
            int dims[] = Util.makeInt(((CArrayType) type).getDims());

            for (int i = 0; i < dims.length; i++) {
                elements *= dims[i];
            }
            return elements;
        } else if (type.isClassType()) {
            int size = 0;
            for (int i = 0; i < type.getCClass().getFields().length; i++) {
                size += getTypeSize(type.getCClass().getFields()[i].getType());
            }
            return size;
        }
        Utils.fail("Unrecognized type");
        return 0;
    }

    public static int[] makeInt(JExpression[] dims) {
        int[] ret = new int[dims.length];

        for (int i = 0; i < dims.length; i++) {
            if (!(dims[i] instanceof JIntLiteral))
                Utils
                    .fail("Array length for tape declaration not an int literal");
            ret[i] = ((JIntLiteral) dims[i]).intValue();
        }
        return ret;
    }

    public static CType getBaseType(CType type) {
        if (type.isArrayType())
            return ((CArrayType) type).getBaseType();
        return type;
    }

    public static String[] makeString(JExpression[] dims) {
        String[] ret = new String[dims.length];

        for (int i = 0; i < dims.length; i++) {
            TraceIRtoC ttoc = new TraceIRtoC();
            dims[i].accept(ttoc);
            ret[i] = ttoc.getPrinter().getString();
        }
        return ret;
    }
    public static String networkReceive(boolean dynamic, CType tapeType) {
        assert KjcOptions.altcodegen;
        if (dynamic) {
            if (tapeType.isFloatingPoint())
                return CGNIFPVAR;
            else
                return CGNIINTVAR;
        } else {
            if (tapeType.isFloatingPoint())
                return CSTIFPVAR;
            else
                return CSTIINTVAR;
        }
    }

    public static String networkSendPrefix(boolean dynamic, CType tapeType) {
        assert KjcOptions.altcodegen;
        StringBuffer buf = new StringBuffer();
        if (dynamic) {
            if (tapeType.isFloatingPoint())
                buf.append(CGNOFPVAR);
            else
                buf.append(CGNOINTVAR);
        } else {
            if (tapeType.isFloatingPoint())
                buf.append(CSTOFPVAR);
            else
                buf.append(CSTOINTVAR);
        }

        buf.append(" = (" + tapeType + ")");
        return buf.toString();
    }

    public static String networkSendSuffix(boolean dynamic) {
        assert KjcOptions.altcodegen;
        return "";
    }

    // the size of the buffer between in, out for the steady state
    public static int steadyBufferSize(Edge edge) {
        return edge.steadyItems() * getTypeSize(edge.getType());
    }

    // the size of the buffer between in / out for the init stage
    public static int initBufferSize(Edge edge) {
        return edge.initItems() * getTypeSize(edge.getType());
    }

    public static int magicBufferSize(Edge edge) {
        // i don't remember why I have the + down there,
        // but i am not going to change
        return Math.max(steadyBufferSize(edge), initBufferSize(edge));
    }

    public static String getFileVar(PredefinedContent content) {
        if (content instanceof FileInputContent
            || content instanceof FileOutputContent) {
            if (!fileVarNames.containsKey(content))
                fileVarNames.put(content, new String("file" + fileID++));
            return (String) fileVarNames.get(content);
        } else
            Utils.fail("Calling getFileVar() on non-filereader/filewriter");
        return null;
    }

    public static String getFileHandle(PredefinedContent content) {
        return "file_" + getFileVar(content);
    }

    public static String getOutputsVar(FileOutputContent out) {
        return "outputs_" + getFileVar(out);
    }

    // given <i> bytes, round <i> up to the nearest cache
    // line divisible int
    public static int cacheLineDiv(int i) {
        if (i % RawChip.cacheLineBytes == 0)
            return i;
        return (RawChip.cacheLineBytes - (i % RawChip.cacheLineBytes)) + i;
    }

    // helper function to add everything in a collection to the set
    public static void addAll(HashSet set, Collection c) {
        Iterator it = c.iterator();
        while (it.hasNext()) {
            set.add(it.next());
        }
    }

    /**
     * Get a traversal (linked list) that includes all the trace nodes of the
     * given trace traversal.
     * 
     * @param traceTraversal
     * @return A LinkedList of TraceNodes.
     */
    public static Iterator traceNodeTraversal(List traces) {
        LinkedList trav = new LinkedList();
        ListIterator it = traces.listIterator();

        while (it.hasNext()) {
            Trace trace = (Trace) it.next();
            TraceNode traceNode = trace.getHead();
            while (traceNode != null) {
                trav.add(traceNode);
                traceNode = traceNode.getNext();
            }

        }

        return trav.listIterator();
    }

    /**
     * Get a traversal (linked list) that includes all the trace nodes of the
     * given trace traversal.
     * 
     * @param traceTraversal
     * @return A LinkedList of TraceNodes.
     */
    public static Iterator traceNodeTraversal(Trace[] traces) {
        LinkedList trav = new LinkedList();

        for (int i = 0; i < traces.length; i++) {
            TraceNode traceNode = traces[i].getHead();
            while (traceNode != null) {
                trav.add(traceNode);
                traceNode = traceNode.getNext();
            }

        }

        return trav.listIterator();
    }

    public static void sendConstFromTileToSwitch(RawTile tile, int c,
                                                 boolean init, boolean primePump, SwitchReg reg) {

        tile.getComputeCode().sendConstToSwitch(c, init || primePump);
        // add the code to receive the const
        MoveIns moveIns = new MoveIns(reg, SwitchIPort.CSTO);
        tile.getSwitchCode().appendIns(moveIns, (init || primePump));
    }

}
