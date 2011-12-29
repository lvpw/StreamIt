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
 * $Id: JLoopStatement.java,v 1.9 2006-03-24 22:45:15 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.JavaStyleComment;
import at.dms.compiler.TokenReference;

/**
 * Loop Statement
 *
 * Root class for loop statement
 */
public abstract class JLoopStatement extends JStatement {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
	 * 
	 */
	private static final long serialVersionUID = -5300100135590281411L;

	protected JLoopStatement() {} // for cloner only

    /**
     * Construct a node in the parsing tree
     * @param   where       the line of this node in the source code
     */
    public JLoopStatement(TokenReference where, JavaStyleComment[] comments) {
        super(where, comments);
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Return the end of this block (for break statement)
     */
    @Override
	public CodeLabel getBreakLabel() {
        return endLabel;
    }

    /**
     * Return the beginning of this block (for continue statement)
     */
    @Override
	public CodeLabel getContinueLabel() {
        return contLabel;
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private CodeLabel       contLabel = new CodeLabel();
    private CodeLabel       endLabel = new CodeLabel();

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() { at.dms.util.Utils.fail("Error in auto-generated cloning methods - deepClone was called on an abstract class."); return null; }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JLoopStatement other) {
        super.deepCloneInto(other);
        other.contLabel = (at.dms.kjc.CodeLabel)at.dms.kjc.AutoCloner.cloneToplevel(this.contLabel);
        other.endLabel = (at.dms.kjc.CodeLabel)at.dms.kjc.AutoCloner.cloneToplevel(this.endLabel);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
