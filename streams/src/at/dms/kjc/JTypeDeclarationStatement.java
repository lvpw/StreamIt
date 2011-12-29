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
 * $Id: JTypeDeclarationStatement.java,v 1.10 2006-03-24 15:54:48 dimock Exp $
 */

package at.dms.kjc;

import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;
import at.dms.compiler.UnpositionedError;

/**
 * JLS 14.3: Local Class Declaration
 *
 * A local type declaration declaration statement declares one type declaration in a body of a method.
 */
public class JTypeDeclarationStatement extends JStatement {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
	 * 
	 */
	private static final long serialVersionUID = 8578197852149471373L;

	protected JTypeDeclarationStatement() {} // for cloner only

    /**
     * Construct a node in the parsing tree
     * @param   where       the line of this node in the source code
     * @param   decl        the type declaration
     */
    public JTypeDeclarationStatement(TokenReference where, JTypeDeclaration decl) {
        super(where, null);
        this.decl = decl;
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Analyses the statement (semantically).
     * @param   context     the analysis context
     * @exception   PositionedError the analysis detected an error
     */
    @Override
	public void analyse(CBodyContext context) throws PositionedError {
        CClass  owner = context.getClassContext().getCClass();
        String  prefix = owner.getQualifiedName() + "$" + context.getClassContext().getNextSyntheticIndex();

        decl.generateInterface(owner, prefix);

        try {
            context.getBlockContext().addClass(decl.getCClass());
        } catch (UnpositionedError cue) {
            throw cue.addPosition(getTokenReference());
        }

        decl.checkInterface(context);
        decl.checkInitializers(context);
        decl.checkTypeBody(context);
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
        p.visitTypeDeclarationStatement(this, decl);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        return p.visitTypeDeclarationStatement(this, decl);
    }

    /**
     * Generates a sequence of bytescodes
     * @param   code        the code list
     */
    @Override
	public void genCode(CodeSequence code) {
        // nothing to do here
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private JTypeDeclaration        decl;

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.JTypeDeclarationStatement other = new at.dms.kjc.JTypeDeclarationStatement();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JTypeDeclarationStatement other) {
        super.deepCloneInto(other);
        other.decl = (at.dms.kjc.JTypeDeclaration)at.dms.kjc.AutoCloner.cloneToplevel(this.decl);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
