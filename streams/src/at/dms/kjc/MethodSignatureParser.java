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
 * $Id: MethodSignatureParser.java,v 1.1 2001-08-30 16:32:53 thies Exp $
 */

package at.dms.kjc;

import java.util.Vector;

import at.dms.util.InconsistencyException;
import at.dms.util.Utils;

public class MethodSignatureParser {

  /**
   * Parses a VM-standard type signature.
   *
   * @param	signature	the type signature
   * @param	from		the start index
   * @param	to		the end index
   * @return	the type represented by the signature
   */
  public final CType parseSignature(String signature) {
    return parseSignature(signature, 0, signature.length());
  }

  /**
   * Parses a VM-standard type signature within a signature string.
   *
   * @param	signature	the type signature
   * @param	from		the start index
   * @param	to		the end index
   * @return	the type represented by the signature
   */
  public CType parseSignature(String signature, int from, int to) {
    CType	type;
    int		bounds;

    bounds = 0;
    for (; signature.charAt(from) == '['; from++) {
      bounds += 1;
    }

    switch (signature.charAt(from)) {
    case 'V':
      type = CStdType.Void;
      break;
    case 'B':
      type = CStdType.Byte;
      break;
    case 'C':
      type = CStdType.Char;
      break;
    case 'D':
      type = CStdType.Double;
      break;
    case 'F':
      type = CStdType.Float;
      break;
    case 'I':
      type = CStdType.Integer;
      break;
    case 'J':
      type = CStdType.Long;
      break;
    case 'L':
      type = CClassType.lookup(signature.substring(from + 1, to - 1));
      break;
    case 'S':
      type = CStdType.Short;
      break;
    case 'Z':
      type = CStdType.Boolean;
      break;
    default:
      throw new InconsistencyException("Unknown signature: " + signature.charAt(from));
    }

    return bounds > 0 ? new CArrayType(type, bounds) : type;
  }

  /**
   * Returns an array of types represented by the type signature
   * For methods, the return type is the last element of the array
   */
  public synchronized CType[] parseMethodSignature(String signature) {
    // assert(sig.charAt(0) == '(');

    Vector	container = new Vector();
    char[]	sig = signature.toCharArray();
    int		current = 1;

    while (sig[current] != ')') {
      int	end = current;

      while (sig[end] == '[') {
	end += 1;
      }
      if (sig[end] != 'L') {
	end += 1;
      } else {
	while (sig[end] != ';') {
	  end += 1;
	}
	end += 1;
      }
      container.addElement(parseSignature(signature, current, end));
      current = end;
    }
    container.addElement(parseSignature(signature, current + 1, signature.length()));

    return (CType[])Utils.toArray(container, CType.class);
  }
}
