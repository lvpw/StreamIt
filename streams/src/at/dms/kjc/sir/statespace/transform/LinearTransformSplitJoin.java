package at.dms.kjc.sir.statespace.transform;

import java.util.Iterator;
import java.util.List;

import at.dms.kjc.sir.statespace.FilterMatrix;
import at.dms.kjc.sir.statespace.FilterVector;
import at.dms.kjc.sir.statespace.LinearFilterRepresentation;
import at.dms.kjc.sir.statespace.LinearPrinter;

/**
 * Contains the code for merging all the filters from a split join
 * into a single monolithic matrix. See the pldi-03-linear
 * <a href="http://cag.lcs.mit.edu/commit/papers/03/pldi-linear.pdf">
 * paper</a> or Andrew's
 * <a href="http://cag.lcs.mit.edu/commit/papers/03/aalamb-meng-thesis.pdf">
 * thesis</a> for more information.<br>
 *
 * $Id: LinearTransformSplitJoin.java,v 1.13 2006-09-25 13:54:46 dimock Exp $
 **/
public class LinearTransformSplitJoin extends LinearTransform{
    LinearFilterRepresentation[] linearRepresentations;
    /** filterExpansionFactors[i] contains the factors by which to expand filter i. **/
    ExpansionRule[] filterExpansionFactors;
    /** the weights of the round robin joiner. **/
    int[] roundRobinJoinerWeights;
    /** joinRep is the (integer) factor by which we need to
        expand all of the weights in the roundrobin joiner. **/ 
    int joinRep;
    
    /**
     * Create one of these transformations with the appropriate pieces.
     * Doesn't copy the arrays, just copies the pointers.
     **/
    private LinearTransformSplitJoin(LinearFilterRepresentation[] lfr,
                                     ExpansionRule[] expandFact,
                                     int[] rrWeights) {
        this.linearRepresentations = lfr;
        this.filterExpansionFactors = expandFact;
        this.roundRobinJoinerWeights = rrWeights;
    }



    /**
     * Implement the actual transformation for a duplicate splitter splitjoin construct.
     * This involves expanding all of the
     * representations by the specified factors and then copying the columns
     * of the expanded matricies into a new linear transform in the correct
     * order.
     **/
    @Override
	public LinearFilterRepresentation transform() throws NoTransformPossibleException {
    
        int filterCount = linearRepresentations.length;
        LinearPrinter.println(" preparing to combine splitjoin of " +
                              filterCount +
                              " filters");
        LinearPrinter.println(" Filter (expansion) factors:");
        for (int i=0; i<filterCount; i++) {
            LinearPrinter.println("   " +
                                  "(" + this.filterExpansionFactors[i] + ")");
        }
    
        // do the expansion of each of the linear reps.
        LinearFilterRepresentation[] expandedReps = new LinearFilterRepresentation[filterCount];
        int totalOutputs = 0;
        int totalInputs = this.filterExpansionFactors[0].pop;
        int storedInputsVal = this.filterExpansionFactors[0].storedInputs;
        int totalStates = storedInputsVal;
        int currInputsVal;
        int factor;
        LinearFilterRepresentation tempRep;

        for (int i=0; i<filterCount; i++) {
            LinearPrinter.println("  expanding filter with " + this.filterExpansionFactors[i]);
            factor = this.filterExpansionFactors[i].pop / this.linearRepresentations[i].getPopCount(); 
            tempRep = this.linearRepresentations[i].expand(factor);
            currInputsVal = tempRep.getStoredInputCount();

            if(storedInputsVal > currInputsVal)
                expandedReps[i] = tempRep.changeStoredInputs(storedInputsVal);
            else
                expandedReps[i] = tempRep;

            totalOutputs += expandedReps[i].getPushCount();

            LinearPrinter.println("i,states: " + i + " " + expandedReps[i].getStateCount());

            totalStates += expandedReps[i].getStateCount() - storedInputsVal;
        }

        // figure how how many columns the "stride" is (eg the sum of the weights)
        int strideLength = 0;
        for (int i=0; i<filterCount; i++) {
            strideLength += this.roundRobinJoinerWeights[i];
        }

        // now, create new matrices that have the appropriate size:

        FilterMatrix expandedA = new FilterMatrix(totalStates, totalStates);
        FilterMatrix expandedB = new FilterMatrix(totalStates, totalInputs);
        FilterMatrix expandedC = new FilterMatrix(totalOutputs, totalStates);
        FilterMatrix expandedD = new FilterMatrix(totalOutputs, totalInputs);
        FilterVector expandedInit = new FilterVector(totalStates);

        FilterMatrix expandedPreA, expandedPreB;
        expandedPreA = null;
        expandedPreB = null;

        boolean overallPreNeeded = storedInputsVal > 0;
 

        if(overallPreNeeded) {
            expandedPreA = new FilterMatrix(totalStates, totalStates);
            expandedPreB = new FilterMatrix(totalStates, storedInputsVal);

            expandedPreB.copyRowsAt(0,FilterMatrix.getIdentity(storedInputsVal),0,storedInputsVal);
    
            expandedA.copyRowsAndColsAt(0,0,expandedReps[0].getA(),0,0,storedInputsVal,storedInputsVal);
            expandedB.copyRowsAt(0,expandedReps[0].getB(),0,storedInputsVal);

        }

        int startOffset = 0;
        int stateOffset = storedInputsVal;
        for (int i=0; i<filterCount; i++) {

            int currStateValue = expandedReps[i].getStateCount() - storedInputsVal;

            // put the filter's state update matrices in the expanded state update matrices

            expandedInit.copyColumnsAt(stateOffset,expandedReps[i].getInit(),storedInputsVal,currStateValue);

            if(storedInputsVal > 0)
                expandedA.copyRowsAndColsAt(stateOffset,0,expandedReps[i].getA(),storedInputsVal,0,currStateValue,storedInputsVal);

            expandedA.copyRowsAndColsAt(stateOffset,stateOffset,expandedReps[i].getA(),storedInputsVal,storedInputsVal,currStateValue,currStateValue);

            expandedB.copyRowsAt(stateOffset,expandedReps[i].getB(),storedInputsVal,currStateValue);

            if(overallPreNeeded) {
                if(expandedReps[i].preworkNeeded()) {
                    expandedPreA.copyRowsAndColsAt(stateOffset,stateOffset,expandedReps[i].getPreWorkA(),storedInputsVal,storedInputsVal,currStateValue,currStateValue);
                    expandedPreB.copyRowsAt(stateOffset,expandedReps[i].getPreWorkB(),storedInputsVal,currStateValue);
                }
            }

            // now, copy the rows of the matrices into the expanded versions
            // for each expanded matrix, copy joinWeight[i] rows into the new matrix
 
            // figure out how many groups of joinWeight rows that we have
            int numGroups = expandedReps[i].getPushCount() / this.roundRobinJoinerWeights[i]; // total # of groups
            LinearPrinter.println("  number of groups for filter " + i + " is " + numGroups);
            // copy the groups from left (in groups of this.roundRobinJoinerWeights) into the new matrix
            for (int j=0; j<numGroups; j++) {
                LinearPrinter.println("  doing group : " + j + " of filter " + i);
                LinearPrinter.println("  combination weight: " + this.roundRobinJoinerWeights[i]);

                // figure out offset into expanded A to copy the rows

                int currentOffset = startOffset + j*strideLength;

                // the offset into the current source matrix is (size-j-1)*joinWeights[i]
                // the number of rows that we are copying is combination weights[i]

                if(storedInputsVal > 0)
                    expandedC.copyRowsAndColsAt(currentOffset,0,expandedReps[i].getC(),j*this.roundRobinJoinerWeights[i],0,this.roundRobinJoinerWeights[i],storedInputsVal);

                expandedC.copyRowsAndColsAt(currentOffset, stateOffset, expandedReps[i].getC(),
                                            j*this.roundRobinJoinerWeights[i], storedInputsVal,
                                            this.roundRobinJoinerWeights[i], currStateValue);


                expandedD.copyRowsAt(currentOffset, expandedReps[i].getD(),
                                     j*this.roundRobinJoinerWeights[i],
                                     this.roundRobinJoinerWeights[i]);

                        
            }
            // update start of the offset for the next expanded rep. 
            startOffset += this.roundRobinJoinerWeights[i];
            stateOffset += currStateValue;
        }

        // calculate what the new pop rate is (it needs to the the same for all expanded filters)
        // if it is not the same, then we have a problem (specifically, this splitjoin is not
        // schedulable....)
        int newPopCount = expandedReps[0].getPopCount();
        for (int i=0; i<filterCount; i++) {
            if (newPopCount != expandedReps[i].getPopCount()) {
                throw new RuntimeException("Inconsistency -- pop counts are not all the same");
            }
        }

        // now, return a new LinearRepresentation that represents the transformed
        // splitjoin with the new matrices, along with the new init vector and peek count.

        if(overallPreNeeded)
            return new LinearFilterRepresentation(expandedA,expandedB,expandedC,expandedD,expandedPreA,expandedPreB,storedInputsVal,expandedInit);
        else
            return new LinearFilterRepresentation(expandedA,expandedB,expandedC,expandedD,storedInputsVal,expandedInit);
    }
    

    /**
     * Utility method.
     * Parses a List of LinearFilter representations into an array of
     * of LinearFilterRepresentations.
     **/
    public static LinearFilterRepresentation[] parseRepList(List<LinearFilterRepresentation> representationList) {
        LinearFilterRepresentation[] filterReps;
        filterReps = new LinearFilterRepresentation[representationList.size()];

        // for each rep, stuff it into the array
        Iterator<LinearFilterRepresentation> repIter = representationList.iterator();
        int currentIndex = 0;
        while(repIter.hasNext()) {
            LinearFilterRepresentation currentRep = repIter.next();
            filterReps[currentIndex] = currentRep;
            currentIndex++;
        }
        return filterReps;
    }

    
    /**
     * Calculate the necessary information to combine the splitjoin when the splitter is
     * a duplicate. See thesis (http://cag.lcs.mit.edu/commit/papers/03/pldi-linear.pdf)
     * for an explanation of what is going on and why the
     * expansion factors are chosen the way that they are.<br>
     *
     * The input is a List of LinearFilterRepresentations and a matching array of joiner
     * weights. Note that this method merely calls the other calculateDuplicate so that
     * I can reuse code in calculateRoundRobin...
     **/
    public static LinearTransform calculateDuplicate(List<LinearFilterRepresentation> representationList,
                                                     int[] joinerWeights) {
        LinearPrinter.println(" calculating splitjoin transform with duplicate splitter.");
        if (representationList.size() != joinerWeights.length) {
            throw new IllegalArgumentException("different numbers of reps and weights " +
                                               "while transforming splitjoin");
        }

        // calculate the rep list from the passed in list
        LinearFilterRepresentation[] filterReps = parseRepList(representationList);
        return calculateDuplicate(filterReps, joinerWeights);
    }

    /**
     * Does the actual work of calculating the
     * necessary expansion factors for filters and the joiner.
     * Basiclly, it takes as input an array of filter reps and an array of joiner weights
     * and (if all checks pass) calculates the weights necessary to expand each filter
     * by and the factor necessary to expand the roundrobin joiner by. It then returns a
     * LinearTransformSplitjoin which has that information embedded in it.
     **/
    private static LinearTransform calculateDuplicate(LinearFilterRepresentation[] filterReps,
                                                      int[] joinerWeights) {
        int filterCount = filterReps.length;

        // calculate the expansion factors needed to match the peek rates of the filters
        // to do this, we calculate the lcm (lcm ( all push(i) and weight(i))) = x
        // Each filter needs to be expanded by a factor fact(i) = x/push(i);
        // overall, the joiner needs to be expanded by a factor x/weight(i) which needs to be
        // the same for all i.
    
        // keep track of the min number of times that the joiner needs to fire for
        // each filter
        int joinerFirings[] = new int[filterCount];
        int currentJoinerExpansionFactor = 0;
        for (int i=0; i<filterCount; i++) {
            int currentLcm = lcm(filterReps[i].getPushCount(),
                                 joinerWeights[i]);
            joinerFirings[i] = currentLcm / joinerWeights[i];
            LinearPrinter.println("  calculating lcm(pushrate,weight)=" +
                                  "lcm(" + filterReps[i].getPushCount() + "," + joinerWeights[i] + ")" +
                                  " for filter " +
                                  i + ":" + currentLcm +
                                  " ==> " + joinerFirings[i] + " firings in the steady state");
        }
        // now, calculate the lcm of all the joiner expansion factors     
        int joinRep = lcm(joinerFirings);

        // the overall number of repetitions for each filter
        int[] overallFilterFirings = new int[filterCount];
    
        LinearPrinter.println("  overall joiner expansion factor: " + joinRep);
        for (int i=0; i<filterCount; i++) {
            LinearPrinter.println("  expand fact: " + joinRep +
                                  " weight: " + joinerWeights[i] +
                                  "#rows: " + filterReps[i].getPushCount());
            overallFilterFirings[i] =  (joinerWeights[i] * joinRep) / filterReps[i].getPushCount();
            LinearPrinter.println("  overall filter rep for " + i + " is " + overallFilterFirings[i]); 
        }

        // calcluate the maximum stored inputs (the max of any of the expanded sub filters)
        int maxStoredInputs = getMaxStoredInputs(filterReps);
    
        // now, calculate the peek, pop and push rates of the individual filters.
        ExpansionRule filterExpansions[] = new ExpansionRule[filterCount];
        for (int i=0; i<filterCount; i++) {
            filterExpansions[i] = new ExpansionRule(maxStoredInputs,
                                                    filterReps[i].getPopCount() * overallFilterFirings[i],
                                                    filterReps[i].getPushCount() * overallFilterFirings[i]);
        }

        // do some sanity checks:
        // 1. The total data pushed is the same as the total consumed by the joiner
        int totalDataProducedByFilters = 0;
        int totalDataConsumedByJoiner = 0;
        for (int i=0; i<filterCount; i++) {
            totalDataProducedByFilters += filterExpansions[i].push;
        }
        for (int i=0; i<joinerWeights.length; i++) {
            totalDataConsumedByJoiner += joinerWeights[i] * joinRep;
        }
        if (totalDataProducedByFilters != totalDataConsumedByJoiner) {
            return new LinearTransformNull("data produced by filters(" + totalDataProducedByFilters +
                                           ") doesn't equal data consumed by joiner(" +
                                           totalDataConsumedByJoiner +")");
        }
        // 2. The total data poped is the same for all of the filters
        int overallPopCount = filterExpansions[0].pop;
        for (int i=0; i<filterCount; i++) {
            if (filterExpansions[i].pop != overallPopCount) {
                return new LinearTransformNull("Overall pop count doesn't match --> split join is unschedulable");
            }
        }
    
        // finally, if we get to here, we have the expansion factors that we need, and
        // we can pass that information into a new LinearTransform object
        return new LinearTransformSplitJoin(filterReps,
                                            filterExpansions,
                                            joinerWeights);
    }

    /** Calculates the maximum input states **/
    public static int getMaxStoredInputs(LinearFilterRepresentation[] filterReps) {

        int maxVal = -1;
        int currVal, temp;
    
        for (int i=0; i<filterReps.length; i++) {
            currVal = filterReps[i].getStoredInputCount();
            temp = filterReps[i].getPreWorkPopCount();

            LinearPrinter.println(i + " (stored,preworkpop): " + currVal + " " + temp);


            if(temp > currVal)
                currVal = temp;
            if (currVal > maxVal) {maxVal = currVal;}
        }
        return maxVal;
    }

    /** Structure to hold new peek/pop/push rates for linear reps. **/
    static class ExpansionRule {
        int storedInputs;
        int pop;
        int push;
        /** Create a new ExpansionRule. **/
        ExpansionRule(int e, int o, int u) {
            this.storedInputs = e;
            this.pop  = o;
            this.push = u;
        }
        /** Nice human readable string (for debugging...) **/
        @Override
		public String toString() {
            return ("(stored:" + this.storedInputs +
                    ", pop:" + this.pop +
                    ", push:" + this.push +
                    ")");
        }
    }
}
