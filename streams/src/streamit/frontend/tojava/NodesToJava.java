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

/**
 * Traverse a front-end tree and produce Java code.  This uses {@link
 * streamit.frontend.nodes.FEVisitor} directly, without going through
 * an intermediate class such as <code>FEReplacer</code>.  Every
 * method actually returns a String.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: NodesToJava.java,v 1.93 2005-03-02 00:44:03 madrake Exp $
 */
public class NodesToJava implements FEVisitor
{
    private StreamSpec ss;
    // A string consisting of an even number of spaces.
    private String indent;
    private boolean libraryFormat;
    private TempVarGen varGen;
    
    public NodesToJava(boolean libraryFormat, TempVarGen varGen)
    {
        this.ss = null;
        this.indent = "";
        this.libraryFormat = libraryFormat;
        this.varGen = varGen;
    }

    // Add two spaces to the indent.
    private void addIndent() 
    {
        indent += "  ";
    }
    
    // Remove two spaces from the indent.
    private void unIndent()
    {
        indent = indent.substring(2);
    }

    // Convert a Type to a String.  If visitors weren't so generally
    // useless for other operations involving Types, we'd use one here.
    public static String convertType(Type type)
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
	else if (type instanceof TypeStructRef)
        {
	    return ((TypeStructRef)type).getName();
        }
        else if (type instanceof TypePrimitive)
        {
            switch (((TypePrimitive)type).getType())
            {
            case TypePrimitive.TYPE_BOOLEAN: return "boolean";
            case TypePrimitive.TYPE_BIT: return "int";
            case TypePrimitive.TYPE_INT: return "int";
            case TypePrimitive.TYPE_FLOAT: return "float";
            case TypePrimitive.TYPE_DOUBLE: return "double";
            case TypePrimitive.TYPE_COMPLEX: return "Complex";
            case TypePrimitive.TYPE_VOID: return "void";
            default: assert false : type; return null;
            }
        }
        else if (type instanceof TypePortal)
        {
            return ((TypePortal)type).getName() + "Portal";
        }
        else
        {
            assert false : type;
            return null;
        }
    }

    public String convertTypeFull(Type type) {
	return convertTypeFull(type, true);
    }

    // Do the same conversion, but including array dimensions.
    public String convertTypeFull(Type type, boolean includePrimitive)
    {
        if (type instanceof TypeArray)
        {
            TypeArray array = (TypeArray)type;
	    String output = "";
	    // first get primitive type
	    if (includePrimitive) {
		Type primitive = array;
		while (primitive instanceof TypeArray) {
		    primitive = ((TypeArray)primitive).getBase();
		}
		output = convertTypeFull(primitive);
	    }
	    return output +
		"[" + (String)array.getLength().accept(this) + "]"
		+ convertTypeFull(array.getBase(), false);
	    
        }
	if (includePrimitive) {
	    return convertType(type);
	} else {
	    return "";
	}
    }

    // Get a constructor for some type.
    public String makeConstructor(Type type)
    {
        if (type instanceof TypeArray)
            return "new " + convertTypeFull(type);
        else
            return "new " + convertTypeFull(type) + "()";
    }

    // Get a Java Class object corresponding to a type.
    public String typeToClass(Type t)
    {
        if (t instanceof TypePrimitive)
        {
            switch (((TypePrimitive)t).getType())
            {
            case TypePrimitive.TYPE_BOOLEAN:
                return "Boolean.TYPE";
            case TypePrimitive.TYPE_BIT:
                return "Integer.TYPE";
            case TypePrimitive.TYPE_INT:
                return "Integer.TYPE";
            case TypePrimitive.TYPE_FLOAT:
                return "Float.TYPE";
            case TypePrimitive.TYPE_DOUBLE:
                return "Double.TYPE";
            case TypePrimitive.TYPE_VOID:
                return "Void.TYPE";
            case TypePrimitive.TYPE_COMPLEX:
                return "Complex.class";
            default:
                assert false : t;
                return null;
            }
        }
        else if (t instanceof TypeStruct)
            return ((TypeStruct)t).getName() + ".class";
        else if (t instanceof TypeArray)
            return "(" + makeConstructor(t) + ").getClass()";
        else
        {
            assert false : t;
            return null;
        }
    }

    // Helpers to get function names for stream types.
    public static String pushFunction(StreamType st)
    {
        return annotatedFunction("output.push", st.getOut());
    }
    
    public static String popFunction(StreamType st)
    {
        return annotatedFunction("input.pop", st.getIn());
    }
    
    public static String peekFunction(StreamType st)
    {
        return annotatedFunction("input.peek", st.getIn());
    }
    
    private static String annotatedFunction(String name, Type type)
    {
        String prefix = "", suffix = "";
        // Check for known suffixes:
        if (type instanceof TypePrimitive)
        {
            switch (((TypePrimitive)type).getType())
            {
            case TypePrimitive.TYPE_BOOLEAN:
                suffix = "Boolean";
                break;
            case TypePrimitive.TYPE_BIT:
                suffix = "Int";
                break;
            case TypePrimitive.TYPE_INT:
                suffix = "Int";
                break;
            case TypePrimitive.TYPE_FLOAT:
                suffix = "Float";
                break;
            case TypePrimitive.TYPE_DOUBLE:
                suffix = "Double";
                break;
            case TypePrimitive.TYPE_COMPLEX:
                if (name.startsWith("input"))
                    prefix  = "(Complex)";
                break;
            default:
                assert false : type;
            }
        }
        else if (name.startsWith("input"))
        {
            prefix = "(" + convertType(type) + ")";
        }
        return prefix + name + suffix;
    }

    // Return a representation of a list of Parameter objects.
    public String doParams(List params, String prefix)
    {
        String result = "(";
        boolean first = true;
        for (Iterator iter = params.iterator(); iter.hasNext(); )
        {
            Parameter param = (Parameter)iter.next();
            if (!first) result += ", ";
            if (prefix != null) result += prefix + " ";
            result += convertType(param.getType());
            result += " ";
            result += param.getName();
            first = false;
        }
        result += ")";
        return result;
    }

    // Return a representation of lhs = rhs, with no trailing semicolon.
    public String doAssignment(Expression lhs, Expression rhs,
                               SymbolTable symtab)
    {
        // If the left-hand-side is a complex variable, we need to
        // properly decompose the right-hand side.
        // We can use a null stream type here since the left-hand
        // side shouldn't contain pushes, pops, or peeks.
        GetExprType eType = new GetExprType(symtab, ss.getStreamType(),
                                            new java.util.HashMap());
        Type lhsType = (Type)lhs.accept(eType);
        if (lhsType.isComplex())
        {
            Expression real = new ExprField(lhs.getContext(), lhs, "real");
            Expression imag = new ExprField(lhs.getContext(), lhs, "imag");
            // If the right hand side is complex too (at this point
            // just test the run-time type of the expression), then we
            // should do field copies; otherwise we only have a real part.
            if (rhs instanceof ExprComplex)
            {
                ExprComplex cplx = (ExprComplex)rhs;
                return real.accept(this) + " = " +
                    cplx.getReal().accept(this) + ";\n" +
                    imag.accept(this) + " = " +
                    cplx.getImag().accept(this);
            }
            else
                return real.accept(this) + " = " +
                    rhs.accept(this) + ";\n" +
                    imag.accept(this) + " = 0.0";
        }
        else
        {
            // Might want to special-case structures and arrays;
            // ignore for now.
            return lhs.accept(this) + " = " + rhs.accept(this);
        }
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
    
    public Object visitExprArrayInit(ExprArrayInit exp)
    {
	StringBuffer sb = new StringBuffer();
	sb.append("{");

	List elems = exp.getElements();
	for (int i=0; i<elems.size(); i++) {
	    sb.append((String)((Expression)elems.get(i)).accept(this));
	    if (i!=elems.size()-1) {
		sb.append(",");
	    }
	    // leave blank line for multi-dim arrays
	    if (exp.getDims()>1) {
		sb.append("\n");
	    }
	}
	
	sb.append("}");

        return sb.toString();
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
        case ExprBinary.BINOP_BAND:op = "&"; break;
        case ExprBinary.BINOP_BOR: op = "|"; break;
        case ExprBinary.BINOP_BXOR:op = "^"; break;
        case ExprBinary.BINOP_LSHIFT: op = "<<"; break;
        case ExprBinary.BINOP_RSHIFT: op = ">>"; break;
        default: assert false : exp; break;
        }
        result += " " + op + " ";
        result += (String)exp.getRight().accept(this);
        result += ")";
        return result;
    }

    public Object visitExprComplex(ExprComplex exp)
    {
        // We should never see one of these at this point.
        assert false : exp;
        // If we do, print something vaguely intelligent:
        String r = "";
        String i = "";
        if (exp.getReal() != null) r = (String)exp.getReal().accept(this);
        if (exp.getImag() != null) i = (String)exp.getImag().accept(this);
        return "/* (" + r + ")+i(" + i + ") */";
    }

    public Object visitExprConstBoolean(ExprConstBoolean exp)
    {
        if (exp.getVal())
            return "true";
        else
            return "false";
    }

    public Object visitExprConstChar(ExprConstChar exp)
    {
        return "'" + exp.getVal() + "'";
    }

    public Object visitExprConstFloat(ExprConstFloat exp)
    {
        return Double.toString(exp.getVal()) + "f";
    }

    public Object visitExprConstInt(ExprConstInt exp)
    {
        return Integer.toString(exp.getVal());
    }
    
    public Object visitExprConstStr(ExprConstStr exp)
    {
        return exp.getVal();
    }

    public Object visitExprDynamicToken(ExprDynamicToken exp) {
	return "Rate.DYNAMIC_RATE";
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
	String result;
        String name = exp.getName();
        // Local function?
        if (ss.getFuncNamed(name) != null) {
            result = name + "(";
        }
	// look for print and println statements; assume everything
	// else is a math function
	else if (name.equals("print")) {
	    result = "System.out.println(";
	} else if (name.equals("println")) {
	    result = "System.out.println(";
        } else if (name.equals("super")) {
            result = "super(";
        } else if (name.equals("setDelay")) {
            result = "setDelay(";
        } else if (name.startsWith("enqueue")) {
            result = name + "(";
	} else {
	    // Math.sqrt will return a double, but we're only supporting
	    // float's now, so add a cast to float.  Not sure if this is
	    // the right thing to do for all math functions in all cases?
	    result = "(float)Math." + name + "(";
	}
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
        return peekFunction(ss.getStreamType()) + "(" + result + ")";
    }
    
    public Object visitExprPop(ExprPop exp)
    {
        return popFunction(ss.getStreamType()) + "()";
    }

    public Object visitExprRange(ExprRange exp) {
	String min = (String)exp.getMin().accept(this);
	String ave = (String)exp.getAve().accept(this);
	String max = (String)exp.getMax().accept(this);
	return "new Rate(" + min + ", " + ave + ", " + max + ")";
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
        default:
            assert false : exp;
            return null;
        }
    }

    public Object visitExprTypeCast(ExprTypeCast exp)
    {
        return "((" + convertType(exp.getType()) + ")(" +
            (String)exp.getExpr().accept(this) + "))";
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
        case ExprUnary.UNOP_COMPLEMENT: return "~" + child;
        default: assert false : exp; return null;
        }
    }

    public Object visitExprVar(ExprVar exp)
    {
        return exp.getName();
    }

    public Object visitFieldDecl(FieldDecl field)
    {
        // Assume all of the fields have the same type.
        String result = indent + convertType(field.getType(0)) + " ";
        for (int i = 0; i < field.getNumFields(); i++)
        {
            if (i > 0) result += ", ";
            result += field.getName(i);
            if (field.getInit(i) != null)
                result += " = " + (String)field.getInit(i).accept(this);
        }
        result += ";";
        if (field.getContext() != null)
            result += " // " + field.getContext();
        result += "\n";
        return result;
    }

    public Object visitFunction(Function func)
    {
        String result = indent + "public ";
        if (!func.getName().equals(ss.getName()))
            result += convertType(func.getReturnType()) + " ";
        result += func.getName();
        String prefix = null;
        if (func.getCls() == Function.FUNC_INIT) prefix = "final";
        result += doParams(func.getParams(), prefix) + " ";
        result += (String)func.getBody().accept(this);
        result += "\n";
        return result;
    }
    
    public Object visitFuncWork(FuncWork func)
    {
        // Nothing special here; we get to ignore the I/O rates.
        return visitFunction(func);
    }

    public Object visitProgram(Program prog)
    {
        // Nothing special here either.  Just accumulate all of the
        // structures and streams.
        String result = "";
        for (Iterator iter = prog.getStructs().iterator(); iter.hasNext(); )
        {
            TypeStruct struct = (TypeStruct)iter.next();
            result += indent + "class " + struct.getName() +
                " extends Structure {\n";
            addIndent();
            for (int i = 0; i < struct.getNumFields(); i++)
            {
                String name = struct.getField(i);
                Type type = struct.getType(name);
                result += indent + convertType(type) + " " + name + ";\n";
            }
            unIndent();
            result += indent + "}\n";
        }
        for (Iterator iter = prog.getStreams().iterator(); iter.hasNext(); )
            result += (String)((StreamSpec)iter.next()).accept(this);
        return result;
    }

    public Object visitSCAnon(SCAnon creator)
    {
        return creator.getSpec().accept(this);
    }
    
    public Object visitSCSimple(SCSimple creator)
    {
        String result;
        if (libraryFormat)
        {
            // Magic for builtins.
            if (creator.getName().equals("Identity") ||
                creator.getName().equals("FileReader") ||
                creator.getName().equals("FileWriter"))
                result = "new " + creator.getName() + "(";
            else
                result = creator.getName() + ".__construct(";
        }
        else
            result = "new " + creator.getName() + "(";
        boolean first = true;
        for (Iterator iter = creator.getParams().iterator(); iter.hasNext(); )
        {
            Expression param = (Expression)iter.next();
            if (!first) result += ", ";
            result += (String)param.accept(this);
            first = false;
        }
        for (Iterator iter = creator.getTypes().iterator(); iter.hasNext(); )
        {
            Type type = (Type)iter.next();
            if (!first) result += ", ";
            result += typeToClass(type);
            first = false;
        }
        result += ")";
        return result;
    }

    public Object visitSJDuplicate(SJDuplicate sj)
    {
        return "DUPLICATE()";
    }

    public Object visitSJRoundRobin(SJRoundRobin sj)
    {
        return "ROUND_ROBIN(" + (String)sj.getWeight().accept(this) + ")";
    }

    public Object visitSJWeightedRR(SJWeightedRR sj)
    {
        String result = "WEIGHTED_ROUND_ROBIN(";
        boolean first = true;
        for (Iterator iter = sj.getWeights().iterator(); iter.hasNext(); )
        {
            Expression weight = (Expression)iter.next();
            if (!first) result += ", ";
            result += (String)weight.accept(this);
            first = false;
        }
        result += ")";
        return result;
    }

    public Object doStreamCreator(String how, StreamCreator sc)
    {
        // If the stream creator involves registering with a portal,
        // we need a temporary variable.
        List portals = sc.getPortals();
        if (portals.isEmpty())
            return how + "(" + (String)sc.accept(this) + ")";
        String tempVar = varGen.nextVar();
        // Need run-time type of the creator.  Assert that only
        // named streams can be added to portals.
        SCSimple scsimple = (SCSimple)sc;
        String result = scsimple.getName() + " " + tempVar + " = " +
            (String)sc.accept(this);
        result += ";\n" + indent + how + "(" + tempVar + ")";
        for (Iterator iter = portals.iterator(); iter.hasNext(); )
        {
            Expression portal = (Expression)iter.next();
            result += ";\n" + indent + (String)portal.accept(this) +
                ".regReceiver(" + tempVar + ")";
        }
        return result;
    }
    
    public Object visitStmtAdd(StmtAdd stmt)
    {
        return doStreamCreator("add", stmt.getCreator());
    }
    
    public Object visitStmtAssign(StmtAssign stmt)
    {
        String op;
        switch(stmt.getOp())
        {
        case ExprBinary.BINOP_ADD: op = " += "; break;
        case ExprBinary.BINOP_SUB: op = " -= "; break;
        case ExprBinary.BINOP_MUL: op = " *= "; break;
        case ExprBinary.BINOP_DIV: op = " /= "; break;
        case ExprBinary.BINOP_LSHIFT: op = " <<= "; break;
        case ExprBinary.BINOP_RSHIFT: op = " >>= "; break;
        case 0: op = " = "; break;
        default: assert false: stmt; op = " = "; break;
        }
        // Assume both sides are the right type.
        return (String)stmt.getLHS().accept(this) + op +
            (String)stmt.getRHS().accept(this);
    }

    public Object visitStmtBlock(StmtBlock stmt)
    {
        // Put context label at the start of the block, too.
        String result = "{";
        if (stmt.getContext() != null)
            result += " // " + stmt.getContext();
        result += "\n";
        addIndent();
        for (Iterator iter = stmt.getStmts().iterator(); iter.hasNext(); )
        {
            Statement s = (Statement)iter.next();
            String line = indent;
            line += (String)s.accept(this);
	    if (!(s instanceof StmtIfThen)) {
		line += ";";
	    }
            if (s.getContext() != null)
                line += " // " + s.getContext();
            line += "\n";
            result += line;
        }
        unIndent();
        result += indent + "}";
        return result;
    }

    public Object visitStmtBody(StmtBody stmt)
    {
        return doStreamCreator("setBody", stmt.getCreator());
    }
    
    public Object visitStmtBreak(StmtBreak stmt)
    {
        return "break";
    }
    
    public Object visitStmtContinue(StmtContinue stmt)
    {
        return "continue";
    }

    public Object visitStmtDoWhile(StmtDoWhile stmt)
    {
        String result = "do ";
        result += (String)stmt.getBody().accept(this);
        result += "while (" + (String)stmt.getCond().accept(this) + ")";
        return result;
    }

    public Object visitStmtEmpty(StmtEmpty stmt)
    {
        return "";
    }

    public Object visitStmtEnqueue(StmtEnqueue stmt)
    {
        // Errk: this doesn't become nice Java code.
        return "/* enqueue(" + (String)stmt.getValue().accept(this) +
            ") */";
    }
    
    public Object visitStmtExpr(StmtExpr stmt)
    {
        String result = (String)stmt.getExpression().accept(this);
        // Gross hack to strip out leading class casts,
        // since they'll illegal (JLS 14.8).
        if (result.charAt(0) == '(' &&
            Character.isUpperCase(result.charAt(1)))
            result = result.substring(result.indexOf(')') + 1);
        return result;
    }

    public Object visitStmtFor(StmtFor stmt)
    {
        String result = "for (";
        if (stmt.getInit() != null)
            result += (String)stmt.getInit().accept(this);
        result += "; ";
        if (stmt.getCond() != null)
            result += (String)stmt.getCond().accept(this);
        result += "; ";
        if (stmt.getIncr() != null)
            result += (String)stmt.getIncr().accept(this);
        result += ") ";
        result += (String)stmt.getBody().accept(this);
        return result;
    }

    public Object visitStmtIfThen(StmtIfThen stmt)
    {
        // must have an if part...
        assert stmt.getCond() != null;
        String result = "if (" + (String)stmt.getCond().accept(this) + ") ";
        result += (String)stmt.getCons().accept(this);
        if (stmt.getAlt() != null)
            result += " else " + (String)stmt.getAlt().accept(this);
        return result;
    }

    public Object visitStmtJoin(StmtJoin stmt)
    {
        assert stmt.getJoiner() != null;
        return "setJoiner(" + (String)stmt.getJoiner().accept(this) + ")";
    }
    
    public Object visitStmtLoop(StmtLoop stmt)
    {
        assert stmt.getCreator() != null;
        return doStreamCreator("setLoop", stmt.getCreator());
    }

    public Object visitStmtPhase(StmtPhase stmt)
    {
        ExprFunCall fc = stmt.getFunCall();
        // ASSERT: the target is always a phase function.
        FuncWork target = (FuncWork)ss.getFuncNamed(fc.getName());
        StmtExpr call = new StmtExpr(stmt.getContext(), fc);
        String peek, pop, push;
        if (target.getPeekRate() == null)
            peek = "0";
        else
            peek = (String)target.getPeekRate().accept(this);
        if (target.getPopRate() == null)
            pop = "0";
        else
            pop = (String)target.getPopRate().accept(this);
        if (target.getPushRate() == null)
            push = "0";
        else
            push = (String)target.getPushRate().accept(this);
        
        return "phase(new WorkFunction(" + peek + "," + pop + "," + push +
            ") { public void work() { " + call.accept(this) + "; } })";
    }

    public Object visitStmtPush(StmtPush stmt)
    {
        return pushFunction(ss.getStreamType()) + "(" +
            (String)stmt.getValue().accept(this) + ")";
    }

    public Object visitStmtReturn(StmtReturn stmt)
    {
        if (stmt.getValue() == null) return "return";
        return "return " + (String)stmt.getValue().accept(this);
    }

    public Object visitStmtSendMessage(StmtSendMessage stmt)
    {
        String receiver = (String)stmt.getReceiver().accept(this);
        String result = "";

        // Issue one of the latency-setting statements.
        if (stmt.getMinLatency() == null)
        {
            if (stmt.getMaxLatency() == null)
                result += receiver + ".setAnyLatency()";
            else
                result += receiver + ".setMaxLatency(" +
                    (String)stmt.getMaxLatency().accept(this) + ")";
        }
        else
        {
            // Hmm, don't have an SIRLatency for only minimum latency.
            // Wing it.
            Expression max = stmt.getMaxLatency();
            if (max == null)
                max = new ExprBinary(null, ExprBinary.BINOP_MUL,
                                     stmt.getMinLatency(),
                                     new ExprConstInt(null, 100));
            result += receiver + ".setLatency(" +
                (String)stmt.getMinLatency().accept(this) + ", " +
                (String)max.accept(this) + ")";
        }
        
        result += ";\n" + indent + receiver + ".enqueueMessage(this, \"" + stmt.getName() + "\", new Object[] {";
        boolean first = true;
        for (Iterator iter = stmt.getParams().iterator(); iter.hasNext(); )
        {
            Expression param = (Expression)iter.next();
            if (!first) result += ", ";
            first = false;
	    // wrapInObject will take the primitive type output here
	    // and wrap it in an object for the sake of reflection
            result += receiver + ".wrapInObject(" + (String)param.accept(this) + ")";
        }
        result += "})";
        return result;
    }

    public Object visitStmtSplit(StmtSplit stmt)
    {
        assert stmt.getSplitter() != null;
        return "setSplitter(" + (String)stmt.getSplitter().accept(this) + ")";
    }

    public Object visitStmtVarDecl(StmtVarDecl stmt)
    {
        String result = "";
        // Hack: if the first variable name begins with "_final_", the
        // variable declaration should be final.
        if (stmt.getName(0).startsWith("_final_"))
            result += "final ";
        result += convertType(stmt.getType(0)) + " ";
        for (int i = 0; i < stmt.getNumVars(); i++)
        {
            if (i > 0)
                result += ", ";
            result += stmt.getName(i);
            if (stmt.getInit(i) != null)
                result += " = " + (String)stmt.getInit(i).accept(this);
        }
        return result;
    }

    public Object visitStmtWhile(StmtWhile stmt)
    {
        assert stmt.getCond() != null;
        assert stmt.getBody() != null;
        return "while (" + (String)stmt.getCond().accept(this) +
            ") " + (String)stmt.getBody().accept(this);
    }

    /**
     * For a non-anonymous StreamSpec, check to see if it has any
     * message handlers.  If it does, then generate a Java interface
     * containing the handlers named (StreamName)Interface, and
     * a portal class named (StreamName)Portal.
     */
    private String maybeGeneratePortal(StreamSpec spec)
    {
        List handlers = new java.util.ArrayList();
        for (Iterator iter = spec.getFuncs().iterator(); iter.hasNext(); )
        {
            Function func = (Function)iter.next();
            if (func.getCls() == Function.FUNC_HANDLER)
                handlers.add(func);
        }
        if (handlers.isEmpty())
            return null;
        
        // Okay.  Assemble the interface:
        StringBuffer result = new StringBuffer();
        result.append(indent + "interface " + spec.getName() +
                      "Interface {\n");
        addIndent();
        for (Iterator iter = handlers.iterator(); iter.hasNext(); )
        {
            Function func = (Function)iter.next();
            result.append(indent + "public ");
            result.append(convertType(func.getReturnType()) + " ");
            result.append(func.getName());
            result.append(doParams(func.getParams(), null));
            result.append(";\n");
        }
        unIndent();
        result.append(indent + "}\n");
        
        // Assemble the portal:
        result.append(indent + "class " + spec.getName() +
                      "Portal extends Portal implements " + spec.getName() +
                      "Interface {\n");
        addIndent();
        for (Iterator iter = handlers.iterator(); iter.hasNext(); )
        {
            Function func = (Function)iter.next();
            result.append(indent + "public ");
            result.append(convertType(func.getReturnType()) + " ");
            result.append(func.getName());
            result.append(doParams(func.getParams(), null));
            result.append(" { }\n");
        }
	unIndent();
        result.append(indent + "}\n");

        return result.toString();
    }

    /**
     * For a non-anonymous StreamSpec in the library path, generate
     * extra functions we need to construct the object.  In the
     * compiler path, generate an empty constructor.
     */
    private String maybeGenerateConstruct(StreamSpec spec)
    {
        StringBuffer result = new StringBuffer();
        
        // The StreamSpec at this point has no parameters; we need to
        // find the parameters of the init function.
        Function init = spec.getInitFunc();
        // (ASSERT: init != null)
        List params = init.getParams();
        
        // In the library path, generate the __construct() mechanism:
        if (libraryFormat)
        {
            // Generate fields for each of the parameters.
            for (Iterator iter = params.iterator(); iter.hasNext(); )
            {
                Parameter param = (Parameter)iter.next();
                result.append(indent + "private " +
                              convertType(param.getType()) +
                              " __param_" + param.getName() + ";\n");
            }

            // Generate a __construct() method that saves these.
            result.append(indent + "public static " + spec.getName() +
                          " __construct(");
            boolean first = true;
            for (Iterator iter = params.iterator(); iter.hasNext(); )
            {
                Parameter param = (Parameter)iter.next();
                if (!first) result.append(", ");
                first = false;
                result.append(convertType(param.getType()) + " " +
                              param.getName());
            }
            result.append(")\n" + indent + "{\n");
            addIndent();
            result.append(indent + spec.getName() + " __obj = new " +
                          spec.getName() + "();\n");
            for (Iterator iter = params.iterator(); iter.hasNext(); )
            {
                Parameter param = (Parameter)iter.next();
                String name = param.getName();
                result.append(indent + "__obj.__param_" + name + " = " +
                              name + ";\n");
            }
            result.append(indent + "return __obj;\n");
            unIndent();
            result.append(indent + "}\n");
            
            // Generate a callInit() method.
            result.append(indent + "protected void callInit()\n" +
                          indent + "{\n");
            addIndent();
            result.append(indent + "init(");
            first = true;
            for (Iterator iter = params.iterator(); iter.hasNext(); )
            {
                Parameter param = (Parameter)iter.next();
                if (!first) result.append(", ");
                first = false;
                result.append("__param_" + param.getName());
            }
            result.append(");\n");
            unIndent();
            result.append(indent + "}\n");
        }
        // In the compiler path, generate an empty constructor.
        else // (!libraryFormat)
        {
            result.append(indent + "public " + spec.getName() + "(");
            boolean first = true;
            for (Iterator iter = params.iterator(); iter.hasNext(); )
            {
                Parameter param = (Parameter)iter.next();
                if (!first) result.append(", ");
                first = false;
                result.append(convertType(param.getType()) + " " +
                              param.getName());
            }
            result.append(")\n" + indent + "{\n" + indent + "}\n");
        }
        
        return result.toString();
    }

    public Object visitStreamSpec(StreamSpec spec)
    {
        String result = "";
        // Anonymous classes look different from non-anonymous ones.
        // This appears in two places: (a) as a top-level (named)
        // stream; (b) in an anonymous stream creator (SCAnon).
        if (spec.getName() != null)
        {
            // Non-anonymous stream.  Maybe it has interfaces.
            String ifaces = maybeGeneratePortal(spec);
            if (ifaces == null)
                ifaces = "";
            else
            {
                result += ifaces;
                ifaces = " implements " + spec.getName() + "Interface";
            }
            result += indent;
            // This is only public if it's the top-level stream,
            // meaning it has type void->void.
            StreamType st = spec.getStreamType();
            if (st != null &&
                st.getIn() instanceof TypePrimitive &&
                ((TypePrimitive)st.getIn()).getType() ==
                TypePrimitive.TYPE_VOID &&
                st.getOut() instanceof TypePrimitive &&
                ((TypePrimitive)st.getOut()).getType() ==
                TypePrimitive.TYPE_VOID)
            {
                result += "public class " + spec.getName() +
                    " extends StreamIt" + spec.getTypeString() + ifaces +
                    " // " + spec.getContext() + "\n";
                result += indent + "{\n";
                addIndent();
                result += indent + "public static void main(String[] args) {\n";
                addIndent();
                result += indent + spec.getName() + " program = new " +
                    spec.getName() + "();\n";
                result += indent + "program.run(args);\n";
                unIndent();
                result += indent + "}\n";
            }
            else
            {
                result += "class " + spec.getName() + " extends ";
                if (spec.getType() == StreamSpec.STREAM_FILTER)
                {
                    // Need to notice now if this is a phased filter.
                    FuncWork work = spec.getWorkFunc();
                    if (work.getPushRate() == null &&
                        work.getPopRate() == null &&
                        work.getPeekRate() == null)
                        result += "PhasedFilter";
                    else
                        result += "Filter";
                }
                else
                    switch (spec.getType())
                    {
                    case StreamSpec.STREAM_PIPELINE:
                        result += "Pipeline";
                        break;
                    case StreamSpec.STREAM_SPLITJOIN:
                        result += "SplitJoin";
                        break;
                    case StreamSpec.STREAM_FEEDBACKLOOP:
                        result += "FeedbackLoop";
                        break;
                    }
                result += ifaces + " // " + spec.getContext() + "\n" +
                    indent + "{\n";
                addIndent();
                // If we're in the library backend, we need a construct()
                // method too; in the compiler backend, a constructor.
                result += maybeGenerateConstruct(spec);
            }
        }
        else
        {
            // Anonymous stream:
            result += "new ";
            switch (spec.getType())
            {
            case StreamSpec.STREAM_FILTER: result += "Filter";
                break;
            case StreamSpec.STREAM_PIPELINE: result += "Pipeline";
                break;
            case StreamSpec.STREAM_SPLITJOIN: result += "SplitJoin";
                break;
            case StreamSpec.STREAM_FEEDBACKLOOP: result += "FeedbackLoop";
                break;
            }
            result += "() {\n" + indent;
            addIndent();
        }
        
        // At this point we get to ignore wholesale the stream type, except
        // that we want to save it.
        StreamSpec oldSS = ss;
        ss = spec;

        // Output field definitions:
        for (Iterator iter = spec.getVars().iterator(); iter.hasNext(); )
        {
            FieldDecl varDecl = (FieldDecl)iter.next();
            result += (String)varDecl.accept(this);
        }
        
        // Output method definitions:
        for (Iterator iter = spec.getFuncs().iterator(); iter.hasNext(); )
            result += (String)(((Function)iter.next()).accept(this));

        ss = oldSS;
        unIndent();
        result += "}\n";
        return result;
    }
    
    public Object visitStreamType(StreamType type)
    {
        // Nothing to do here.
        return "";
    }
    
    public Object visitOther(FENode node)
    {
        if (node instanceof ExprJavaConstructor)
        {
            ExprJavaConstructor jc = (ExprJavaConstructor)node;
            return makeConstructor(jc.getType());
        }
        if (node instanceof StmtIODecl)
        {
            StmtIODecl io = (StmtIODecl)node;
            String result = io.getName() + " = new Channel(" +
                typeToClass(io.getType()) + ", " +
                (String)io.getRate1().accept(this);
            if (io.getRate2() != null)
                result += ", " + (String)io.getRate2().accept(this);
            result += ")";
            return result;
        }
        if (node instanceof StmtAddPhase)
        {
            StmtAddPhase ap = (StmtAddPhase)node;
            String result;
            if (ap.isInit())
                result = "addInitPhase";
            else result = "addSteadyPhase";
            result += "(";
            if (ap.getPeek() == null)
                result += "0, ";
            else
                result += (String)ap.getPeek().accept(this) + ", ";
            if (ap.getPop() == null)
                result += "0, ";
            else
                result += (String)ap.getPop().accept(this) + ", ";
            if (ap.getPush() == null)
                result += "0, ";
            else
                result += (String)ap.getPush().accept(this) + ", ";
            result += "\"" + ap.getName() + "\")";
            return result;
        }
        if (node instanceof StmtSetTypes)
        {
            StmtSetTypes sst = (StmtSetTypes)node;
            return "setIOTypes(" + typeToClass(sst.getInType()) +
                ", " + typeToClass(sst.getOutType()) + ")";
        }
        else
        {
            assert false : node;
            return "";
        }
    }
}
