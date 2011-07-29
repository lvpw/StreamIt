package at.dms.kjc.slir.fission;

import java.util.*;
import at.dms.kjc.*;
import at.dms.kjc.slir.*;

public class FissionEdgeMemoizer {
    private static HashMap<EdgeDescriptor, InterFilterEdge> edges =
        new HashMap<EdgeDescriptor, InterFilterEdge>();

    public static void reset() {
        edges.clear();
    }

    public static void addEdge(InterFilterEdge edge) {
        EdgeDescriptor edgeDscr = new EdgeDescriptor(edge);

        edges.put(edgeDscr, edge);
    }

    public static InterFilterEdge getEdge(OutputNode src, InputNode dest) {
        EdgeDescriptor edgeDscr = new EdgeDescriptor(src, dest);      

        InterFilterEdge edge = edges.get(edgeDscr);

        if(edge == null) {
            edge = new InterFilterEdge(src, dest);
            edges.put(edgeDscr, edge);
        }

        return edge;
    }

    public static InterFilterEdge getEdge(Filter src, Filter dest) {
        return getEdge(src.getOutputNode(), dest.getInputNode());
    }

    private static class EdgeDescriptor {
        public OutputNode src;
        public InputNode dest;

        public EdgeDescriptor(OutputNode src, InputNode dest) {
            this.src = src;
            this.dest = dest;
        }
        
        public EdgeDescriptor(Filter src, Filter dest) {
            this(src.getOutputNode(), dest.getInputNode());
        }

        public EdgeDescriptor(InterFilterEdge edge) {
            this(edge.getSrc(), edge.getDest());
        }
        
        public boolean equals(Object obj) {
            if(obj instanceof EdgeDescriptor) {
                EdgeDescriptor edge = (EdgeDescriptor)obj;
                
                if(this.src.equals(edge.src) &&
                   this.dest.equals(edge.dest))
                    return true;
                
                return false;
            }
            
            return false;
        }
        
        public int hashCode() {
            return src.hashCode() + dest.hashCode();
        }
    }
}