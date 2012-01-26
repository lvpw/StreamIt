package at.dms.kjc.smp;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import at.dms.kjc.CStdType;
import at.dms.kjc.CType;
import at.dms.kjc.JBlock;
import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JStatement;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.SLIRReplacingVisitor;
import at.dms.kjc.backendSupport.Channel;
import at.dms.kjc.backendSupport.CodeStoreHelper;
import at.dms.kjc.backendSupport.InterSSGChannel;
import at.dms.kjc.sir.SIRBeginMarker;
import at.dms.kjc.sir.SIREndMarker;
import at.dms.kjc.sir.SIRPeekExpression;
import at.dms.kjc.sir.SIRPopExpression;
import at.dms.kjc.sir.SIRPushExpression;
import at.dms.kjc.slir.Filter;
import at.dms.kjc.slir.InputPort;
import at.dms.kjc.slir.InterSSGEdge;
import at.dms.kjc.slir.InternalFilterNode;
import at.dms.kjc.slir.IntraSSGEdge;
import at.dms.kjc.slir.OutputContent;
import at.dms.kjc.slir.OutputPort;
import at.dms.kjc.slir.SchedulingPhase;
import at.dms.kjc.slir.StaticSubGraph;
import at.dms.kjc.slir.WorkNode;
import at.dms.kjc.slir.WorkNodeInfo;

/**
 * Process a FilterSliceNode creating code in the code store.
 * 
 */
public class ProcessFilterWorkNode {

	/**
	 * Private class for setting up dynamic push and pop calls
	 * 
	 * @author soule
	 * 
	 */
	private static class PushPopReplacingVisitor extends SLIRReplacingVisitor {

		String peekName;
		String popManyName;
		String popName;  
		String pushName;
		@SuppressWarnings("rawtypes")
		Channel inputChannel;
		@SuppressWarnings("rawtypes")
		Channel outputChannel;
		Map<Filter, Integer> filterToThreadId;
		boolean isDynamicPop;
		boolean isDynamicPush;
		WorkNode filter;
		Map<String, List<String>> dominators;

		private JExpression staticPop(SIRPopExpression self, CType tapeType) {			
			if (self.getNumPop() > 1) {
				return new JMethodCallExpression(popManyName,
						new JExpression[] { new JIntLiteral(self.getNumPop()) });
			} else {
				JExpression methodCall = new JMethodCallExpression(popName,
						new JExpression[0]);
				methodCall.setType(tapeType);
				return methodCall;
			}
		}
					
		private JExpression dynamicPop(SIRPopExpression self, CType tapeType) {
			InputPort inputPort = ((InterSSGChannel) inputChannel).getEdge()
					.getDest();
			String threadId = filterToThreadId.get(
					inputPort.getSSG().getTopFilters()[0]).toString();

			String buffer = "dyn_buf_" + threadId;
			JExpression dyn_queue = new JEmittedTextExpression(buffer);
			JExpression index = new JEmittedTextExpression(threadId);
						
			int num_multipliers = (dominators.get(filter.toString()) == null) ? 0 : dominators.get(filter.toString()).size();   
			
			JExpression num_dominated = new JIntLiteral(num_multipliers);			
			JExpression dominated = new JEmittedTextExpression("multipliers");
			if (self.getNumPop() > 1) {
				return new JMethodCallExpression(popManyName,
						new JExpression[] { dyn_queue, index, num_dominated, dominated,
								new JIntLiteral(self.getNumPop()) });
			} else {
				JExpression methodCall = new JMethodCallExpression(popName,
						new JExpression[] { dyn_queue, index, num_dominated, dominated });
				methodCall.setType(tapeType);
				return methodCall;
			}
		}

		@SuppressWarnings("rawtypes")
		public void init(WorkNode filter, Channel inputChannel,
				Channel outputChannel, String peekName, String popManyName,
				String popName, String pushName,			
				Map<Filter, Integer> filterToThreadId,
				Map<String, List<String>> dominators, boolean isDynamicPop,
				boolean isDynamicPush) {
			this.filter = filter;
			this.peekName = peekName;
			this.popManyName = popManyName;
			this.popName = popName;		
			this.pushName = pushName;
			this.inputChannel = inputChannel;
			this.outputChannel = outputChannel;
			this.filterToThreadId = filterToThreadId;
			this.dominators = dominators;
			this.isDynamicPop = isDynamicPop;
			this.isDynamicPush = isDynamicPush;
		}

		@Override
		public Object visitPeekExpression(SIRPeekExpression self,
				CType tapeType, JExpression arg) {
			if (isDynamicPop) {
				InputPort inputPort = ((InterSSGChannel) inputChannel)
						.getEdge().getDest();
				String threadId = filterToThreadId.get(
						inputPort.getSSG().getTopFilters()[0]).toString();
				String buffer = "dyn_buf_" + threadId;
				JExpression dyn_queue = new JEmittedTextExpression(buffer);
				JExpression index = new JEmittedTextExpression(threadId);
				JExpression newArg = (JExpression) arg.accept(this);
                JExpression dominated = new JEmittedTextExpression("multipliers");				                
                int num_multipliers = (dominators.get(filter.toString()) == null) ? 0 : dominators.get(filter.toString()).size();   
                JExpression num_dominated = new JIntLiteral(num_multipliers);
				JExpression methodCall = new JMethodCallExpression(peekName,
						new JExpression[] { dyn_queue, index, num_dominated, dominated, newArg});
				methodCall.setType(tapeType);
				return methodCall;

			} else {
				JExpression newArg = (JExpression) arg.accept(this);
				JExpression methodCall = new JMethodCallExpression(peekName,
						new JExpression[] { newArg });
				methodCall.setType(tapeType);
				return methodCall;
			}
		}

		@Override
		public Object visitPopExpression(SIRPopExpression self, CType tapeType) {
		    
		    System.out.println("ProcessFilterWorkNode.visitPopExpression filter=" 
		            + filter.getWorkNodeContent()
		            + " filter.getPrevious()=" 
		            + filter.getPrevious().getParent().getWorkNode());

		    // We have to special case when there is a dynamic pop rate 
		    // that follows a FileReader. First we check if this is the 
		    // first filter in the ssg.
		    StaticSubGraph ssg = filter.getParent().getStaticSubGraph();
		    if (ssg.getFilterGraph()[0].equals(filter.getParent())) {
		        System.out.println("ProcessFilterWorkNode.visitPopExpression filter=" 
	                    + filter.getWorkNodeContent()
	                    + " is first in SSG"); 
		        // If it is, then check what SSG it is connected to. 
		        // If that SSG has only 1 filter, and that filter is a
		        // FileReader, then we should have a static pop instead
		        // of a dynamic pop
		        List<InterSSGEdge> edges = ssg.getInputPort().getLinks();
	            for (InterSSGEdge edge : edges) {
	                OutputPort outputPort = edge.getSrc();
	                StaticSubGraph outputSSG = outputPort.getSSG();
	                Filter[] filterGraph = outputSSG.getFilterGraph();
	                if (filterGraph.length == 1) {
	                    System.out.println("ProcessFilterWorkNode.visitPopExpression filterGraph[0]=" + filterGraph[0].getWorkNode().isFileInput());   
	                    if (filterGraph[0].getWorkNode().isFileInput()) {
	                      return staticPop(self, tapeType);
	                  }	                    	                    	                    
	                }	                
	            }		        		       		       
		    }
			if (isDynamicPop) {
				return dynamicPop(self, tapeType);
			} else {
				return staticPop(self, tapeType);
			}
		}

		@Override
		public Object visitPushExpression(SIRPushExpression self,
				CType tapeType, JExpression arg) {
			JExpression newArg = (JExpression) arg.accept(this);
			if (isDynamicPush) {

				InterSSGEdge edge = ((InterSSGChannel) outputChannel).getEdge();
				InputPort inputPort = edge.getDest();

				String threadId = filterToThreadId.get(
						inputPort.getSSG().getTopFilters()[0]).toString();

				String buffer = "dyn_buf_" + threadId;
				JExpression dyn_queue = new JEmittedTextExpression(buffer);
				return new JMethodCallExpression(pushName, new JExpression[] {
						dyn_queue, newArg });
			} else {
				return new JMethodCallExpression(pushName,
						new JExpression[] { newArg });

			}
		}

	}

	/** print debugging info? */
	public static boolean debug = false;

	private static int uid = 0;

	/** set of filters for which we have written basic code. */
	// uses WeakHashMap to be self-cleaning, but now have to insert some value.
	protected static Map<InternalFilterNode, Boolean> basicCodeWritten = new WeakHashMap<InternalFilterNode, Boolean>();

	private static PushPopReplacingVisitor pushPopReplacingVisitor = new PushPopReplacingVisitor();

	/**
	 * Get code for a filter. If code not yet made, then makes it.
	 * 
	 * @param filter
	 *            A FilterSliceNode for which we want code.
	 * @param inputChannel
	 *            The input channel -- specified routines to call to replace
	 *            peek, pop.
	 * @param outputChannel
	 *            The output channel -- specified routeines to call to replace
	 *            push.
	 * @param backEndBits
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static CodeStoreHelper getFilterCode(WorkNode filter,
			Channel inputChannel, Channel outputChannel,
			SMPBackEndFactory backEndBits, boolean isDynamicPop,
			boolean isDynamicPush) {

		CodeStoreHelper filterCode = CodeStoreHelper
				.findHelperForSliceNode(filter);
		if (filterCode == null) {
			filterCode = makeFilterCode(filter, inputChannel, outputChannel,
					backEndBits, isDynamicPop, isDynamicPush);
			CodeStoreHelper.addHelperForSliceNode(filter, filterCode);
		}
		return filterCode;
	}

	public static int getUid() {
		return uid++;
	}

	
	@SuppressWarnings("rawtypes")
    private static boolean isSourceEdge(Channel inputChannel) {    	  
        Object edgeObj = inputChannel.getEdge();
	    // I don't believe we need to check for the case of if (edgeObj instanceof InterSSGEdge), 
	    // since we don't have dynamic rates from a FileReader        
        if (edgeObj instanceof IntraSSGEdge) {
            return ((InternalFilterNode) ((IntraSSGEdge) edgeObj).getSrc()).isInputSlice();                        
        }
        return false;
	}
	
	/**
	 * Take a code unit (here a FilterContent) and return one with all push,
	 * peek, pop replaced with calls to channel routines. Clones the input
	 * methods and munges on the clones, further changes to the returned code
	 * will not affect the methods of the input code unit.
	 * 
	 * @param code
	 *            The code (fields and methods)
	 * @param inputChannel
	 *            The input channel -- specifies routines to call to replace
	 *            peek, pop.
	 * @param outputChannel
	 *            The output channel -- specifies routines to call to replace
	 *            push.
	 * @return a CodeStoreHelper with no push, peek, or pop instructions in the
	 *         methods.
	 */
	private static CodeStoreHelper makeFilterCode(WorkNode filter,
			@SuppressWarnings("rawtypes") Channel inputChannel,
			@SuppressWarnings("rawtypes") Channel outputChannel,
			SMPBackEndFactory backEndFactory, final boolean isDynamicPop,
			final boolean isDynamicPush) {

		final String peekName;
		final String popName;
		final String pushName;
		final String popManyName;
		
		if (inputChannel != null) {
		    isSourceEdge(inputChannel);
		}

		if (inputChannel != null) {
			peekName = inputChannel.peekMethodName();
			popName = inputChannel.popMethodName();
			popManyName = inputChannel.popManyMethodName();
		} else {
			peekName = "/* peek from non-existent channel */";
			popName = "/* pop() from non-existent channel */";
			popManyName = "/* pop(N) from non-existent channel */";
		}

		if (outputChannel != null) {
			pushName = outputChannel.pushMethodName();
		} else {
			pushName = "/* push() to non-existent channel */";
		}

		CodeStoreHelper helper = backEndFactory.getCodeStoreHelper(filter);
		JMethodDeclaration[] methods = helper.getMethods();

		Map<Filter, Integer> filterToThreadId = ThreadMapper.getMapper().getFilterToThreadId();		        
		Map<String, List<String>> dominators = ThreadMapper.getMapper().getDominators();

		pushPopReplacingVisitor.init(filter, inputChannel, outputChannel,
				peekName, popManyName, popName, pushName, filterToThreadId,
				dominators, isDynamicPop, isDynamicPush);

		for (JMethodDeclaration method : methods) {

		    int num_multipliers = (dominators.get(filter.toString()) == null) ? 0 : dominators.get(filter.toString()).size();   
		    
		    String call = "int* multipliers[" + num_multipliers + "] = {";		    		    
		    if (dominators.get(filter.toString()) != null) {
		      int j = 0;
		      for (String d : dominators.get(filter.toString())) {
		          if (j != 0) {
		              call += ", ";
		          }
		          call += "&" + d + "_multiplier";
		          j++;
		      }		        		        
		    }
            call += "}";
		    		    
		    method.addStatementFirst(new JExpressionStatement(
		                new JEmittedTextExpression(call)));
		    
			method.accept(pushPopReplacingVisitor);
			// Add markers to code for debugging of emitted code:
			String methodName = "filter "
					+ filter.getWorkNodeContent().getName() + "."
					+ method.getName();
			method.addStatementFirst(new SIRBeginMarker(methodName));
			method.addStatement(new SIREndMarker(methodName));
		}

		return helper;
	}

	protected CodeStoreHelper filterCode;
	protected SMPComputeCodeStore codeStore;
	protected WorkNode filterNode;
	protected SchedulingPhase whichPhase;
	protected SMPBackEndFactory backEndFactory;
	protected Core location;


	/**
	 * Create a new instance of a ProcessFilterWorkNode
	 */
	public ProcessFilterWorkNode() {
		/* do nothing */
	}

	/**
	 * Create code for a FilterSliceNode. May request creation of channels, and
	 * cause Process{Input/Filter/Output}SliceNode to be called for other slice
	 * nodes.
	 * 
	 * @param filterNode
	 *            the filterNode that needs code generated.
	 * @param whichPhase
	 *            a scheduling phase {@link SchedulingPhase}
	 * @param backEndFactory
	 *            a BackEndFactory to access layout, etc.
	 */
	public void doit(WorkNode filterNode, SchedulingPhase whichPhase,
			SMPBackEndFactory backEndFactory) {
		this.filterNode = filterNode;
		this.whichPhase = whichPhase;
		this.backEndFactory = backEndFactory;
		location = backEndFactory.getLayout().getComputeNode(filterNode);
		assert location != null;
		codeStore = location.getComputeCode();
		// remember that this tile has code that needs to execute
		codeStore.setHasCode();

		filterCode = CodeStoreHelper.findHelperForSliceNode(filterNode);
		// We should only generate code once for a filter node.

		StaticSubGraph ssg = backEndFactory.getScheduler().getGraphSchedule()
				.getSSG();
		boolean hasDynamicInput = false;
		boolean hasDynamicOutput = false;

		Filter[] graph = ssg.getFilterGraph();
		int last = graph.length - 1;

		// A particular filter will only have dynamic input if it is
		// the top node of an SSG, and if the SSG has dynamic input.s		
		if (filterNode.equals(graph[0].getWorkNode())
				&& (filterNode.getParent().getInputNode().getType() != CStdType.Void)) {
			hasDynamicInput = ssg.hasDynamicInput();
		}

		if (filterNode.equals(graph[last].getWorkNode())) {
			hasDynamicOutput = ssg.hasDynamicOutput();
		}

		if (filterCode == null) {

			@SuppressWarnings("rawtypes")
			Channel inputBuffer = null;
			@SuppressWarnings("rawtypes")
			Channel outputBuffer = null;

			if (hasDynamicInput) {
				inputBuffer = InterSSGChannel.getInputBuffer(filterNode);
			} else if (backEndFactory.sliceHasUpstreamChannel(filterNode
					.getParent())) {
				inputBuffer = RotatingBuffer.getInputBuffer(filterNode);
			}

			if (hasDynamicOutput) {
				outputBuffer = InterSSGChannel.getOutputBuffer(filterNode, ssg);
			} else if (backEndFactory.sliceHasDownstreamChannel(filterNode
					.getParent())) {
				outputBuffer = RotatingBuffer.getOutputBuffer(filterNode);
			}

			filterCode = getFilterCode(filterNode, inputBuffer, outputBuffer,
					backEndFactory, hasDynamicInput, hasDynamicOutput);
		}

		switch (whichPhase) {
		case PREINIT:
			standardPreInitProcessing();			
			break;
		case INIT:
			standardInitProcessing();
			break;
		case PRIMEPUMP:
			standardPrimePumpProcessing(hasDynamicInput);
			break;
		case STEADY:
			standardSteadyProcessing(hasDynamicInput);
			break;
		}
	}
	
	protected void standardInitProcessing() {
		// Have the main function for the CodeStore call out init.
		codeStore.addInitFunctionCall(filterCode.getInitMethod());
		JMethodDeclaration workAtInit = filterCode.getInitStageMethod();
		if (workAtInit != null) {
			// if there are calls to work needed at init time then add
			// method to general pool of methods
			codeStore.addMethod(workAtInit);
			// and add call to list of calls made at init time.
			// Note: these calls must execute in the order of the
			// initialization schedule -- so caller of this routine
			// must follow order of init schedule.
			codeStore.addInitStatement(new JExpressionStatement(null,
					new JMethodCallExpression(null, new JThisExpression(null),
							workAtInit.getName(), new JExpression[0]), null));
		}
	}

	protected void standardPreInitProcessing() {

	}

	protected void standardPrimePumpProcessing(boolean hasDynamicInput) {
		// TODO: We need to change this so we have the correct prime pump
		// processing.
		if (hasDynamicInput) {
			System.out
					.println("WARNING: need to change ProcessFilterWorkNode.standardPrimePumpProcessing to have the correct schedule");
			return;
		}
		JMethodDeclaration primePump = filterCode.getPrimePumpMethod();
		if (primePump != null && !codeStore.hasMethod(primePump)) {
			// Add method -- but only once
			codeStore.addMethod(primePump);
		}
		if (primePump != null) {
			// for each time this method is called, it adds another call
			// to the primePump routine to the initialization.
			codeStore.addInitStatement(new JExpressionStatement(null,
					new JMethodCallExpression(null, new JThisExpression(null),
							primePump.getName(), new JExpression[0]), null));

		}
	}

	protected void standardSteadyProcessing(boolean isDynamicPop) {
	    JBlock steadyBlock = filterCode.getSteadyBlock();
		List<JStatement> tokenWrites = filterCode.getTokenWrite();

		if (!basicCodeWritten.containsKey(filterNode)) {
			codeStore.addFields(filterCode.getFields());
			codeStore.addMethods(filterCode.getUsefulMethods());
			if (filterNode.getWorkNodeContent() instanceof OutputContent) {
				codeStore.addCleanupStatement(((OutputContent) filterNode
						.getWorkNodeContent()).closeFile());
			}
			basicCodeWritten.put(filterNode, true);
		}

		if (isDynamicPop) {
			
			Map<Filter, Integer> filterToThreadId = ThreadMapper.getMapper()
					.getFilterToThreadId();
			String threadId = filterToThreadId.get(filterNode.getParent())
					.toString();
		
			int threadIndex = Integer.parseInt(threadId);
			codeStore.addThreadHelper(threadIndex, steadyBlock);
			codeStore.addSteadyThreadCall(threadIndex);
			for (JStatement stmt : tokenWrites ) {
			    codeStore.addSteadyLoopStatement(stmt);
			}			
			
		} else {		 
		    for (JStatement stmt : tokenWrites ) {
		        steadyBlock.addStatement(stmt);
            }   
		    codeStore.addSteadyLoopStatement(steadyBlock);
		
		}
		if (debug) {
			// debug info only: expected splitter and joiner firings.
			System.err.print("(Filter"
					+ filterNode.getWorkNodeContent().getName());
			System.err.print(" "
					+ WorkNodeInfo.getFilterInfo(filterNode).getMult(
							SchedulingPhase.INIT));
			System.err.print(" "
					+ WorkNodeInfo.getFilterInfo(filterNode).getMult(
							SchedulingPhase.STEADY));
			System.err.println(")");
			System.err.print("(Joiner joiner_"
					+ filterNode.getWorkNodeContent().getName());
			System.err.print(" "
					+ WorkNodeInfo.getFilterInfo(filterNode)
							.totalItemsReceived(SchedulingPhase.INIT));
			System.err.print(" "
					+ WorkNodeInfo.getFilterInfo(filterNode)
							.totalItemsReceived(SchedulingPhase.STEADY));
			System.err.println(")");
			System.err.print("(Splitter splitter_"
					+ filterNode.getWorkNodeContent().getName());
			System.err.print(" "
					+ WorkNodeInfo.getFilterInfo(filterNode).totalItemsSent(
							SchedulingPhase.INIT));
			System.err.print(" "
					+ WorkNodeInfo.getFilterInfo(filterNode).totalItemsSent(
							SchedulingPhase.STEADY));
			System.err.println(")");
		}

	}

}
