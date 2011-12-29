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
 * $Id: CodeLabel.java,v 1.5 2006-03-24 15:54:47 dimock Exp $
 */

package at.dms.kjc;

import java.io.Serializable;

import at.dms.classfile.AbstractInstructionAccessor;

/**
 * This class represents a position in the code array where the associated
 * instruction has not yet been generated.
 */
class CodeLabel extends AbstractInstructionAccessor implements Serializable, DeepCloneable {

    // --------------------------------------------------------------------
    // CONSTRUCTORS
    // --------------------------------------------------------------------

    /**
     * Constructs a new code label.
     */
    public CodeLabel() {
        this.address = -1;
    }

    // --------------------------------------------------------------------
    // ACCESSORS
    // --------------------------------------------------------------------

    /**
     * Sets the address of the label in the code array.
     */
    public void setAddress(int address) {
        this.address = address;
    }

    /**
     * Returns the address of the label in the code array.
     */
    public int getAddress() {
        return address;
    }

    /**
     * Returns true iff the label has already been planted.
     */
    public boolean hasAddress() {
        return address != -1;
    }

    // --------------------------------------------------------------------
    // DATA MEMBERS
    // --------------------------------------------------------------------

    private int             address;

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.CodeLabel other = new at.dms.kjc.CodeLabel();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.CodeLabel other) {
        super.deepCloneInto(other);
        other.address = this.address;
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
