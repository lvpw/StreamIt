/*
 * Created on Feb 4, 2004
 */
package streamit.eclipse.grapheditor.editor.pad.actions;

import java.awt.event.ActionEvent;

import streamit.eclipse.grapheditor.editor.GPGraphpad;
import streamit.eclipse.grapheditor.editor.pad.GPDocument;
import streamit.eclipse.grapheditor.editor.utils.Utilities;
import streamit.eclipse.grapheditor.graph.GraphStructure;
import streamit.eclipse.grapheditor.graph.utils.JGraphLayoutManager;

/**
 * Action that performs the layou algorithm on the graph. 
 * @author jcarlos
 */
public class GraphDoLayout extends AbstractActionDefault {

    /**
     * Constructor for GraphDoLayout.
     * @param graphpad
     */
    public GraphDoLayout(GPGraphpad graphpad) {
        super(graphpad);
    }

    /**
     * Performs the layout algorithm on the graph. 
     */
    public void actionPerformed(ActionEvent e) 
    {
        GPDocument doc = graphpad.getCurrentDocument();
        GraphStructure graphStruct = doc.getGraphStructure();
        
        /** If the containers are visible, we must hide them in order to do layout */
        if (!(doc.areContainersInvisible()))
            {
                for (int i = graphStruct.containerNodes.getCurrentLevelView(); i >= 0; i--)
                    {
                        graphStruct.containerNodes.hideContainersAtLevel(i);
                    }
            }
        
        /** Perform the layout algorithm */
        JGraphLayoutManager manager = new JGraphLayoutManager(graphStruct);
        manager.arrange();
        
        /** Make the containers visible again after the layout algorithm */
        if (!(doc.areContainersInvisible()))
            {
                ViewSetContainerLocation ac = (ViewSetContainerLocation) graphpad.getCurrentActionMap().
                    get(Utilities.getClassNameWithoutPackage(ViewSetContainerLocation.class));
                ac.actionPerformed(null);
            }
    }

}
