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
 * $Id: CParseClassContext.java,v 1.1 2001-08-30 16:32:50 thies Exp $
 */

package at.dms.kjc;

import java.util.Vector;
import java.util.Stack;

import at.dms.util.Utils;

public class CParseClassContext {
  public static CParseClassContext getInstance() {
    return stack.size() == 0 ?
      new CParseClassContext() :
      (CParseClassContext)stack.pop();
  }

  public void release() {
    release(this);
  }

  public static void release(CParseClassContext context) {
    context.clear();
    stack.push(context);
  }

  /**
   * All creations done through getInstance().
   */
  private CParseClassContext() {
  }

  private void clear() {
    fields.setSize(0);
    methods.setSize(0);
    inners.setSize(0);
    body.setSize(0);
  }

  // ----------------------------------------------------------------------
  // ACCESSORS (ADD)
  // ----------------------------------------------------------------------

  public void addFieldDeclaration(JFieldDeclaration decl) {
    fields.addElement(decl);
    body.addElement(decl);
  }

  public void addMethodDeclaration(JMethodDeclaration decl) {
    methods.addElement(decl);
  }

  public void addInnerDeclaration(JTypeDeclaration decl) {
    inners.addElement(decl);
  }

  public void addBlockInitializer(JClassBlock block) {
    body.addElement(block);
  }

  // ----------------------------------------------------------------------
  // ACCESSORS (GET)
  // ----------------------------------------------------------------------

  public JFieldDeclaration[] getFields() {
    return (JFieldDeclaration[])Utils.toArray(fields, JFieldDeclaration.class);
  }

  public JMethodDeclaration[] getMethods() {
    return (JMethodDeclaration[])Utils.toArray(methods, JMethodDeclaration.class);
  }

  public JTypeDeclaration[] getInnerClasses() {
    return (JTypeDeclaration[])Utils.toArray(inners, JTypeDeclaration.class);
  }

  public JPhylum[] getBody() {
    return (JPhylum[])Utils.toArray(body, JPhylum.class);
  }

  // ----------------------------------------------------------------------
  // DATA MEMBERS
  // ----------------------------------------------------------------------

  private Vector fields = new Vector();
  private Vector methods = new Vector();
  private Vector inners = new Vector();
  private Vector body = new Vector();

  private static Stack stack = new Stack();
}
