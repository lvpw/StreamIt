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
 * $Id: CNullType.java,v 1.6 2006-03-24 15:54:47 dimock Exp $
 */

package at.dms.kjc;

/**
 * This class represents null class type in the type structure
 */
public class CNullType extends CClassType {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Construct a class type
     */
    public CNullType() {
        super(new CBadClass("<NULL TYPE>"));
    }

    private Object readResolve() throws Exception {
        return CStdType.Null;
    }


    // ----------------------------------------------------------------------
    // INTERFACE CHECKING
    // ----------------------------------------------------------------------

    /**
     * Can this type be converted to the specified type by assignment conversion (JLS 5.2) ?
     * @param   dest        the destination type
     * @return  true iff the conversion is valid
     */
    @Override
	public boolean isAssignableTo(CType dest) {
        return dest.isReference();
    }

    /**
     * Can this type be converted to the specified type by casting conversion (JLS 5.5) ?
     * @param   dest        the destination type
     * @return  true iff the conversion is valid
     */
    @Override
	public boolean isCastableTo(CType dest) {
        return dest.isReference();
    }

    /**
     * Returns the class object associated with this type
     *
     * If this type was never checked (read from class files)
     * check it!
     *
     * @return the class object associated with this type
     */
    @Override
	public CClass getCClass() {
        return CStdType.Object.getCClass();
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Transforms this type to a string
     */
    @Override
	public String toString() {
        return "null-literal";
    }

    /**
     * Returns the stack size (conservative estimate of maximum number
     * of bytes needed in C on 32-bit machine) used by a value of this
     * type.
     */
    @Override
	public int getSizeInC() {
        // not clear what to return here... what does null type mean in
        // C?
        return 4;
    }

    /**
     * Returns the stack size used by a value of this type.
     */
    @Override
	public int getSize() {
        return 1;
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.CNullType other = new at.dms.kjc.CNullType();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.CNullType other) {
        super.deepCloneInto(other);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
