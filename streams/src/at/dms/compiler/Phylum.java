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
 * $Id: Phylum.java,v 1.12 2006-10-11 17:45:32 dimock Exp $
 */

package at.dms.compiler;

import at.dms.util.Utils;

/**
 * This class represents the root class for all elements of the parsing tree
 */
public abstract class Phylum extends Utils {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
	 * 
	 */
	private static final long serialVersionUID = 2622629319648236608L;

	protected Phylum() {} // for cloner only
    /**
     * construct an element of the parsing tree
     * @param where the token reference of this node
     */
    public Phylum(TokenReference where) {
        this.where = where;
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns the token reference of this node in the source text.
     * @return the entire token reference
     */
    public TokenReference getTokenReference() {
        return where;
    }
    
    /**
     * Update the token reference of this node.
     * Useful if transforming a JPhylum but want to track location.
     * @param where : new TokenReference value.  Just stored, not cloned.
     */
    public void setTokenReference(TokenReference where) {
        this.where = where;
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private /* final */ TokenReference  where;      // position in the source text.  removed "final" for cloner.

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() { at.dms.util.Utils.fail("Error in auto-generated cloning methods - deepClone was called on an abstract class."); return null; }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.compiler.Phylum other) {
        super.deepCloneInto(other);
        other.where = (at.dms.compiler.TokenReference)at.dms.kjc.AutoCloner.cloneToplevel(this.where);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
