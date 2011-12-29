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
 * $Id: JMinusExpression.java,v 1.10 2007-11-15 04:02:37 rabbah Exp $
 */

package at.dms.kjc;

import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;
import at.dms.compiler.UnpositionedError;

/**
 * This class implements '-' specific operations
 * Plus operand may be String, numbers
 */
public class JMinusExpression extends JBinaryArithmeticExpression {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
	 * 
	 */
	private static final long serialVersionUID = -7987002055181791015L;

	protected JMinusExpression() {} // for cloner only
    /**
     * Construct a node in the parsing tree
     * This method is directly called by the parser
     * @param   where       the line of this node in the source code
     * @param   left        the left operand
     * @param   right       the right operand
     */
    public JMinusExpression(TokenReference where,
                            JExpression left,
                            JExpression right) {
        super(where, left, right);
    }

    @Override
	public String toString() {
        return "JMinusExpression["+left+","+right+"]";
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
        left = left.analyse(context);
        right = right.analyse(context);

        try {
            type = computeType(left.getType(), right.getType());
        } catch (UnpositionedError e) {
            throw e.addPosition(getTokenReference());
        }

        left = left.convertType(type, context);
        right = right.convertType(type, context);

        if (left.isConstant() && right.isConstant()) {
            return constantFolding();
        } else {
            return this;
        }
    }

    /**
     * compute the type of this expression according to operands
     * @param   leftType        the type of left operand
     * @param   rightType       the type of right operand
     * @return  the type computed for this binary operation
     * @exception   UnpositionedError   this error will be positioned soon
     */
    public static CType computeType(CType   leftType, CType rightType) throws UnpositionedError {
        if (leftType.isNumeric() && rightType.isNumeric()) {
            return CNumericType.binaryPromote(leftType, rightType);
        }
        throw new UnpositionedError(KjcMessages.MINUS_BADTYPE, leftType, rightType);
    }

    @Override
	public CType getType() {
        try {
            return computeType(left.getType(),right.getType());
        } catch(UnpositionedError e) {
            return super.getType();
        }
    }
    
    // ----------------------------------------------------------------------
    // CONSTANT FOLDING
    // ----------------------------------------------------------------------

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    @Override
	public int compute(int left, int right) {
        return left - right;
    }

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    @Override
	public long compute(long left, long right) {
        return left - right;
    }

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    @Override
	public float compute(float left, float right) {
        return left - right;
    }

    /**
     * Computes the result of the operation at compile-time (JLS 15.28).
     * @param   left        the first operand
     * @param   right       the seconds operand
     * @return  the result of the operation
     */
    @Override
	public double compute(double left, double right) {
        return left - right;
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
        p.visitBinaryExpression(this, "-", left, right);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        return    p.visitBinaryExpression(this, "-", left, right);
    }

    /**
     * Accepts the specified visitor
     * @param p the visitor
     * @param o object containing extra data to be passed to visitor
     * @return object containing data generated by visitor 
     */
    @Override
    public <S,T> S accept(ExpressionVisitor<S,T> p, T o) {
        return p.visitMinus(this,o);
    }


    /**
     * @param   type        the type of result
     * @return  the type of opcode for this operation
     */
    public static int getOpcode(CType type) {
        switch (type.getTypeID()) {
        case TID_FLOAT:
            return opc_fsub;
        case TID_LONG:
            return opc_lsub;
        case TID_DOUBLE:
            return opc_dsub;
        default:
            return opc_isub;
        }
    }

    /**
     * Generates JVM bytecode to evaluate this expression.
     *
     * @param   code        the bytecode sequence
     * @param   discardValue    discard the result of the evaluation ?
     */
    @Override
	public void genCode(CodeSequence code, boolean discardValue) {
        setLineNumber(code);

        left.genCode(code, false);
        right.genCode(code, false);
        code.plantNoArgInstruction(getOpcode(getType()));

        if (discardValue) {
            code.plantPopInstruction(getType());
        }
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.JMinusExpression other = new at.dms.kjc.JMinusExpression();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JMinusExpression other) {
        super.deepCloneInto(other);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
