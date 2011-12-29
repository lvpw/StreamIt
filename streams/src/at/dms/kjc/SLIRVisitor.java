/*
 * LIRVisitor.java: visit StreaMIT Low IR nodes
 * $Id: SLIRVisitor.java,v 1.34 2006-10-27 20:48:55 dimock Exp $
 */

package at.dms.kjc;

import java.util.List;

import at.dms.kjc.lir.LIRFileReader;
import at.dms.kjc.lir.LIRFileWriter;
import at.dms.kjc.lir.LIRFunctionPointer;
import at.dms.kjc.lir.LIRIdentity;
import at.dms.kjc.lir.LIRMainFunction;
import at.dms.kjc.lir.LIRNode;
import at.dms.kjc.lir.LIRRegisterReceiver;
import at.dms.kjc.lir.LIRSetBodyOfFeedback;
import at.dms.kjc.lir.LIRSetChild;
import at.dms.kjc.lir.LIRSetDecode;
import at.dms.kjc.lir.LIRSetDelay;
import at.dms.kjc.lir.LIRSetEncode;
import at.dms.kjc.lir.LIRSetJoiner;
import at.dms.kjc.lir.LIRSetLoopOfFeedback;
import at.dms.kjc.lir.LIRSetParallelStream;
import at.dms.kjc.lir.LIRSetPeek;
import at.dms.kjc.lir.LIRSetPop;
import at.dms.kjc.lir.LIRSetPush;
import at.dms.kjc.lir.LIRSetSplitter;
import at.dms.kjc.lir.LIRSetStreamType;
import at.dms.kjc.lir.LIRSetTape;
import at.dms.kjc.lir.LIRSetWork;
import at.dms.kjc.lir.LIRStreamType;
import at.dms.kjc.lir.LIRWorkEntry;
import at.dms.kjc.lir.LIRWorkExit;
import at.dms.kjc.sir.SIRCreatePortal;
import at.dms.kjc.sir.SIRDynamicToken;
import at.dms.kjc.sir.SIRInitStatement;
import at.dms.kjc.sir.SIRInterfaceTable;
import at.dms.kjc.sir.SIRIterationExpression;
import at.dms.kjc.sir.SIRJoinType;
import at.dms.kjc.sir.SIRLatency;
import at.dms.kjc.sir.SIRLatencyMax;
import at.dms.kjc.sir.SIRLatencyRange;
import at.dms.kjc.sir.SIRLatencySet;
import at.dms.kjc.sir.SIRMarker;
import at.dms.kjc.sir.SIRMessageStatement;
import at.dms.kjc.sir.SIRPeekExpression;
import at.dms.kjc.sir.SIRPopExpression;
import at.dms.kjc.sir.SIRPortal;
import at.dms.kjc.sir.SIRPrintStatement;
import at.dms.kjc.sir.SIRPushExpression;
import at.dms.kjc.sir.SIRRangeExpression;
import at.dms.kjc.sir.SIRRegReceiverStatement;
import at.dms.kjc.sir.SIRRegSenderStatement;
import at.dms.kjc.sir.SIRSplitType;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.JVectorLiteral;

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
                            SIRStream target);

    /* Visits an interface table.
     */
    void visitInterfaceTable(SIRInterfaceTable self);
    
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

    void visitCreatePortalExpression(SIRCreatePortal self);

    /**
     * Visits a message statement.
     */
    void visitMessageStatement(SIRMessageStatement self,
                               JExpression portal,
                               String iname,
                               String ident,
                               JExpression[] args,
                               SIRLatency latency);

    /**
     * Visits a range expression.
     */
    void visitRangeExpression(SIRRangeExpression self);

    /**
     * Visits a dynamic token.
     */
    void visitDynamicToken(SIRDynamicToken self);

    /**
     * Visits a iteration expression.
     */
	void visitIterationExpression(SIRIterationExpression sirIterationExpression);
    
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
     * Visits a message-receiving portal.
     */
    void visitPortal(SIRPortal self);

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
                                   JExpression portal,
                                   SIRStream receiver,
                                   JMethodDeclaration[] methods);

    /**
     * Visits a register-sender statement.
     */
    void visitRegSenderStatement(SIRRegSenderStatement self,
                                 String portal,
                                 SIRLatency latency);


    /**
     * Visit SIRMarker.
     */
    void visitMarker(SIRMarker self);

    /**
     * LIR NODES.
     */

    /**
     * Visits a function pointer.
     */
    void visitFunctionPointer(LIRFunctionPointer self,
                              String name);

    /**
     * Visits a file reader.
     */
    void visitFileReader(LIRFileReader self);
    
    /**
     * Visits a file writer.
     */
    void visitFileWriter(LIRFileWriter self);

    /**
     * Visits an identity creator.
     */
    void visitIdentity(LIRIdentity self);
    
    /**
     * Visits an LIR node.
     */
    void visitNode(LIRNode self);

    /**
     * Visits an LIR register-receiver statement.
     */
    void visitRegisterReceiver(LIRRegisterReceiver self,
                               JExpression streamContext,
                               SIRPortal portal,
                               String childName,
                               SIRInterfaceTable itable);

    /**
     * Visits a child registration node.
     */
    void visitSetChild(LIRSetChild self,
                       JExpression streamContext,
                       String childType,
                       String childName);
    
    /**
     * Visits a decoder registration node.
     */
    void visitSetDecode(LIRSetDecode self,
                        JExpression streamContext,
                        LIRFunctionPointer fp);

    /**
     * Visits a feedback loop delay node.
     */
    void visitSetDelay(LIRSetDelay self,
                       JExpression data,
                       JExpression streamContext,
                       int delay,
                       CType type,
                       LIRFunctionPointer fp);
    
    /**
     * Visits an encoder registration node.
     */
    void visitSetEncode(LIRSetEncode self,
                        JExpression streamContext,
                        LIRFunctionPointer fp);

    /**
     * Visits a joiner-setting node.
     */
    void visitSetJoiner(LIRSetJoiner self,
                        JExpression streamContext,
                        SIRJoinType type,
                        int ways,
                        int[] weights);
    
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
     * Visits a splitter-setting node.
     */
    void visitSetSplitter(LIRSetSplitter self,
                          JExpression streamContext,
                          SIRSplitType type,
                          int ways,
                          int[] weights);
    
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

    /**
     * Visits a main function contents.
     */
    void visitMainFunction(LIRMainFunction self,
                           String typeName,
                           LIRFunctionPointer init,
                           List<JStatement> initStatements);


    /**
     * Visits a set body of feedback loop.
     */
    void visitSetBodyOfFeedback(LIRSetBodyOfFeedback self,
                                JExpression streamContext,
                                JExpression childContext,
                                CType inputType,
                                CType outputType,
                                int inputSize,
                                int outputSize);

    /**
     * Visits a set loop of feedback loop.
     */
    void visitSetLoopOfFeedback(LIRSetLoopOfFeedback self,
                                JExpression streamContext,
                                JExpression childContext,
                                CType inputType,
                                CType outputType,
                                int inputSize,
                                int outputSize);

    /**
     * Visits a set a parallel stream.
     */
    void visitSetParallelStream(LIRSetParallelStream self,
                                JExpression streamContext,
                                JExpression childContext,
                                int position,
                                CType inputType,
                                CType outputType,
                                int inputSize,
                                int outputSize);

    /**
     * Visits a work function entry.
     */
    void visitWorkEntry(LIRWorkEntry self);

    /**
     * Visits a work function exit.
     */
    void visitWorkExit(LIRWorkExit self);
    
    /**
     * Visit a vector literal value.
     */
    void visitVectorLiteral(JVectorLiteral self, JLiteral scalar);
}

