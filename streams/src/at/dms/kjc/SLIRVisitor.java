/*
 * LIRVisitor.java: visit StreaMIT Low IR nodes
 * $Id: SLIRVisitor.java,v 1.6 2001-10-03 13:29:12 thies Exp $
 */

package at.dms.kjc;

import at.dms.kjc.*;
import at.dms.kjc.lir.*;
import at.dms.kjc.sir.*;

/**
 * This visitor is for visiting statement-level constructs in the
 * streamit IR.  It visits both high-level constructs like
 * SIRInitStatement that never appear in the LIR, as well as low-level
 * constructs like LIRSetPeek that never appear in the low IR.
 */
public interface SLIRVisitor extends KjcVisitor
{

    /**
     * SIR NODES.
     */

    /**
     * Visits an init statement.
     */
    void visitInitStatement(SIRInitStatement self,
			    JExpression[] args,
			    SIRStream target);
    /**
     * Visits a latency.
     */
    void visitLatency(SIRLatency self);

    /**
     * Visits a max latency.
     */
    void visitLatencyMax(SIRLatencyMax self);

    /**
     * Visits a latency range.
     */
    void visitLatencyRange(SIRLatencyRange self);

    /**
     * Visits a latency set.
     */
    void visitLatencySet(SIRLatencySet self);

    /**
     * Visits a message statement.
     */
    void visitMessageStatement(SIRMessageStatement self,
			       String portal,
			       String ident,
			       JExpression[] args,
			       SIRLatency latency);

    /**
     * Visits a peek expression.
     */
    void visitPeekExpression(SIRPeekExpression self,
                             CType tapeType,
			     JExpression arg);

    /**
     * Visits a pop expression.
     */
    void visitPopExpression(SIRPopExpression self,
                            CType tapeType);

    /**
     * Visits a print statement.
     */
    void visitPrintStatement(SIRPrintStatement self,
			     JExpression arg);

    /**
     * Visits a push expression.
     */
    void visitPushExpression(SIRPushExpression self,
                             CType tapeType,
			     JExpression arg);

    /**
     * Visits a register-receiver statement.
     */
    void visitRegReceiverStatement(SIRRegReceiverStatement self,
				   String portal);

    /**
     * Visits a register-sender statement.
     */
    void visitRegSenderStatement(SIRRegSenderStatement self,
				 String portal,
				 SIRLatency latency);

    /**
     * LIR NODES.
     */

    /**
     * Visits a function pointer.
     */
    void visitFunctionPointer(LIRFunctionPointer self,
                              String name);
    
    /**
     * Visits an LIR node.
     */
    void visitNode(LIRNode self);

    /**
     * Visits a child registration node.
     */
    void visitSetChild(LIRSetChild self,
                       JExpression streamContext,
                       String childType,
		       String childName,
		       LIRFunctionPointer childInit);
    
    /**
     * Visits a decoder registration node.
     */
    void visitSetDecode(LIRSetDecode self,
                        JExpression streamContext,
                        LIRFunctionPointer fp);
    
    /**
     * Visits an encoder registration node.
     */
    void visitSetEncode(LIRSetEncode self,
                        JExpression streamContext,
                        LIRFunctionPointer fp);
    
    /**
     * Visits a peek-rate-setting node.
     */
    void visitSetPeek(LIRSetPeek self,
                      JExpression streamContext,
                      int peek);
    
    /**
     * Visits a pop-rate-setting node.
     */
    void visitSetPop(LIRSetPop self,
                     JExpression streamContext,
                     int pop);
    
    /**
     * Visits a push-rate-setting node.
     */
    void visitSetPush(LIRSetPush self,
                      JExpression streamContext,
                      int push);

    /**
     * Visits a stream-type-setting node.
     */
    void visitSetStreamType(LIRSetStreamType self,
                            JExpression streamContext,
                            LIRStreamType streamType);
    
    /**
     * Visits a work-function-setting node.
     */
    void visitSetWork(LIRSetWork self,
                      JExpression streamContext,
                      LIRFunctionPointer fn);

    /**
     * Visits a tape registerer.
     */
    void visitSetTape(LIRSetTape self,
                      JExpression streamContext,
		      JExpression srcStruct,
		      JExpression dstStruct,
		      CType type,
		      int size);
}

