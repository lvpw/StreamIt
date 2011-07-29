package at.dms.kjc.slir;

import at.dms.kjc.CType;

/**
 * An Edge connects two {@link InternalFilterNode}s.
 * Edges can be differentiated into {@link InterFilterEdge}s that connect the OutputSliceNode of a slice
 * and the InputSliceNode of a slice, and {@link IntraFilterEdge} that connect two SliceNodes in
 * the same slice.  
 * 
 * TODO: <b>Warning</b> Edge is currently used as a key for Channel, but sophisticated graph optimizations
 * remove SIRIdentity filters, and remove redundant adjacent edges. A RR(1) splitter connected to a 
 * RR(1) joiner in a slice graph could represent an identity operation, but could also represent a 
 * permutation operation (e.g. by having the first output of the splitter connect to the second input
 * of the joiner, and the second output of the splitter connect to the first input of the joiner).
 * Our optimizations would change this permutation to an identity.  Luckily this sort of permutation is impossible
 * in the SIR graph, and is not introduced by any current optimization on the slice graph.
 * @author mgordon
 *
 */

public class Edge implements at.dms.kjc.DeepCloneable {
    public static final String[] DO_NOT_CLONE_THESE_FIELDS = { "src", "dest" };
    /**
     * Source of directed edge in Slice graph
     */
    protected InternalFilterNode src;

    /**
     * Destination of directed edge in Slice graph
     */
    protected InternalFilterNode dest;

    /**
     * Caches type for {@link #getType()} calls
     */
    private CType type;

    /**
     * Full constructor, (type will be inferred from src / dest).
     * @param src   Source assumed to be an OutputSliceNode or a FilterSliceNode.
     * @param dest  Dest assumed to be an InputSliceNode or a FilterSliceNode.
     */
    public Edge(InternalFilterNode src, InternalFilterNode dest) {
        assert src != null;
        assert dest != null;
        this.src = src;
        this.dest = dest;
        type = null;
    }

    /**
     * Partial constructor, for subclasses.
     *
     */
    protected Edge() { }
    
    
    /**
     * @return source SliceNode
     */
    public InternalFilterNode getSrc() {
        return src;
    }

    public Edge(OutputNode src) {
        this.src = src;
    }

    public Edge(InputNode dest) {
        this.dest = dest;
    }

    public CType getType() {
        if (type != null) {
            return type;
        }
        // inter-slice edge
        if (src instanceof OutputNode && dest instanceof InputNode) {
            WorkNodeContent srcContent;
            WorkNodeContent dstContent;
            CType srcType;
            CType dstType;
            srcContent = ((OutputNode)src).getPrevFilter().getFilter();
            dstContent = ((InputNode)dest).getNextFilter().getFilter();
            srcType = srcContent.getOutputType();
            dstType = dstContent.getInputType();
            type = dstType;
            assert srcType.equals(dstType) : "Error calculating type: " + 
            srcContent + " -> " + dstContent;
            return type;
        }
        
        // intra-slice edges:
        if (src instanceof InputNode && dest instanceof WorkNode) {
            type = ((WorkNode)dest).getFilter().getInputType();
            return type;
        }
        if (src instanceof WorkNode && dest instanceof OutputNode) {
            type = ((WorkNode)src).getFilter().getOutputType();
            return type;
        }
        // only for general slices...
        if (src instanceof WorkNode
                && dest instanceof WorkNode) {
            type = ((WorkNode)src).getFilter().getOutputType();
            assert type == ((WorkNode)dest).getFilter().getInputType() 
            : "Error calculating type: " + 
            ((WorkNode)src).getFilter() + " -> " + ((WorkNode)dest).getFilter();
            return type;
        }
        throw new AssertionError ("Unexpected SliceNode connection " + src + " -> " + dest);
    }

    /**
     * @return dest SliceNode
     */
    public InternalFilterNode getDest() {
        return dest;
    }

    /**
     * Set the source SliceNode
     * @param src
     */
    public void setSrc(InternalFilterNode src) {
        this.src = src;
    }

    /**
     * Set the destination SliceNode
     * @param dest
     */
    public void setDest(InternalFilterNode dest) {
        this.dest = dest;
    }

    public String toString() {
        return src + "->" + dest + "(" + hashCode() + ")";
    }
    /**
     * Return a FilterSliceNode that is either the passed node, or the next node if an InputSliceNode.
     * Error if passed an OutputSliceNode.
     * @param node a FilterSliceNode or InputSliceNode
     * @return a FilterSliceNode
     */
    private static WorkNode nextFilter(InternalFilterNode node) {
        if (node instanceof WorkNode) {
            return (WorkNode)node;
        } else {
            return ((InputNode)node).getNextFilter();
        }
    }
    
    /**
     * Return a FilterSliceNode that is either the passed node, or the previous node if an OutputSliceNode.
     * Error if passed an InputSliceNode.
     * @param node a FilterSliceNode or OutputSliceNode
     * @return a FilterSliceNode
     */
   
    private static WorkNode prevFilter(InternalFilterNode node) {
        if (node instanceof WorkNode) {
            return (WorkNode)node;
        } else {
            return ((OutputNode)node).getPrevFilter();
        }
    }



    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.kjc.slir.Edge other = new at.dms.kjc.slir.Edge();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.slir.Edge other) {
        other.src = this.src;
        other.dest = this.dest;
        other.type = (at.dms.kjc.CType)at.dms.kjc.AutoCloner.cloneToplevel(this.type);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
