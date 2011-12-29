package at.dms.kjc.raw;

import java.util.HashSet;
import java.util.Iterator;

import at.dms.kjc.Constants;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.sir.SIRFilter;

public class RateMatch extends at.dms.util.Utils 
    implements FlatVisitor, Constants 
{
    //To test that there are no crossed routes, 
    //keep this hashset of all tiles that are used to 
    //route items (excluding the source/dest of a route)
    HashSet<Coordinate> routerTiles;
    private static boolean fail;

    public static boolean doit(FlatNode top) 
    {
        //assume there are no overlapping routes
        fail = false;

        top.accept(new RateMatch(), null, true);
    
        return !fail;
    }

    public RateMatch() 
    {
        routerTiles = new HashSet<Coordinate>();
    }

    public void visitNode(FlatNode node) 
    {
        if (Layout.isAssigned(node)) {
            if (node.isFilter() &&
                (((SIRFilter)node.contents).getInputType().isArrayType() ||
                 ((SIRFilter)node.contents).getOutputType().isArrayType() ||
                 ((SIRFilter)node.contents).getInputType().isClassType() ||
                 ((SIRFilter)node.contents).getOutputType().isClassType()))
                fail = true;
        
            Iterator<FlatNode> it = Util.getAssignedEdges(node).iterator();
        
            while (it.hasNext()) {
                FlatNode dest = it.next();
                Iterator<Coordinate> route = Router.getRoute(node, dest).listIterator();
                //remove the source
                Coordinate current = route.next();
                while (route.hasNext()) {
                    current = route.next();
                    //now check to see if this tile has been routed thru
                    //before
                    if (current != Layout.getTile(dest)) {
                        //now check if we have routed thru here before
                        //or if the intermediate hop is a tile assigned 
                        //to a filter or joiner
                        if (routerTiles.contains(current) ||
                            (Layout.getNode(current) != null))
                            fail = true;
                        routerTiles.add(current);
                    }
            
                }
            }
        }
    }
}
