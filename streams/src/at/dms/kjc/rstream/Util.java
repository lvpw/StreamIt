package at.dms.kjc.rstream;

import at.dms.kjc.CArrayType;
import at.dms.kjc.CType;
import at.dms.kjc.JAddExpression;
import at.dms.kjc.JArrayAccessExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JMinusExpression;
import at.dms.kjc.JMultExpression;
import at.dms.kjc.JPhylum;
import at.dms.kjc.ObjectDeepCloner;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.sir.SIRFileReader;
import at.dms.kjc.sir.SIRFileWriter;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRIdentity;
import at.dms.util.Utils;

/**
 * This class contains various function used by multiple passes
 */
public class Util extends at.dms.util.Utils {
    /**
	 * 
	 */
	private static final long serialVersionUID = -1021395935707484957L;
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

    /*
      for a given CType return the size (number of elements that need to be sent
      when routing).
    */
    public static int getTypeSize(CType type) {

        if (!(type.isArrayType() || type.isClassType()))
            return 1;
        else if (type.isArrayType()) {
            int elements = 1;
            int dims[] = Util.makeInt(((CArrayType)type).getDims());
        
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
    
    /** get the base type of a type, so for array's return the element type **/
    public static CType getBaseType (CType type) 
    {
        if (type.isArrayType())
            return ((CArrayType)type).getBaseType();
        return type;
    }

    /** get the variable access in an array access expression **/
    public static JExpression getVar(JArrayAccessExpression expr) 
    {
        if (!(expr.getPrefix() instanceof JArrayAccessExpression))
            return expr.getPrefix();
        else
            return getVar((JArrayAccessExpression)expr.getPrefix());
    }
    
    /** turn an array of expressions to any array of strings using FlatIRToRS **/
    public static String[] makeString(JExpression[] dims) {
        String[] ret = new String[dims.length];
    
    
        for (int i = 0; i < dims.length; i++) {
            FlatIRToRS ftoc = new FlatIRToRS();
            dims[i].accept(ftoc);
            ret[i] = ftoc.getPrinter().getString();
        }
        return ret;
    }

    /** turn an array of JIntLiterals into an array of ints, fail if not JIntLiterals **/
    public static int[] makeInt(JExpression[] dims) {
        int[] ret = new int[dims.length];
    
        for (int i = 0; i < dims.length; i++) {
            if (!(dims[i] instanceof JIntLiteral))
                Utils.fail("Array length for tape declaration not an int literal");
            ret[i] = ((JIntLiteral)dims[i]).intValue();
        }
        return ret;
    }
    
    /** return the number of items pushed from *from* to *to*
        on each iteration of *from*.
        If from is a splitter take this into account **/
    public static int getItemsPushed(FlatNode from, FlatNode to)  
    {
        if (from.isFilter())
            return ((SIRFilter)from.contents).getPushInt();
        else if (from.isJoiner())
            return from.getTotalIncomingWeights();
        else if (from.isSplitter())
            return from.getWeight(to);
    
        return -1;
    }

    
    /** convert an IR tree to a string of C code **/
    public static String JPhylumToC(JPhylum top) 
    {
        FlatIRToRS toC = new FlatIRToRS();
        top.accept(toC);
        return toC.getPrinter().getString();
    }
    
    /** construct a new JAddExpression, *left* + *right* where
        both are of type int.  Try to fold constants **/
    public static JExpression newIntAddExpr(JExpression left,
                                            JExpression right) 
    {
        if (left instanceof JIntLiteral &&
            right instanceof JIntLiteral)
            return new JIntLiteral(((JIntLiteral)left).intValue() +
                                   ((JIntLiteral)right).intValue());

        if ((left instanceof JIntLiteral &&
             ((JIntLiteral)left).intValue() == 0))
            return (JExpression)ObjectDeepCloner.deepCopy(right);
    
        if ((right instanceof JIntLiteral &&
             ((JIntLiteral)right).intValue() == 0))
            return (JExpression)ObjectDeepCloner.deepCopy(left);
    
        return new JAddExpression(null,
                                  left, right);
    }

    /** construct a new JMultExpression, *left* * *right* where
        both are of type int.  Try to fold constants **/
    public static JExpression newIntMultExpr(JExpression left, 
                                             JExpression right) 
    {
        if (left instanceof JIntLiteral &&
            right instanceof JIntLiteral)
            return new JIntLiteral(((JIntLiteral)left).intValue() *
                                   ((JIntLiteral)right).intValue());
    
        if ((left instanceof JIntLiteral &&
             ((JIntLiteral)left).intValue() == 0) ||
            (right instanceof JIntLiteral &&
             ((JIntLiteral)right).intValue() == 0))
            return new JIntLiteral(0);

        if ((left instanceof JIntLiteral &&
             ((JIntLiteral)left).intValue() == 1))
            return (JExpression)ObjectDeepCloner.deepCopy(right);
    
        if ((right instanceof JIntLiteral &&
             ((JIntLiteral)right).intValue() == 1))
            return (JExpression)ObjectDeepCloner.deepCopy(left);
    
        return new JMultExpression(null,
                                   left, right);
    }

    /** construct a new JMinusExpression, *left* - *right* where
        both are of type int.  Try to fold constants **/
    public static JExpression newIntSubExpr(JExpression left,
                                            JExpression right) 
    {
        if (isIntZero(right))
            return (JExpression)ObjectDeepCloner.deepCopy(left);
    
        return new JMinusExpression(null, left, right);
    }
    
    /** return true if this exp is a JIntLiteral and the int value is 0 **/
    public static boolean isIntZero(JExpression exp) 
    {
        return ((Utils.passThruParens(exp) instanceof JIntLiteral) &&
                ((JIntLiteral)Utils.passThruParens(exp)).intValue() == 0);
    }
    
    /** return true if this exp is a JIntLiteral and the int value is 1 **/
    public static boolean isIntOne(JExpression exp) 
    {
        return ((Utils.passThruParens(exp) instanceof JIntLiteral) &&
                ((JIntLiteral)Utils.passThruParens(exp)).intValue() == 1);
    }
    
}



