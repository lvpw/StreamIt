package at.dms.kjc.sir.linear;

import java.util.*;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.linear.*;
import at.dms.kjc.iterator.*;
import at.dms.compiler.*;


/**
 * A LinearDirectReplacer replaces the contents of the work functions for
 * linear filters (as determined by the linear filter analyzer) with an appripriate
 * direct implementation (eg a bunch of push statements with the specified
 * combination of input values. <p>
 * Eg a filter that had linear form [1 2 3]+4 would get a work function:
 * <pre>
 * work {
 *   push(3*peek(0) + 2*peek(1) + 1*peek(2) + 4);
 * }
 * </pre>
 *
 * It also can replace splitjoins and pipelines with linear representations
 * with a single filter that computes the same function.
 * <p>
 * $Id: LinearDirectReplacer.java,v 1.3 2003-03-31 20:36:22 thies Exp $
 **/
public class LinearDirectReplacer extends LinearReplacer implements Constants{
    /** the linear analyzier which keeps mappings from filters-->linear representations**/
    LinearAnalyzer linearityInformation;
    /** the cost calculator which guides us in whether or not we should stream constructs with direct implementations. **/
    LinearReplaceCalculator replaceGuide;
    
    private LinearDirectReplacer(LinearAnalyzer lfa, LinearReplaceCalculator costs) {
	if (lfa == null){
	    throw new IllegalArgumentException("Null linear filter analyzer!");
	}
	if (costs == null) {
	    throw new IllegalArgumentException("Null linear replace calculator!");
	}
	this.linearityInformation = lfa;
	this.replaceGuide = costs;
    }

    /** start the process of replacement on str using the Linearity information in lfa. **/
    public static void doReplace(LinearAnalyzer lfa, SIRStream str) {
	// calculate the best way to replace linear components.
	LinearReplaceCalculator replaceCosts = new LinearReplaceCalculator(lfa);
	str.accept(replaceCosts);
	LinearPrinter.println("starting replacement pass. Will replace " + replaceCosts.getDoReplace().keySet().size() + " filters:");
	Iterator keyIter = replaceCosts.getDoReplace().keySet().iterator();
	while(keyIter.hasNext()) {
	    Object key = keyIter.next();
	    LinearPrinter.println(" " + key);
	}
	// make a new replacer with the information contained in the analyzer and the costs
	LinearDirectReplacer replacer = new LinearDirectReplacer(lfa, replaceCosts);
	// pump the replacer through the stream graph.
	IterFactory.createIter(str).accept(replacer);
    }

    /**
     * Visit a pipeline, splitjoin or filter, replacing them with a new filter
     * that directly implements the linear representation. This only
     * occurs if the replace calculator says that this stream should be replaced.
     **/
    public boolean makeReplacement(SIRStream self) {
// 	if (!this.replaceGuide.shouldReplace(self)) {
// 	    LinearPrinter.println(self + ": replacement doesn't decrease cost.");
// 	    LinearPrinter.println(" stop.");
// 	    return;
// 	}
	LinearPrinter.println("Creating linear replacement for " + self);
	SIRContainer parent = self.getParent();
	if (parent == null) {
	    // we are done, this is the top level stream
	    LinearPrinter.println(" aborting, top level stream: " + self);
	    LinearPrinter.println(" stop.");
	    return false;
	}
	LinearPrinter.println(" parent: " + parent);
	if (!this.linearityInformation.hasLinearRepresentation(self)) {
	    LinearPrinter.println(" no linear information about: " + self);
	    LinearPrinter.println(" stop.");
	    return false;
	}
	
	// generate a new implementation as a single filter
	LinearFilterRepresentation linearRep;
	linearRep = this.linearityInformation.getLinearRepresentation(self);
	SIRStream newImplementation;
	newImplementation = makeEfficientImplementation(self, linearRep);
	newImplementation.setParent(parent);
	// do the acutal replacment of the current pipeline with the new implementation
	parent.replace(self, newImplementation);

	LinearPrinter.println("Relative child name: " + newImplementation.getRelativeName());

	// return that we replaced something
	return true;
    }

    /**
     * Creates a filter that has a work function that directly implements
     * the linear representation that is passed in.<p>
     *
     * Eventually, this will determine (by some yet to be determined method) the
     * most efficient implementation and then create an IR structure that implements
     * that. For now, we always return the direct matrix multply implementation.
     **/
    private SIRStream makeEfficientImplementation(SIRStream oldStream,
						  LinearFilterRepresentation linearRep) {
	// if we have a linear representation of this filter
	if (!linearityInformation.hasLinearRepresentation(oldStream)) {
	    throw new RuntimeException("no linear info");
	}

	// create a new work function that calculates the linear representation directly
	JMethodDeclaration newWork = makeDirectWork(linearRep,
						    oldStream.getInputType(),
						    oldStream.getOutputType(),
						    linearRep.getPopCount());
	JMethodDeclaration newInit = SIRStream.makeEmptyInit();
	
	// create a new filter with the new work and init functions
	
	SIRFilter newFilter = new SIRFilter("Linear" + oldStream.getIdent());
	newFilter.setWork(newWork);
	newFilter.setInit(newInit);
	newFilter.setPeek(linearRep.getPeekCount());
	newFilter.setPop (linearRep.getPopCount());
	newFilter.setPush(linearRep.getPushCount());
	newFilter.setInputType(oldStream.getInputType());
	newFilter.setOutputType(oldStream.getOutputType());

	LinearPrinter.println(" created new filter: " + newFilter);
	return newFilter;
    }


    /**
     * Create a method that computes the function represented in the
     * linear form. (Eg it pushes the direct sum of inputs to the output.)
     * inputType is the variable type of the peek/pop expression that this filter uses
     * and output type is the type of the pushExpressions.<p>
     *
     * The basic format of the resulting method is:<p>
     * <pre>
     * push(a1*peek(0) + b1*peek(1) + ... + x1*peek(n));
     * push(a2*peek(0) + b2*peek(1) + ... + x2*peek(n));
     * ...
     * pop();
     * pop();
     * ...
     * </pre>
     **/
    JMethodDeclaration makeDirectWork(LinearFilterRepresentation representation,
				      CType inputType,
				      CType outputType,
				      int popCount) {
	// generate the push expressions that will make up the body of the
	// new work method.
	Vector pushStatements = makePushStatementVector(representation, inputType, outputType);
	// make a vector filled with the appropriate number of pop expressions
	Vector popStatements  = new Vector();
	for (int i=0; i<popCount; i++) {
	    SIRPopExpression popExpr = new SIRPopExpression(inputType);
	    // wrap the pop expression so it is a statement.
	    JExpressionStatement popWrapper = new JExpressionStatement(null, // token reference,
								       popExpr, // expr
								       new JavaStyleComment[0]);  // comments
	    popStatements.add(popWrapper);
	}

	// now, generate the body of the new method, concatenating push then pop expressions
	JBlock body = new JBlock();
	body.addAllStatements(pushStatements);
	body.addAllStatements(popStatements);

	// now, assemble the pieces needed for a new JMethod.

	return new JMethodDeclaration(null, // tokenReference
				      ACC_PUBLIC,//modifiers
				      CStdType.Void, // returnType
				      "work",
				      new JFormalParameter[0], // params
				      new CClassType[0], // exceptions
				      body, // body (obviously)
				      null, // javadoc
				      new JavaStyleComment[0]); // comments
				      
				      

    }

    /**
     * Generate a Vector of JExprssionStatements which wrap
     * SIRPushExpressions that implement (directly) the
     * matrix multiplication represented by the linear representation.
     **/
    public Vector makePushStatementVector(LinearFilterRepresentation representation,
					   CType inputType,
					   CType outputType) {
	Vector returnVector = new Vector();

	int peekCount = representation.getPeekCount();
	int pushCount = representation.getPushCount();

	// for each output value (eg push count), construct push expression
	for (int i = 0; i < pushCount; i++) {
	    // the first push will have index pushCount, etc.
	    int currentPushIndex = pushCount - 1 - i;
	    
	    // go through each of the elements in this column of the matrix. If the element
	    // is non zero, then we want to produce a peek(index)*weight term (which we will then add together).
	    // Currently bomb out if we have a non real number (no way to generate non-reals at the present).
	    Vector combinationExpressions = new Vector();

	    // a note about indexes: the matrix [[0] [1] [2]] implies peek(0)*2 + peek(1)*1 + peek(2)*0.
	    for (int j = 0; j < peekCount; j++) {
		int currentPeekIndex = peekCount - 1 - j;
		ComplexNumber currentWeight = representation.getA().getElement(currentPeekIndex,
									       currentPushIndex);
		// if we have a non real number, bomb Mr. Exception
		if (!currentWeight.isReal()) {
		    throw new RuntimeException("Direct implementation with complex " +
					       "numbers is not yet implemented.");
		}

		// if we have a non zero weight, add a weight*peek node
		if (currentWeight.equals(ComplexNumber.ZERO)) {
		    // do nothing for a zero weight
		} else {
		    // make an integer IR node for the appropriate peek index (peek (0) corresponds to
		    // to the array row of  at peekSize-1
		    JIntLiteral peekOffsetNode = new JIntLiteral(j);
		    // make a peek expression with the appropriate index
		    SIRPeekExpression peekNode = new SIRPeekExpression(peekOffsetNode, inputType);

		    // IR node for the expression (either peek, or weight*peek)
		    JExpression exprNode;
		    // If we have a one, no need to do a multiply
		    if (currentWeight.equals(ComplexNumber.ONE)) {
			exprNode = peekNode;
		    } else {
			// make literal weight (special case if the weight is an integer)
			JLiteral weightNode;
			if (currentWeight.isRealInteger()) {
			    weightNode = new JIntLiteral(null, (int)currentWeight.getReal());
			} else {
			    weightNode = new JFloatLiteral(null, (float)currentWeight.getReal());
			}
			// make a JMultExpression with weight*peekExpression
			exprNode = new JMultExpression(null,        // tokenReference
						       weightNode,  // left
						       peekNode);   // right
		    }
		    // add in the new expression node
		    combinationExpressions.add(exprNode);
		}
	    }
	    
	    // now, we need to create the appropriate constant to represent the offset
	    ComplexNumber currentOffset = representation.getb().getElement(currentPushIndex);
	    if (!currentOffset.isReal()) {throw new RuntimeException("Non real complex number in offset vector");}
	    JLiteral offsetNode;
	    // make the offset node for integers, and others
	    if (currentOffset.isRealInteger()) {
		offsetNode = new JIntLiteral(null, (int)currentOffset.getReal());
	    } else {
		offsetNode = new JDoubleLiteral(null, currentOffset.getReal());
	    }
	    
	    // now we have all of the combination nodes and the offset node.
	    // What we want to do is to is to combine them all together using addition.
	    // To do this, we create an add expression tree expanding downward to the right as we go.
	    JExpression pushArgument;
	    // if no combination expressions, then the push arg is only the offset
	    if (combinationExpressions.size() == 0) {
		// if we have no combination expressions, it means we should simply output a zero
		pushArgument = offsetNode;
	    } else {
		// combination expressions need to be nested.
		// Start with the right most node
		int numCombos = combinationExpressions.size();
		pushArgument = new JAddExpression(null, // tokenReference
						  ((JExpression)combinationExpressions.get(numCombos-1)), // left
						  offsetNode); // right
		// now, for all of the other combinations, make new add nodes with the
		// comb. exprs as the left argument and the current add expr as the right
		// argument.
		for (int k=2; k<=numCombos; k++) {
		    pushArgument = new JAddExpression(null, // tokenReference,
						      ((JExpression)combinationExpressions.get(numCombos-k)), // left
						      pushArgument); // right (use the previous expression)
		}
	    }
	    	    	    
	    // now, armed with the appropriate push argument, we can
	    // simply generate the appropriate push expression and stick it in our list.
	    SIRPushExpression pushExpr = new SIRPushExpression(pushArgument, // arg
							       outputType); // output tape type (eg push type)
	    // wrap the push expression in a expression statement
	    JExpressionStatement pushWrapper = new JExpressionStatement(null, // tokenReference
									pushExpr, // expr
									new JavaStyleComment[0]); // comments
	    returnVector.add(pushWrapper);
	}
	return returnVector;
    }
    
    /**
     * This visitor calculates the best way to replace filters in a stream
     * graph with direct implementations. Specifically, it calculates the
     * the replacement that has the lowest cost.
     **/
    static class LinearReplaceCalculator extends EmptyAttributeStreamVisitor {
	/**
	 * Maps SIRStreams-->Boolean. If the value is true, we want to replace this member, and
	 * if the value is false, we do not want to do the replacement.
	 **/
	HashMap doReplace;
	LinearAnalyzer linearInformation;
	public LinearReplaceCalculator(LinearAnalyzer la) {
	    doReplace = new HashMap();
	    linearInformation = la;
	}
	/**
	 * visiting a filter is easy. There are no children, and by assumption we want to
	 * replace the generic code given with our matrix code. Stick in the appropriate
	 * mapping in doReplace and then return.
	 **/
	public Object visitFilter(SIRFilter self,
				  JFieldDeclaration[] fields,
				  JMethodDeclaration[] methods,
				  JMethodDeclaration init,
				  JMethodDeclaration work,
				  CType inputType, CType outputType) {
	    if (linearInformation.hasLinearRepresentation(self)) {
		doReplace.put(self, new Boolean(true));
	    } 
	    return self;
	}

	public Object visitFeedbackLoop(SIRFeedbackLoop self,
					JFieldDeclaration[] fields,
					JMethodDeclaration[] methods,
					JMethodDeclaration init,
					JMethodDeclaration initPath) {
	    // we don't really care about feedback loops because we don't include them in our analysis
	    return self;
	}
	    /* pre-visit a pipeline */
	public Object visitPipeline(SIRPipeline self,
				    JFieldDeclaration[] fields,
				    JMethodDeclaration[] methods,
				    JMethodDeclaration init) {
	    return visitContainer(self);
	}
	public Object visitSplitJoin(SIRSplitJoin self,
				     JFieldDeclaration[] fields,
				     JMethodDeclaration[] methods,
				     JMethodDeclaration init,
				     SIRSplitter splitter,
				     SIRJoiner joiner) {
	    return visitContainer(self);
	}
	
	/**
	 * generic method for visiting container streams:<p>
	 * If we have linear information for the container, we calculate the cost
	 * of using the linear representation of the container, and recursively
	 * calculate the cost of using the linear information of the children.
	 * We then use the solution which generates minimal cost.
	 **/
	public Object visitContainer(SIRContainer self) {
	    LinearPrinter.println(" calculating cost of: " + self);
	    // if we don't know anything about this container, we are done, though we need
	    // to do the recursion to the children
	    if (!linearInformation.hasLinearRepresentation(self)) {
		Iterator childIter = self.getChildren().iterator();
		while (childIter.hasNext()) {
		    ((SIROperator)childIter.next()).accept(this);
		}
		return self;
	    }

	    // calcuate the cost of doing a direct replacement of this container
	    LinearCost containerCost = linearInformation.getLinearRepresentation(self).getCost();

	    // calculate the cost of doing the optimal replacement of the children.
	    LinearReplaceCalculator childCalculator = new LinearReplaceCalculator(linearInformation);
	    Iterator childIter = self.getChildren().iterator();
	    while(childIter.hasNext()) {
		((SIROperator)childIter.next()).accept(childCalculator);
	    }
	    LinearCost childCost = childCalculator.getTotalCost();

	    // now, if the container cost is less than the child cost, use the
	    // container, otherwise use the child
	    if (containerCost.lessThan(childCost)) {
		doReplace.put(self, new Boolean(true));
	    } else {
		doReplace.putAll(childCalculator.getDoReplace()); // remember which children were used
	    }
	    return self;
	}

	/** get the mappings from streams to true if we want to replace them. **/
	HashMap getDoReplace() {return this.doReplace;}

	/** calculate the total cost of doing the replacements that is described in doReplace. **/
	LinearCost getTotalCost() {
	    LinearCost currentCost = LinearCost.ZERO;
	    
	    Iterator keyIter = this.doReplace.keySet().iterator();
	    while(keyIter.hasNext()) {
		// the only mappings that we have in the map are the streams we want to include
		SIRStream currentStream = (SIRStream)keyIter.next();
		LinearFilterRepresentation currentChildRep = linearInformation.getLinearRepresentation(currentStream);
		LinearCost currentChildCost = currentChildRep.getCost();
		currentCost = currentCost.plus(currentChildCost);
	    }
	    return currentCost;
	}

	/**
	 * returns true if we should replace this filter with a direct implementation (eg if
	 * we have a mapping from the stream to Boolean(true) in doReplace.
	 **/
	public boolean shouldReplace(SIRStream str) {
	    return this.doReplace.containsKey(str);
	}
    }
}


