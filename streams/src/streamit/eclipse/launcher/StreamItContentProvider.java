/*******************************************************************************
 * StreamIt Launcher adapted from 
 * org.eclipse.ui.examples.readmetool.ReadmeSectionsView
 * @author kkuo
 *******************************************************************************/

/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package streamit.eclipse.launcher;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class StreamItContentProvider implements IStructuredContentProvider {

	public Object[] getElements(Object inputElement) {
		return null;
	}
	
	public void dispose() {
	}
	
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}
}