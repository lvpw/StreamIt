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
 * $Id: CNumericType.java,v 1.6 2006-03-24 15:54:47 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.UnpositionedError;
import at.dms.util.SimpleStringBuffer;

/**
 * This class represents java and kopi numericals types
 * Such as byte, short, int, long, float, double
 */
public abstract class CNumericType extends CType {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Constructor
     * @param   typeID      the ident (int value) of this type
     */
    protected CNumericType(int typeID) {
        super(typeID);
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns a string representation of this type.
     */
    @Override
	public abstract String toString();

    /**
     * Returns the VM signature of this type.
     */
    @Override
	public abstract String getSignature();

    /**
     * Appends the VM signature of this type to the specified buffer.
     */
    @Override
	protected abstract void appendSignature(SimpleStringBuffer buffer);

    /**
     * Returns the stack size (conservative estimate of maximum number
     * of bytes needed in C on 32-bit machine) used by a value of this
     * type.
     */
    @Override
	public abstract int getSizeInC();

    /**
     * Returns the stack size used by a value of this type.
     */
    @Override
	public abstract int getSize();

    /**
     * Is this type ordinal ?
     */
    @Override
	public abstract boolean isOrdinal();

    /**
     * Is this a floating point type ?
     */
    @Override
	public abstract boolean isFloatingPoint();

    /**
     * Is this a numeric type ?
     */
    @Override
	public boolean isNumeric() {
        return true;
    }

    // ----------------------------------------------------------------------
    // BODY CHECKING
    // ----------------------------------------------------------------------

    /**
     * check that type is valid
     * necessary to resolve String into java/lang/String
     * @exception   UnpositionedError   this error will be positioned soon
     */
    @Override
	public void checkType(CContext context) throws UnpositionedError {
    }

    /**
     * Can this type be converted to the specified type by assignment conversion (JLS 5.2) ?
     * @param   dest        the destination type
     * @return  true iff the conversion is valid
     */
    @Override
	public abstract boolean isAssignableTo(CType dest);

    /**
     * Can this type be converted to the specified type by casting conversion (JLS 5.5) ?
     * @param   dest        the destination type
     * @return  true iff the conversion is valid
     */
    @Override
	public boolean isCastableTo(CType dest) {
        return dest.isNumeric();
    }

    /**
     * unaryPromote
     * search the type corresponding to the type after computation
     * @param t1 the type
     * @return the corresponding type after operation
     */
    public static CType unaryPromote(CType t1) {
        if (t1 == CStdType.Byte || t1 == CStdType.Short || t1 == CStdType.Char) {
            return CStdType.Integer;
        } else {
            return t1;
        }
    }

    /**
     * binaryPromote
     * search the type corresponding to the promotion of the two types
     * @param t1 the first type
     * @param t2 the second type
     * @return the corresponding type or null
     */
    public static CType binaryPromote(CType t1, CType t2) {
        assert t1.isNumeric() && t2.isNumeric();
        if (t1 == CStdType.Double) {
            return CStdType.Double;
        } else if (t2 == CStdType.Double) {
            return CStdType.Double;
        } else if (t1 == CStdType.Float) {
            return CStdType.Float;
        } else if (t2 == CStdType.Float) {
            return CStdType.Float;
        } else if (t1 == CStdType.Long) {
            return CStdType.Long;
        } else if (t2 == CStdType.Long) {
            return CStdType.Long;
        } else {
            return CStdType.Integer;
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
    public abstract void genCastTo(CNumericType dest, CodeSequence code);

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() { at.dms.util.Utils.fail("Error in auto-generated cloning methods - deepClone was called on an abstract class."); return null; }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.CNumericType other) {
        super.deepCloneInto(other);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
