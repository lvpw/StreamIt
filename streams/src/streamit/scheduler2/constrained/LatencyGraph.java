/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.scheduler2.constrained;

import streamit.misc.DLList;
import streamit.misc.DLList_const;
import streamit.misc.DLListIterator;
import streamit.misc.Pair;
import streamit.misc.OMap;
import streamit.misc.OMapIterator;
import streamit.misc.OSet;
import streamit.misc.OSetIterator;

import streamit.scheduler2.SDEPData;

public class LatencyGraph extends streamit.misc.AssertedClass
{
    /*
     * This is a list of all nodes (Filters, splitter and joiners)
     * in the graph. Each element is a LatencyNode.
     */
    final DLList nodes = new DLList();

    /*
     * This a map of StreamInterfaces to DLList of StreamInterfaces.
     * The second entry contains a list of ancestor of the key, 
     * ordered from top-most to bottom-most (StreamIt object downto
     * the immediate parent). A stream with no parents (out-most
     * pipeline) has an empty list.
     */
    final OMap ancestorLists = new OMap();

    DLList getAncestorList(StreamInterface stream)
    {
        OMapIterator listIter = ancestorLists.find(stream);
        assert !listIter.equals(ancestorLists.end());

        DLList ancestors = (DLList)listIter.getData();
        assert ancestors != null;

        return ancestors;
    }

    public StreamInterface findLowestCommonAncestor(
                                                    LatencyNode src,
                                                    LatencyNode dst)
    {
        DLList_const srcAncestors = src.getAncestors();
        DLList_const dstAncestors = dst.getAncestors();

        DLListIterator srcIter = srcAncestors.begin();
        DLListIterator dstIter = dstAncestors.begin();

        DLListIterator lastSrcIter = srcAncestors.end();
        DLListIterator lastDstIter = dstAncestors.end();

        StreamInterface lowestAncestor = (StreamInterface)srcIter.get();
        assert (StreamInterface)dstIter.get() == lowestAncestor;

        for (;
             (!srcIter.equals(lastSrcIter))
                 && (!dstIter.equals(lastDstIter));
             srcIter.next(), dstIter.next())
            {
                if (srcIter.get() != dstIter.get())
                    break;

                lowestAncestor = (StreamInterface)srcIter.get();
            }

        return lowestAncestor;
    }

    void registerParent(StreamInterface child, StreamInterface parent)
    {
        if (parent == null)
            {
                // no parent - just add an empty list
                ancestorLists.insert(child, new DLList());
            }
        else
            {
                // has a parent - find parent's ancestors, copy the list
                // and add the parent - that's the child's list of ancestors
                DLList parentAncestors =
                    (DLList)ancestorLists.find(parent).getData();
                DLList ancestors = parentAncestors.copy();
                ancestors.pushBack(parent);
                ancestorLists.insert(child, ancestors);
            }
    }

    LatencyNode addFilter(Filter filter)
    {
        LatencyNode newNode =
            new LatencyNode(filter, getAncestorList(filter));
        nodes.pushBack(newNode);
        return newNode;
    }

    LatencyNode addSplitter(SplitJoin sj)
    {
        LatencyNode newNode =
            new LatencyNode(sj, true, getAncestorList(sj));
        nodes.pushBack(newNode);
        return newNode;
    }

    LatencyNode addJoiner(SplitJoin sj)
    {
        LatencyNode newNode =
            new LatencyNode(sj, false, getAncestorList(sj));
        nodes.pushBack(newNode);
        return newNode;
    }

    LatencyNode addSplitter(FeedbackLoop fl)
    {
        LatencyNode newNode =
            new LatencyNode(fl, true, getAncestorList(fl));
        nodes.pushBack(newNode);
        return newNode;
    }

    LatencyNode addJoiner(FeedbackLoop fl)
    {
        LatencyNode newNode =
            new LatencyNode(fl, false, getAncestorList(fl));
        nodes.pushBack(newNode);
        return newNode;
    }

    /**
     * Returns whether or not this graph has a cycle, contained within
     * <ancestor>.
     */
    private boolean hasCycle(StreamInterface ancestor) {
        // do a depth-first search; if we find a back-edge, report
        // cycle
        OSet visiting = new OSet();  // currently being visited
        OSet done = new OSet();      // done being visited
        
        LatencyNode top = ancestor.getTopLatencyNode();

        return hasCycleHelper(top, visiting, done);
    }
    /**
     * Visits <node> and all its children in the depth-first
     * search for cycles.  Returns whether or not it found a cycle.
     */
    private boolean hasCycleHelper(LatencyNode node, OSet visiting, OSet done) {
        if (!visiting.find(node).equals(visiting.end())) {
            // if we are already visiting <node>, then report cycle
            return true;
        } else if (!done.find(node).equals(done.end())) {
            // if we already visited <node>, then no cycle here
            return false;
        }

        // otherwise, start visiting node
        visiting.insert(node);

        // visit all children of <node> and return true if any of them
        // lead to a cycle
        DLList_const downstreamEdges = node.getDependants();
        DLListIterator edgeIter = downstreamEdges.begin();
        DLListIterator lastEdgeIter = downstreamEdges.end();
        for (; !edgeIter.equals(lastEdgeIter); edgeIter.next()) {
            LatencyEdge edge = (LatencyEdge)edgeIter.get();
            assert edge.getSrc() == node;
            
            LatencyNode downstreamNode = edge.getDst();
            if (hasCycleHelper(downstreamNode, visiting, done)) {
                return true;
            }
        }

        // mark as done
        visiting.erase(node);
        done.insert(node);

        return false;
    }

    /**
     * Returns edges between two nodes, assuming one is upstream and
     * other is downstream.
     */
    private OSet getEdgesBetween(LatencyNode upstreamNode, LatencyNode downstreamNode) {
        StreamInterface ancestor =
            findLowestCommonAncestor(upstreamNode, downstreamNode);

        OSet result =
            visitGraph(
                       upstreamNode,
                       false,
                       true,
                       ancestor,
                       false,
                       true);

        OSet edgesUpstream =
            visitGraph(
                       downstreamNode,
                       true,
                       false,
                       ancestor,
                       false,
                       true);

        // find an intersection between these two sets:
        {
            OSetIterator edgesIter = result.begin();
            OSetIterator edgesLastIter = result.end();
            OSetIterator upstreamEdgesLastIter = edgesUpstream.end();

            DLList uselessEdges = new DLList();

            for (; !edgesIter.equals(edgesLastIter); edgesIter.next())
                {
                    // if the edgesUpstream set doesn't have this edge,
                    // store it to be removed from the set
                    if (edgesUpstream
                        .find(edgesIter.get())
                        .equals(upstreamEdgesLastIter))
                        uselessEdges.pushBack(edgesIter.get());
                }

            // remove useless edges
            while (!uselessEdges.empty())
                {
                    result.erase(uselessEdges.front().get());
                    uselessEdges.popFront();
                }

            // now result holds all edges that can be traversed
            // between upstreamNode and downstreamNode
        }

        // if there are any loops in the graph within <ancestor>,
        // remove the backward pointing edges:
        if (hasCycle(ancestor))
        {
            OSet backwardPointingEdges =
                findBackPointingEdges(upstreamNode, ancestor);
            OSetIterator backEdgeLastIter = backwardPointingEdges.end();
            for (OSetIterator backEdgeIter =
                     backwardPointingEdges.begin();
                 !backEdgeIter.equals(backEdgeLastIter);
                 backEdgeIter.next())
                {
                    result.erase(backEdgeIter.get());
                }
        } 

        return result;
    }

    /**
     * Returns true iff there is a downstream path from <node1> to <node2>.
     */
    public boolean isDownstreamPath(LatencyNode node1, LatencyNode node2) {
        OSet edges = getEdgesBetween(node1, node2);
        return !edges.empty();
    }

    /**
     * Returns true iff there is an upstream path from <node1> to <node2>.
     */
    public boolean isUpstreamPath(LatencyNode node1, LatencyNode node2) {
        OSet edges = getEdgesBetween(node2, node1);
        return !edges.empty();
    }

    public SDEPData computeSDEP(
                                LatencyNode upstreamNode,
                                LatencyNode downstreamNode)
        throws NoPathException
    {
        // first find all the edges that need to be traversed when figuring
        // out the dependencies between nodes
        OSet edgesToTraverse = getEdgesBetween(upstreamNode, downstreamNode);

        // make sure that there are SOME edges between the two nodes
        // if this assert fails, then either the srcIsUpstream is reversed
        // or there is no path between upstreamNode and downstreamNode
        // within their lowest common ancestor.
        // if you don't understand this, or think it's wrong, ask karczma 
        // (03/07/15)
        //assert !edgesToTraverse.empty();
        if (edgesToTraverse.empty()) {
            throw new NoPathException();
        }

        // now go through all the edges and count how many useful edges
        // arrive at each node that will be traversed
        OMap nodes2numEdges = new OMap();
        OMapIterator lastN2NEIter = nodes2numEdges.end();
        {
            OSetIterator edgeIter = edgesToTraverse.begin();
            OSetIterator lastEdgeIter = edgesToTraverse.end();

            Integer ONE = new Integer(1);

            for (; !edgeIter.equals(lastEdgeIter); edgeIter.next())
                {
                    LatencyNode nodeDst =
                        ((LatencyEdge)edgeIter.get()).getDst();
                    OMapIterator n2eIter = nodes2numEdges.find(nodeDst);

                    if (!n2eIter.equals(lastN2NEIter))
                        {
                            Integer useCount = (Integer)n2eIter.getData();

                            // increase the data useful edge count
                            n2eIter.setData(new Integer(useCount.intValue() + 1));
                        }
                    else
                        {
                            nodes2numEdges.insert(nodeDst, ONE);
                        }
                }
        }

        /*
         * The following map maps LatencyNode to LatencyEdge
         * The LatencyEdge is a latency LatencyEdge (meaning that it
         * never is concerned about # of data items that travel on tapes,
         * only which execution of the src translates to an execution of
         * the dst) going from upstreamNode to the key node. 
         */
        OMap nodes2latencyEdges = new OMap();
        OMapIterator lastNodes2latencyEdgesIter = nodes2latencyEdges.end();

        // insert an identity latency edge from src to src into the map
        nodes2latencyEdges.insert(
                                  upstreamNode,
                                  new LatencyEdge(upstreamNode));

        OSet nodesToVisit = new OSet();
        nodesToVisit.insert(upstreamNode);

        // compute the dependency list for all the nodes
        // wrt to the upstreamNode
        while (!nodesToVisit.empty())
            {
                OSetIterator nodesToVisitIter = nodesToVisit.begin();
                LatencyNode node = (LatencyNode)nodesToVisitIter.get();
                nodesToVisit.erase(nodesToVisitIter);

                DLList_const dependants = node.getDependants();
                DLListIterator dependantIter = dependants.begin();
                DLListIterator lastDependant = dependants.end();

                OSetIterator edgesLastIter = edgesToTraverse.end();
                for (;
                     !dependantIter.equals(lastDependant);
                     dependantIter.next())
                    {
                        LatencyEdge edge = (LatencyEdge)dependantIter.get();
                        LatencyNode edgeSrc = edge.getSrc();
                        LatencyNode edgeDst = edge.getDst();

                        // if this edge doesn't need to be traversed, don't
                        OSetIterator edgeDstNodeIter = edgesToTraverse.find(edge);
                        if (edgeDstNodeIter.equals(edgesLastIter))
                            continue;

                        // create an edge from upstreamNode to edgeDst
                        {
                            // first just create an edge by combining edges:
                            // upstreamNode->edge.src and edge
                            LatencyEdge newEdge;
                            {
                                OMapIterator upstreamNode2srcIter =
                                    nodes2latencyEdges.find(edgeSrc);
                                assert !upstreamNode2srcIter.equals
                                    (lastNodes2latencyEdgesIter);

                                newEdge =
                                    new LatencyEdge(
                                                    (LatencyEdge)upstreamNode2srcIter.getData(),
                                                    edge);
                            }

                            // if there already is an edge going from upstreamNode 
                            // to edgeDst, I need to combine that edge with this 
                            // newEdge
                            {
                                OMapIterator upstreamNode2dstIter =
                                    nodes2latencyEdges.find(edgeDst);
                                if (!upstreamNode2dstIter
                                    .equals(lastNodes2latencyEdgesIter))
                                    {
                                        newEdge =
                                            new LatencyEdge(
                                                            newEdge,
                                                            (LatencyEdge)upstreamNode2dstIter
                                                            .getData());
                                    }
                            }

                            // and finally, insert the new edge into the map.
                            // if an edge going to edgeDst already exists there,
                            // this will replace it, which is good

                            Pair result =
                                nodes2latencyEdges.insert(edgeDst, newEdge);
                            OMapIterator iter = (OMapIterator)result.getFirst();
                            iter.setData(newEdge);
                        }

                        // find how many more edges need to lead to this node
                        OMapIterator nodeNumEdgesIter =
                            nodes2numEdges.find(edge.getDst());
                        int nodeNumEdges =
                            ((Integer)nodeNumEdgesIter.getData()).intValue();
                        assert nodeNumEdges > 0;

                        // decrease the number of edges that need to lead to this node
                        // by one
                        nodeNumEdges--;
                        nodeNumEdgesIter.setData(new Integer(nodeNumEdges));

                        // if there are no more edges leading into this node,
                        // I'm ready to visit the node
                        if (nodeNumEdges == 0)
                            {
                                nodesToVisit.insert(edge.getDst());
                            }
                    }
            }

        return (LatencyEdge)
            (nodes2latencyEdges.find(downstreamNode).getData());
    }

    public OSet visitGraph(
                           LatencyNode startNode,
                           boolean travelUpstream,
                           boolean travelDownstream,
                           StreamInterface withinStream,
                           boolean visitNodes,
                           boolean visitEdges)
    {
        assert startNode != null;
        DLList nodesToExplore = new DLList();
        nodesToExplore.pushBack(startNode);
        OSet nodesVisited = new OSet();
        nodesVisited.insert(startNode);
        OSetIterator lastNodeVisited = nodesVisited.end();

        OSet resultNodesNEdges = new OSet();

        if (visitNodes)
            resultNodesNEdges.insert(startNode);

        while (!nodesToExplore.empty())
            {
                LatencyNode node = (LatencyNode)nodesToExplore.begin().get();
                nodesToExplore.popFront();

                if (travelUpstream)
                    {
                        DLList_const upstreamEdges = node.getDependecies();
                        DLListIterator edgeIter = upstreamEdges.begin();
                        DLListIterator lastEdgeIter = upstreamEdges.end();

                        for (; !edgeIter.equals(lastEdgeIter); edgeIter.next())
                            {
                                LatencyEdge edge = (LatencyEdge)edgeIter.get();
                                assert edge.getDst() == node;

                                LatencyNode upstreamNode = edge.getSrc();

                                // should I visit this node or is it not within
                                // the boundary ancestor?
                                if (withinStream != null
                                    && !upstreamNode.hasAncestor(withinStream))
                                    continue;

                                // visit the edge                        
                                if (visitEdges)
                                    resultNodesNEdges.insert(edge);

                                // if I've visited the node already once, there's no point
                                // in visiting it again!
                                if (!nodesVisited
                                    .find(upstreamNode)
                                    .equals(lastNodeVisited))
                                    continue;
                                nodesVisited.insert(upstreamNode);
                                nodesToExplore.pushBack(upstreamNode);

                                // visit the node
                                if (visitNodes)
                                    resultNodesNEdges.insert(upstreamNode);

                            }
                    }

                if (travelDownstream)
                    {
                        DLList_const downstreamEdges = node.getDependants();
                        DLListIterator edgeIter = downstreamEdges.begin();
                        DLListIterator lastEdgeIter = downstreamEdges.end();

                        for (; !edgeIter.equals(lastEdgeIter); edgeIter.next())
                            {
                                LatencyEdge edge = (LatencyEdge)edgeIter.get();
                                assert edge.getSrc() == node;

                                LatencyNode downstreamNode = edge.getDst();

                                // should I visit this node or is it not within
                                // the boundary ancestor?
                                if (withinStream != null
                                    && !downstreamNode.hasAncestor(withinStream))
                                    continue;

                                // visit the edge                        
                                if (visitEdges)
                                    resultNodesNEdges.insert(edge);

                                // if I've visited the node already once, there's no point
                                // in visiting it again!
                                if (!nodesVisited
                                    .find(downstreamNode)
                                    .equals(lastNodeVisited))
                                    continue;
                                nodesVisited.insert(downstreamNode);
                                nodesToExplore.pushBack(downstreamNode);

                                // visit the node
                                if (visitNodes)
                                    resultNodesNEdges.insert(downstreamNode);
                            }
                    }
            }

        return resultNodesNEdges;
    }

    public OSet findBackPointingEdges(
                                      LatencyNode startNode,
                                      StreamInterface withinStream)
    {
        // first find all the nodes in the stream (just to count them!)
        OSet nodesInStream = new OSet();
        OSetIterator nodeInStreamLastIter = nodesInStream.end();
        {
            DLList nodesToVisit = new DLList();
            nodesToVisit.pushBack(startNode);

            OSetIterator lastNodeToVisitIter = nodesInStream.end();
            while (!nodesToVisit.empty())
                {
                    LatencyNode node = (LatencyNode)nodesToVisit.front().get();
                    nodesToVisit.popFront();

                    // have I visited the node already?
                    if (!nodesInStream.find(node).equals(lastNodeToVisitIter))
                        continue;

                    // is the node within the minimal ancestor?                    
                    if (withinStream != null
                        && !node.hasAncestor(withinStream))
                        continue;

                    // add the node as in the stream
                    nodesInStream.insert(node);

                    // visit all the downstream nodes of this node
                    DLList_const dependants = node.getDependants();
                    DLListIterator lastDependantIter = dependants.end();
                    for (DLListIterator depIter = dependants.begin();
                         !depIter.equals(lastDependantIter);
                         depIter.next())
                        {
                            nodesToVisit.pushBack(
                                                  ((LatencyEdge)depIter.get()).getDst());
                        }
                }
        }
        int nNodesInStream = nodesInStream.size();

        // set up the backwards-pointing edge computation
        OMap node2int = new OMap();
        OMap int2node = new OMap();
        int nodeVectors[][] = new int[nNodesInStream][nNodesInStream];
        {
            OSetIterator nodeInStreamIter = nodesInStream.begin();

            for (int nNode = 0;
                 nNode < nNodesInStream;
                 nNode++, nodeInStreamIter.next())
                {
                    LatencyNode node = (LatencyNode)nodeInStreamIter.get();
                    Integer idx = new Integer(nNode);
                    node2int.insert(node, idx);
                    int2node.insert(idx, node);
                    nodeVectors[nNode][nNode] = 1;
                }
        }

        // find the backward pointing edges
        OSet backwardPointingEdges = new OSet();
        DLList nodesToVisit = new DLList();
        for (nodesToVisit.pushBack(startNode);
             !nodesToVisit.empty();
             nodesToVisit.popFront())
            {
                LatencyNode srcNode = (LatencyNode)nodesToVisit.front().get();
                DLList_const edgeList = srcNode.getDependants();
                DLListIterator edgeListIterLast = edgeList.end();
                for (DLListIterator edgeListIter = edgeList.begin();
                     !edgeListIter.equals(edgeListIterLast);
                     edgeListIter.next())
                    {
                        LatencyEdge edge = (LatencyEdge)edgeListIter.get();
                        LatencyNode dstNode = edge.getDst();

                        // is my dst within my desired stream?
                        if (nodesInStream
                            .find(dstNode)
                            .equals(nodeInStreamLastIter))
                            // no? skip it!
                            continue;

                        // get the vectors for both my src and dst nodes
                        int srcIdx =
                            ((Integer)node2int.find(srcNode).getData()).intValue();
                        int srcVector[] = nodeVectors[srcIdx];

                        int dstIdx =
                            ((Integer)node2int.find(dstNode).getData()).intValue();
                        int dstVector[] = nodeVectors[dstIdx];

                        // have I visited dst already?
                        if (srcVector[dstIdx] != 0)
                            {
                                // yes! add edge to backwardPointingEdges and skip it
                                backwardPointingEdges.insert(edge);
                                continue;
                            }

                        // okay, propagate the visited nodes:
                        int oldTotal = 0;
                        int newTotal = 0;
                        for (int nIdx = 0; nIdx < nNodesInStream; nIdx++)
                            {
                                oldTotal += dstVector[nIdx];
                                dstVector[nIdx] |= srcVector[nIdx];
                                newTotal += dstVector[nIdx];
                            }

                        if (newTotal != oldTotal)
                            {
                                // I've added nodes to the dst node vector
                                // will have to visit this node in near future!
                                nodesToVisit.pushBack(dstNode);
                            }
                    }

            }

        return backwardPointingEdges;
    }
}
