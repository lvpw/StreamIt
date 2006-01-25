/*
 * @(#)Edge.java    1.0 1/1/02
 *
 * Copyright (C) 2001 Gaudenz Alder
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

package org.jgraph.graph;

import java.io.Serializable;

/**
 * Defines the requirements for an object that
 * represents an Edge in a GraphModel.
 *
 * @version 1.0 1/1/02
 * @author Gaudenz Alder
 */

public interface Edge extends GraphCell {

    /**
     * Returns the source of the edge.
     */
    Object getSource();

    /**
     * Returns the target of the edge.
     */
    Object getTarget();

    /**
     * Sets the source of the edge.
     */
    void setSource(Object port);

    /**
     * Returns the target of <code>edge</code>.
     */
    void setTarget(Object port);


    //
    // Routing
    //

    public static interface Routing extends Serializable {

        public void route(EdgeView edge, java.util.List points);

    }


}
