package streamit.scheduler2.constrained;

import streamit.scheduler2.iriter./*persistent.*/
PipelineIter;

/**
 * streamit.scheduler2.constrained.Pipeline is the pipeline constrained 
 * scheduler. It assumes that all streams in the program use the constrained
 * scheduler
 */

public class Pipeline
    extends streamit.scheduler2.hierarchical.Pipeline
    implements StreamInterface
{
    final private LatencyGraph latencyGraph;
    
    public Pipeline(
        PipelineIter iterator,
        streamit.scheduler2.constrained.StreamFactory factory)
    {
        super(iterator, factory);

        latencyGraph = factory.getLatencyGraph();

        // add all children to the latency graph
        for (int nChild = 0; nChild + 1 < getNumChildren(); nChild++)
        {
            StreamInterface topStream = getConstrainedChild(nChild);
            StreamInterface bottomStream = getConstrainedChild(nChild + 1);

            LatencyNode topNode = topStream.getBottomLatencyNode();
            LatencyNode bottomNode = bottomStream.getTopLatencyNode();

            LatencyEdge edge = new LatencyEdge(topNode, 0, bottomNode, 0, this);
        }
    }

    StreamInterface getConstrainedChild(int nChild)
    {
        streamit.scheduler2.base.StreamInterface child;
        child = getChild(nChild);

        if (!(child instanceof StreamInterface))
        {
            ERROR("This pipeline contains a child that is not CONSTRAINED");
        }

        return (StreamInterface)child;
    }

    public LatencyNode getBottomLatencyNode()
    {
        return getConstrainedChild(getNumChildren() - 1).getBottomLatencyNode();
    }

    public LatencyNode getTopLatencyNode()
    {
        return getConstrainedChild(0).getTopLatencyNode();
    }

    public void computeSchedule()
    {
        ERROR("Not implemented yet.");

    }
}
