package at.dms.kjc.sir.lowering.fusion;

//import at.dms.util.IRPrinter;
import at.dms.util.Utils;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
//import at.dms.kjc.sir.lowering.partition.*;
//import at.dms.kjc.lir.*;

//import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;

class ShiftPipelineFusion {

    /**
     * This is the name of the field that is used to hold the items
     * that are peeked.
     */
    private static final String PEEK_BUFFER_NAME = "___PEEK_BUFFER";

    /**
     * This is the name of the field that is used to hold the items
     * that are popped / looked at.
     */
    private static final String POP_BUFFER_NAME = "___POP_BUFFER";

    /**
     * The name of the counter that is used to write into buffers.
     */
    private static final String PUSH_INDEX_NAME = "___PUSH_INDEX";

    /**
     * The name of the counter that is used to read from buffers.
     */
    private static final String POP_INDEX_NAME = "___POP_INDEX";

    /**
     * The name of the counter that is used to count work executions
     * of a phase.
     */

    private static final String COUNTER_NAME_WORK = "___COUNTER_WORK";
    /**
     * The name of the counter that is used to control the peek restore
     * loop of a phase
     */

    private static final String COUNTER_NAME_RESTORE = "___COUNTER_RESTORE";
    /**
     * The name of the counter that is used to count executions of the 
     * peek backup loop of a phase.
     */
    private static final String COUNTER_NAME_BACKUP = "___COUNTER_BACKUP";

    /**
     * The name of the initial work function.
     */
    public static final String INIT_WORK_NAME() { return FusePipe.INIT_WORK_NAME; }

    /**
     * Prefix for name of parameters in fused init function.
     */
    protected static final String INIT_PARAM_NAME = "___param";

    /**
     * The fundamental fuse operation.
     *
     * Fuses ALL children of pipe, requiring that they are fusable
     * filters.  Leaves just a single filter <f>, which will be
     * <pipe>'s only child following this call.
     */
    public static void doFusion(SIRPipeline pipe) {
	// construct set of filter info
	List filterInfo = makeFilterInfo(pipe);

	InitFuser initFuser;

	// make the initial work function
	JMethodDeclaration initWork =  makeWork(filterInfo, true);
	
	// make the steady-state work function
	JMethodDeclaration steadyWork = makeWork(filterInfo, false);
	
	// make the fused init functions
	initFuser = makeInitFunction(filterInfo);
	
	// fuse all other fields and methods
	SIRFilter result = makeFused(filterInfo, initFuser.getInitFunction(), initWork, steadyWork);

	// insert the fused filter in the parent
	FusePipe.replace(pipe, result, initFuser.getInitArgs());
    }

    /**
     * Tabulates info on <filterList> that is needed for fusion.
     */
    private static List makeFilterInfo(SIRPipeline pipe) {
	// make the result
	List result = new LinkedList();
	// get execution counts for <pipe>
	HashMap[] execCount = SIRScheduler.getExecutionCounts(pipe);

	// for each filter...
	for (int i=0; i<pipe.size(); i++) {
	    // the filter
	    SIRFilter filter = (SIRFilter)pipe.get(i);

	    // the peek buffer
	    JVariableDefinition peekBufferVar = 
		new JVariableDefinition(null,
					at.dms.kjc.Constants.ACC_FINAL,
					new CArrayType(Utils.voidToInt(filter.
								       getInputType()), 
						       1 /* dimension */ ),
					PEEK_BUFFER_NAME + "_" + i,
					null);
	    JFieldDeclaration peekBuffer = new JFieldDeclaration(null,
								 peekBufferVar,
								 null,
								 null);
	    
	    // number of executions (num[0] is init, num[1] is steady)
	    int[] num = new int[2];
	    for (int j=0; j<2; j++) {
		int[] count = (int[])execCount[j].get(filter);
		if (count==null) {
		    num[j] = 0;
		} else {
		    num[j] = count[0];
		}
	    }

	    // calculate how much data we should buffer between the
	    // i'th and (i-1)'th filter.  This part of the code is
	    // ready for two stage filters even though they might not
	    // be passed here yet.
	    int peekBufferSize;
	    if (i==0) {
		// for the first filter, don't need any peek buffer
		peekBufferSize = 0;
	    } else {
		// otherwise, need however many were left over during
		// initialization between this filter and the last
		FilterInfo last = (FilterInfo)result.get(result.size()-1);
		int lastProduce = 0;
		// need to count first execution of a two-stage filter separately
		if (last.filter instanceof SIRTwoStageFilter &&
		    last.init.num > 0) {
		    //System.err.println("last is two-stage.  getInitPush=" + ((SIRTwoStageFilter)last.filter).getInitPush() + " last.init.num=" + last.init.num + " last.filter.getPushInt=" + last.filter.getPushInt() + " getInitPeek=" + ((SIRTwoStageFilter)last.filter).getInitPeek() + " getInitPop=" + ((SIRTwoStageFilter)last.filter).getInitPop());
		    lastProduce = ((SIRTwoStageFilter)last.filter).getInitPushInt() + 
			(last.init.num-1) * last.filter.getPushInt();
		} else {
		    lastProduce = last.init.num * last.filter.getPushInt();
		}
		int myConsume = 0;
		if (filter instanceof SIRTwoStageFilter &&
		    num[0] > 0) {
		    myConsume = ((SIRTwoStageFilter)filter).getInitPopInt() + 
			(num[0]-1) * filter.getPopInt();
		} else {
		    myConsume = num[0] * filter.getPopInt();
		}
		// the peek buffer is the difference between what the
		// previous one produces and this one consumes
		peekBufferSize = lastProduce - myConsume;
		assert peekBufferSize>=0:
                    "Pipeline fusion trying to create " +
                    "a negative peek buffer of " + peekBufferSize +
                    " when fusing between " + last.filter + " and " +
                    filter + "\n" +
                    "  peekBufferSize = lastProduce - myConsume = " +
                    lastProduce + " - " + myConsume;
	    }

	    // get ready to make rest of phase-specific info
	    JVariableDefinition popBuffer[] = new JVariableDefinition[2];
	    JVariableDefinition popCounter[] = new JVariableDefinition[2];
	    JVariableDefinition pushCounter[] = new JVariableDefinition[2];
	    JVariableDefinition loopCounterWork[] = new JVariableDefinition[2];
	    JVariableDefinition loopCounterRestore[] = new JVariableDefinition[2];
	    JVariableDefinition loopCounterBackup[] = new JVariableDefinition[2];
	    
	    for (int j=0; j<2; j++) {
		// the pop buffer
		popBuffer[j] = makePopBuffer(filter, peekBufferSize, j, num[j], i);

		// the pop counter.
		popCounter[j] = 
		    new JVariableDefinition(null, 0, CStdType.Integer,
					    POP_INDEX_NAME + "_" + j + "_" + i,
					    new
					    JIntLiteral(- 1 /* this is since we're starting
							       at -1 and doing pre-inc 
							       instead of post-inc */ ));
	    
		// the push counter.  In the steady state, the initial
		// value of the push counter is the first slot after
		// the peek values are restored, which is peekBufferSize-1
		// (-1 since we're pre-incing, not post-incing).  In
		// the inital work function, the push counter starts
		// at -1 (since we're pre-incing, not post-incing).
		int pushInit = 
		    j==0 ? -1 : peekBufferSize - 1;
		pushCounter[j] = 
		    new JVariableDefinition(null, 0, CStdType.Integer,
					    PUSH_INDEX_NAME + "_" + j + "_" +i,
					    new JIntLiteral(pushInit));

		// the exec counter
		loopCounterWork[j] = 
		    new JVariableDefinition(null, 0, CStdType.Integer,
					    COUNTER_NAME_WORK + "_" + j + "_" +i,
					    null);
		
		// the peek restore counter
		loopCounterRestore[j] = 
		    new JVariableDefinition(null, 0, CStdType.Integer,
					    COUNTER_NAME_RESTORE + "_" + j + "_" +i,
					    null);

		// the peek backup counter
		loopCounterBackup[j] = 
		    new JVariableDefinition(null, 0, CStdType.Integer,
					    COUNTER_NAME_BACKUP + "_" + j + "_" +i,
					    null);
	    }

	    // add a filter info to <result>
	    result.add(new 
		       FilterInfo(filter, peekBuffer, peekBufferSize,
				  new PhaseInfo(num[0], 
						popBuffer[0], 
						popCounter[0], 
						pushCounter[0],
						loopCounterWork[0],
						loopCounterRestore[0],
						loopCounterBackup[0]),
				  new PhaseInfo(num[1], 
						popBuffer[1],
						popCounter[1], 
						pushCounter[1],
						loopCounterWork[1],
						loopCounterRestore[1],
						loopCounterBackup[1])
				  ));
	}
	// return result
	return result;
    }
	
    /**
     * Returns a JVariableDefinition for a pop buffer for <filter>
     * that executes <num> times in stage <stage> and appears in the
     * <pos>'th position of its pipeline.
     */
    private static JVariableDefinition makePopBuffer(SIRFilter filter, 
						     int peekBufferSize,
						     int stage,
						     int num,
						     int pos) {
	// get the number of items looked at in an execution round
	int lookedAt = num * filter.getPopInt() + peekBufferSize;
	// make an initializer to make a buffer of extent <lookedAt>
	JExpression[] dims = { new JIntLiteral(null, lookedAt) };
	JExpression initializer = 
	    new JNewArrayExpression(null,
				    Utils.voidToInt(filter.getInputType()),
				    dims,
				    null);
	// make a buffer for all the items looked at in a round
	return new JVariableDefinition(null,
				       at.dms.kjc.Constants.ACC_FINAL,
				       new CArrayType(Utils.voidToInt(filter.
								      getInputType()), 
						      1 /* dimension */ ),
				       POP_BUFFER_NAME + "_" + pos + "_" + stage,
				       initializer);
    }

    /**
     * Builds the initial work function for <filterList>, where <init>
     * indicates whether or not we're doing the initialization work
     * function.  If in init mode and there are no statements in the
     * work function, then it returns null instead (to indicate that
     * initWork is not needed.)
     */
    private static JMethodDeclaration makeWork(List filterInfo, boolean init) {
	// make a statement list for the init function
	JBlock statements = new JBlock(null, new JStatement[0], null);

	// add the variable declarations
	makeWorkDecls(filterInfo, statements, init);

	// add the work statements
	int before = statements.size();
	makeWorkBody(filterInfo, statements, init);
	int after = statements.size();

	if (after-before==0 && init) {
	    // return null to indicate empty initWork function
	    return null;
	} else {
	    // return result
	    return new JMethodDeclaration(null,
					  at.dms.kjc.Constants.ACC_PUBLIC,
					  CStdType.Void,
					  init ? INIT_WORK_NAME() : "work",
					  JFormalParameter.EMPTY,
					  CClassType.EMPTY,
					  statements,
					  null,
					  null);
	}
    }

    /**
     * Adds local variable declarations to <statements> that are
     * needed by <filterInfo>.  If <init> is true, it does it for init
     * phase; otherwise for steady phase.
     */
    private static void makeWorkDecls(List filterInfo,
				      JBlock statements,
				      boolean init) {
	// add declarations for each filter
	for (ListIterator it = filterInfo.listIterator(); it.hasNext(); ) {
	    FilterInfo info = (FilterInfo)it.next();
	    // get list of local variable definitions from <filterInfo>
	    List locals = 
		init ? info.init.getVariables() : info.steady.getVariables();
	    // go through locals, adding variable declaration
	    for (ListIterator loc = locals.listIterator(); loc.hasNext(); ) {
		// get local
		JVariableDefinition local = 
		    (JVariableDefinition)loc.next();
		// add variable declaration for local
		statements.
		    addStatement(new JVariableDeclarationStatement(null, 
								   local, 
								   null));
	    }
	}
    }

    /**
     * Adds the body of the initial work function.  <init> indicates
     * whether or not this is the initial run of the work function
     * instead of the steady-state version.
     */
    private static void makeWorkBody(List filterInfo, 
				     JBlock statements,
				     boolean init) {

	FindVarDecls findVarDecls = new FindVarDecls();

	// for all the filters...
	for (int i=0; i<filterInfo.size(); i++) {
	    FilterInfo cur = (FilterInfo)filterInfo.get(i);
	    PhaseInfo curPhase = init ? cur.init : cur.steady;
	    // we'll only need the "next" fields if we're not at the
	    // end of a pipe.
	    FilterInfo next = null;
	    PhaseInfo nextPhase = null;
	    // get the next fields
	    if (i<filterInfo.size()-1) {
		next = (FilterInfo)filterInfo.get(i+1);
		nextPhase = init ? next.init : next.steady;
	    }

	    // if the current filter doesn't execute at all, continue
	    // (FIXME this is part of some kind of special case for 
	    // filters that don't execute at all in a schedule, I think.)
	    if (curPhase.num!=0) {

		// if in the steady-state phase, restore the peek values
		if (!init) {
		    statements.addStatement(makePeekRestore(cur, curPhase));
		}
		SIRFilter filter=cur.filter;
		// get the filter's work function
		JMethodDeclaration work = filter.getWork();
		// take a deep breath and clone the body of the work function
		JBlock oldBody = new JBlock(null, work.getStatements(), null);
		JBlock body = (JBlock)ObjectDeepCloner.deepCopy(oldBody);

		if (KjcOptions.rename1) {
		    body = (JBlock)findVarDecls.findAndReplace(body);
		}

		// mutate <statements> to make them fit for fusion
		FusingVisitor fuser = 
		    new FusingVisitor(curPhase, nextPhase, i!=0,
				      i!=filterInfo.size()-1);
		for (ListIterator it = body.getStatementIterator(); 
		     it.hasNext() ; ) {
		    ((JStatement)it.next()).accept(fuser);
		}
		if(init&&(filter instanceof SIRTwoStageFilter)) {
		    JMethodDeclaration initWork = ((SIRTwoStageFilter)filter).getInitWork();
		    // take a deep breath and clone the body of the work function
		    JBlock oldInitBody = new JBlock(null, initWork.getStatements(), null);
		    JBlock initBody = (JBlock)ObjectDeepCloner.deepCopy(oldInitBody);
		    // mutate <statements> to make them fit for fusion
		    fuser = 
			new FusingVisitor(curPhase, nextPhase, i!=0,
					  i!=filterInfo.size()-1);
		    for (ListIterator it = initBody.getStatementIterator(); 
			 it.hasNext() ; ) {
			((JStatement)it.next()).accept(fuser);
		    }
		    statements.addStatement(initBody);
		    if(curPhase.num>1)
			statements.addStatement(makeForLoop(body,
							    curPhase.loopCounterWork,
							    new 
							    JIntLiteral(curPhase.num-1))
						);
		} else {
		    
		    // get <body> into a loop in <statements>
		    statements.addStatement(makeForLoop(body,
							curPhase.loopCounterWork,
							new 
							JIntLiteral(curPhase.num))
					    );
		}
	    } else {
		if(init&&(cur.filter instanceof SIRTwoStageFilter))
		    System.err.println("Warning: Two-Stage filter "+cur.filter+" did not fire in init phase.");
	    }
	    // if there's any peek buffer, store items to it
	    if (cur.peekBufferSize>0) {
		statements.addStatement(makePeekBackup(cur, curPhase));
	    }
	}

	//add variable declarations calculated by FindVarDecls
	if (KjcOptions.rename1) {
	    findVarDecls.addVariableDeclarations(statements);
	}
    }

    /**
     * Given that a phase is about to be executed, restores the peek
     * information to the front of the pop buffer.
     */
    private static JStatement makePeekRestore(FilterInfo filterInfo,
					      PhaseInfo phaseInfo) {
	// make a statement that will copy peeked items into the pop
	// buffer, assuming the counter will count from 0 to peekBufferSize

	// the lhs of the source of the assignment
	JExpression sourceLhs = 
	    new JFieldAccessExpression(null,
				       new JThisExpression(null),
				       filterInfo.peekBuffer.
				       getVariable().getIdent());

	// the rhs of the source of the assignment
	JExpression sourceRhs = 
	    new JLocalVariableExpression(null, 
					 phaseInfo.loopCounterRestore);

	// the lhs of the dest of the assignment
	JExpression destLhs = 
	    new JLocalVariableExpression(null,
					 phaseInfo.popBuffer);
	    
	// the rhs of the dest of the assignment
	JExpression destRhs = 
	    new JLocalVariableExpression(null,
					 phaseInfo.loopCounterRestore);

	// the expression that copies items from the pop buffer to the
	// peek buffer
	JExpression copyExp = 
	    new JAssignmentExpression(null,
				      new JArrayAccessExpression(null,
								 destLhs,
								 destRhs),
				      new JArrayAccessExpression(null,
								 sourceLhs,
								 sourceRhs));

	// finally we have the body of the loop
	JStatement body = new JExpressionStatement(null, copyExp, null);

	// return a for loop that executes (peek-pop) times.
	return makeForLoop(body,
			   phaseInfo.loopCounterRestore, 
			   new JIntLiteral(filterInfo.peekBufferSize));
    }

    /**
     * Given that a phase has already executed, backs up the state of
     * unpopped items into the peek buffer.
     */
    private static JStatement makePeekBackup(FilterInfo filterInfo,
					     PhaseInfo phaseInfo) {
	// make a statement that will copy unpopped items into the
	// peek buffer, assuming the counter will count from 0 to peekBufferSize

	// the lhs of the destination of the assignment
	JExpression destLhs = 
	    new JFieldAccessExpression(null,
				       new JThisExpression(null),
				       filterInfo.peekBuffer.
				       getVariable().getIdent());

	// the rhs of the destination of the assignment
	JExpression destRhs = 
	    new JLocalVariableExpression(null, 
					 phaseInfo.loopCounterBackup);

	// the lhs of the source of the assignment
	JExpression sourceLhs = 
	    new JLocalVariableExpression(null,
					 phaseInfo.popBuffer);
	    
	// the rhs of the source of the assignment... (add one to the
	// push index because of our pre-inc convention.)
	JExpression sourceRhs = 
	    new
	    JAddExpression(null, 
			   new JLocalVariableExpression(null, 
							phaseInfo.loopCounterBackup),
			   new JAddExpression(null, new JIntLiteral(1),
					      new JLocalVariableExpression(null,
									   phaseInfo.popCounter)));
	/*
	// need to subtract the difference in peek and pop counts to
	// see what we have to backup
	JExpression sourceRhs =
	new JMinusExpression(null,
	sourceRhs1,
	new JIntLiteral(filterInfo.peekBufferSize));
	*/

	// the expression that copies items from the pop buffer to the
	// peek buffer
	JExpression copyExp = 
	    new JAssignmentExpression(null,
				      new JArrayAccessExpression(null,
								 destLhs,
								 destRhs),
				      new JArrayAccessExpression(null,
								 sourceLhs,
								 sourceRhs));

	// finally we have the body of the loop
	JStatement body = new JExpressionStatement(null, copyExp, null);

	// return a for loop that executes (peek-pop) times.
	return makeForLoop(body,
			   phaseInfo.loopCounterBackup, 
			   new JIntLiteral(filterInfo.peekBufferSize));
    }

    /**
     * Returns a for loop that uses local variable <var> to count
     * <count> times with the body of the loop being <body>.  If count
     * is non-positive, just returns the initial assignment statement.
     */
    private static JStatement makeForLoop(JStatement body,
					  JLocalVariable var,
					  JExpression count) {
	// make init statement - assign zero to <var>.  We need to use
	// an expression list statement to follow the convention of
	// other for loops and to get the codegen right.
	JExpression initExpr[] = {
	    new JAssignmentExpression(null,
				      new JLocalVariableExpression(null, var),
				      new JIntLiteral(0)) };
	JStatement init = new JExpressionListStatement(null, initExpr, null);
	// if count==0, just return init statement
	if (count instanceof JIntLiteral) {
	    int intCount = ((JIntLiteral)count).intValue();
	    if (intCount<=0) {
		// return empty statement
		return new JEmptyStatement(null, null);
	    }
	}
	// make conditional - test if <var> less than <count>
	JExpression cond = 
	    new JRelationalExpression(null,
				      Constants.OPE_LT,
				      new JLocalVariableExpression(null, var),
				      count);
	JExpression incrExpr = 
	    new JPostfixExpression(null, 
				   Constants.OPE_POSTINC, 
				   new JLocalVariableExpression(null, var));
	JStatement incr = 
	    new JExpressionStatement(null, incrExpr, null);

	return new JForStatement(null, init, cond, incr, body, null);
    }

    /**
     * Returns an init function that is the combinatio of those in
     * <filterInfo> and includes a call to <initWork>.  Also patches
     * the parent's init function to call the new one, given that
     * <result> will be the resulting fused filter.
     */
    private static 
	InitFuser makeInitFunction(List filterInfo) {
	// make an init function builder out of <filterList>
	InitFuser initFuser = new InitFuser(filterInfo);
	
	// do the work on the parent
	initFuser.doit((SIRPipeline)((FilterInfo)filterInfo.get(0)).filter.getParent());

	// make the finished initfuser
	return initFuser;
    }

    /**
     * Returns an array of the fields that should appear in filter
     * fusing all in <filterInfo>.
     */
    private static JFieldDeclaration[] getFields(List filterInfo) {
	// make result
	List result = new LinkedList();
	// add the peek buffer's and the list of fields from each filter
	int i=0;
	for (ListIterator it = filterInfo.listIterator(); it.hasNext(); i++) {
	    FilterInfo info = (FilterInfo)it.next();
	    result.add(info.peekBuffer);
	    result.addAll(Arrays.asList(info.filter.getFields()));
	}
	// return result
	return (JFieldDeclaration[])result.toArray(new JFieldDeclaration[0]);
    }

    /**
     * Returns an array of the methods fields that should appear in
     * filter fusing all in <filterInfo>, with extra <init>, <initWork>, 
     * and <steadyWork> appearing in the fused filter.
     */
    private static 
	JMethodDeclaration[] getMethods(List filterInfo,
					JMethodDeclaration init,
					JMethodDeclaration initWork,
					JMethodDeclaration steadyWork) {
	// make result
	List result = new LinkedList();
	// start with the methods that we were passed
	result.add(init);
	if (initWork!=null) {
	    result.add(initWork);
	}
	result.add(steadyWork);
	// add methods from each filter that aren't work methods
	for (ListIterator it = filterInfo.listIterator(); it.hasNext(); ) {
	    FilterInfo info = (FilterInfo)it.next();
	    SIRFilter filter=info.filter;
	    List methods = Arrays.asList(filter.getMethods());
	    for (ListIterator meth = methods.listIterator(); meth.hasNext(); ){
		JMethodDeclaration m = (JMethodDeclaration)meth.next();
		// add methods that aren't work (or initwork)
		if (m!=info.filter.getWork()) {
		    if(filter instanceof SIRTwoStageFilter) {
			if(m!=((SIRTwoStageFilter)filter).getInitWork())
			    result.add(m);
		    }
		    else
			result.add(m);
		}
	    }
	}
	// return result
	return (JMethodDeclaration[])result.toArray(new JMethodDeclaration[0]);
    }

    /**
     * Returns the final, fused filter.
     */
    private static SIRFilter makeFused(List filterInfo, 
				       JMethodDeclaration init, 
				       JMethodDeclaration initWork, 
				       JMethodDeclaration steadyWork) {
	// get the first and last filters' info
	FilterInfo first = (FilterInfo)filterInfo.get(0);
	FilterInfo last = (FilterInfo)filterInfo.get(filterInfo.size()-1);

	// calculate the peek, pop, and push count for the fused
	// filter in the STEADY state
	int steadyPop = first.steady.num * first.filter.getPopInt();
	int steadyPeek = 
	    (first.filter.getPeekInt() - first.filter.getPopInt()) + steadyPop;
	int steadyPush = last.steady.num * last.filter.getPushInt();

	SIRFilter result;
	// if initWork is null, then we can get away with a filter for
	// the fused result; otherwise we need a two-stage filter
	if (initWork==null) {
	    result = new SIRFilter(first.filter.getParent(),
				   FusePipe.getFusedName(mapToFilters(filterInfo)),
				   getFields(filterInfo),
				   getMethods(filterInfo, 
					      init, 
					      initWork, 
					      steadyWork),
				   new JIntLiteral(steadyPeek), 
				   new JIntLiteral(steadyPop),
				   new JIntLiteral(steadyPush),
				   steadyWork,
				   first.filter.getInputType(),
				   last.filter.getOutputType());
	} else {
	    // calculate the peek, pop, and push count for the fused
	    // filter in the INITIAL state
	    /*int initPop = first.init.num * first.filter.getPopInt();
	      int initPeek =
	      (first.filter.getPeekInt() - first.filter.getPopInt()) + initPop;
	      int initPush = last.init.num * last.filter.getPushInt();*/
	    
	    
	    int initPop = first.init.num * first.filter.getPopInt();
	    if(first.filter instanceof SIRTwoStageFilter)
		initPop = ((SIRTwoStageFilter)first.filter).getInitPopInt()+(first.init.num-1) * first.filter.getPopInt();
	    int initPeek =
		(first.filter.getPeekInt() - first.filter.getPopInt()) + initPop;
	    int initPush = last.init.num * last.filter.getPushInt();
	    if(last.filter instanceof SIRTwoStageFilter)
		initPush = ((SIRTwoStageFilter)last.filter).getInitPushInt()+(last.init.num-1) * last.filter.getPushInt();
	    
	    // make a new filter to represent the fused combo
	    result = new SIRTwoStageFilter(first.filter.getParent(),
					   FusePipe.getFusedName(mapToFilters(filterInfo)),
					   getFields(filterInfo),
					   getMethods(filterInfo, 
						      init, 
						      initWork, 
						      steadyWork),
					   new JIntLiteral(steadyPeek), 
					   new JIntLiteral(steadyPop),
					   new JIntLiteral(steadyPush),
					   steadyWork,
					   new JIntLiteral(initPeek),
					   new JIntLiteral(initPop),
					   new JIntLiteral(initPush),
					   initWork,
					   first.filter.getInputType(),
					   last.filter.getOutputType());
	}
	
	// set init function of fused filter
	result.setInit(init);
	return result;
    }

    /**
     * Given list of filter info's, maps them to list of filters.
     */
    static List mapToFilters(List filterInfo) {
	List result = new LinkedList();
	for (int i=0; i<filterInfo.size(); i++) {
	    result.add(((FilterInfo)filterInfo.get(i)).filter);
	}
	return result;
    }

    /**
     * Contains information that is relevant to a given filter's
     * inclusion in a fused pipeline.
     */
    static class FilterInfo {
	/**
	 * The filter itself.
	 */
	public final SIRFilter filter;

	/**
	 * The persistent buffer for holding peeked items
	 */
	public final JFieldDeclaration peekBuffer;

	/**
	 * The size of the peek buffer
	 */
	public final int peekBufferSize;

	/**
	 * The info on the initial execution.
	 */
	public final PhaseInfo init;
	
	/**
	 * The info on the steady-state execution.
	 */
	public final PhaseInfo steady;

	public FilterInfo(SIRFilter filter, JFieldDeclaration peekBuffer,
			  int peekBufferSize, PhaseInfo init, PhaseInfo steady) {
	    this.filter = filter;
	    this.peekBuffer = peekBuffer;
	    this.peekBufferSize = peekBufferSize;
	    this.init = init;
	    this.steady = steady;
	}
    }

    static class PhaseInfo {
	/**
	 * The number of times this filter is executed in the parent.
	 */ 
	public final int num;

	/**
	 * The buffer for holding popped items.
	 */
	public final JVariableDefinition popBuffer;

	/**
	 * The counter for popped items.
	 */
	public final JVariableDefinition popCounter;

	/*
	 * The counter for pushed items (of the CURRENT phase)
	 */
	public final JVariableDefinition pushCounter;

	/**
	 * The counter for keeping track of work loop executions
	 */
	public final JVariableDefinition loopCounterWork;
	
	/**
	 * The counter for keeping track of peek restore executions 
	 */
	public final JVariableDefinition loopCounterRestore;

	/**
	 * The counter for keeping track of peek backup executions 
	 */
	public final JVariableDefinition loopCounterBackup;
    
	public PhaseInfo(int num, 
			 JVariableDefinition popBuffer,
			 JVariableDefinition popCounter,
			 JVariableDefinition pushCounter,
			 JVariableDefinition loopCounterWork,
			 JVariableDefinition loopCounterRestore,
			 JVariableDefinition loopCounterBackup) {
	    this.num = num;
	    this.popBuffer = popBuffer;
	    this.popCounter = popCounter;
	    this.pushCounter = pushCounter;
	    this.loopCounterWork = loopCounterWork;
	    this.loopCounterRestore = loopCounterRestore;
	    this.loopCounterBackup = loopCounterBackup;
	}

	/**
	 * Returns list of JVariableDefinitions of all var defs in here.
	 */
	public List getVariables() {
	    List result = new LinkedList();
	    result.add(popBuffer);
	    result.add(popCounter);
	    result.add(pushCounter);
	    result.add(loopCounterWork);
	    result.add(loopCounterRestore);
	    result.add(loopCounterBackup);
	    return result;
	}
    }

    static class FusingVisitor extends SLIRReplacingVisitor {
	/**
	 * The info for the current filter.
	 */
	private final PhaseInfo curInfo;

	/**
	 * The info for the next filter in the pipeline.
	 */
	private final PhaseInfo nextInfo;

	/**
	 * Whether or not peek and pop expressions should be fused.
	 */
	private final boolean fuseReads;

	/**
	 * Whether or not push expressions should be fused.
	 */
	private final boolean fuseWrites;

	public FusingVisitor(PhaseInfo curInfo, PhaseInfo nextInfo,
			     boolean fuseReads, boolean fuseWrites) {
	    this.curInfo = curInfo;
	    this.nextInfo = nextInfo;
	    this.fuseReads = fuseReads;
	    this.fuseWrites = fuseWrites;
	}

	public Object visitPopExpression(SIRPopExpression self,
					 CType tapeType) {
	    // leave it alone not fusing reads
	    if (!fuseReads) {
		return super.visitPopExpression(self, tapeType);
	    }

	    // build ref to pop array
	    JLocalVariableExpression lhs = 
		new JLocalVariableExpression(null, curInfo.popBuffer);

	    // build increment of index to array
	    JExpression rhs =
		new JPrefixExpression(null, 
				      Constants.OPE_PREINC, 
				      new JLocalVariableExpression(null,
								   curInfo.
								   popCounter));
	    // return a new array access expression
	    return new JArrayAccessExpression(null, lhs, rhs);
	}

	public Object visitPeekExpression(SIRPeekExpression oldSelf,
					  CType oldTapeType,
					  JExpression oldArg) {
	    // leave it alone not fusing reads
	    if (!fuseReads) {
		return super.visitPeekExpression(oldSelf, oldTapeType, oldArg);
	    }

	    // do the super
	    SIRPeekExpression self = 
		(SIRPeekExpression)
		super.visitPeekExpression(oldSelf, oldTapeType, oldArg);
	
	    // build ref to pop array
	    JLocalVariableExpression lhs = 
		new JLocalVariableExpression(null, curInfo.popBuffer);

	    // build subtraction of peek index from current pop index (add
	    // one to the pop index because of our pre-inc convention)
	    JExpression rhs =
		new JAddExpression(null,
				   new JAddExpression(null,
						      new JIntLiteral(1),
						      new JLocalVariableExpression(null,
										   curInfo.
										   popCounter)),
				   self.getArg());

	    // return a new array access expression
	    return new JArrayAccessExpression(null, lhs, rhs);
	}

	public Object visitPushExpression(SIRPushExpression oldSelf,
					  CType oldTapeType,
					  JExpression oldArg) {
	    // leave it alone not fusing writes
	    if (!fuseWrites) {
		return super.visitPushExpression(oldSelf, oldTapeType, oldArg);
	    }

	    // do the super
	    SIRPushExpression self = 
		(SIRPushExpression)
		super.visitPushExpression(oldSelf, oldTapeType, oldArg);
	
	    // build ref to push array
	    JLocalVariableExpression lhs = 
		new JLocalVariableExpression(null, nextInfo.popBuffer);

	    // build increment of index to array
	    JExpression rhs =
		new JPrefixExpression(null,
				      Constants.OPE_PREINC, 
				      new JLocalVariableExpression(null,
								   nextInfo.
								   pushCounter));
	    // return a new array assignment to the right spot
	    return new JAssignmentExpression(
					     null,
					     new JArrayAccessExpression(null, lhs, rhs),
					     self.getArg());
	}
    }
    /**
     * This builds up the init function of the fused class by traversing
     * the init function of the parent.
     */
    static class InitFuser {
	/**
	 * The info on the filters we're trying to fuse.
	 */
	private final List filterInfo;

	/**
	 * The block of the resulting fused init function.
	 */
	private JBlock fusedBlock;
    
	/**
	 * A list of the parameters of the fused block, all of type
	 * JFormalParameter.
	 */
	private List fusedParam;
    
	/**
	 * A list of the arguments to the init function of the fused
	 * block, all of type JExpression.
	 */
	private List fusedArgs;

	/**
	 * Cached copy of the method decl for the init function.
	 */
	private JMethodDeclaration initFunction;

	/**
	 * The number of filter's we've fused.
	 */
	private int numFused;

	/**
	 * <fusedFilter> represents what -will- be the result of the
	 * fusion.  It has been allocated, but is not filled in with
	 * correct values yet.
	 */
	public InitFuser(List filterInfo) {
	    this.filterInfo = filterInfo;
	    this.fusedBlock = new JBlock(null, new JStatement[0], null);
	    this.fusedParam = new LinkedList();
	    this.fusedArgs = new LinkedList();
	    this.numFused = 0;
	}

	public void doit(SIRPipeline parent) {
	    for (ListIterator it = filterInfo.listIterator(); it.hasNext(); ) {
		// process the arguments to a filter being fused
		FilterInfo info = (FilterInfo)it.next();
		int index = parent.indexOf(info.filter);
		processArgs(info, parent.getParams(index));
	    }
	    makeInitFunction();
	}

	/**
	 * Given that we found <args> in an init call to <info>,
	 * incorporate this info into the init function of the fused
	 * filter.
	 */
	private void processArgs(FilterInfo info, List args) {
	    // make parameters for <args>, and build <newArgs> to pass
	    // to new init function call
	    JExpression[] newArgs = new JExpression[args.size()];
	    for (int i=0; i<args.size(); i++) {
		JFormalParameter param = 
		    new JFormalParameter(null,
					 0,
					 ((JExpression)args.get(i)).getType(),
					 ShiftPipelineFusion.INIT_PARAM_NAME + 
					 "_" + i + "_" + numFused,
					 false);
		// add to list
		fusedParam.add(param);
		// make a new arg
		newArgs[i] = new JLocalVariableExpression(null, param);
		// increment fused count
		numFused++;
	    }

	    // add the arguments to the list
	    fusedArgs.addAll(args);

	    // make a call to the init function of <info> with <params>
	    fusedBlock.addStatement(new JExpressionStatement(
							     null,
							     new JMethodCallExpression(null,
										       new JThisExpression(null),
										       info.filter.getInit().getName(),
										       newArgs), null));
	}

	/**
	 * Prepares the init function for the fused block once the
	 * traversal of the parent's init function is complete.
	 */
	private void makeInitFunction() {
	    // add allocations for peek buffers
	    int i=0;
	    for (ListIterator it = filterInfo.listIterator(); it.hasNext(); ) {
		// get the next info
		FilterInfo info = (FilterInfo)it.next();
		// calculate dimensions of the buffer
		JExpression[] dims = { new JIntLiteral(null, 
						       info.peekBufferSize) };
		// add a statement initializeing the peek buffer
		fusedBlock.addStatementFirst(new JExpressionStatement(null,
								      new JAssignmentExpression(
												null,
												new JFieldAccessExpression(null,
															   new JThisExpression(null),
															   info.peekBuffer.
															   getVariable().getIdent()),
												new JNewArrayExpression(null,
															Utils.voidToInt(info.filter.
																	getInputType()),
															dims,
															null)), null));
	    }
	    // now we can make the init function
	    this.initFunction = new JMethodDeclaration(null,
						       at.dms.kjc.Constants.ACC_PUBLIC,
						       CStdType.Void,
						       "init",
						       (JFormalParameter[])
						       fusedParam.toArray(new 
									  JFormalParameter[0]),
						       CClassType.EMPTY,
						       fusedBlock,
						       null,
						       null);
	}
    
	/**
	 * Returns fused init function of this.
	 */
	public JMethodDeclaration getInitFunction() {
	    assert initFunction!=null;
	    return initFunction;
	}

	/**
	 * Returns the list of arguments that should be passed to init
	 * function.
	 */
	public List getInitArgs() {
	    return fusedArgs;
	}
    
    }
}
