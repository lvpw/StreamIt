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
 * $Id: JVariableDeclarationStatement.java,v 1.12 2006-03-24 15:54:48 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.JavaStyleComment;
import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;
import at.dms.compiler.UnpositionedError;

/**
 * JLS 14.4: Local Variable Declaration Statement
 *
 * A local variable declaration statement declares one or more local variable names.
 */
public class JVariableDeclarationStatement extends JStatement {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
	 * 
	 */
	private static final long serialVersionUID = -2167390334975453564L;

	protected JVariableDeclarationStatement() {} // for cloner only

    /**
     * Construct a node in the parsing tree
     * @param   where       the line of this node in the source code
     * @param   vars        the variables declared by this statement
     */
    public JVariableDeclarationStatement(TokenReference where, JVariableDefinition[] vars, JavaStyleComment[] comments) {
        super(where, comments);

        this.vars = vars;
    }

    public JVariableDeclarationStatement(JVariableDefinition[] vars) {
        this(null, vars, null);
    }

    /**
     * Construct a node in the parsing tree
     * @param   where       the line of this node in the source code
     * @param   var     the variable declared by this statement
     */
    public JVariableDeclarationStatement(TokenReference where, JVariableDefinition var, JavaStyleComment[] comments) {
        super(where, comments);

        this.vars = new JVariableDefinition[] {var};
    }

    public JVariableDeclarationStatement(JVariableDefinition var) {
        this(null, var, null);
    }

    /**
     * Returns an array of variable definition declared by this statement
     */
    public JVariableDefinition[] getVars() {
        return vars;
    }

    /**
     * Sets vars
     */
    public void setVars(JVariableDefinition[] vars) {
        this.vars=vars;
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Sets the variables to be for variables
     */
    public void setIsInFor() {
        for (int i = 0; i < this.vars.length; i++) {
            vars[i].setIsLoopVariable();
        }
    }

    /**
     * Unsets the variables to be for variables
     */
    public void unsetIsInFor() {
        for (int i = 0; i < this.vars.length; i++) {
            vars[i].unsetIsLoopVariable();
        }
    }

    /**
     * Analyses the statement (semantically).
     * @param   context     the analysis context
     * @exception   PositionedError the analysis detected an error
     */
    @Override
	public void analyse(CBodyContext context) throws PositionedError {
        for (int i = 0; i < this.vars.length; i++) {
            try {
                context.getBlockContext().addVariable(vars[i]);
                vars[i].analyse(context);

                if (vars[i].hasInitializer()) {
                    context.setVariableInfo(vars[i].getIndex(), CVariableInfo.INITIALIZED);
                }
            } catch (UnpositionedError e) {
                throw new CLineError(getTokenReference(), e.getFormattedMessage());
            }
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
        super.accept(p);
        p.visitVariableDeclarationStatement(this, vars);
    }
    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        return p.visitVariableDeclarationStatement(this, vars);
    }
      

    /**
     * Generates a sequence of bytescodes
     * @param   code        the code list
     */
    @Override
	public void genCode(CodeSequence code) {
        setLineNumber(code);

        for (int i = 0; i < this.vars.length; i++) {
            if (vars[i].getValue() != null) {
                vars[i].getValue().genCode(code, false);
                vars[i].genStore(code);
            }
        }
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private JVariableDefinition[]       vars;

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.JVariableDeclarationStatement other = new at.dms.kjc.JVariableDeclarationStatement();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JVariableDeclarationStatement other) {
        super.deepCloneInto(other);
        other.vars = (at.dms.kjc.JVariableDefinition[])at.dms.kjc.AutoCloner.cloneToplevel(this.vars);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
