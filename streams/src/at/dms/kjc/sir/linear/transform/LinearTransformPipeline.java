package at.dms.kjc.sir.linear.transform;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import at.dms.kjc.sir.linear.*;

/**
 * Represents a pipeline combination transform. Combines two filter that
 * come one after another in a pipeline into a single filter that does
 * the same work. This combination might require each of the individual
 * filters to be expanded by some factor, and then a matrix multiplication
 * can be performed.<p>
 *
 * $Id: LinearTransformPipeline.java,v 1.8 2002-12-10 20:07:09 aalamb Exp $
 **/
public class LinearTransformPipeline extends LinearTransform {
    List repList;
    
    private LinearTransformPipeline(List l) {
	// assert that the list has more than one element in it.
	if (l.size() < 2) {
	    throw new IllegalArgumentException("Representation list has fewer than two elements: " +
					       l.size());
	}
	// assert that all arguments are LinearFilterRepresentations
	Iterator iter = l.iterator();
	while(iter.hasNext()) {
	    if (!(iter.next() instanceof LinearFilterRepresentation)) {
		throw new IllegalArgumentException("non LFR in list passed to linear transform pipeline");
	    }
	}
	this.repList = new LinkedList(l);
    }

    public LinearFilterRepresentation transform() throws NoTransformPossibleException {
	// we know that our rep list has at least two children in it.
	// start running down the rep list transforming things
	LinearFilterRepresentation rep1; // the current "upstream" filter
	LinearFilterRepresentation rep2; // the current "downstream" filter

	Iterator repIter = this.repList.iterator();
	
	rep1 = (LinearFilterRepresentation)repIter.next();
	// iterate over all of the represenations
	while(repIter.hasNext()) {
	    rep2 = (LinearFilterRepresentation)repIter.next();
	    // pull out peek, pop, and push rates so the following code looks as much like
	    // the paper as possible (e=peek, o=pop, u=push)
	    int e1 = rep1.getPeekCount();
	    int o1 = rep1.getPopCount();
	    int u1 = rep1.getPushCount();
	    int e2 = rep2.getPeekCount();
	    int o2 = rep2.getPopCount();
	    int u2 = rep2.getPushCount();

	    // calculate chanPeek and chanPop
	    int chanPop = lcm(u1,o2);
	    int chanPeek = chanPop + (e2-o2);

	    // calculate expanded peek, pop, and push rates
	    int ee1 = divCeiling(chanPeek,u1) * o1 + (e1-o1);
	    int oe1 = chanPop*o1/u1;
	    int ue1 = chanPeek;
	    int ee2 = chanPeek;
	    int oe2 = chanPop;
	    int ue2 = chanPop*u2/o2;

	    LinearPrinter.println("  expansion for upstream filter:(peek,pop,push):" +
				  "(" + e1 + "," + o1 + "," + u1 + ")-->" +
				  "(" + ee1 + "," + oe1 + "," + ue1 + ")");
	    LinearPrinter.println("  expansion for downstream filter:(peek,pop,push):" +
				  "(" + e2 + "," + o2 + "," + u2 + ")-->" +
				  "(" + ee2 + "," + oe2 + "," + ue2 + ")");

	    
	    // now, actually create the expanded reps.
	    LinearFilterRepresentation rep1Expanded = rep1.expand(ee1, oe1, ue1);
	    LinearFilterRepresentation rep2Expanded = rep2.expand(ee2, oe2, ue2);

	    // figure out the matrix and vector of the combined rep
	    // A' = Ae1*Ae2
	    FilterMatrix Aprime = rep1Expanded.getA().times(rep2Expanded.getA());
	    // b' = be1*Ae2 + be2
	    FilterMatrix partialProduct = rep1Expanded.getb().times(rep2Expanded.getA());
	    FilterVector bprime = FilterVector.toVector(partialProduct.plus(rep2Expanded.getb()));
	    	    
	    // now, assemble the overall linear rep.
	    LinearFilterRepresentation combinedRep;
	    combinedRep = new LinearFilterRepresentation(Aprime, bprime, oe1);


	    LinearPrinter.println("Created new linear rep: \n" +
				  " peek=" + combinedRep.getPeekCount() + "\n" +
				  " pop=" + combinedRep.getPopCount() + "\n" +
				  " push=" + combinedRep.getPushCount());
				  

	    // now, we set the combined rep to be rep1 and repeat
	    rep1 = combinedRep;
	}

	// all we have to do is to return the combined rep and we are done
	return rep1;
    }

    

    /**
     * Sets up the calculation of the overall linear representation of
     * a sequential list of linear representations. 
     *
     * If filter one computes y = xA1 + b1 and filter 2 computes y=xA2 + b2 then
     * the overall filter filter1 --> filter 2 will compute
     * y = (xA1 + b1)A2 + b2 = xA1A2 + (b1A2 + b2), which itself can be represented  
     * with the LFR: A = A1A2 and b = (b1A2 + b2).<p>
     *
     * There are a bunch of subtlties involved with computing the overall representation
     * due to various size restrictions (eg the sizes of the matrices have to be
     * compatible. See the pldi-03-linear paper for the gory details.<p> 
     **/
    public static LinearTransform calculate(List linearRepList) {
	// actually, we are going to punt any actual work until the "transform"
	// method is called.
	return new LinearTransformPipeline(linearRepList);
    }
}
