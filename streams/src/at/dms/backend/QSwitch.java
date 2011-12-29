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
 * $Id: QSwitch.java,v 1.2 2006-01-25 17:00:34 thies Exp $
 */

package at.dms.backend;

import at.dms.classfile.SwitchInstruction;

/**
 * This class represent an abstract node
 */
abstract class QSwitch extends QVoid {

    public QSwitch(QOrigin origin) {
        super(origin);
    }

    // ----------------------------------------------------------------------
    // ACCESSORS
    // ----------------------------------------------------------------------

    /**
     * isJump
     */
    @Override
	public boolean isSwitch() {
        return true;
    }

    /**
     * Returns this node a a jump
     */
    @Override
	public QSwitch getSwitch() {
        return this;
    }

    /**
     * Returns the targets
     */
    public BasicBlock[] getTargets() {
        SwitchInstruction   insn = (SwitchInstruction)getInstruction().getInstruction();

        // We have to take care of a good order
        BasicBlock[]    bblocks = new BasicBlock[insn.getSwitchCount() + 1];
        for (int i = -1; i < bblocks.length - 1; i++) {
            bblocks[i + 1] = (BasicBlock)insn.getTarget(i);
        }

        return bblocks;
    }
}
