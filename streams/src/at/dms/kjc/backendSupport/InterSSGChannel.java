/**
 * 
 */
package at.dms.kjc.backendSupport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import at.dms.kjc.CEmittedTextType;
import at.dms.kjc.CStdType;
import at.dms.kjc.CType;
import at.dms.kjc.JBlock;
import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JFormalParameter;
import at.dms.kjc.JIfStatement;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JStatement;
import at.dms.kjc.slir.Filter;
import at.dms.kjc.slir.InterSSGEdge;
import at.dms.kjc.slir.StaticSubGraph;
import at.dms.kjc.slir.StreamGraph;
import at.dms.kjc.slir.WorkNode;
import at.dms.kjc.smp.Core;
import at.dms.kjc.smp.Util;
import at.dms.util.Utils;

/**
 * @author soule
 * 
 */
public class InterSSGChannel extends Channel<InterSSGEdge> {

	/** a set of all the buffer types in the application */
	protected static HashSet<String> types;

	/** maps each WorkNode to Input/OutputRotatingBuffers */
	protected static HashMap<WorkNode, InterSSGChannel> inputBuffers;
	protected static HashMap<WorkNode, InterSSGChannel> outputBuffers;

	static {
		types = new HashSet<String>();
		inputBuffers = new HashMap<WorkNode, InterSSGChannel>();
		outputBuffers = new HashMap<WorkNode, InterSSGChannel>();
	}

	/**
	 * @param streamGraph
	 */
	public static void createBuffers(StreamGraph streamGraph) {
		createInputBuffers(streamGraph);
		createOutputBuffers(streamGraph);
	}

	/**
	 * 
	 * @param streamGraph
	 */
	private static void createInputBuffers(StreamGraph streamGraph) {
		for (StaticSubGraph ssg : streamGraph.getSSGs()) {
			Filter top = ssg.getTopFilters()[0];
			List<InterSSGEdge> links = ssg.getInputPort().getLinks();
			if (links.size() == 0) {
				continue;
			}
			InterSSGEdge edge = ssg.getInputPort().getLinks().get(0);
			InterSSGChannel channel = new InterSSGChannel(edge);
			if (ssg.getTopFilters() != null) {
				top = ssg.getTopFilters()[0];
				setInputBuffer(top.getWorkNode(), channel);
			} else {
				assert false : "InterSSGChannel::createInputBuffers() : ssg.getTopFilters() is null";
			}
		}
	}

	/**
	 * 
	 * @param streamGraph
	 */
	private static void createOutputBuffers(StreamGraph streamGraph) {
		for (StaticSubGraph ssg : streamGraph.getSSGs()) {
			List<InterSSGEdge> links = ssg.getOutputPort().getLinks();
			if (links.size() == 0) {
				continue;
			}
			InterSSGEdge edge = ssg.getOutputPort().getLinks().get(0);
			InterSSGChannel channel = new InterSSGChannel(edge);
			if (ssg.getTopFilters() != null) {
				Filter top = ssg.getTopFilters()[0];
				outputBuffers.put(top.getWorkNode(), channel);
			} else {
				assert false : "InterSSGChannel::createOutputBuffers() : ssg.getTopFilters() is null";
			}
		}

	}

	/**
	 * Return the input buffer associated with the filter node.
	 * 
	 * @param fsn
	 *            The filter node in question.
	 * @return The input buffer of the filter node.
	 */
	public static InterSSGChannel getInputBuffer(WorkNode fsn) {
		return inputBuffers.get(fsn);
	}

	/**
	 * 
	 * @param t
	 * @return
	 */
	public static Set<InterSSGChannel> getInputBuffersOnCore(Core t) {
		HashSet<InterSSGChannel> set = new HashSet<InterSSGChannel>();
		for (InterSSGChannel b : inputBuffers.values()) {
			set.add(b);
		}
		return set;
	}

	/**
	 * @param filterNode
	 * @param ssg
	 * @return
	 */
	public static Channel<InterSSGEdge> getOutputBuffer(WorkNode filterNode,
			StaticSubGraph ssg) {
		InterSSGEdge edge = ssg.getOutputPort().getLinks().get(0);
		return new InterSSGChannel(edge);
	}

	/**
	 * 
	 * @param n
	 * @return
	 */
	public static Set<InterSSGChannel> getOutputBuffersOnCore(Core n) {
		System.out.println("InterSSGChannel::getOutputBuffersOnCore(n)");
		System.out
				.println("InterSSGChannel::getOutputBuffersOnCore(n) outputBuffers.size()=="
						+ outputBuffers.size());
		HashSet<InterSSGChannel> set = new HashSet<InterSSGChannel>();
		for (InterSSGChannel b : outputBuffers.values()) {
			// if
			// (SMPBackend.scheduler.getComputeNode(b.getFilterNode()).equals(t))
			set.add(b);
		}
		return set;
	}

	/**
	 * 
	 * @param node
	 * @param buf
	 */
	public static void setInputBuffer(WorkNode node, InterSSGChannel buf) {
		inputBuffers.put(node, buf);
	}

	/**
	 * 
	 * @param node
	 * @param buf
	 */
	public static void setOutputBuffer(WorkNode node, InterSSGChannel buf) {
		outputBuffers.put(node, buf);
	}

	private WorkNode filterNode;

	/**
	 * @param edge
	 */
	protected InterSSGChannel(InterSSGEdge edge) {
		super(edge);
	}

	/**
	 * @return
	 */
	public List<JStatement> dataDecls() {
		// System.out.println("InterSSGChannel::dataDecls()");
		List<JStatement> statements = new LinkedList<JStatement>();
		JStatement stmt = new JExpressionStatement(new
				JEmittedTextExpression("static queue_ctx_ptr dyn_read_current;"));
		statements.add(stmt);		
		stmt = new JExpressionStatement(new
				JEmittedTextExpression("static queue_ctx_ptr dyn_write_current;"));
		statements.add(stmt);		
		return statements;
		//return new LinkedList<JStatement>();
	}

	/**
	 * Return the filter this buffer is associated with.
	 * 
	 * @return Return the filter this buffer is associated with.
	 */
	public WorkNode getFilterNode() {
		return filterNode;
	}

	/**
	 * @return
	 */
	public String peekMethodName() {
		return "dynamic_buffer_peek";
	}

	/**
	 * @return
	 */
	public String popManyMethodName() {
		return "dynamic_buffer_pop_many";
	}

	/**
	 * @return
	 */
	public String popMethodName() {
		return "queue_pop";
	}

	public String pushMethodName() {
		return "queue_push";
	}

	public JMethodDeclaration popMethod() {
		for (String str : types) {
			System.out.print("Type is: " + str);
		}
				
		JBlock methodBody = new JBlock();
		JBlock ifBody = new JBlock();
		
		ifBody.addStatement(Util.toStmt("/* Set Downstream Multiplier */"));
		ifBody.addStatement(Util.toStmt("/* *write_d_multiplier  = 0; */"));

		Utils.addSetFlag(ifBody, 0, "MASTER", "MASTER", "AWAKE");
		Utils.addSignal(ifBody, 0, "MASTER");	
		
		Utils.addCondWait(ifBody, 0, "DYN_READER", "DYN_READER", 
				Utils.makeEqualityCondition("q->size", "0"));				
				
		JIfStatement ifStatement = Utils.makeIfStatement(Utils.makeEqualityCondition("q->size", "0"), ifBody);						
				
		methodBody.addStatement(ifStatement);	
		
		String formalParamName = "q";
		CType formalParamType = new CEmittedTextType("queue_ctx_ptr");

		methodBody.addStatement(new JExpressionStatement(new JEmittedTextExpression("int elem = q->buffer[q->first]")));
		methodBody.addStatement(new JExpressionStatement(new JEmittedTextExpression("q->size--")));
		methodBody.addStatement(new JExpressionStatement(new JEmittedTextExpression("q->first = (q->first + 1) & q->max")));
		methodBody.addStatement(new JExpressionStatement(new JEmittedTextExpression("return elem")));

		//		queue_pop(queue_ctx_ptr q, TYPE * retval) {

		new JFormalParameter(formalParamType, formalParamName);
		
		//JFormalParameter param = 
		JMethodDeclaration popMethod = new JMethodDeclaration(CStdType.Integer, "queue_pop", 				
				new JFormalParameter[]{new JFormalParameter(formalParamType, formalParamName)},
				methodBody);
		return popMethod;		
	}

	public List<JStatement> readDeclsExtern() {
//		List<JStatement> statements = new LinkedList<JStatement>();
//		JStatement stmt = new JExpressionStatement(new JEmittedTextExpression(
//				"extern queue_ctx_ptr dyn_queue"));
//		statements.add(stmt);
//		return statements;
		return new LinkedList<JStatement>();
	}

	public List<JStatement> writeDeclsExtern() {
//		List<JStatement> statements = new LinkedList<JStatement>();
//		JStatement stmt = new JExpressionStatement(new JEmittedTextExpression(
//				"extern queue_ctx_ptr dyn_queue"));
//		statements.add(stmt);
//		return statements;
		return new LinkedList<JStatement>();
	}

}
