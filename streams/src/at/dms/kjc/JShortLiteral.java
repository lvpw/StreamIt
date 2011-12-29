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
 * $Id: JShortLiteral.java,v 1.13 2006-10-27 20:48:55 dimock Exp $
 */

package at.dms.kjc;

import at.dms.classfile.PushLiteralInstruction;
import at.dms.compiler.TokenReference;
import at.dms.util.InconsistencyException;

/**
 * JLS 3.10.1 Integer Literals. This class represents short literals.
 * Instances of this class are only created by conversion operations.
 */
public class JShortLiteral extends JLiteral {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    protected JShortLiteral() {} // for cloner only

    /**
     * Constructs a literal expression from a constant value.
     * @param   where       the line of this node in the source code
     * @param   value       the constant value
     */
    public JShortLiteral(TokenReference where, short value) {
        super(where);
        this.value = value;
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns the type of this expression.
     */
    @Override
	public CType getType() {
        return CStdType.Short;
    }

    /**
     * Returns the constant value of the expression.
     */
    @Override
	public short shortValue() {
        return value;
    }

    /**
     * Returns true iff the value of this literal is the
     * default value for this type (JLS 4.5.5).
     */
    @Override
	public boolean isDefault() {
        return value == 0;
    }

    @Override
	public String convertToString() {
        return ""+value;
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Can this expression be converted to the specified type by assignment conversion (JLS 5.2) ?
     * @param   dest        the destination type
     * @return  true iff the conversion is valid
     */
    @Override
	public boolean isAssignableTo(CType dest) {
        switch (dest.getTypeID()) {
        case TID_BYTE:
            return (byte)value == value;
        case TID_SHORT:
            return true;
        case TID_CHAR:
            return (char)value == value;
        default:
            return CStdType.Short.isAssignableTo(dest);
        }
    }

    /**
     * convertType
     * changes the type of this expression to an other
     * @param  dest the destination type
     */
    @Override
	public JExpression convertType(CType dest, CExpressionContext context) {
        switch (dest.getTypeID()) {
        case TID_BYTE:
            return new JByteLiteral(getTokenReference(), (byte)value);
        case TID_SHORT:
            return this;
        case TID_CHAR:
            return new JCharLiteral(getTokenReference(), (char)value);
        case TID_INT:
            return new JIntLiteral(getTokenReference(), value);
        case TID_LONG:
            return new JLongLiteral(getTokenReference(), value);
        case TID_FLOAT:
            return new JFloatLiteral(getTokenReference(), value);
        case TID_DOUBLE:
            return new JDoubleLiteral(getTokenReference(), value);
        case TID_CLASS:
            if (dest != CStdType.String) {
                throw new InconsistencyException("cannot convert from short to " + dest);
            }
            return new JStringLiteral(getTokenReference(), "" + value);
        default:
            throw new InconsistencyException("cannot convert from short to " + dest);
        }
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Accepts the specified visitor
     * @param   p       the visitor
     */
    @Override
	public void accept(KjcVisitor p) {
        p.visitShortLiteral(value);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        return    p.visitShortLiteral(this, value);
    }

    /**
     * Accepts the specified visitor
     * @param p the visitor
     * @param o object containing extra data to be passed to visitor
     * @return object containing data generated by visitor 
     */
    @Override
    public <S,T> S accept(ExpressionVisitor<S,T> p, T o) {
        return p.visitShortLiteral(this,o);
    }


    /**
     * Generates JVM bytecode to evaluate this expression.
     *
     * @param   code        the bytecode sequence
     * @param   discardValue    discard the result of the evaluation ?
     */
    @Override
	public void genCode(CodeSequence code, boolean discardValue) {
        if (! discardValue) {
            setLineNumber(code);
            code.plantInstruction(new PushLiteralInstruction(value));
        }
    }

    /**
     * Returns whether or <pre>o</pre> this represents a literal with the same
     * value as this.
     */
    @Override
	public boolean equals(Object o) {
        return (o!=null && 
                (o instanceof JShortLiteral) &&
                ((JShortLiteral)o).value==this.value);
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private /* final */ short       value; // removed final for cloner

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.JShortLiteral other = new at.dms.kjc.JShortLiteral();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JShortLiteral other) {
        super.deepCloneInto(other);
        other.value = this.value;
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
