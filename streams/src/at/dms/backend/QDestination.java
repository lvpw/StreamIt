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
 * $Id: QDestination.java,v 1.2 2006-01-25 17:00:34 thies Exp $
 */

package at.dms.backend;

/**
 * This class represents the destination of a quadruple
 */
interface QDestination {

    // ----------------------------------------------------------------------
    // ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Returns the defined temporary.
     */
    QTemporary getDef();

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Generates instructions for destination
     * @param   seq     The code sequence of instruction
     * @param   isLive      Is the destination live after this store ?
     */
    void store(CodeSequence seq, boolean isLive);
}
