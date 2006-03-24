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
 * $Id: CIntType.java,v 1.7 2006-03-24 15:54:47 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.UnpositionedError;
import at.dms.util.InconsistencyException;
import at.dms.util.SimpleStringBuffer;

/**
 * This class represents the Java type "int".
 * There is only one instance of this type.
 */
public class CIntType extends CNumericType {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Constructs a new instance.
     */
    protected CIntType() {
        super(TID_INT);
    }

    private Object readResolve() throws Exception {
        return CStdType.Integer;
    }

    
    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns a string representation of this type.
     */
    public String toString() {
        return "int";
    }

    /**
     * Returns the VM signature of this type.
     */
    public String getSignature() {
        return "I";
    }

    /**
     * Appends the VM signature of this type to the specified buffer.
     */
    protected void appendSignature(SimpleStringBuffer buffer) {
        buffer.append('I');
    }

    /**
     * Returns the stack size (conservative estimate of maximum number
     * of bytes needed in C on 32-bit machine) used by a value of this
     * type.
     */
    public int getSizeInC() {
        return 4;
    }

    /**
     * Returns the stack size used by a value of this type.
     */
    public int getSize() {
        return 1;
    }

    /**
     * Is this type ordinal ?
     */
    public boolean isOrdinal() {
        return true;
    }

    /**
     * Is this a floating point type ?
     */
    public boolean isFloatingPoint() {
        return false;
    }

    /**
     * Can this type be converted to the specified type by assignment conversion (JLS 5.2) ?
     * @param   dest        the destination type
     * @return  true iff the conversion is valid
     */
    public boolean isAssignableTo(CType dest) {
        if (dest == this) {
            // JLS 5.1.1 Identity Conversion
            return true;
        } else {
            // JLS 5.1.2 Widening Primitive Conversion
            switch (dest.getTypeID()) {
            case TID_LONG:
            case TID_FLOAT:
            case TID_DOUBLE:
                return true;
            default:
                return false;
            }
        }
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Generates a bytecode sequence to convert a value of this type to the
     * specified destination type.
     * @param   dest        the destination type
     * @param   code        the code sequence
     */
    public void genCastTo(CNumericType dest, CodeSequence code) {
        if (dest != this) {
            switch (dest.type) {
            case TID_BYTE:
                code.plantNoArgInstruction(opc_i2b);
                break;

            case TID_CHAR:
                code.plantNoArgInstruction(opc_i2c);
                break;

            case TID_SHORT:
                code.plantNoArgInstruction(opc_i2s);
                break;

            case TID_LONG:
                code.plantNoArgInstruction(opc_i2l);
                break;

            case TID_FLOAT:
                code.plantNoArgInstruction(opc_i2f);
                break;

            case TID_DOUBLE:
                code.plantNoArgInstruction(opc_i2d);
                break;

            default:
                throw new InconsistencyException();
            }
        }
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.kjc.CIntType other = new at.dms.kjc.CIntType();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.CIntType other) {
        super.deepCloneInto(other);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
