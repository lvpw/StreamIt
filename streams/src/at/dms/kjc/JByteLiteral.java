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
 * $Id: JByteLiteral.java,v 1.12 2006-09-27 23:40:34 dimock Exp $
 */

package at.dms.kjc;

import at.dms.classfile.PushLiteralInstruction;
import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;
import at.dms.util.InconsistencyException;

/**
 * JLS 3.10.1 Integer Literals. This class represents byte literals.
 * Instances of this class are only created by conversion operations.
 */
public class JByteLiteral extends JLiteral {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    protected JByteLiteral() {} // for cloner only

    /**
     * Constructs a literal expression from a constant value.
     * @param   where       the line of this node in the source code
     * @param   value       the constant value
     */
    public JByteLiteral(TokenReference where, byte value) {
        super(where);
        this.value = value;
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns the type of this expression.
     */
    public CType getType() {
        return CStdType.Byte;
    }

    /**
     * Returns the constant value of the expression.
     */
    public byte byteValue() {
        return value;
    }

    /**
     * Returns true iff the value of this literal is the
     * default value for this type (JLS 4.5.5).
     */
    public boolean isDefault() {
        return value == 0;
    }

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
    public boolean isAssignableTo(CType dest) {
        switch (dest.getTypeID()) {
        case TID_BYTE:
            return true;
        case TID_SHORT:
            return true;
        case TID_CHAR:
            return (char)value == value;
        default:
            return CStdType.Byte.isAssignableTo(dest);
        }
    }

    /**
     * convertType
     * changes the type of this expression to an other
     * @param  dest the destination type
     */
    public JExpression convertType(CType dest, CExpressionContext context) {
        switch (dest.getTypeID()) {
        case TID_BYTE:
            return this;
        case TID_SHORT:
            return new JShortLiteral(getTokenReference(), (short)value);
        case TID_CHAR:
            return new JCharLiteral(getTokenReference(), (char)value);
        case TID_INT:
            return new JIntLiteral(getTokenReference(), (int)value);
        case TID_LONG:
            return new JLongLiteral(getTokenReference(), (long)value);
        case TID_FLOAT:
            return new JFloatLiteral(getTokenReference(), (float)value);
        case TID_DOUBLE:
            return new JDoubleLiteral(getTokenReference(), (double)value);
        case TID_CLASS:
            if (dest != CStdType.String) {
                throw new InconsistencyException("cannot convert from byte to " + dest);
            }
            return new JStringLiteral(getTokenReference(), "" + value);
        default:
            throw new InconsistencyException("cannot convert from byte to " + dest);
        }
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Accepts the specified visitor
     * @param   p       the visitor
     */
    public void accept(KjcVisitor p) {
        p.visitByteLiteral(value);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    public Object accept(AttributeVisitor p) {
        return    p.visitByteLiteral(this, value);
    }

    /**
     * Accepts the specified visitor
     * @param p the visitor
     * @param o object containing extra data to be passed to visitor
     * @return object containing data generated by visitor 
     */
    public Object accept(ExpressionVisitor p, Object o) {
        return p.visitByteLiteral(this,o);
    }


    /**
     * Returns whether or <pre>o</pre> this represents a literal with the same
     * value as this.
     */
    public boolean equals(Object o) {
        return (o!=null && 
                (o instanceof JByteLiteral) &&
                ((JByteLiteral)o).value==this.value);
    }

    /**
     * Generates JVM bytecode to evaluate this expression.
     *
     * @param   code        the bytecode sequence
     * @param   discardValue    discard the result of the evaluation ?
     */
    public void genCode(CodeSequence code, boolean discardValue) {
        if (! discardValue) {
            setLineNumber(code);
            code.plantInstruction(new PushLiteralInstruction(value));
        }
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private /* final */ byte        value; // removed final for cloner

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.kjc.JByteLiteral other = new at.dms.kjc.JByteLiteral();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JByteLiteral other) {
        super.deepCloneInto(other);
        other.value = this.value;
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
