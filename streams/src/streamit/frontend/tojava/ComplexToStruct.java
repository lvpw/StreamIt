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

import java.util.Iterator;
import java.util.List;

import streamit.frontend.nodes.FEReplacer;
import streamit.frontend.nodes.FieldDecl;
import streamit.frontend.nodes.Function;
import streamit.frontend.nodes.Parameter;
import streamit.frontend.nodes.Program;
import streamit.frontend.nodes.StmtVarDecl;
import streamit.frontend.nodes.StreamSpec;
import streamit.frontend.nodes.StreamType;
import streamit.frontend.nodes.Type;
import streamit.frontend.nodes.TypeArray;
import streamit.frontend.nodes.TypePrimitive;
import streamit.frontend.nodes.TypeStruct;

/**
 * Replace primitive complex types with the <code>Complex</code>
 * structure.  This looks through all of the structure declarations
 * and replaces types in variable and field declarations and
 * parameters with the correct actual structure type.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id: ComplexToStruct.java,v 1.8 2006-09-25 13:54:54 dimock Exp $
 */
public class ComplexToStruct extends FEReplacer
{
    private TypeStruct cplxType;
    private TypeStruct float2Type;
    private TypeStruct float3Type;
    private TypeStruct float4Type;

    private Type remapType(Type type)
    {
        if (type.isComplex())
            return cplxType;

        if (type.isComposite()) {
            int ty = ((TypePrimitive)type).getType();
            switch(ty) {
            case TypePrimitive.TYPE_FLOAT2: return float2Type;
            case TypePrimitive.TYPE_FLOAT3: return float3Type;
            case TypePrimitive.TYPE_FLOAT4: return float4Type;
            default:
                return null;
            }
        }   

        if (type instanceof TypeArray)
            {
                TypeArray ta = (TypeArray)type;
                type = new TypeArray(remapType(ta.getBase()), ta.getLength());
            }
        return type;
    }

    public ComplexToStruct()
    {
        cplxType = null;
    }
    
    public Object visitProgram(Program prog)
    {
        // Go through the list of structures, and notice them all.
        // We also need to rewrite the structures, in case there are
        // structs that contain structs.
        List newStructs = new java.util.ArrayList();
        // Pass 1: find the struct named "Complex".
        for (Iterator iter = prog.getStructs().iterator(); iter.hasNext(); )
            {
                TypeStruct struct = (TypeStruct)iter.next();
                if (struct.getName().equals("Complex"))
                    cplxType = struct;
                else if (struct.getName().equals("float2"))
                    float2Type = struct;
                else if (struct.getName().equals("float3"))
                    float3Type = struct;
                else if (struct.getName().equals("float4"))
                    float4Type = struct;

            }
        // Pass 2: rewrite existing structures.
        for (Iterator iter = prog.getStructs().iterator(); iter.hasNext(); )
            {
                TypeStruct struct = (TypeStruct)iter.next();
                List newNames = new java.util.ArrayList();
                List newTypes = new java.util.ArrayList();
                for (int i = 0; i < struct.getNumFields(); i++)
                    {
                        String name = struct.getField(i);
                        Type type = remapType(struct.getType(name));
                        newNames.add(name);
                        newTypes.add(type);
                    }
                struct = new TypeStruct(struct.getContext(), struct.getName(),
                                        newNames, newTypes);
                newStructs.add(struct);
            }
        prog = new Program(prog.getContext(), prog.getStreams(), newStructs, prog.getHelpers());
        return super.visitProgram(prog);
    }

    public Object visitFieldDecl(FieldDecl field)
    {
        List<Type> newTypes = new java.util.ArrayList<Type>();
        for (int i = 0; i < field.getNumFields(); i++)
            newTypes.add(remapType(field.getType(i)));
        return new FieldDecl(field.getContext(), newTypes,
                             field.getNames(), field.getInits());
    }

    public Object visitFunction(Function func)
    {
        // Visit the parameter list, then let FEReplacer do the
        // rest of the work.
        List newParams = new java.util.ArrayList();
        for (Iterator iter = func.getParams().iterator(); iter.hasNext(); )
            {
                Parameter param = (Parameter)iter.next();
                Type type = remapType(param.getType());
                param = new Parameter(type, param.getName());
                newParams.add(param);
            }
        Type returnType = remapType(func.getReturnType());
        return super.visitFunction(new Function(func.getContext(),
                                                func.getCls(),
                                                func.getName(),
                                                returnType,
                                                newParams,
                                                func.getBody(),
                                                func.getPeekRate(),
                                                func.getPopRate(),
                                                func.getPushRate()));
    }

    public Object visitStmtVarDecl(StmtVarDecl stmt)
    {
        List<Type> newTypes = new java.util.ArrayList<Type>();
        for (int i = 0; i < stmt.getNumVars(); i++)
            newTypes.add(remapType(stmt.getType(i)));
        return new StmtVarDecl(stmt.getContext(), newTypes,
                               stmt.getNames(), stmt.getInits());
    }

    public Object visitStreamSpec(StreamSpec ss)
    {
        // Visit the parameter list, then let FEReplacer do the
        // rest of the work.
        List newParams = new java.util.ArrayList();
        for (Iterator iter = ss.getParams().iterator(); iter.hasNext(); )
            {
                Parameter param = (Parameter)iter.next();
                Type type = remapType(param.getType());
                param = new Parameter(type, param.getName());
                newParams.add(param);
            }
        return super.visitStreamSpec(new StreamSpec(ss.getContext(),
                                                    ss.getType(),
                                                    ss.getStreamType(),
                                                    ss.getName(),
                                                    newParams,
                                                    ss.getVars(),
                                                    ss.getFuncs(),
                                                    ss.isStateful()));
    }

    public Object visitStreamType(StreamType st)
    {
        return new StreamType(st.getContext(),
                              remapType(st.getIn()),
                              remapType(st.getOut()),
                              remapType(st.getLoop()));
    }
}
