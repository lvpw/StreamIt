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
 * $Id: JArrayInitializer.java,v 1.17 2006-12-20 18:03:32 dimock Exp $
 */

package at.dms.kjc;

import at.dms.classfile.PushLiteralInstruction;
import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;

/**
 * This class implements an array of expressions and array
 * initializers used to initialize arrays.
 */
public class JArrayInitializer extends JExpression {

    /**
	 * 
	 */
	private static final long serialVersionUID = 4166662208884128372L;

	protected JArrayInitializer() {} // for cloner only

    /**
     * Construct a node in the parsing tree
     * This method is directly called by the parser
     * @param   where       the line of this node in the source code
     * @param   elems       the elements of the initializer
     */
    public JArrayInitializer(TokenReference where, JExpression[] elems) {
        super(where);

        this.elems = elems;
    }

    public JArrayInitializer(JExpression[] elems) {
        this(null, elems);
    }

    public JExpression[] getElems() 
    {
        return elems;
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
     * Must be called with a CArrayType.
     */
    @Override
	public void setType(CType type) {
        assert type instanceof CArrayType;
        this.type = (CArrayType)type;
    }
    
    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Sets the type of this expression. Assume type is already checked.
     * @param   type        the type of this array
     */
    public void setType(CArrayType type) {
        assert type.checked();
        this.type = type;
    }

    /**
     * Analyses the expression (semantically).
     * @param   context     the analysis context
     * @return  an equivalent, analysed expression
     * @exception   PositionedError the analysis detected an error
     */
    @Override
	public JExpression analyse(CExpressionContext context) throws PositionedError {
        assert type != null;

        CType   elementType = type.getElementType();

        if (elementType.isArrayType()) {
            for (int i = 0; i < elems.length; i++) {
                if (elems[i] instanceof JArrayInitializer) {
                    ((JArrayInitializer)elems[i]).setType((CArrayType)elementType);
                }
                elems[i] = elems[i].analyse(context);
                check(context, elems[i].isAssignableTo(elementType),
                      KjcMessages.ARRAY_INIT_BADTYPE, elementType, elems[i].getType());
            }
        } else {
            for (int i = 0; i < elems.length; i++) {
                check(context,
                      !(elems[i] instanceof JArrayInitializer),
                      KjcMessages.ARRAY_INIT_NOARRAY, elementType);
                elems[i] = elems[i].analyse(context);
                check(context, elems[i].isAssignableTo(elementType),
                      KjcMessages.ARRAY_INIT_BADTYPE, elementType, elems[i].getType());
                elems[i] = elems[i].convertType(elementType, context);
            }
        }

        return this;
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
        p.visitArrayInitializer(this, elems);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        return p.visitArrayInitializer(this, elems);
    }

    /**
     * Accepts the specified visitor
     * @param p the visitor
     * @param o object containing extra data to be passed to visitor
     * @return object containing data generated by visitor 
     */
    @Override
    public <S,T> S accept(ExpressionVisitor<S,T> p, T o) {
        return p.visitArrayInitializer(this,o);
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

        // create array instance
        code.plantInstruction(new PushLiteralInstruction(elems.length));
        code.plantNewArrayInstruction(type.getElementType());

        // initialize array
        int     opcode = type.getElementType().getArrayStoreOpcode();

        for (int i = 0; i < elems.length; i++) {
            code.plantNoArgInstruction(opc_dup);
            code.plantInstruction(new PushLiteralInstruction(i));
            elems[i].genCode(code, false);
            code.plantNoArgInstruction(opcode);
        }

        if (discardValue) {
            code.plantPopInstruction(getType());
        }
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private CArrayType      type;
    private JExpression[]       elems;

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.JArrayInitializer other = new at.dms.kjc.JArrayInitializer();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JArrayInitializer other) {
        super.deepCloneInto(other);
        other.type = (at.dms.kjc.CArrayType)at.dms.kjc.AutoCloner.cloneToplevel(this.type);
        other.elems = (at.dms.kjc.JExpression[])at.dms.kjc.AutoCloner.cloneToplevel(this.elems);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
