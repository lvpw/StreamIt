/*
 * LIRVisitor.java: visit StreaMIT Low IR nodes
 * $Id: SLIREmptyVisitor.java,v 1.22 2006-10-27 20:48:55 dimock Exp $
 */

package at.dms.kjc;

import java.util.List;

import at.dms.kjc.*;
import at.dms.kjc.lir.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.JVectorLiteral;

/**
 * This visitor is for visiting statement-level constructs in the
 * streamit IR.  It visits both high-level constructs like
 * SIRInitStatement that never appear in the LIR, as well as low-level
 * constructs like LIRSetPeek that never appear in the high IR.
 */
public class SLIREmptyVisitor extends KjcEmptyVisitor 
    implements Constants, SLIRVisitor
{

    /**
     * SIR NODES.
     */

    /**
     * Visits an init statement.
     */
    public void visitInitStatement(SIRInitStatement self,
                                   SIRStream target) {
        for (int i=0; i<self.getArgs().size(); i++) {
            ((JExpression)self.getArgs().get(i)).accept(this);
        }
    }

    /**
     * Visits an interface table.
     */
    public void visitInterfaceTable(SIRInterfaceTable self) {}

    /**
     * Visits a latency.
     */
    public void visitLatency(SIRLatency self) {}

    /**
     * Visits a max latency.
     */
    public void visitLatencyMax(SIRLatencyMax self) {}

    /**
     * Visits a latency range.
     */
    public void visitLatencyRange(SIRLatencyRange self) {}

    /**
     * Visits a latency set.
     */
    public void visitLatencySet(SIRLatencySet self) {}

    public void visitCreatePortalExpression(SIRCreatePortal self) {}

    /**
     * Visits a message statement.
     */
    public void visitMessageStatement(SIRMessageStatement self,
                                      JExpression portal,
                                      String iname,
                                      String ident,
                                      JExpression[] args,
                                      SIRLatency latency) {
        portal.accept(this);
        for (int i=0; i<args.length; i++) {
            args[i].accept(this);
        }
        latency.accept(this);
    }

    /**
     * Visits a range expression.
     */
    public void visitRangeExpression(SIRRangeExpression self) {
        self.getMin().accept(this);
        self.getAve().accept(this);
        self.getMax().accept(this);
    }

    /**
     * Visits a dynamic token.
     */
    public void visitDynamicToken(SIRDynamicToken self) {
    }
    
    /**
     * Visits an iteration count expression.
     */
	public void visitIterationExpression(
			SIRIterationExpression sirIterationExpression) {
	}
    
    /**
     * Visits a peek expression.
     */
    public void visitPeekExpression(SIRPeekExpression self,
                                    CType tapeType,
                                    JExpression arg) {
        arg.accept(this);
    }

    /**
     * Visits a pop expression.
     */
    public void visitPopExpression(SIRPopExpression self,
                                   CType tapeType) {
    }

    /**
     * Visits a message-receiving portal.
     */
    public void visitPortal(SIRPortal self) {
    }

    /**
     * Visits a print statement.
     */
    public void visitPrintStatement(SIRPrintStatement self,
                                    JExpression arg) {
        arg.accept(this);
    }

    /**
     * Visits a push expression.
     */
    public void visitPushExpression(SIRPushExpression self,
                                    CType tapeType,
                                    JExpression arg) {
        arg.accept(this);
    }

    /**
     * Visits a register-receiver statement.
     */
    public void visitRegReceiverStatement(SIRRegReceiverStatement self,
                                          JExpression portal,
                                          SIRStream receiver,
                                          JMethodDeclaration[] methods) {
        portal.accept(this);
    }

    /**
     * Visits a register-sender statement.
     */
    public void visitRegSenderStatement(SIRRegSenderStatement self,
                                        String portal,
                                        SIRLatency latency) {
        latency.accept(this);
    }


    /**
     * Visit SIRMarker.
     */
    public void visitMarker(SIRMarker self) {
    }

    /**
     * LIR NODES.
     */

    /**
     * Visits a function pointer.
     */
    public void visitFunctionPointer(LIRFunctionPointer self,
                                     String name) {
    }
    
    /**
     * Visits an LIR node.
     */
    public void visitNode(LIRNode self) {}

    /**
     * Visits an LIR register-receiver statement.
     */
    public void visitRegisterReceiver(LIRRegisterReceiver self,
                                      JExpression streamContext,
                                      SIRPortal portal,
                                      String childName,
                                      SIRInterfaceTable itable) {
        streamContext.accept(this);
        portal.accept(this);
        itable.accept(this);
    }

    /**
     * Visits a child registration node.
     */
    public void visitSetChild(LIRSetChild self,
                              JExpression streamContext,
                              String childType,
                              String childName) {
        streamContext.accept(this);
    }
    
    /**
     * Visits a decoder registration node.
     */
    public void visitSetDecode(LIRSetDecode self,
                               JExpression streamContext,
                               LIRFunctionPointer fp) {
        streamContext.accept(this);
        fp.accept(this);
    }

    /**
     * Visits a feedback loop delay node.
     */
    public void visitSetDelay(LIRSetDelay self,
                              JExpression data,
                              JExpression streamContext,
                              int delay,
                              CType type,
                              LIRFunctionPointer fp) {
        data.accept(this);
        streamContext.accept(this);
        fp.accept(this);
    }
    
    /**
     * Visits a file reader.
     */
    public void visitFileReader(LIRFileReader self) {
        self.getStreamContext().accept(this);
    }
    
    /**
     * Visits a file writer.
     */
    public void visitFileWriter(LIRFileWriter self) {
        self.getStreamContext().accept(this);
    }

    /**
     * Visits an identity creator.
     */
    public void visitIdentity(LIRIdentity self) {
        self.getStreamContext().accept(this);
    }
    
    /**
     * Visits an encoder registration node.
     */
    public void visitSetEncode(LIRSetEncode self,
                               JExpression streamContext,
                               LIRFunctionPointer fp) {
        streamContext.accept(this);
        fp.accept(this);
    }

    /**
     * Visits a joiner-setting node.
     */
    public void visitSetJoiner(LIRSetJoiner self,
                               JExpression streamContext,
                               SIRJoinType type,
                               int ways,
                               int[] weights) {
        streamContext.accept(this);
    }
    
    /**
     * Visits a peek-rate-setting node.
     */
    public void visitSetPeek(LIRSetPeek self,
                             JExpression streamContext,
                             int peek) {
        streamContext.accept(this);
    }
    
    /**
     * Visits a pop-rate-setting node.
     */
    public void visitSetPop(LIRSetPop self,
                            JExpression streamContext,
                            int pop) {
        streamContext.accept(this);
    }
    
    /**
     * Visits a push-rate-setting node.
     */
    public void visitSetPush(LIRSetPush self,
                             JExpression streamContext,
                             int push) {
        streamContext.accept(this);
    }

    /**
     * Visits a splitter-setting node.
     */
    public void visitSetSplitter(LIRSetSplitter self,
                                 JExpression streamContext,
                                 SIRSplitType type,
                                 int ways,
                                 int[] weights) {
        streamContext.accept(this);
    }
    
    /**
     * Visits a stream-type-setting node.
     */
    public void visitSetStreamType(LIRSetStreamType self,
                                   JExpression streamContext,
                                   LIRStreamType streamType) {
        streamContext.accept(this);
    }
    
    /**
     * Visits a work-function-setting node.
     */
    public void visitSetWork(LIRSetWork self,
                             JExpression streamContext,
                             LIRFunctionPointer fn) {
        streamContext.accept(this);
        fn.accept(this);
    }

    /**
     * Visits a tape registerer.
     */
    public void visitSetTape(LIRSetTape self,
                             JExpression streamContext,
                             JExpression srcStruct,
                             JExpression dstStruct,
                             CType type,
                             int size) {
        streamContext.accept(this);
        srcStruct.accept(this);
        dstStruct.accept(this);
    }

    /**
     * Visits a main function contents.
     */
    public void visitMainFunction(LIRMainFunction self,
                                  String typeName,
                                  LIRFunctionPointer init,
                                  List<JStatement> initStatements) {
        init.accept(this);
        for (int i=0; i<initStatements.size(); i++) {
            initStatements.get(i).accept(this);
        }
    }


    /**
     * Visits a set body of feedback loop.
     */
    public void visitSetBodyOfFeedback(LIRSetBodyOfFeedback self,
                                       JExpression streamContext,
                                       JExpression childContext,
                                       CType inputType,
                                       CType outputType,
                                       int inputSize,
                                       int outputSize) {
        streamContext.accept(this);
        childContext.accept(this);
    }


    /**
     * Visits a set loop of feedback loop.
     */
    public void visitSetLoopOfFeedback(LIRSetLoopOfFeedback self,
                                       JExpression streamContext,
                                       JExpression childContext,
                                       CType inputType,
                                       CType outputType,
                                       int inputSize,
                                       int outputSize) {
        streamContext.accept(this);
        childContext.accept(this);
    }

    /**
     * Visits a set a parallel stream.
     */
    public void visitSetParallelStream(LIRSetParallelStream self,
                                       JExpression streamContext,
                                       JExpression childContext,
                                       int position,
                                       CType inputType,
                                       CType outputType,
                                       int inputSize,
                                       int outputSize) {
        streamContext.accept(this);
        childContext.accept(this);
    }

    /**
     * Visits a work function entry.
     */
    public void visitWorkEntry(LIRWorkEntry self)
    {
        self.getStreamContext().accept(this);
    }

    /**
     * Visits a work function exit.
     */
    public void visitWorkExit(LIRWorkExit self)
    {
        self.getStreamContext().accept(this);
    }

    /**
     * Visits InlineAssembly
     */
    public void visitInlineAssembly(InlineAssembly self,String[] asm,String[] input,String[] clobber) {}
    
    /**
     * Visits a vector literal
     */
    public void visitVectorLiteral(JVectorLiteral self, JLiteral scalar) {
        scalar.accept(this);
    }

}

