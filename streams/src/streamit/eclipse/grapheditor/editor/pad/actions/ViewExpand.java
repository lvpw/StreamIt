/*
 * Created on Dec 5, 2003
 *
 */
package streamit.eclipse.grapheditor.editor.pad.actions;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.Hashtable;
import java.util.Map;

import org.jgraph.graph.CellView;
import org.jgraph.graph.GraphConstants;
import org.jgraph.graph.GraphLayoutCache;

import streamit.eclipse.grapheditor.editor.GPGraphpad;
import streamit.eclipse.grapheditor.editor.utils.Utilities;
import streamit.eclipse.grapheditor.graph.GEContainer;
import streamit.eclipse.grapheditor.graph.GEStreamNode;
import streamit.eclipse.grapheditor.graph.GEType;
import streamit.eclipse.grapheditor.graph.GraphStructure;
import streamit.eclipse.grapheditor.graph.utils.JGraphLayoutManager;

/**
 * Action to expand a GEStreamNode. 
 * @author jcarlos
 */
public class ViewExpand extends AbstractActionDefault {

    /**
     * Constructor for ViewScaleZoomOut.
     * @param graphpad
     * @param name
     */
    public ViewExpand(GPGraphpad graphpad) {
        super(graphpad);
    }

    /**
     * Expand the GEContainer nodes at the current level.
     */
    public void actionPerformed(ActionEvent e) 
    {
        graphpad.getCurrentDocument().setResizeAction(null);
        GraphStructure graphStruct = graphpad.getCurrentDocument().getGraphStructure();
        
        /** Expand the containers at the current level */
        int currentLevelView = graphStruct.containerNodes.getCurrentLevelView();
        
        graphStruct.containerNodes.expandContainersAtLevel(currentLevelView);
        JGraphLayoutManager manager = new JGraphLayoutManager(graphStruct);
        manager.arrange();
        
        /** Set the container locations (layout the containers) now that they
         * have been expanded in the currentLevel view */
        ViewSetContainerLocation ac = (ViewSetContainerLocation) graphpad.getCurrentActionMap().
            get(Utilities.getClassNameWithoutPackage(ViewSetContainerLocation.class));
        ac.actionPerformed(null);
        
        /** Since we have expanded, we need to update the current level */
        graphStruct.containerNodes.setCurrentLevelView(++currentLevelView);
        
        /** Scale the view in order to zoom out to better view the expanded graph */
        graphpad.getCurrentDocument().setScale(graphpad.getCurrentGraph().getScale() / 1.2);
        if (graphpad.getCurrentGraph().getSelectionCell() != null)
            {
                graphpad.getCurrentGraph().scrollCellToVisible(getCurrentGraph().getSelectionCell());
            }
    
        /** Layout the graph so that the graph is centered */
        //      centerLayout();
    }   

    /**
     * The cells that are going to be centered are only the ones that are included inside of the 
     * toplevel node. The Sugiyama layout algorithm places the nodes that are not connected to 
     * the graph outside of the toplevel.
     */
    // TODO: Fix centerLayout so that splitjoin inner childs don't get centered and so that the 
    // feedbackloop gets formatted in a special manner. 
    public void centerLayout()
    { 
        Map attributes = graphpad.getCurrentDocument().getGraphStructure().getTopLevel().getAttributes();
        boolean doCenterLayout = true;
    
        //      Object[] cells = graphpad.getCurrentDocument().getGraphStructure().allNodesInGraph().toArray();
        Object[] cells = graphpad.getCurrentDocument().getGraphStructure().getJGraph().getRoots(GraphConstants.getBounds(attributes));
        
        GraphLayoutCache gv = getCurrentGraphLayoutCache();
        if (cells != null) 
            {
                //Rectangle r = getCurrentGraph().getCellBounds(Acells);
                Rectangle r = getCurrentGraph().getCellBounds(cells);
                int cx = r.width / 2;
                Map viewMap = new Hashtable();
            
                for (int i = 0; i < cells.length; i++) 
                    {
                        int yOffset = 0;
                        if (cells[i] instanceof GEStreamNode)
                            {   
                                GEStreamNode strNode = (GEStreamNode) cells[i];
                        
                                /** Move the GEStreamNode inside a container away from the top border if
                                 * it is not a container. **/
                                if ( ! (strNode instanceof GEContainer)) 
                                    {
                                        yOffset = 40;
                                    }
                        
                                GEStreamNode parentNode = strNode.getEncapsulatingNode();
                                if(parentNode != null)
                                    {
                                        /** Do not center the nodes of a splitjoin (excluding the splitter and joiner)**/
                                        if ((parentNode.getType() == GEType.SPLIT_JOIN) &&
                                            (!(strNode.getType() == GEType.SPLITTER)) &&
                                            (!(strNode.getType() == GEType.JOINER)))                
                                            {
                                                doCenterLayout = false; 
                                
                                            }   
                            
                                        /** If  the immediate parent is a feedbackloop, then */
                            
                            
                                                
                                    }           
                                if (doCenterLayout)
                                    {
                                        CellView view = gv.getMapping(strNode, false); //causes exception when there is no view
                                        //CellView view = gv.getMapping(strNode, true);
                                        Map map = GraphConstants.cloneMap(view.getAllAttributes());
                                        Rectangle bounds = GraphConstants.getBounds(map);
                                        if (bounds != null) 
                                            {
                                                bounds.setLocation(r.x + cx - bounds.width / 2,
                                                                   bounds.y + yOffset);
                                                viewMap.put(strNode, map);
                                            }
                                        //doCenterLayout = true;
                                    }
                                doCenterLayout = true;
                            }
                    }
                gv.edit(viewMap, null, null, null);
                
                
                //      graphpad.getCurrentDocument().getGraphStructure().setLocationContainersAtLevel(2);
                /*
                  Iterator containerIter = graphpad.getCurrentDocument().getGraphStructure().getAllContainers().iterator();
                  while(containerIter.hasNext())
                  {
                  GEStreamNode node = (GEStreamNode) containerIter.next();
                  if (node.getType() == GEType.SPLIT_JOIN)
                  {
                  GESplitJoin splitjoin = (GESplitJoin) node;
                  Rectangle sjRect = GraphConstants.getBounds(splitjoin.getAttributes());
                  ArrayList children = splitjoin.getSuccesors();
                  int numberOfChildren = children.size();
                  System.out.println("the bounds are " + sjRect);       
                  for (int i = 0; i < numberOfChildren; i++)
                  {
                  GEStreamNode streamNode = (GEStreamNode)children.get(i);
                  if (i == 0)
                  {
                  GraphConstants.setBounds(streamNode.getAttributes(),
                  new Rectangle(sjRect.x +50, sjRect.y+350, 200,120));
                  } 
                  else
                  {
                            
                  GraphConstants.setBounds(streamNode.getAttributes(),
                  new Rectangle(sjRect.x +250, sjRect.y+350, 200,120));
                  }
                  Map nest = new Hashtable ();
                  nest.put(streamNode, streamNode.getAttributes());
                  graphpad.getCurrentDocument().getGraph().getModel().edit(nest, null, null, null);             
                  }                         
                  } 
                  }
                */
            }
        getCurrentGraph().clearSelection();
    }
}
