package at.dms.kjc.sir.linear;

import java.util.*;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.linear.*;
import at.dms.kjc.sir.linear.transform.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.util.SIRPrinter;
import at.dms.kjc.iterator.*;


/**
 * The LinearAnalyzer visits all of the Filter definitions in
 * a StreamIT program. It determines which filters calculate linear
 * functions of their inputs, and for those that do, it keeps a mapping from
 * the filter name to the filter's matrix representation.
 *
 * $Id: LinearAnalyzer.java,v 1.25 2003-04-08 01:58:56 aalamb Exp $
 **/
public class LinearAnalyzer extends EmptyStreamVisitor {
    /** Mapping from streams to linear representations. never would have guessed that, would you? **/
    HashMap streamsToLinearRepresentation;

    /** Set of streams that we've already explored and determined to
     * be non-linear, or that we couldn't convert into a linear
     * form (do to errors or whatever).  **/
    HashSet nonLinearStreams;

    /** Whether or not we should refactor linear children into a
     * separate pipeline, if some of their siblings are not linear.
     */
    boolean refactorLinearChildren;

    // counters to keep track of how many of what type of stream constructs we have seen.
    int filtersSeen        = 0;
    int pipelinesSeen      = 0;
    int splitJoinsSeen     = 0;
    int feedbackLoopsSeen  = 0;

    public LinearAnalyzer(boolean refactorLinearChildren) {
	this.streamsToLinearRepresentation = new HashMap();
	this.nonLinearStreams = new HashSet();
	this.refactorLinearChildren = refactorLinearChildren;
	checkRep();
    }

    ///////// Accessors    
    /** Returns true if the specified filter has a linear representation that we have found. **/
    public boolean hasLinearRepresentation(SIRStream stream) {
	checkRep();
	// just check to see if the hash set has a mapping to something other than null.
	return (this.streamsToLinearRepresentation.get(stream) != null);
    }
    /**
     * returns the mapping from stream to linear representation that we have. Returns
     * null if we do not have a mapping.
     **/
    public LinearFilterRepresentation getLinearRepresentation(SIRStream stream) {
	checkRep();
	return (LinearFilterRepresentation)this.streamsToLinearRepresentation.get(stream);
    }
    
    /** removes the specified SIRStream from the linear represention list. **/
    public void removeLinearRepresentation(SIRStream stream) {
	checkRep();
	if (!this.hasLinearRepresentation(stream)) {
	    throw new IllegalArgumentException("Don't know anything about " + stream);
	}
	this.streamsToLinearRepresentation.remove(stream);
    }
    /** adds a mapping from sir stream to linear filter rep. **/
    public void addLinearRepresentation(SIRStream key, LinearFilterRepresentation rep) {
	checkRep();
	if ((key == null) || (rep == null)) {
	    throw new IllegalArgumentException("null in add linear rep");
	}
	if (this.streamsToLinearRepresentation.containsKey(key)) {
	    throw new IllegalArgumentException("tried to add key mapping.");
	}
	if (this.streamsToLinearRepresentation.containsValue(rep)) {
	    throw new IllegalArgumentException("tried to add rep second time.");
	}
	this.streamsToLinearRepresentation.put(key,rep);
	checkRep();
    }
    /** gets an iterator over all of the linear representations that this LinearAnalyzer knows about **/
    public Iterator getFilterIterator() {
	return this.streamsToLinearRepresentation.keySet().iterator();
    }
    
    /**
     * Main entry point -- searches the passed stream for linear
     * filters and calculates their associated matricies.  Uses a
     * fresh linear analyzer.
     *
     * If the debug flag is set, then we print a lot of debugging information
     *
     * <refactorLinearChildren> indicates whether or not we should
     * refactor linear children into a separate pipeline, if some of
     * their siblings are not linear.
     **/
    public static LinearAnalyzer findLinearFilters(SIRStream str, boolean debug, boolean refactorLinearChildren) {
	return findLinearFilters(str, debug, new LinearAnalyzer(refactorLinearChildren));
    }
    
    /**
     * Same as above, but specifies an existing <lfa> to use instead
     * of creating a fresh one.
     */
    public static LinearAnalyzer findLinearFilters(SIRStream str, boolean debug, LinearAnalyzer lfa) {
	// set up the printer to either print or not depending on the debug flag
	LinearPrinter.setOutput(debug);
	LinearPrinter.println("aal--In linear filter visitor");
	IterFactory.createIter(str).accept(lfa);

	// generate a report and print it.
	LinearPrinter.println(lfa.generateReport());
	
	return lfa;
    }
    
    /**
     * Returns a filter that is a clone of <self> but has all
     * unrolling (and other pre-partitioning processing) done on
     * it.
     */
    private static SIRFilter getUnrolledFilter(SIRFilter self) {
	SIRFilter result;
	if (KjcOptions.unroll>=100000) {
	    // this means that <self> was already unrolled enough
	    result = self;
	} else {
	    result = (SIRFilter)ObjectDeepCloner.deepCopy(self);
	    // set all loops to be unrolled again
	    IterFactory.createIter(result).accept(new EmptyStreamVisitor() {
		    public void preVisitStream(SIRStream self, SIRIterator iter) {
			for (int i=0; i<self.getMethods().length; i++) {
			    self.getMethods()[i].accept(new SLIREmptyVisitor() {
				    public void visitForStatement(JForStatement self, JStatement init, JExpression cond,
								  JStatement incr, JStatement body) {
					self.setUnrolled(false);
				    }
				});
			}
		    }
		});
	    // now do unrolling
	    int origUnroll = KjcOptions.unroll;
	    KjcOptions.unroll = 100000;
	    FieldProp.doPropagate(result);
	    KjcOptions.unroll = origUnroll;
	}
	return result;
    }

    /////////////////////////////////////
    ///////////////////// Visitor methods
    /////////////////////////////////////
    
    /** More or less get a callback for each stram **/
    public void visitFilter(SIRFilter self, SIRFilterIter iter) {
	// short-circuit the case where we've already seen this stream
	// (for dynamic programming partitioner)
	if (streamsToLinearRepresentation.containsKey(self) || nonLinearStreams.contains(self)) {
	    return;
	}
	
	this.filtersSeen++;
	LinearPrinter.println("Visiting " + "(" + self + ")");

	// set up the visitor that will actually collect the data
	int peekRate = extractInteger(self.getPeek());
	int pushRate = extractInteger(self.getPush());
	int popRate  = extractInteger(self.getPop());
	LinearPrinter.println("  Peek rate: " + peekRate);
	LinearPrinter.println("  Push rate: " + pushRate);
	LinearPrinter.println("  Pop rate: "  + popRate);
	// if we have a peek or push rate of zero, this isn't a linear filter that we care about,
	// so only try and visit the filter if both are non-zero
	if ((peekRate == 0) || (pushRate == 0)) {
	    LinearPrinter.println("  " + self.getIdent() + " is source/sink.");
	    nonLinearStreams.add(self);
	    return;
	}

	LinearFilterVisitor theVisitor = new LinearFilterVisitor(self.getIdent(),
								 peekRate, pushRate, popRate);
	
	// if we haven't unrolled this node yet, then do it here, by cloning
	SIRFilter unrolledSelf = getUnrolledFilter(self);

	// pump the visitor through the work function
	// (we might need to send it thought the init function as well so that
	//  we can determine the initial values of fields. However, I think that
	//  field prop is supposed to take care of this.)
	try {
	    unrolledSelf.getWork().accept(theVisitor);
	} catch (NonLinearException e) {
	    LinearPrinter.println("  caught a non-linear exception -- eg the filter is non linear.");
	    theVisitor.setNonLinear(); // throw the flag that says the filter is non linear
	}
	
	// print out the results of pumping the visitor
	// incidentally, this output is parsed by some perl scripts to verify results,
	// so it probably shouldn't be changed.
	if (theVisitor.computesLinearFunction()) {
	    // since printing the matrices takes so long, if debugging is not on,
	    // don't even generate the string.
	    if (LinearPrinter.getOutput()) {
		LinearPrinter.println("Linear filter found: " + self +
				      "\n-->Matrix:\n" + theVisitor.getMatrixRepresentation() +
				      "\n-->Constant Vector:\n" + theVisitor.getConstantVector());
	    }
	    // add a mapping from the filter to its linear form.
	    this.streamsToLinearRepresentation.put(self,
						   theVisitor.getLinearRepresentation());
	} else {
	    nonLinearStreams.add(self);
	}
	// check that we have not violated the rep invariant
	checkRep();
    }


    
    //void preVisitFeedbackLoop(SIRFeedbackLoop self, SIRFeedbackLoopIter iter) {}
    public void postVisitFeedbackLoop(SIRFeedbackLoop self, SIRFeedbackLoopIter iter) {
	// short-circuit the case where we've already seen this stream
	// (for dynamic programming partitioner)
	if (streamsToLinearRepresentation.containsKey(self) || nonLinearStreams.contains(self)) {
	    return;
	}

	this.feedbackLoopsSeen++;
    }


    //void preVisitPipeline(SIRPipeline self, SIRPipelineIter iter) {}
    /**
     * We visit a pipeline after all sub-streams have been visited.
     * We combine any adjacent linear children into their own wrapper
     * pipeline and also determine a linear form for them.
     **/
    public void postVisitPipeline(SIRPipeline self, SIRPipelineIter iter) {
	// short-circuit the case where we've already seen this stream
	// (for dynamic programming partitioner)
	if (streamsToLinearRepresentation.containsKey(self) || nonLinearStreams.contains(self)) {
	    return;
	}

	this.pipelinesSeen++;
	LinearPrinter.println("Visiting pipeline: " + "(" + self + ")");
	
	// This bit just goes and prints out the children of the pipeline (for debugging)
	Iterator kidIter = self.getChildren().iterator();
	LinearPrinter.println("Children: ");
	while(kidIter.hasNext()) {
	    SIRStream currentKid = (SIRStream)kidIter.next();
	    String linearString = (this.hasLinearRepresentation(currentKid)) ? "linear" : "non-linear";
	    LinearPrinter.println("  " + currentKid + "(" + linearString + ")");
	}

	// start going down the list of pipeline children.
	kidIter = self.getChildren().iterator();
	List childrenToWrap = new LinkedList();
	while(kidIter.hasNext()) {
	    // grab the next child. If we have a linear rep, add the kid to the pipeline
	    SIRStream currentKid = (SIRStream)kidIter.next();
	    if (this.hasLinearRepresentation(currentKid)) {
		// add this kid to the current linear pipeline.
		childrenToWrap.add(currentKid);
		LinearPrinter.println(" adding linear child:(" + currentKid + ")");
	    } else {
		// kid was non-linear.  This means that we won't be linear, so mark as such
		nonLinearStreams.add(self);
		// If we're not refactoring linear children, then just quit here
		if (!refactorLinearChildren) {
		    break;
		} else {
		    // Otherwise, replace the children thus far in self with the new pipeline
		    // and update the mapping from filters to LinearFilterRepresentations.
		    LinearPrinter.println(" non-linear child:(" + currentKid + ")");
		    LinearPrinter.println(" wrapping children.");
		    doPipelineAdd(self, childrenToWrap, this.streamsToLinearRepresentation);
		    // reset our list
		    childrenToWrap = new LinkedList();
		}
	    }
	}
	// add the current list of children (to catch the end of the
	// pipeline).  We should do this if we're refactoring, or if
	// all of the children are linear (and we should mark the
	// whole pipeline as linear.)  Note: this doesn't add empty
	// pipelines
	if (refactorLinearChildren || childrenToWrap.size()==self.size()) {
	    doPipelineAdd(self, childrenToWrap, this.streamsToLinearRepresentation);
	}
	// check to make sure that we didn't foobar ourselves.
	checkRep();

	// debugging stuff -- print out the final contents of the pipeline with parents
	kidIter = self.getChildren().iterator();
	LinearPrinter.println(" Contents of (" + self +") post processing:");
	while(kidIter.hasNext()) {
	    SIRStream currentChild = (SIRStream)kidIter.next();
	    LinearPrinter.println("  child:(" + currentChild + ")");

	}
	    
    }

    /**
     * This method does the actual work of replacing a group
     * of adjacent pipeline children with a wrapping pipeline,
     * calculating the overall linear form of that wrapping
     * pipeline, and updating the map from filters to linear representations.
     *
     * Since the parent pointer is automatically set when SIRPipeline.add is called
     * we keep the list of children that we want to wrap in a list instead. If it is
     * a list that is more than one but less than the size of the parent pipeline,
     * we wrap the children in a new wrapper pipeline,
     * find the corresponding combinaed linear form, update the mapping, and return.
     * Otherwise (eg if the list is either 0,1,or all pipeline children)
     * we do nothing.
     **/
    private void doPipelineAdd(SIRPipeline parentPipe,
			       List childrenToWrap,
			       HashMap linearRepMap) {
	
	// this just prints out the children we are passed
	LinearPrinter.println(" new child pipe in doPipelineAdd: ");
	Iterator newKidIter = childrenToWrap.iterator();
	while(newKidIter.hasNext()) {LinearPrinter.println("  " + newKidIter.next());}
	

	SIRPipeline overallPipe;
	
	// if the new child pipe has 0,1, children, we are done
	if (childrenToWrap.size() == 0) {
	    LinearPrinter.println("  (no children)");
	    return;
	} else if (childrenToWrap.size() == 1) {
	    LinearPrinter.println("  (one child)");
	    return;
	} else if (parentPipe.size() == childrenToWrap.size()) {
	    // if the newChild pipeline contains all of the children of
	    // the parent pipe, there is no need to do any replacing
	    // (we would just be adding a pipeline with its only child a pipeline),
	    // we only need to set the overallPipeline to the the parent
	    // pipeline so that we can determine the overall linear rep of the pipeline
	    overallPipe = parentPipe;
	} else {
	    // we are actually going to wrap the children in a pipeline
	    // set up the new overall pipeline (parent gets set when we add it to the
	    // parent pipeline
	    overallPipe = new SIRPipeline("LinearWrapper");
	    overallPipe.setInit(SIRStream.makeEmptyInit()); // set to empty init method
	    // remember the location of the first wrapper pipe's child
	    // in the original parent pipeline (so we can insert the wrapper
	    // pipe at the appropriate place.
	    int newPipeIndex = parentPipe.indexOf((SIRStream)childrenToWrap.get(0));
	    if (newPipeIndex == -1) {throw new RuntimeException("unknown child in wrapper pipe");}
	    // now, remove all of the streams that appear in the new child wrapper pipeline
	    Iterator wrappedChildIter = childrenToWrap.iterator();
	    while(wrappedChildIter.hasNext()) {
		SIRStream removeKid = (SIRStream)wrappedChildIter.next() ;
		parentPipe.remove(removeKid); // remove the child from the parent
		overallPipe.add(removeKid);   // add the child into the overall pipeline.
	    }
	    // now, add the new child pipeline to the parent at the index of first wrapped child
	    parentPipe.add(newPipeIndex,overallPipe);
	}
	
	
	// now, calculate the overall linear rep of the child pipeline
	List repList = getLinearRepList(linearRepMap, childrenToWrap); // list of linear reps
	LinearTransform pipeTransform = LinearTransformPipeline.calculate(repList);
	try {
	    LinearFilterRepresentation newRep = pipeTransform.transform();
	    // add a mapping from child pipe to the new linear form.
	    linearRepMap.put(overallPipe, newRep);
	    // write output for output parsing scripts (check output mode
	    // because printing this takes a loooooong time for big matrices) 
	    if (LinearPrinter.getOutput()) {
		LinearPrinter.println("Linear pipeline found: " + overallPipe +
				      "\n-->Matrix:\n" + newRep.getA() +
				      "\n-->Constant Vector:\n" + newRep.getb());
	    }
	
	} catch (NoTransformPossibleException e) {
	    // otherwise something bad happened in the combination process.
	    LinearPrinter.println(" can't combine transform reps: " + e.getMessage());
	    //throw new RuntimeException("Error combining pipeline!");
	}
	return;
    }

    /**
     * Given kidList, returns a list of the values of kidMap.get(kidList)
     * in the same ovder as the kidList.
     **/
    private static List getLinearRepList(HashMap kidMap, List kidList) {
	List repList = new LinkedList(); 
	Iterator kidIter = kidList.iterator();
	while(kidIter.hasNext()) {
	    repList.add(kidMap.get(kidIter.next()));
	}
	return repList;
    }	

    
    //public void preVisitSplitJoin(SIRSplitJoin self, SIRSplitJoinIter iter) {}

    /**
     * Visits a split join after all of its childern have been visited. If we have
     * linear representations for all the children, then we can try to combine
     * the entre split join construct into a single linear representation.
     **/
    public void postVisitSplitJoin(SIRSplitJoin self, SIRSplitJoinIter iter) {
	// short-circuit the case where we've already seen this stream
	// (for dynamic programming partitioner)
	if (streamsToLinearRepresentation.containsKey(self) || nonLinearStreams.contains(self)) {
	    return;
	}

	this.splitJoinsSeen++;

	SIRSplitter splitter = self.getSplitter();
	SIRJoiner   joiner   = self.getJoiner();
	int[] splitWeights   = splitter.getWeights();
	int[] joinWeights    = joiner.getWeights();
	
	LinearPrinter.println("Visiting SplitJoin (" + self + ")");
	LinearPrinter.println(" splitter: " + splitter.getType() +
			      makeStringFromIntArray(splitWeights));
	LinearPrinter.println(" joiner: " + joiner.getType() + 
			      makeStringFromIntArray(joinWeights));


	// this is just debugging information that gets printed
	Iterator childIter = self.getChildren().iterator();
	boolean nonLinearFlag = false;
	while(childIter.hasNext()) {
	    SIROperator currentChild = (SIROperator)childIter.next();
	    if (currentChild instanceof SIRStream) {
		boolean childLinearity = this.hasLinearRepresentation((SIRStream)currentChild);
		LinearPrinter.println(" child: " + currentChild + "(" +
				      (childLinearity ? "linear" : "non-linear") +
				      ")");
		// so we know if the splitjoin has any non-linear children.
		nonLinearFlag = (nonLinearFlag || (!childLinearity)); 
	    } else {
		LinearPrinter.println(" child: " + currentChild);
	    }
	}

	// if we had any non-linear children, give up.
	if (nonLinearFlag) {
	    LinearPrinter.println(" aborting split join combination  -- non-linear child stream detected.");
	    nonLinearStreams.add(self);
	    return;
	}

	// first things first -- for each child we need a linear rep, so
	// iterate through all children and create a parallel list
	// of linear representations. Remember that the first two elements
	// are the splitter and the joiner so we get rid of them right off the
	// bat.
	List repList = new LinkedList();
	List childList = self.getChildren(); // get copy of the child list 
	childList.remove(0); childList.remove(childList.size()-1); // remove the splitter and the joiner
	childIter = childList.iterator();
	while (childIter.hasNext()) {
	    SIRStream currentChild = (SIRStream)childIter.next();
	    repList.add(this.getLinearRepresentation(currentChild));
	}
	
	// the transform that we will set based on the splitter type.
	LinearTransform sjTransform = null;
	
	// dispatch based on the splitter 
	if (splitter.getType().equals(SIRSplitType.DUPLICATE)) {
	    // process duplicate splitter
	    sjTransform = LinearTransformSplitJoin.calculateDuplicate(repList, joinWeights);
	} else if (splitter.getType().equals(SIRSplitType.ROUND_ROBIN) ||
		   splitter.getType().equals(SIRSplitType.WEIGHTED_RR)) {
	    // first, convert the repList to a list of equivalent reps that would result
	    // from transforming the roundrobin duplicate to a splitjoin
	    repList = convertToDuplicate(repList, splitWeights);
	    // then, process the split join
	    sjTransform = LinearTransformSplitJoin.calculateDuplicate(repList, joinWeights);		
	} else if (splitter.getType() == SIRSplitType.NULL) {
	    LinearPrinter.println(" Ignoring null splitter.");
	} else {
	    LinearPrinter.println(" Ignoring unknown splitter type: " + splitter.getType());
	}

	// if we don't have a transform, we are done and return here
	if (sjTransform == null) {  
	    LinearPrinter.println(" no transform found. stop");
	    nonLinearStreams.add(self);
	    return;
	}

	// do the actual transform
	LinearPrinter.println(" performing transform.");
	LinearFilterRepresentation newRep;
	try {
	    newRep = sjTransform.transform();
	} catch (NoTransformPossibleException e) {
	    LinearPrinter.println(" error performing transform: " + e.getMessage());
	    nonLinearStreams.add(self);
	    return;
	}
	
	LinearPrinter.println(" transform successful.");
	// check for debugging so we don't waste time producing output if not needed
	if (LinearPrinter.getOutput()) {
	    LinearPrinter.println("Linear splitjoin found: " + self +
				  "\n-->Matrix:\n" + newRep.getA() +
				  "\n-->Constant Vector:\n" + newRep.getb());
	}
	// add a mapping from this split join to the new linear representation
	this.streamsToLinearRepresentation.put(self, newRep);
    }
    
    


    /**
     * This procedure modifies the list of LinearFilterRepresentations
     * that are in a SIRSplitJoin with a roundrobin splitter to
     * the representations that would be present in a SIRSplitJoin with
     * a duplicate splitter.
     * The general procedure for doing this is as follows:
     *
     * Wrap each of the children in a (virtual) pipeline that contains
     * a decimator followed by the child. The decimator takes in
     * the sum of the splitter weights and outputs the data that was originally
     * destined for that child. Then we use a pipeline transformation to calculate the
     * overall linear rep of those two combinations.
     **/
    private List convertToDuplicate(List repList, int[] splitterWeights) {
	// check the length of the two lists
	if (splitterWeights.length != repList.size()) {
	    throw new RuntimeException("Splitter weights don't match the number of reps in convertToDuplicate");
	}
	int repCount = repList.size();
	List newList = new LinkedList();

	// figure out the total sum of the splitter weights
	int totalSplitWeight = 0;
	for (int i=0; i<repCount; i++) {
	    totalSplitWeight += splitterWeights[i];
	}

	// for each child filter of the splitjoin
	Iterator childIter = repList.iterator();
	int childPosition = 0;
	while(childIter.hasNext()) {
	    // cast current child appropriately (stupid java)
	    LinearFilterRepresentation currentChildRep;
	    currentChildRep = (LinearFilterRepresentation)childIter.next();
	    // create the appropriate decimator linearRep
	    LinearFilterRepresentation currentDecimatorRep;
	    currentDecimatorRep = makeDecimator(totalSplitWeight, splitterWeights, childPosition);

	    // make a pipelin list (decimator, child) to pass to pipeline transform 
	    List virtualPipeline = new LinkedList();
	    virtualPipeline.add(currentDecimatorRep);
	    virtualPipeline.add(currentChildRep);

	    // create a pipeline transformation and execute it
	    LinearTransform pipeTransform = LinearTransformPipeline.calculate(virtualPipeline);
	    LinearFilterRepresentation equivalentDuplicateRep;
	    try {equivalentDuplicateRep = pipeTransform.transform();
	    } catch (NoTransformPossibleException e) {
		throw new RuntimeException("error with pipeline transformation of decimator and child:" +
					   e.getMessage());
	    }

	    // update the new repList with the transformed rep
	    newList.add(equivalentDuplicateRep);

	    // update child position
	    childPosition++;
	    
	// lather, rise and repeat
	}

	// return the new repList.
	return newList;
    }

    /**
     * Creates a decimator filter representation. A decimator filter inputs all of the
     * data used for a single firing of a round robin splitter (vSum) and it outputs
     * only the data that was originally bound for child childPos (counting from left).
     * This transformation is used to transform a splitjoin with a roundrobin splitter
     * to a splitjoins with a duplicate splitter.
     **/
    private LinearFilterRepresentation makeDecimator(int vTot, int[] splitWeights, int childPos) {
	// make matrix (A starts out all zeros)
	// we want to input vSum items and output as many items as the
	// child would have gotten in the original splitter (eg splitWeights[childPos])
	// which implies the new matrix has vSum rows and splitWeights[childPos] cols

	FilterMatrix newA = new FilterMatrix(vTot, splitWeights[childPos]);
	FilterVector newb = new FilterVector(vTot);

	// determine the number of rows between the bottom and the start of the decimator's ones
	int vSum_k1 = 0;
	for (int i=0; i<=childPos; i++) {
	    vSum_k1 += splitWeights[i];
	}

	// set the splitWeights[childPos] rows of ones
	for (int i=0; i<splitWeights[childPos]; i++) { // row = vTot-vSum_k1, col=i
	    newA.setElement(vTot - vSum_k1 + i, i, ComplexNumber.ONE);
	}

	// make a new decimator rep out of the new A and b
	return new LinearFilterRepresentation(newA, newb, vTot); // pop==peek
    }
    




	

    /** returns a string like "(a[0], a[1], ... a[N-1])". **/
    private String makeStringFromIntArray(int[] arr) {
	// make a nice string with all the weights in it.
	String weightString = "   (";
	for (int i=0; i<arr.length; i++) {
	    weightString += arr[i] + ",";
	}
	weightString = weightString.substring(0, weightString.length()-1) + ")";
	return weightString;
    }
	







	



     ///////////////////////
     // Utility Methods, etc.
     ///////////////////////     

    /**
     * Make a nice report on the number of stream constructs processed
     * and the number of things that we found linear forms for.
     **/
    private String generateReport() {
	String rpt;
	rpt = "Linearity Report\n";
	rpt += "---------------------\n";
	rpt += "Filters:       " + makePctString(getFilterReps(),
						 this.filtersSeen);
	rpt += "Pipelines:     " + makePctString(getPipelineReps(),
						 this.pipelinesSeen);
	rpt += "SplitJoins:    " + makePctString(getSplitJoinReps(),
						 this.splitJoinsSeen);
	rpt += "FeedbackLoops: " + makePctString(getFeedbackLoopReps(),
						 this.feedbackLoopsSeen);
	rpt += "---------------------\n";
	return rpt;
    }    
    /** simple utility for report generation. makes "top/bottom (pct) string **/
    private String makePctString(int top, int bottom) {
	float pct;
	if (bottom == 0) {
	    pct = 0;
	} else {
	    // figure out percent to two decimal places
	    pct = ((float)top)/((float)bottom);
	    pct *= 10000;
	    pct = Math.round(pct);
	    pct /= 100;
	}
	return top + "/" + bottom + " (" + pct + "%)\n";
    }

    /** returns the number of filters that we have linear reps for. **/
    private int getFilterReps() {
	int filterCount = 0;
	Iterator keyIter = this.streamsToLinearRepresentation.keySet().iterator();
	while(keyIter.hasNext()) {
	    if (keyIter.next() instanceof SIRFilter) {filterCount++;}
	}
	return filterCount;
    }
    /** returns the number of pipelines that we have linear reps for. **/
    private int getPipelineReps() {
	int count = 0;
	Iterator keyIter = this.streamsToLinearRepresentation.keySet().iterator();
	while(keyIter.hasNext()) {
	    if (keyIter.next() instanceof SIRPipeline) {count++;}
	}
	return count;
    }
    /** returns the number of splitjoins that we have linear reps for. **/
    private int getSplitJoinReps() {
	int count = 0;
	Iterator keyIter = this.streamsToLinearRepresentation.keySet().iterator();
	while(keyIter.hasNext()) {
	    if (keyIter.next() instanceof SIRSplitJoin) {count++;}
	}
	return count;
    }
    /** returns the number of feedbackloops that we have linear reps for. **/
    private int getFeedbackLoopReps() {
	int count = 0;
	Iterator keyIter = this.streamsToLinearRepresentation.keySet().iterator();
	while(keyIter.hasNext()) {
	    if (keyIter.next() instanceof SIRFeedbackLoop) {count++;}
	}
	return count;
    }


    
    /** extract the actual value from a JExpression that is actually a literal... **/
    private static int extractInteger(JExpression expr) {
	if (expr == null) {throw new RuntimeException("null peek rate");}
	if (!(expr instanceof JIntLiteral)) {throw new RuntimeException("non integer literal peek rate...");}
	JIntLiteral literal = (JIntLiteral)expr;
	return literal.intValue();
    }

    private void checkRep() {
	// make sure that all keys in FiltersToMatricies are strings, and that all
	// values are LinearForms.
	Iterator keyIter = this.streamsToLinearRepresentation.keySet().iterator();
	while(keyIter.hasNext()) {
	    Object o = keyIter.next();
	    if (o == null) {throw new RuntimeException("Null key in LinearAnalyzer.");}
	    if (!(o instanceof SIRStream)) {throw new RuntimeException("Non stream key in LinearAnalyzer");}
	    SIRStream key = (SIRStream)o;
	    Object val = this.streamsToLinearRepresentation.get(key);
	    if (val == null) {throw new RuntimeException("Null value found in LinearAnalyzer");}
	    if (!(val instanceof LinearFilterRepresentation)) {
		throw new RuntimeException("Non LinearFilterRepresentation found in LinearAnalyzer");
	    }
	}
    }
}
