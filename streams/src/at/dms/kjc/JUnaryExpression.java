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
 * $Id: JUnaryExpression.java,v 1.11 2006-12-20 18:03:33 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.TokenReference;

/**
 * This class represents unary expressions.
 */
public abstract class JUnaryExpression extends JExpression {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
	 * 
	 */
	private static final long serialVersionUID = -7869464398164875114L;

	protected JUnaryExpression() {} // for cloning only

    /**
     * Construct a node in the parsing tree
     * @param   where       the line of this node in the source code
     * @param   expr        the operand
     */
    public JUnaryExpression(TokenReference where, JExpression expr) {
        super(where);
        this.expr = expr;
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Compute the type of this expression (called after parsing)
     * @return the type of this expression
     */
    @Override
	public CType getType() {
        return type;
    }
    
    /**
     * 
     */
    @Override
	public void setType(CType type) {
        this.type = type;
    }
    
    public JExpression getExpr() { return expr; }
    
    public void setExpr(JExpression e) {
        this.expr = e;
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    protected JExpression       expr;
    protected CType     type;

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() { at.dms.util.Utils.fail("Error in auto-generated cloning methods - deepClone was called on an abstract class."); return null; }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JUnaryExpression other) {
        super.deepCloneInto(other);
        other.expr = (at.dms.kjc.JExpression)at.dms.kjc.AutoCloner.cloneToplevel(this.expr);
        other.type = (at.dms.kjc.CType)at.dms.kjc.AutoCloner.cloneToplevel(this.type);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
