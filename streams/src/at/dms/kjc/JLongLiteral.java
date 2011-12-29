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
 * $Id: JLongLiteral.java,v 1.13 2006-10-27 20:48:54 dimock Exp $
 */

package at.dms.kjc;

import at.dms.classfile.PushLiteralInstruction;
import at.dms.compiler.NumberParser;
import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;
import at.dms.util.InconsistencyException;

/**
 * JLS 3.10.1 Long Literals. This class represents long literals.
 */
public class JLongLiteral extends JLiteral {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
	 * 
	 */
	private static final long serialVersionUID = -5526935835378426597L;

	protected JLongLiteral() {} // for cloner only

    /**
     * Constructs a literal expression from a textual representation.
     * @param   where       the line of this node in the source code
     * @param   image       the textual representation of this literal
     */
    public JLongLiteral(TokenReference where, String image)
        throws PositionedError
    {
        super(where);
        if (image.startsWith("0")) {
            // octal or hexadecimal
            try {
                this.value = NumberParser.decodeLong(image);
            } catch (NumberFormatException e) {
                throw new PositionedError(where, KjcMessages.INVALID_LONG_LITERAL, image);
            }
            this.invert = false;
        } else {
            // decimal
            long    value;

            try {
                value = NumberParser.decodeLong("-" + image);
            } catch (NumberFormatException e) {
                throw new PositionedError(where, KjcMessages.INVALID_LONG_LITERAL, image);
            }
            if (value == Long.MIN_VALUE) {
                this.value = value;
                this.invert = true;
            } else {
                this.value = -value;
                this.invert = false;
            }
        }
    }

    /**
     * Constructs a literal expression from a constant value.
     * @param   where       the line of this node in the source code
     * @param   value       the constant value
     */
    public JLongLiteral(TokenReference where, long value) {
        super(where);
        this.value = value;
        this.invert = false;
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns a literal with the sign inverted.
     * This is needed to handle 9223372036854775808L which cannot be stored
     * in a variable of type long.
     *
     * JLS 3.10.1 :
     * The largest decimal literal of type long is 9223372036854775808L (2^63).
     * All decimal literals from 0L to 9223372036854775807L may appear anywhere
     * a long literal may appear, but the literal 9223372036854775808L may
     * appear only as the operand of the unary negation operator -.
     */
    public JLongLiteral getOppositeLiteral() throws PositionedError {
        return new JLongLiteral(getTokenReference(), invert ? Long.MIN_VALUE : -value);
    }

    /**
     * Returns the type of this expression.
     */
    @Override
	public CType getType() {
        return CStdType.Long;
    }

    /**
     * Returns the constant value of the expression.
     */
    @Override
	public long longValue() {
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

    /**
     * Returns a string representation of this literal.
     */
    @Override
	public String toString() {
        StringBuffer    buffer = new StringBuffer();

        buffer.append("JLongLiteral[");
        if (invert) {
            buffer.append("9223372036854775808L (= 2^63)");
        } else {
            buffer.append(value);
        }
        buffer.append("]");
        return buffer.toString();
    }

    @Override
	public String convertToString() {
        return ""+value;
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Analyses the expression (semantically).
     * @param   context     the analysis context
     * @return  an equivalent, analysed expression
     * @exception   PositionedError the analysis detected an error
     */
    @Override
	public JExpression analyse(CExpressionContext context) throws PositionedError {
        check(context, !this.invert, KjcMessages.INVALID_LONG_LITERAL, "9223372036854775808L (= 2^63)");
        return this;
    }

    /**
     * convertType
     * changes the type of this expression to an other
     * @param  dest the destination type
     */
    @Override
	public JExpression convertType(CType dest, CExpressionContext context) {
        if (this.invert) {
            throw new InconsistencyException();
        }

        switch (dest.getTypeID()) {
        case TID_BYTE:
            return new JByteLiteral(getTokenReference(), (byte)value);
        case TID_SHORT:
            return new JShortLiteral(getTokenReference(), (short)value);
        case TID_CHAR:
            return new JCharLiteral(getTokenReference(), (char)value);
        case TID_INT:
            return new JIntLiteral(getTokenReference(), (int)value);
        case TID_LONG:
            return this;
        case TID_FLOAT:
            return new JFloatLiteral(getTokenReference(), value);
        case TID_DOUBLE:
            return new JDoubleLiteral(getTokenReference(), value);
        case TID_CLASS:
            if (dest != CStdType.String) {
                throw new InconsistencyException("cannot convert from long to " + dest);
            }
            return new JStringLiteral(getTokenReference(), "" + value);
        default:
            throw new InconsistencyException("cannot convert from long to " + dest);
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
        p.visitLongLiteral(value);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        return    p.visitLongLiteral(this, value);
    }

    /**
     * Accepts the specified visitor
     * @param p the visitor
     * @param o object containing extra data to be passed to visitor
     * @return object containing data generated by visitor 
     */
    @Override
    public <S,T> S accept(ExpressionVisitor<S,T> p, T o) {
        return p.visitLongLiteral(this,o);
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
                (o instanceof JLongLiteral) &&
                ((JLongLiteral)o).value==this.value);
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    // value = MAX_VALUE + 1, valid only as argument to unary minus
    private /* final */ boolean     invert; // removed final for cloner
    private /* final */ long        value;  // removed final for cloner

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.JLongLiteral other = new at.dms.kjc.JLongLiteral();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JLongLiteral other) {
        super.deepCloneInto(other);
        other.invert = this.invert;
        other.value = this.value;
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
