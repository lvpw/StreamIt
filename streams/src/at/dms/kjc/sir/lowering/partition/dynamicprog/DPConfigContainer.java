package at.dms.kjc.sir.lowering.partition.dynamicprog;

import java.util.*;
import java.io.*;

import at.dms.kjc.*;
import at.dms.util.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.fusion.*;
import at.dms.kjc.sir.lowering.fission.*;
import at.dms.kjc.sir.lowering.partition.*;

abstract class DPConfigContainer extends DPConfig {
    /**  
     * A_s[x1][x2][y1][y2][n][j] holds minimum cost of assigning
     * children (x1..x2, y1..y2) of stream s to n tiles.  <j> is 1 if
     * these children are next to a downstream joiner in the current
     * configuration; <j> is zero otherwise.  If this corresponds to a
     * filter's config, then A is null.
     */
    private int[][][][][][] A;

    /**
     * The stream for this container.
     */
    protected SIRContainer cont;
    /**
     * Partitioner corresponding to this.
     */
    protected DynamicProgPartitioner partitioner;
    /**
     * Specifies the width of the y_i'th component of this
     */
    private int[] width;
    /**
     * Whether or not the i'th row of this is uniform -- that is,
     * if all of its components have the same work estimate.
     */
    boolean[] uniform;
    /**
     * Max width from i to j, for i<=j.
     */
    int maxWidth[][];
    /**
     * Whether or not there is the same width from i to j, for i<=j.
     */
    boolean sameWidth[][];
    
    /**
     * <width> and <height> represent the dimensions of the stream.
     */
    protected DPConfigContainer(SIRContainer cont, DynamicProgPartitioner partitioner, 
				int[] width, int height) {
	super(partitioner);
	this.cont = cont;
	this.partitioner = partitioner;
	this.width = width;
	// find maxWidth
	int maxWidth = -1;
	for (int i=0; i<height; i++) {
	    maxWidth = Math.max(maxWidth, width[i]);
	}
	// for simplicity, allocate the bounding box for A
	this.A = new int[maxWidth][maxWidth][height][height][partitioner.getNumTiles()+1][2];
	this.uniform = new boolean[height];
	initUniform();
	//maxAlias();
	initWidth();
    }

    private void initWidth() {
	int height=A[0][0].length;
	maxWidth = new int[height][height];
	sameWidth = new boolean[height][height];
	for (int y1=0; y1<height; y1++) {
	    int max = width[y1];
	    boolean same = true;
	    for (int y2=y1; y2<height; y2++) {
		max = Math.max(max, width[y2]);
		same = same && width[y2]==width[y1];
		maxWidth[y1][y2] = max;
		sameWidth[y1][y2] = same;
	    }
	}
    }

    /**
     * Initialize <uniform> array and introduce aliasing into [A] accordingly.
     */
    private void initUniform() {
	// find the uniform rows
	WorkEstimate work = partitioner.getWorkEstimate();
	for (int i=0; i<uniform.length; i++) {
	    SIRStream child1 = childConfig(0, i).getStream();
	    int work1=0;
	    // right now work estimate is stupid and only gets filter
	    // works... should be extended to deal with containers and
	    // pre-defined filters as well.
	    if (child1 instanceof SIRFilter && !(child1 instanceof SIRPredefinedFilter)) {
		work1 = work.getWork((SIRFilter)child1);
	    } else {
		continue;
	    }
	    // will try disproving this
	    uniform[i] = true;
	    search:
	    for (int j=1; j<width[i]; j++) {
		SIRStream child2 = childConfig(j, i).getStream();
		if (!(child2 instanceof SIRFilter) || (child2 instanceof SIRPredefinedFilter) || work1!=work.getWork((SIRFilter)child2)) {
		    uniform[i] = false;
		    break search;
		}
	    }
	}
	// print out the uniform rows
	/*
	for (int i=0; i<uniform.length; i++) {
	    if (uniform[i] && width[i]>1) {
		System.err.println("Found row " + i + "/" + (uniform.length-1) + " of " + cont.getName() + " to be uniform.");
	    }
	}
	*/
	// now find maximal uniform regions with the same width, and alias their memoization tables
	int low=0;
	while (low<uniform.length) {
	    while (low<uniform.length && !uniform[low]) {
		low++;
	    }
	    if (low<uniform.length) {
		int high = low;
		while (high+1<uniform.length && uniform[high+1] && (width[high+1]==width[low])) {
		    high++;
		}
		// alias low..high across the board, to point to values in 0'th column
		for (int i1=low; i1<=high; i1++) {
		    // only aliasing from i2=i1..high matters, but we'd just as well go i2=low...high
		    for (int i2=low; i2<=high; i2++) {
			for (int xWidth=0; xWidth<width[low]-1; xWidth++) {
			    for (int xStart=1; xStart<width[low]-xWidth; xStart++) {
				A[xStart][xStart+xWidth][i1][i2] = A[0][xWidth][i1][i2];
			    }
			}
		    }
		}
	    }
	    low++;
	}
    }

    public static int aliases=0;
    // NOTE: this is a very unsafe hash because it takes the sum of
    // work instead of a hash of work.
    private void maxAlias() {
	// consider every rectangle [x1,y1]->[x2,y2]
	int height=A[0][0].length;
	for (int y1=0; y1<height; y1++) {
	    int rectWidth = width[y1];
	    for (int y2=y1; y2<height; y2++) {
		rectWidth = Math.min(rectWidth, width[y2]);
		for (int x1=0; x1<rectWidth; x1++) {
		    for (int x2=0; x2<rectWidth; x2++) {
			// my rectangle
			int rect1=workFor(x1,x2,y1,y2);
			if (rect1==-1) { continue; }
			// rectangle to left
			int rect3=workFor(x1-1,x2-1,y1,y2);
			if (rect1==rect3) {
			    A[x1][x2][y1][y2] = A[x1-1][x2-1][y1][y2];
			    //System.err.println("For " + cont.getName() + ", aliasing (" + x1 + ", " + y1 + ")->(" + x2 + ", " + y2 + ") left");
			    aliases++;
			    continue;
			}
			// rectangle above
			int rect2=workFor(x1,x2,y1-1,y2-1);
			if (rect1==rect3) {
			    A[x1][x2][y1][y2] = A[x1][x2][y1-1][y2-1];
			    //System.err.println("For " + cont.getName() + ", aliasing (" + x1 + ", " + y1 + ")->(" + x2 + ", " + y2 + ") up");
			    aliases++;
			    continue;
			}
		    }
		}
	    }
	}
    }

    /**
     * Return work for rectangle in range; return -1 if undefined.
     */
    private int workFor(int x1, int x2, int y1, int y2) {
	// check bounds
	if (x1<0 || y1<0 || x2<0 || y2<0 || x2<x1 || y2<y1 || x2>width[y1]-1 ) {
	    return -1;
	}
	// do sum
	int sum = 0;
	for (int x=x1; x<=x2; x++) {
	    for (int y=y1; y<=y2; y++) {
		int s = workFor(childConfig(x, y).getStream());
		// quit if we ever hit undefined
		if (s==-1) {
		    return -1;
		}  else {
		    sum+=s;
		}
	    }
	}
	return sum;
    }

    /**
     * Right now work estimate doesn't recognize some things like
     * predefined filters and containers, so wrap it like this.
     */
    private int workFor(SIRStream str) {
	try {
	    return partitioner.getWorkEstimate().getWork((SIRFilter)str);
	} catch (RuntimeException e) {
	    return -1;
	}
    }

    public SIRStream getStream() {
	return cont;
    }

    /**
     * Requires <str> is a container.
     */
    protected void setStream(SIRStream str) {
	Utils.assert(str instanceof SIRContainer);
	this.cont = (SIRContainer)str;
    }

    private void debugMessage(String str) {
	if (KjcOptions.debug) {
	    for (int i=0; i<indent; i++) { System.err.print(" "); }
	    System.err.println(str);
	}
    }

    protected int get(int tileLimit, int nextToJoiner) {
	// otherwise, compute it
	return get(0, A.length-1, 0, A[0][0].length-1, tileLimit, nextToJoiner);
    }

    private static int indent=0;
    protected int get(int x1, int x2, int y1, int y2, int tileLimit, int nextToJoiner) {
	//indent++;
	//String callStr = cont.getName() + ".get(" + x1 + ", " + x2 + ", " + y1 + ", " + y2 + ")[" + tileLimit + "][" + nextToJoiner +"]";
	//debugMessage("calling " + callStr); 

	Utils.assert(x1<maxWidth[y1][y2], "x1=" + x1 + " <= maxWidth[y1][y2]= " + maxWidth[y1][y2] + " with x2=" + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);
	Utils.assert(x1<=x2, "x1=" + x1 + " > x2= " + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);

	// if we've exceeded the width of this node, then trim down to actual width
	if (x2>maxWidth[y1][y2]-1) {
	    x2 = maxWidth[y1][y2]-1;
	}

	// if we've memoized the value before, return it
	if (A[x1][x2][y1][y2][tileLimit][nextToJoiner]>0) {
	    /*
	      System.err.println("Found memoized A[" + child1 + "][" + child2 + "][" + tileLimit + "] = " + 
	      A[child1][child2][tileLimit] + " for " + cont.getName());
	    */
	    return A[x1][x2][y1][y2][tileLimit][nextToJoiner];
	}

	// if we are down to one child, then descend into child
	if (x1==x2 && y1==y2) {
	    int childCost = childConfig(x1, y1).get(tileLimit, nextToJoiner); 
	    A[x1][x2][y1][y2][tileLimit][nextToJoiner] = childCost;
	    //System.err.println("Returning " + childCost + " from descent into child.");
	    return childCost;
	}

	// otherwise, if <tileLimit> is 1, then just sum the work
	// of our components
	if (tileLimit==1) {
	    /*
	    int sum=0;
	    for (int x=y1; x<=y2; x++) {
		for (int y=x1; y<width[x]; y++) {
		    sum += get(y, y, x, x, 1, 0);
		}
	    }
	    */
	    int sum = get(x1, x1, y1, y1, tileLimit, nextToJoiner);
	    sum += (x1<x2 && x1+1<width[y1]) ? get( x1+1, x2, y1, y1, tileLimit, nextToJoiner) : 0;
	    sum += (y1<y2 && x1<width[y1+1]) ? get(x1, x1, y1+1, y2, tileLimit, nextToJoiner) : 0;
	    sum += (x1<x2 && y1<y2 && x1+1<width[y1+1]) ? get(x1+1, x2, y1+1, y2, tileLimit, nextToJoiner) : 0;
	    // since we went to down to one child, the cost is the
	    // same whether or not there is a joiner, so record both
	    // ways.
	    A[x1][x2][y1][y2][tileLimit][0] = sum;
	    A[x1][x2][y1][y2][tileLimit][1] = sum;
	    //System.err.println("Returning sum " + sum + " from fusion.");
	    return sum;
	}

	// otherwise, try making a vertical cut...
	// see if we can do a vertical cut -- first, that there
	// are two streams to cut between
	boolean tryVertical = x1<x2 && sameWidth[y1][y2];

	// then, if we're starting on a pipeline, and have more than
	// one row, and this is our first vertical cut, that we can
	// remove the synchronization between y1 and y2
	boolean firstVertCut = x1==0 && x2==width[y1]-1;
	if (tryVertical && y1<y2) {
	    if (cont instanceof SIRPipeline && firstVertCut) {
		// make a copy of pipeline, to see if we can remove
		// the sync.
		SIRPipeline copy = new SIRPipeline(cont.getParent(),cont.getIdent()+"_copy");
		for (int i=y1; i<=y2; i++) { copy.add(((SIRPipeline)cont).get(i)); }
		// now remove synchronization in <copy>.
		RefactorSplitJoin.removeMatchingSyncPoints(copy);
		// undo effects of adding to someone else
		cont.reclaimChildren();
		// now if we only have one splitjoin left as the
		// child, sync removal was successful, and we can do a
		// cut
		tryVertical = (copy.size()==1);
	    }
	}

	// try a vertical cut if possible.  A vertical cut will
	// necessitate a joiner at this node, if we don't already have
	// one.  This has two consequences: 1) the nextToJoiner
	// argument for children will be true (1), and 2) A tile is
	// used by the joiner.  We represent #2 by tilesAvail, the
	// number of tailes available after the joiner is taken.
	int tilesAvail = tileLimit - (1-nextToJoiner);
	int min = Integer.MAX_VALUE;
	if (tryVertical) {
	    for (int xPivot=x1; xPivot<x2; xPivot++) {
		for (int tPivot=1; tPivot<tilesAvail; tPivot++) {
		    int cost = Math.max(getWithFusionOverhead(x1, xPivot, y1, y2, tPivot, 1, tilesAvail),
					getWithFusionOverhead(xPivot+1, x2, y1, y2, tilesAvail-tPivot, 1, tilesAvail));
		    if (cost < min) {
			//System.err.println("possible vertical cut at x=" + xPivot + " from y=" + y1 + " to y=" + y2 + " in " + cont.getName());
			min = cost;
		    }
		}
	    }
	}

	// try making horizontal cut (for splitjoin, pipeline,
	// feedbackloop).  In this case, we keep whatever joiner we
	// have for the bottom piece of the cut, but the top piece
	// will need to make its own joiner.  The arguments are thus
	// false (0) for the top, and true (1) for the bottom.
	for (int yPivot=y1; yPivot<y2; yPivot++) {
	    for (int tPivot=1; tPivot<tileLimit; tPivot++) {
		int cost = Math.max(getWithFusionOverhead(x1, x2, y1, yPivot, tPivot, 0, tileLimit),
				    getWithFusionOverhead(x1, x2, yPivot+1, y2, tileLimit-tPivot, nextToJoiner, tileLimit));
		if (cost < min) {
		    //System.err.println("possible horizontal cut at y=" + yPivot + " from x=" + x1 + " to x=" + x2 + " in " + cont.getName());
		    min = cost;
		}
	    }
	}
	
	A[x1][x2][y1][y2][tileLimit][nextToJoiner] = min;
	return min;
    }

    /**
     * <tileLimit> is number of tiles for this partition of children.
     * <tilesAvail> is number of tiles that were available in parent.
     */
    private int getWithFusionOverhead(int x1, int x2, int y1, int y2, int tileLimit, int nextToJoiner, int tilesAvail) {
	// get cost
	int cost = get(x1, x2, y1, y2, tileLimit, nextToJoiner);
	int overhead = 0;
	// add rough estimate of overhead for horizontal fusion.  Do
	// this at the toplevel node--where we had more tiles before,
	// but only one afterwards.
	if (tileLimit==1 && tilesAvail>1 && x1<x2) {
	    // for filters, add cost estimate according to their
	    // rates; otherwise, add generic cost estimate...
	    // do input filters
	    for (int i=x1; i<=Math.min(x2, width[y1]-1); i++) {
		DPConfig config = childConfig(i,y1);
		if (config instanceof DPConfigFilter) {
		    // add input rate
		    SIRFilter filter = (SIRFilter)config.getStream();
		    overhead += (filter.getPopInt() * 
				 partitioner.getWorkEstimate().getReps(filter) * 
				 DynamicProgPartitioner.HORIZONTAL_FILTER_OVERHEAD_FACTOR);
		} else {
		    // add generic rate
		    overhead += DynamicProgPartitioner.HORIZONTAL_CONTAINER_OVERHEAD;
		}
	    }
	    // do output filters
	    for (int i=x1; i<=Math.min(x2, width[y2]-1); i++) {
		DPConfig config = childConfig(i,y2);
		if (config instanceof DPConfigFilter) {
		    // add input rate
		    SIRFilter filter = (SIRFilter)config.getStream();
		    overhead += filter.getPushInt() * 
			partitioner.getWorkEstimate().getReps(filter) * 
			DynamicProgPartitioner.HORIZONTAL_FILTER_OVERHEAD_FACTOR;
		} else {
		    // add generic rate
		    overhead += DynamicProgPartitioner.HORIZONTAL_CONTAINER_OVERHEAD;
		}
	    }
	}
	/*
	if (overhead>0) {
	    System.err.println("For " + cont.getName() + "[" + x1 + "][" + x2 + "][" + y1 + "][" + y2 + "], cost=" + cost + " and overhead=" + overhead);
	}
	*/
	return cost + overhead;
    }

    /**
     * Traceback function.
     */
    public StreamTransform traceback(LinkedList partitions, PartitionRecord curPartition, int tileLimit, int nextToJoiner) {
	StreamTransform st = traceback(partitions, curPartition, 0, A.length-1, 0, A[0][0].length-1, tileLimit, nextToJoiner);
	// if the whole container is assigned to one tile, record it
	// as such.
	if (tileLimit==1) {
	    curPartition.add(cont);
	} 
	return st;
    }
	
    /**
     * Traceback helper function.
     */
    protected StreamTransform traceback(LinkedList partitions, PartitionRecord curPartition,
					int x1, int x2, int y1, int y2, int tileLimit, int nextToJoiner) {
	indent++;
	String callStr = cont.getName() + ".traceback(" + x1 + ", " + x2 + ", " + y1 + ", " + y2 + ")[" + tileLimit + "][" + nextToJoiner +"]";
	debugMessage("calling " + callStr); 

	Utils.assert(x1<maxWidth[y1][y2], "x1=" + x1 + " <= maxWidth[y1][y2]= " + maxWidth[y1][y2] + " with x2=" + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);
	Utils.assert(x1<=x2, "x1=" + x1 + " > x2= " + x2 + " with y1= " + y1 + " and y2=" + y2 + " in " + cont);
	// if we've exceeded the width of this node, then trim down to actual width
	if (x2>maxWidth[y1][y2]-1) {
	    x2 = maxWidth[y1][y2]-1;
	}

	// if we're down to one node, then descend into it
	if (x1==x2 && y1==y2) {
	    StreamTransform child = childConfig(x1, y1).traceback(partitions, curPartition, tileLimit, nextToJoiner);
	    // if this config container only has one child, then we
	    // should wrap this in an identity so that we don't apply
	    // it to ourself
	    if (A.length==1 && A[0][0].length==1) {
		StreamTransform result = new IdentityTransform();
		result.addSucc(child);
		indent--;
		return result.reduce();
	    } else {
		indent--;
		return child.reduce();
	    }
	}
	
	// if we only have one tile left, return fusion transform with
	// children fused first
	if (tileLimit==1) {
	    FusionTransform result = new FusionTransform();
	    result.addPartition(0);
	    if (y1<y2) {
		// if there are vertical streams, fuse them first
		result.addPartition(1+y2-y1);
		for (int y=y1; y<=y2; y++) {
		    result.addPred(traceback(partitions, curPartition, x1, x2, y, y, tileLimit, nextToJoiner));
		}
	    } else {
		// otherwise, fuse the horizontal streams
		result.addPartition(1+x2-x1);
		for (int x=x1; x<=x2; x++) {
		    result.addPred(traceback(partitions, curPartition, x, x, y1, y2, tileLimit, nextToJoiner));
		}
	    }
	    indent--;
	    return result.reduce();
	}

	// see if we can do a vertical cut -- first, that there
	// are two streams to cut between
	boolean tryVertical = x1<x2 && sameWidth[y1][y2];
	
	// then, if we're starting on a pipeline, and have more than
	// one row, and this is our first vertical cut, that we can
	// remove the synchronization between y1 and y2
	boolean firstVertCut = x1==0 && x2==width[y1]-1;
	if (tryVertical && y1<y2) {
	    if (cont instanceof SIRPipeline && firstVertCut) {
		// make a copy of pipeline, to see if we can remove
		// the sync.
		SIRPipeline copy = new SIRPipeline(cont.getParent(),cont.getIdent()+"_copy");
		for (int i=y1; i<=y2; i++) { copy.add(((SIRPipeline)cont).get(i)); }
		// now remove synchronization in <copy>.
		RefactorSplitJoin.removeMatchingSyncPoints(copy);
		// undo effects of adding to someone else
		cont.reclaimChildren();
		// now if we only have one splitjoin left as the
		// child, sync removal was successful, and we can do a
		// cut
		tryVertical = (copy.size()==1);
	    }
	}

	if (tryVertical) {
	    // otherwise, see if we made a vertical cut (breaking into
	    // left/right pieces).  As with get procedure, pass
	    // nextToJoiner as true and adjust tileLimit around the call.
	    int tilesAvail = tileLimit - (1-nextToJoiner);
	    for (int xPivot=x1; xPivot<x2; xPivot++) {
		for (int tPivot=1; tPivot<tilesAvail; tPivot++) {
		    int cost = Math.max(getWithFusionOverhead(x1, xPivot, y1, y2, tPivot, 1, tilesAvail),
					getWithFusionOverhead(xPivot+1, x2, y1, y2, tilesAvail-tPivot, 1, tilesAvail));
		    if (cost==A[x1][x2][y1][y2][tileLimit][nextToJoiner]) {
			// there's a division at this <xPivot>.  We'll
			// return a vertical cut
			//System.err.println("tracing vertical cut at x=" + xPivot + " from y=" + y1 + " to y=" + y2 + " in " + cont.getName());
			StreamTransform result = new VerticalCutTransform(xPivot-x1);
			// recurse left and right, adding transforms as post-ops
			result.addSucc(traceback(partitions, curPartition, x1, xPivot, y1, y2, tPivot, 1));
			// mark that we have a partition here
			curPartition = new PartitionRecord();
			partitions.add(curPartition);
			result.addSucc(traceback(partitions, curPartition, xPivot+1, x2, y1, y2, tilesAvail-tPivot, 1));
			/*
			if (nextToJoiner==0) {
			    // if we have to add a joiner, then add it to the partition record
			    curPartition = new PartitionRecord();
			    if (cont instanceof SIRSplitJoin) {
				curPartition.add(((SIRSplitJoin)cont).getJoiner(), 0);
			    } else {
				curPartition.add(((SIRSplitJoin)cont.get(cont.size()-1)).getJoiner(), 0);
			    }
			    partitions.add(curPartition);
			}
			*/

			// Here we have a pipeline.  If y1==y2 then we
			// don't need the sync removal, but we DO need
			// an identity to unwrap the pipeline.  If
			// y1<y2, need sync removal.
			if (y1<y2) {
			    StreamTransform newResult = new RemoveSyncTransform();
			    newResult.addSucc(result);
			    result = newResult;
			} else {
			    StreamTransform newResult = new IdentityTransform();
			    newResult.addSucc(result);
			    result = newResult;
			}

			// all done
			indent--;
			return result.reduce();
		    }
		}
	    }
	}

	// otherwise, see if we made a horizontal cut (breaking into
	// top/bottom pieces).  Pass nextToJoiner to bottom child
	// since it will share our joiner if we have one, but 0 to top
	// child since it will need its own.
	for (int yPivot=y1; yPivot<y2; yPivot++) {
	    for (int tPivot=1; tPivot<tileLimit; tPivot++) {
		int cost = Math.max(getWithFusionOverhead(x1, x2, y1, yPivot, tPivot, 0, tileLimit),
				    getWithFusionOverhead(x1, x2, yPivot+1, y2, tileLimit-tPivot, nextToJoiner, tileLimit));
		if (cost==A[x1][x2][y1][y2][tileLimit][nextToJoiner]) {
		    // there's a division at this <yPivot>.  We'll
		    // return a horizontal cut.
		    //System.err.println("tracing horizontal cut at y=" + yPivot + " from x=" + x1 + " to x=" + x2 + " in " + cont.getName());
		    StreamTransform result = new HorizontalCutTransform(yPivot-y1);
		    // recurse left and right, adding transforms as post-ops
		    result.addSucc(traceback(partitions, curPartition, x1, x2, y1, yPivot, tPivot, 0));
		    // mark that we have a partition here
		    curPartition = new PartitionRecord();
		    partitions.add(curPartition);
		    result.addSucc(traceback(partitions, curPartition, x1, x2, yPivot+1, y2, tileLimit-tPivot, nextToJoiner));
		    // all done
		    indent--;
		    return result.reduce();
		}
	    }
	}
	
	// if we make it this far, then we didn't find our traceback
	Utils.fail("Didn't find traceback.");
	return null;
    }

    /**
     * Prints the array of memoized values of this.
     */
    public void printArray() {
	String msg = "Printing array for " + getStream().getIdent() + " --------------------------";
	System.err.println(msg);
	for (int i1=0; i1<A.length; i1++) {
	    for (int i2=0; i2<A[0].length; i2++) {
		for (int i3=0; i3<A[0][0].length; i3++) {
		    for (int i4=0; i4<A[0][0][0].length; i4++) {
			System.err.println();
			for (int i5=0; i5<A[0][0][0][0].length; i5++) {
			    for (int i6=0; i6<2; i6++) {
				System.err.println(getStream().getIdent() + "[" + i1 + "][" + i2 + "][" + i3 + "][" + i4 + "][" + 
						   i5 + "][" + i6 + "] = " + A[i1][i2][i3][i4][i5][i6]);
			    }
			}
		    }
		}
	    }
	}
	for (int i=0; i<msg.length(); i++) {
	    System.err.print("-");
	}
	System.err.println();
	}

    /**
     * Returns config for child at index <x, y>
     */
    protected abstract DPConfig childConfig(int x, int y);
}
