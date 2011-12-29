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
 * $Id: JSwitchGroup.java,v 1.10 2006-09-25 13:54:34 dimock Exp $
 */

package at.dms.kjc;

import java.util.Vector;

import at.dms.compiler.PositionedError;
import at.dms.compiler.TokenReference;

/**
 * This class represents a parameter declaration in the syntax tree
 */
public class JSwitchGroup extends JPhylum {

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    protected JSwitchGroup() {} // for cloner only

    /**
     * Construct a node in the parsing tree
     * This method is directly called by the parser
     * @param   where       the line of this node in the source code
     * @param   labels      a group of labels
     * @param   stmts       a group of statements
     */
    public JSwitchGroup(TokenReference where,
                        JSwitchLabel[] labels,
                        JStatement[] stmts)
    {
        super(where);

        this.labels = labels;
        this.stmts = stmts;
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * Returns a list of statements
     */
    public JStatement[] getStatements() {
        return stmts;
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Analyses the node (semantically).
     * @param   context     the analysis context
     * @exception   PositionedError the analysis detected an error
     */
    public void analyse(CSwitchGroupContext context) throws PositionedError {
        for (int i = 0; i < labels.length; i++) {
            labels[i].analyse(context);
        }

        context.setReachable(true);
        for (int i = 0; i < stmts.length; i++) {
            try {
                if (!context.isReachable()) {
                    throw new CLineError(stmts[i].getTokenReference(), KjcMessages.STATEMENT_UNREACHABLE);
                }
                stmts[i].analyse(context);
            } catch (CLineError e) {
                context.reportTrouble(e);
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
        p.visitSwitchGroup(this, labels, stmts);
    }

    /**
     * Accepts the specified attribute visitor
     * @param   p       the visitor
     */
    @Override
	public Object accept(AttributeVisitor p) {
        return    p.visitSwitchGroup(this, labels, stmts);
    }

    /**
     * Generates a sequence of bytescodes
     * @param   matches         a vector of values to match
     * @param   targets         a vector of target labels
     */
    public void collectLabels(CodeLabel deflab, Vector<Integer> matches, Vector targets) {
        pos = null;

        // check if one of the labels is "default:"
        for (int i = 0; pos == null && i < this.labels.length; i++) {
            if (this.labels[i].isDefault()) {
                pos = deflab;
            }
        }

        if (pos == null) {
            // no default: define a new label
            pos = new CodeLabel();

            for (int i = 0; i < this.labels.length; i++) {
                matches.addElement(this.labels[i].getLabel());
                targets.addElement(pos);
            }
        }
    }

    /**
     * Generates a sequence of bytescodes
     * @param   code        the code list
     */
    public void genCode(CodeSequence code) {
        setLineNumber(code);

        code.plantLabel(pos);
        for (int i = 0; i < stmts.length; i++) {
            stmts[i].genCode(code);
        }
    }

    // ----------------------------------------------------------------------
    // DATA MEMBERS
    // ----------------------------------------------------------------------

    private JSwitchLabel[]  labels;
    private JStatement[]        stmts;
    private CodeLabel       pos;

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.JSwitchGroup other = new at.dms.kjc.JSwitchGroup();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.JSwitchGroup other) {
        super.deepCloneInto(other);
        other.labels = (at.dms.kjc.JSwitchLabel[])at.dms.kjc.AutoCloner.cloneToplevel(this.labels);
        other.stmts = (at.dms.kjc.JStatement[])at.dms.kjc.AutoCloner.cloneToplevel(this.stmts);
        other.pos = (at.dms.kjc.CodeLabel)at.dms.kjc.AutoCloner.cloneToplevel(this.pos);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
