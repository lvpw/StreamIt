package at.dms.kjc.common;

import java.util.HashMap;

import at.dms.kjc.CArrayType;
import at.dms.kjc.CType;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.sir.SIRFileReader;
import at.dms.kjc.sir.SIRFileWriter;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRIdentity;
import at.dms.kjc.sir.SIRSplitter;
import at.dms.util.Utils;

/**
 * This class contains various function used by multiple passes
 */
public class RawUtil extends at.dms.util.Utils {
    /**
	 * 
	 */
	private static final long serialVersionUID = -3557118165469406038L;
	public static String CSTOINTVAR = "__csto_integer__";
    public static String CSTOFPVAR = "__csto_float__";
    public static String CSTIFPVAR = "__csti_float__";
    public static String CSTIINTVAR = "__csti_integer__";


    //returns true if this filter is mapped
    public static boolean countMe(SIRFilter filter) {
        return !(filter instanceof SIRIdentity ||
                 filter instanceof SIRFileWriter ||
                 filter instanceof SIRFileReader);
    }
        
    public static int nextPow2(int i) {
        String str = Integer.toBinaryString(i);
        if  (str.indexOf('1') == -1)
            return 0;
        int bit = str.length() - str.indexOf('1');
        int ret = (int)Math.pow(2, bit);
        if (ret == i * 2)
            return i;
        return ret;
    
    }

    /**
      get the execution Count of the previous node
    */
    public static int getCount(HashMap counts, FlatNode node) 
    {
        Integer count = ((Integer)counts.get(node));

        if (count == null)
            return 0;
        return count.intValue();
    }
    
    /**
      get the execution count of the previous node
    */
    public static int getCountPrev(HashMap counts, FlatNode prev, FlatNode node) 
    {
        if (!(prev.contents instanceof SIRSplitter))
            return getCount(counts, prev);
    
        //  if (((SIRSplitter)prev.contents).getType() == SIRSplitType.DUPLICATE)
        //   return getCount(counts, prev);
    
        //prev is a splitter
        double rate = getRRSplitterWeight(prev, node);
        return ((int)(rate * getCount(counts, prev)));
    }

    //get the percentage of items sent from splitter prev to  node
    public static double getRRSplitterWeight(FlatNode prev, FlatNode node) {
        //prev is a splitter
        int sumWeights = 0;
        for (int i = 0; i < prev.ways; i++) 
            sumWeights += prev.weights[i];
        int thisWeight = -1;
        for (int i = 0; i < prev.ways; i++) {
            if (prev.getEdges()[i].equals(node)) {
                thisWeight = prev.weights[i];
                break;
            }
        }
    
        if (thisWeight == -1)
            Utils.fail("Splitter not connected to node: "+prev+"->"+node);
        return ((double)thisWeight) / ((double)sumWeights);
    }

    /**
      for a given CType return the size (number of elements that need to be sent
      when routing).
    */
    public static int getTypeSize(CType type) {

        if (!(type.isArrayType() || type.isClassType()))
            return 1;
        else if (type.isArrayType()) {
            int elements = 1;
            int dims[] = CommonUtils.makeArrayInts(((CArrayType)type).getDims());
        
            for (int i = 0; i < dims.length; i++) {
                elements *= dims[i];
            }
            return elements;
        }
        else if (type.isClassType()) {
            int size = 0;
            for (int i = 0; i < type.getCClass().getFields().length; i++) {
                size += getTypeSize(type.getCClass().getFields()[i].getType());
            }
            return size;
        }
        Utils.fail("Unrecognized type");
        return 0;
    }


    public static String staticNetworkReceivePrefix() {
//        if(KjcOptions.altcodegen || KjcOptions.decoupled) 
            return "";
//        else 
//            //return "/* receive */ asm volatile (\"sw $csti, %0\" : \"=m\" (";
//            return "/* receive */ asm volatile (\"move %0, $csti\" : \"=r\" (";
    }

    public static String staticNetworkReceiveSuffix(CType tapeType) {
//        if(KjcOptions.altcodegen || KjcOptions.decoupled) {
            if (tapeType.isFloatingPoint())
                return "= " + CSTIFPVAR + ";";
            else
                return "= " + CSTIINTVAR + ";";
//        }
//        else 
//            return "));";
    }

    public static String staticNetworkSendPrefix(CType tapeType) {
        StringBuffer buf = new StringBuffer();
    
//        if (KjcOptions.altcodegen || KjcOptions.decoupled) {
            if (tapeType.isFloatingPoint())
                buf.append(CSTOFPVAR);
            else
                buf.append(CSTOINTVAR);
            //temporary fix for type changing filters
            buf.append(" = (" + tapeType + ")");
//        } 
//        else {
//            buf.append("(");
//            if (RawBackend.FILTER_DEBUG_MODE)
//                buf.append("static_send_print");
//            else
//                buf.append("static_send");
//            buf.append("(");    
//            //temporary fix for type changing filters
//            //commented out by MGordon 9/24, this should not longer be necessary
//            //buf.append("(" + tapeType + ")");
//        }
        return buf.toString();
    }

    public static String staticNetworkSendSuffix() {
//        if (KjcOptions.altcodegen || KjcOptions.decoupled) 
            return "";
//        else 
//            return "))";
    }

/* hope cluster does not need this
    //return the FlatNodes that are directly downstream of the 
    //given flatnode and are themselves assigned a tile in the
    //layout
    public static HashSet getAssignedEdges(FlatNode node) {
        HashSet set = new HashSet();

        if (node == null)
            return set;

        for (int i = 0; i < node.edges.length; i++) 
            getAssignedEdgesHelper(node.edges[i], set);

        return set;
    }

    private static void getAssignedEdgesHelper(FlatNode node, HashSet set) {
        if (node == null) 
            return;
        else if (Layout.isAssigned(node)) {
            set.add(node);
            return;
        }
        else {
            for (int i = 0; i < node.edges.length; i++) 
                getAssignedEdgesHelper(node.edges[i], set);
        }
    }
*/

/* hope cluster does not need this
   //get all filters/joiners that are directly connected downstream to this
    //node, but go thru all splitters.  The node itself is a joiner or 
    //filter,  NOTE, THIS HAS NOT BEEN TESTED BUT IT SHOULD WORK, I DID NOT 
    //NEED IT FOR WHAT I WROTE IT FOR
    public static HashSet getDirectDownstream(FlatNode node) 
    {
        if (node == null || node.isSplitter())
            Utils.fail("getDirectDownStream(...) error. Node not filter or joiner.");
        if (node.ways > 0)
            return getDirectDownstreamHelper(node.edges[0]);
        else 
            return new HashSet();
    }
    
    private static HashSet getDirectDownstreamHelper(FlatNode current) 
    {
        if (current == null)
            return new HashSet();
        else if (current.isFilter() || current.isJoiner()) {
            HashSet ret = new HashSet();
            ret.add(current);
            return ret;
        }
        else if (current.isSplitter()) {
            HashSet ret = new HashSet();
        
            for (int i = 0; i < current.ways; i++) {
                if(current.weights[i]!=0)
                    RawBackend.addAll(ret, getDirectDownstreamHelper(current.edges[i]));
            }
            return ret;
        }
        return null;
    }    
*/
}


