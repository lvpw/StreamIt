/*
 * NodesToJava.java: traverse a front-end tree and produce Java objects
 * David Maze <dmaze@cag.lcs.mit.edu>
 * $Id: NodesToJava.java,v 1.4 2002-07-15 18:58:17 dmaze Exp $
 */

package streamit.frontend.tojava;

import streamit.frontend.nodes.*;
import java.util.Iterator;

/**
 * NodesToJava is a front-end visitor that produces Java code from
 * expression trees.  Every method actually returns a String.
 */
public class NodesToJava implements FEVisitor
{
    private StreamType st;
    
    public NodesToJava(StreamType st)
    {
        this.st = st;
    }

    // Convert a Type to a String.  If visitors weren't so generally
    // useless for other operations involving Types, we'd use one here.
    public String convertType(Type type)
    {
        // This is So Wrong in the greater scheme of things.
        if (type instanceof TypeArray)
        {
            TypeArray array = (TypeArray)type;
            String base = convertType(array.getBase());
            return base + "[]";
        }
        else if (type instanceof TypeStruct)
        {
            return ((TypeStruct)type).getName();
        }
        else if (type instanceof TypePrimitive)
        {
            switch (((TypePrimitive)type).getType())
            {
            case TypePrimitive.TYPE_INT: return "int";
            case TypePrimitive.TYPE_FLOAT: return "float";
            case TypePrimitive.TYPE_DOUBLE: return "double";
            case TypePrimitive.TYPE_COMPLEX: return "Complex";
            case TypePrimitive.TYPE_VOID: return "void";
            }
        }
        return null;
    }
    
    public Object visitExprArray(ExprArray exp)
    {
        String result = "";
        result += (String)exp.getBase().accept(this);
        result += "[";
        result += (String)exp.getOffset().accept(this);
        result += "]";
        return result;
    }
    
    public Object visitExprBinary(ExprBinary exp)
    {
        String result;
        String op = null;
        result = "(";
        result += (String)exp.getLeft().accept(this);
        switch (exp.getOp())
        {
        case ExprBinary.BINOP_ADD: op = "+"; break;
        case ExprBinary.BINOP_SUB: op = "-"; break;
        case ExprBinary.BINOP_MUL: op = "*"; break;
        case ExprBinary.BINOP_DIV: op = "/"; break;
        case ExprBinary.BINOP_MOD: op = "%"; break;
        case ExprBinary.BINOP_AND: op = "&&"; break;
        case ExprBinary.BINOP_OR:  op = "||"; break;
        case ExprBinary.BINOP_EQ:  op = "=="; break;
        case ExprBinary.BINOP_NEQ: op = "!="; break;
        case ExprBinary.BINOP_LT:  op = "<"; break;
        case ExprBinary.BINOP_LE:  op = "<="; break;
        case ExprBinary.BINOP_GT:  op = ">"; break;
        case ExprBinary.BINOP_GE:  op = ">="; break;
        }
        result += " " + op + " ";
        result += (String)exp.getRight().accept(this);
        result += ")";
        return result;
    }

    public Object visitExprComplex(ExprComplex exp)
    {
        // This should cause an assertion failure, actually.
        String r = "";
        String i = "";
        if (exp.getReal() != null) r = (String)exp.getReal().accept(this);
        if (exp.getImag() != null) i = (String)exp.getImag().accept(this);
        return "/* (" + r + ")+i(" + i + ") */";
    }

    public Object visitExprConstChar(ExprConstChar exp)
    {
        return "'" + exp.getVal() + "'";
    }

    public Object visitExprConstFloat(ExprConstFloat exp)
    {
        return Double.toString(exp.getVal());
    }

    public Object visitExprConstInt(ExprConstInt exp)
    {
        return Integer.toString(exp.getVal());
    }
    
    public Object visitExprConstStr(ExprConstStr exp)
    {
        return "\"" + exp.getVal() + "\"";
    }

    public Object visitExprField(ExprField exp)
    {
        String result = "";
        result += (String)exp.getLeft().accept(this);
        result += ".";
        result += (String)exp.getName();
        return result;
    }

    public Object visitExprFunCall(ExprFunCall exp)
    {
        String result = exp.getName() + "(";
        boolean first = true;
        for (Iterator iter = exp.getParams().iterator(); iter.hasNext(); )
        {
            Expression param = (Expression)iter.next();
            if (!first) result += ", ";
            first = false;
            result += (String)param.accept(this);
        }
        result += ")";
        return result;
    }

    public Object visitExprPeek(ExprPeek exp)
    {
        String result = (String)exp.getExpr().accept(this);
        return st.peekFunction() + "(" + result + ")";
    }
    
    public Object visitExprPop(ExprPop exp)
    {
        return st.popFunction() + "()";
    }

    public Object visitExprTernary(ExprTernary exp)
    {
        String a = (String)exp.getA().accept(this);
        String b = (String)exp.getB().accept(this);
        String c = (String)exp.getC().accept(this);
        switch (exp.getOp())
        {
        case ExprTernary.TEROP_COND:
            return "(" + a + " ? " + b + " : " + c + ")";
        }
        
        return null;
    }

    public Object visitExprUnary(ExprUnary exp)
    {
        String child = (String)exp.getExpr().accept(this);
        switch(exp.getOp())
        {
        case ExprUnary.UNOP_NOT: return "!" + child;
        case ExprUnary.UNOP_NEG: return "-" + child;
        case ExprUnary.UNOP_PREINC: return "++" + child;
        case ExprUnary.UNOP_POSTINC: return child + "++";
        case ExprUnary.UNOP_PREDEC: return "--" + child;
        case ExprUnary.UNOP_POSTDEC: return child + "--";
        }

        return null;
    }

    public Object visitExprVar(ExprVar exp)
    {
        return exp.getName();
    }
}
