/*
 * Copyright (C) 1990-2001 DMS Decision Management Systems Ges.m.b.H.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * $Id: CBinaryMethod.java,v 1.7 2006-03-24 15:54:46 dimock Exp $
 */

package at.dms.kjc;

import at.dms.classfile.MethodInfo;

/**
 * This class represents a loaded (already compiled) class method.
 */
public class CBinaryMethod extends CMethod {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    protected CBinaryMethod() {} // for cloner only

    /**
     * Constructs method
     * @param   owner       the owner of this method
     * @param   methodInfo  a method info from a class file
     */
    public CBinaryMethod(CClass owner, MethodInfo methodInfo) {
        super(owner,
              methodInfo.getModifiers(),
              methodInfo.getName(),
              buildReturnType(methodInfo),
              buildParameterTypes(methodInfo),
              buildExceptionTypes(methodInfo),
              methodInfo.isDeprecated());
    }

    private static CType buildReturnType(MethodInfo methodInfo) {
        CType[] types = CType.parseMethodSignature(methodInfo.getSignature());

        return types[types.length - 1];
    }

    private static CType[] buildParameterTypes(MethodInfo methodInfo) {
        CType[] signature = CType.parseMethodSignature(methodInfo.getSignature());
        CType[] paramTypes = new CType[signature.length - 1];

        paramTypes = new CType[signature.length - 1];
 
        for (int i = 0; i < paramTypes.length; i++) {
            paramTypes[i] = signature[i];
        }
        return paramTypes;
    }

    private static CClassType[] buildExceptionTypes(MethodInfo methodInfo) {
        String[]    exceptions = methodInfo.getExceptions();

        if (exceptions == null) {
            return new CClassType[0];
        } else {
            CClassType[]    types = new CClassType[exceptions.length];

            for (int i = 0; i < exceptions.length; i++) {
                types[i] = CClassType.lookup(exceptions[i]);
            }
            return types;
        }
    }

    // ----------------------------------------------------------------------
    // CHECK MATCHING
    // ----------------------------------------------------------------------

    /**
     * equals
     * search if two methods have same signature
     * @param   other       the other method
     */
    @Override
	public boolean equals(CMethod other) {
        CClass owner = getOwner();

        if (!isConstructor() 
            || !other.isConstructor()
            || !owner.isNested() 
            || !owner.hasOuterThis() 
            || other instanceof CBinaryMethod) {
            return super.equals(other);
        } else {
            final CType[]       parameters = getParameters();
            final CType[]       otherParameters = other.getParameters();
      
            // in constructors of inner classes first parameter is enclosed this
            if (!getOwner().equals(other.getOwner())) {
                return false;
            } else if (getIdent() != other.getIdent()) {
                return false;
            } else if (parameters.length != otherParameters.length-1) {
                return false;
            } else {
                for (int i = 1; i < parameters.length; i++) {
                    if (!parameters[i].equals(otherParameters[i-1])) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    /**
     * Is this method applicable to the specified invocation (JLS 15.12.2.1) ?
     * @param   ident       method invocation name
     * @param   actuals     method invocation arguments
     */
    @Override
	public boolean isApplicableTo(String ident, CType[] actuals) {
        CClass owner = getOwner();

        if (!isConstructor() 
            || ident != JAV_CONSTRUCTOR 
            || !owner.isNested() 
            || !owner.hasOuterThis()) {
            return super.isApplicableTo(ident, actuals);
        } else {
            final CType[]       parameters = getParameters();

            if (ident != getIdent()) {
                return false;
            } else if (actuals.length+1 != parameters.length) {
                return false;
            } else {
                for (int i = 0; i < actuals.length; i++) {
                    // method invocation conversion = assigment conversion without literal narrowing
                    // we just look at the type and do not consider literal special case
                    if (!actuals[i].isAssignableTo(parameters[i+1])) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    /**
     * Is this method more specific than the one given as argument (JLS 15.12.2.2) ?
     * @param   other       the method to compare to
     */
    @Override
	public boolean isMoreSpecificThan(CMethod other) {
        CClass owner = getOwner();

        if (!isConstructor() 
            || !other.isConstructor()
            || !owner.isNested() 
            || !owner.hasOuterThis()
            || other instanceof CBinaryMethod) {
            return super.isMoreSpecificThan(other);
        } else {
            final CType[]       parameters = getParameters();
            final CType[]       otherParameters = other.getParameters();

            if (!getOwner().getType().isAssignableTo(other.getOwner().getType())) {
                return false;
            } else if (parameters.length != otherParameters.length+1) {
                return false;
            } else {
                for (int i = 0; i < otherParameters.length; i++) {
                    // method invocation conversion = assigment conversion without literal narrowing
                    // we just look at the type and do not consider literal special case
                    if (!parameters[i+1].isAssignableTo(otherParameters[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    /**
     * Has this method the same signature as the one given as argument ?
     * NOTE: return type not considered
     * @param   other       the method to compare to
     */
    @Override
	public boolean hasSameSignature(CMethod other) {
        CClass owner = getOwner();

        if (!isConstructor() 
            || !other.isConstructor()
            || !owner.isNested() 
            || !owner.hasOuterThis()
            || other instanceof CBinaryMethod) {
            return super.hasSameSignature(other);
        } else {
            final CType[]       parameters = getParameters();
            final CType[]       otherParameters = other.getParameters();

            if (parameters.length != otherParameters.length+1) {
                return false;
            } else {
                for (int i = 0; i < otherParameters.length; i++) {
                    if (!parameters[i+1].equals(otherParameters[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    /**
     * Returns a string representation of this method.
     */
    @Override
	public String toString() {
        CClass owner = getOwner();

        if (!isConstructor() 
            || !owner.isNested() 
            || !owner.hasOuterThis()) {
            return super.toString();
        } else {
            StringBuffer    buffer = new StringBuffer();
            final CType[]     parameters = getParameters();

            buffer.append(getReturnType());
            buffer.append(" ");
            buffer.append(getOwner());
            buffer.append(".");
            buffer.append(getIdent());
            buffer.append("(");
            for (int i = 1; i < parameters.length; i++) {
                if (i != 1) {
                    buffer.append(", ");
                }
                buffer.append(parameters[i]);
            }
            buffer.append(")");

            return buffer.toString();
        }
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.CBinaryMethod other = new at.dms.kjc.CBinaryMethod();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.CBinaryMethod other) {
        super.deepCloneInto(other);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
