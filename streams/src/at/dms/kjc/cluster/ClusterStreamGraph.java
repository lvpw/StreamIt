package at.dms.kjc.cluster;

import java.util.HashMap;
import java.util.Map;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.ScheduledStreamGraph;
import at.dms.kjc.flatgraph.StaticStreamGraph;
import at.dms.kjc.flatgraph.StreamGraph;

public class ClusterStreamGraph extends ScheduledStreamGraph {

    public ClusterStreamGraph(FlatNode top) {
        super(top);
    }

    /**
     * Use in place of "new StaticStreamGraph" for subclassing.
     * 
     * A subclass of StreamGraph may refer to a subclass of StaticStreamGraph.
     * If we just used "new StaticStreamGraph" in this class we would
     * only be making StaticStreamGraph's of the type in this package.
     * 
     * 
     * @param sg       a StreamGraph
     * @param realTop  the top node
     * @return         a new StaticStreamGraph
     */
    @Override protected ClusterStaticStreamGraph new_StaticStreamGraph(StreamGraph sg, FlatNode realTop) {
        return new ClusterStaticStreamGraph(sg,realTop);
    }
    
    /** Clean up any data structures affected by fusion. */
    public void cleanupForFused() {
        // handle fusion in each StaticStreamGraph
        for (StaticStreamGraph ssg : staticSubGraphs) {
            ClusterStaticStreamGraph csg = (ClusterStaticStreamGraph)ssg;
            csg.cleanupForFused();
        }
        // handle fusion in parentMap
        HashMap<FlatNode,StaticStreamGraph> newParentMap = new HashMap<FlatNode,StaticStreamGraph>();
        for (Map.Entry<FlatNode,StaticStreamGraph> parent : parentMap.entrySet()) {
            FlatNode k = parent.getKey();
            StaticStreamGraph v = parent.getValue();
            if (ClusterFusion.isEliminated(k)) {
                k = ClusterFusion.getMaster(k);
            }
            newParentMap.put(k,v);
        }
        parentMap = newParentMap;

        // topLevelFlatNode was listed as protected, but should have been private...
//        // handle fusion in topLevelFlatNode
//        if (ClusterFusion.isEliminated(topLevelFlatNode)) {
//            topLevelFlatNode = ClusterFusion.getMaster(topLevelFlatNode);
//        }
    }
}
