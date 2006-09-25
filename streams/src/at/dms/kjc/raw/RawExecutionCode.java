package at.dms.kjc.raw;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.common.CommonConstants;
import at.dms.kjc.common.CommonUtils;
import at.dms.util.Utils;
import java.util.List;
import java.util.ListIterator;
import java.util.HashMap;

public class RawExecutionCode extends at.dms.util.Utils 
    implements FlatVisitor, Constants
{
    /*** fields for the var names we introduce ***/
    public static String recvBuffer = "__RECVBUFFER__";
    public static String recvBufferSize = "__RECVBUFFERSIZE__";
    public static String recvBufferBits = "__RECVBUFFERBITS__";

    //the output buffer for ratematching
    public static String sendBuffer = "__SENDBUFFER__";
    public static String sendBufferIndex = "__SENDBUFFERINDEX__";
    public static String rateMatchSendMethod = "__RATEMATCHSEND__";
    
    //recvBufferIndex points to the beginning of the tape
    public static String recvBufferIndex = "__RECVBUFFERINDEX__";
    //recvIndex points to the end of the tape
    public static String recvIndex = "_RECVINDEX__";

    public static String simpleIndex = "__SIMPLEINDEX__";
    
    public static String exeIndex = "__EXEINDEX__";
    public static String exeIndex1 = "__EXEINDEX__1__";

    /* didn't want to change all the references */
    public static String ARRAY_INDEX;
    public static String ARRAY_COPY;

    static {
        ARRAY_INDEX = CommonConstants.ARRAY_INDEX; 
        ARRAY_COPY = CommonConstants.ARRAY_COPY;
    }
    
    public static String initSchedFunction = "__RAWINITSCHED__";
    public static String steadySchedFunction = "__RAWSTEADYSCHED__";
    
    public static String receiveMethod = "static_receive_to_mem";
    public static String structReceiveMethodPrefix = "__popPointer";
    public static String arrayReceiveMethod = "__array_receive__";

    public static String rawMain = "__RAWMAIN__";
    

    //These next fields are set by calculateItems()
    //see my thesis for a better explanation
    //number of items to receive between preWork() and work()
    private int bottomPeek = 0; 
    //number of times the filter fires in the init schedule
    private int initFire = 0;
    //number of items to receive after initialization
    private int remaining = 0;
    

    //class to hold the local variables we create 
    //so we can pass these across functions
    class LocalVariables 
    {
        JVariableDefinition recvBuffer;
        JVariableDefinition recvBufferSize;
        JVariableDefinition recvBufferBits;
        JVariableDefinition recvBufferIndex;
        JVariableDefinition recvIndex;
        JVariableDefinition exeIndex;
        JVariableDefinition exeIndex1;
        JVariableDefinition[] ARRAY_INDEX;
        JVariableDefinition[] ARRAY_COPY;
        JVariableDefinition simpleIndex;
        JVariableDefinition sendBufferIndex;
        JVariableDefinition sendBuffer;
    }
    
    
    public static void doit(FlatNode top) 
    {
        top.accept((new RawExecutionCode()), null, true);
    }
    
    public void visitNode(FlatNode node) 
    {
        if (node.isFilter()){
            SIRFilter filter = (SIRFilter)node.contents;
            
            // remove any multi-pop statement, as communication code
            // we are about to generate does not handle it
            RemoveMultiPops.doit(filter);

            //Skip Identities now
            if(filter instanceof SIRIdentity ||
               filter instanceof SIRFileWriter ||
               filter instanceof SIRFileReader)
                return;
            if (!KjcOptions.decoupled)
                calculateItems(filter);
            System.out.print("Generating Raw Code: " + 
                             node.contents.getName() + " ");
        
            //attempt to generate direct communication code
            //(no buffer), if this returns true it was sucessful
            //and the code was produced 
            if (bottomPeek == 0 && 
                remaining == 0 &&
                DirectCommunication.doit((SIRFilter)node.contents)) {
                System.out.println("(Direct Communication)");
                return;
            }
        
            //otherwise generate code the old way
            LocalVariables localVariables = new LocalVariables();
        
            JBlock block = new JBlock(null, new JStatement[0], null);
        
            if (isSimple(filter))
                System.out.print("(simple) ");
            else if (noBuffer(filter))
                System.out.print("(no buffer) ");
            else {
                if (remaining > 0)
                    System.out.print("(remaining) ");
                if (filter.getPeekInt() > filter.getPopInt())
                    System.out.print("(peeking)");
        
            }
            System.out.println();
        
            createLocalVariables(node, block, localVariables);
            convertCommExps(filter, 
                            isSimple(filter),
                            localVariables);
        
            rawMainFunction(node, block, localVariables);
        
            //create the method and add it to the filter
            JMethodDeclaration rawMainFunct = 
                new JMethodDeclaration(null, 
                                       at.dms.kjc.Constants.ACC_PUBLIC,
                                       CStdType.Void,
                                       rawMain,
                                       JFormalParameter.EMPTY,
                                       CClassType.EMPTY,
                                       block,
                                       null,
                                       null);
            filter.addMethod(rawMainFunct);
        }
    } 

    //calcuate bottomPeek, initFire, remaining
    //see my thesis section 5.1.2
    void calculateItems(SIRFilter filter) 
    {
        int pop = filter.getPopInt();
        int peek = filter.getPeekInt();
    
        //set up prePop, prePeek
        int prePop = 0;
        int prePeek = 0;
    
        if (filter instanceof SIRTwoStageFilter) {
            prePop = ((SIRTwoStageFilter)filter).getInitPopInt();
            prePeek = ((SIRTwoStageFilter)filter).getInitPeekInt();
        }
    
        //the number of times this filter fires in the initialization
        //schedule
        initFire = 0;

        // initExec counts might be null if we're calling for work
        // estimation before exec counts have been determined.
        if (RawBackend.initExecutionCounts!=null) {
            Integer init = RawBackend.initExecutionCounts.
                get(Layout.getNode(Layout.getTile(filter)));
    
            if (init != null) 
                initFire = init.intValue();
        } else {
            // otherwise, we should be doing this only for work
            // estimation--check that the filter is the only thing in the graph
            assert filter.getParent()==null ||
                filter.getParent().size()==1 &&
                filter.getParent().getParent()==null:
                "Found null pointer where unexpected.";
        }
    
        //if this is not a twostage, fake it by adding to initFire,
        //so we always think the preWork is called
        if (!(filter instanceof SIRTwoStageFilter))
            initFire++;

        //the number of items produced by the upstream filter in
        //initialization
        int upStreamItems = 0;
    
        FlatNode node = Layout.getNode(Layout.getTile(filter));
        FlatNode previous = null;
    
        if (node.inputs > 0) {
            previous = node.incoming[0];
            //okay the number of items produced by the upstream splitter may not 
            //equal the number of items that produced by the filter feeding the splitter
            //now, since I do not map splitter, this discrepancy must be accounted for.  
            //We do not have an incoming buffer for ths splitter, so the data must
            //trickle down to the filter(s) that the splitter feeds
            if (previous.contents instanceof SIRSplitter) {
                upStreamItems = getPrevSplitterUpStreamItems(previous, node);
            }
            else {
                //upstream not a splitter, just get the number of executions
                upStreamItems = getUpStreamItems(RawBackend.initExecutionCounts,
                                                 node);
            }
        }
        
        //see my thesis for an explanation of this calculation
        if (initFire  - 1 > 0) {
            bottomPeek = Math.max(0, 
                                  peek - (prePeek - prePop));
        }
        else
            bottomPeek = 0;
    
        remaining = upStreamItems -
            (prePeek + 
             bottomPeek + 
             Math.max((initFire - 2), 0) * pop);
    
        System.out.println("Remaining for " + filter + " " + remaining + 
                           "(" + upStreamItems + " >>> " + 
                           (prePeek + 
                            bottomPeek + 
                            Math.max((initFire - 2), 0) * pop) + ")");
    }
    
    /* node is not directly connected upstream to a splitter, this function
       will calculate the number of items send to node */
    private int getUpStreamItems(HashMap<FlatNode, Integer> executionCounts, FlatNode node) 
    {
        //if the node has not incoming channels then just return 0
        if (node.inputs < 1)
            return 0;
    
        FlatNode previous = node.incoming[0];
    
        int prevPush = 0;
    
        //get the number of times the previous node executes in the 
        //schedule
        int prevInitCount = Util.getCountPrev(executionCounts, 
                                              previous, node);
    
        //get the push rate for the previous
        if (prevInitCount > 0) {
            if (previous.contents instanceof SIRSplitter || 
                previous.contents instanceof SIRJoiner) {
                prevPush = 1;
            }
            else
                prevPush = ((SIRFilter)previous.contents).getPushInt();
        }
    
        //push * executions
        int upStreamItems = (prevInitCount * prevPush);

        //System.out.println("previous: " + previous.getName());
        //System.out.println("prev Push: " + prevPush + " prev init: " + prevInitCount);

        //if the previous node is a two stage filter then count its initWork
        //in the initialItemsTo Receive
        if (previous != null && previous.contents instanceof SIRTwoStageFilter) {
            upStreamItems -= ((SIRTwoStageFilter)previous.contents).getPushInt();
            upStreamItems += ((SIRTwoStageFilter)previous.contents).getInitPushInt();
        }

        return upStreamItems;
    }


    /**
       If the filter's upstream neighbor is a splitter we may have a problem 
       where the filter feeding the splitter produces more data than the splitter
       passes on in the init stage.  So we need to recognize this and forward the data
       on to the filter(s) that the splitter feeds.  Remember, we do not map splitters
       so the data on the splitters input tape after the init stage is over must go somewhere.
    **/
    private int getPrevSplitterUpStreamItems(FlatNode prev, FlatNode node) 
    {
        double roundRobinMult = 1.0;
        
        //there is nothing feeding this splitter, so just return 0
        if (prev.inputs < 1) 
            return 0;
    
        //follow the channels backward until we get to a filter or joiner,
        //remembering the weights on the rr splitters that connect
        //the filter (joiner) to node, so we know the number of items passed to <node>
        FlatNode current = prev;
        FlatNode downstream = node;
        while (current.contents instanceof SIRSplitter) {
            if (!(((SIRSplitter)current.contents).getType() == SIRSplitType.DUPLICATE))
                roundRobinMult *= Util.getRRSplitterWeight(current, downstream);
            if (current.inputs < 1)
                return 0;
            downstream = current;
            current = current.incoming[0];
        }
    
        //now current must be a joiner or filter
        //get the number of item current produces
        int currentUpStreamItems = getUpStreamItems(RawBackend.initExecutionCounts,
                                                    current.edges[0]);
        System.out.println(currentUpStreamItems);
        /*
          if (getUpStreamItems(RawBackend.initExecutionCounts, node) != 
          ((int)(currentUpStreamItems * roundRobinMult)))
          System.out.println
          ("***** CORRECTING FOR INCOMING SPLITTER BUFFER IN INIT SCHEDULE (" + 
          node.contents.getName() + " " + ((int)(currentUpStreamItems * roundRobinMult))
          + " vs. " + getUpStreamItems(RawBackend.initExecutionCounts, node) + ") *****\n");
        */
    
        //return the number of items passed from current to node thru the splitters
        //(some may be roundrobin so we must use the weight multiplier.
        return (int)(currentUpStreamItems * roundRobinMult);
    }
    
    //returns the dimensions for the buffer array.
    private JExpression[] bufferDims(SIRFilter filter, CType inputType, int buffersize) 
    {
        //this is an array type
        if (inputType.isArrayType()) {
            CType baseType = ((CArrayType)inputType).getBaseType();
            //create the array to hold the dims of the buffer
            JExpression baseTypeDims[] = ((CArrayType)inputType).getDims();
            //the buffer is an array itself, so add one to the size of the input type
            JExpression[] dims =  new JExpression[baseTypeDims.length + 1];
            //the first dim is the buffersize
            dims[0] = new JIntLiteral(buffersize);
            //copy the dims for the basetype
            for (int i = 0; i < baseTypeDims.length; i++)
                dims[i+1] = baseTypeDims[i];

            return dims;
        } else {
            JExpression dims[] = {new JIntLiteral(buffersize)};
            return dims;
        }
    }
    

    private void createLocalVariables(FlatNode node, JBlock block,
                                      LocalVariables localVariables)
    {
        SIRFilter filter = (SIRFilter)node.contents;

        //index variable for certain for loops
        JVariableDefinition exeIndexVar = 
            new JVariableDefinition(null, 
                                    0, 
                                    CStdType.Integer,
                                    exeIndex,
                                    null);

        //remember the JVarDef for latter (in the raw main function)
        localVariables.exeIndex = exeIndexVar;
    
        block.addStatement
            (new JVariableDeclarationStatement(null,
                                               exeIndexVar,
                                               null));
    
        //index variable for certain for loops
        JVariableDefinition exeIndex1Var = 
            new JVariableDefinition(null, 
                                    0, 
                                    CStdType.Integer,
                                    exeIndex1,
                                    null);

        localVariables.exeIndex1 = exeIndex1Var;

        block.addStatement
            (new JVariableDeclarationStatement(null,
                                               exeIndex1Var,
                                               null));
    
    
        //only add the receive buffer and its vars if the 
        //filter receives data
        if (!noBuffer(filter)) {
            //initialize the buffersize to be the size of the 
            //struct being passed over it
            int buffersize;
        
            int prepeek = 0;
            int maxpeek = filter.getPeekInt();
            if (filter instanceof SIRTwoStageFilter)
                prepeek = ((SIRTwoStageFilter)filter).getInitPeekInt();
            //set up the maxpeek
            maxpeek = (prepeek > maxpeek) ? prepeek : maxpeek;
        
        
            if (isSimple(filter)) {
                //simple filter (no remaining items)
                if (KjcOptions.ratematch) {
                    //System.out.println(filter.getName());
            
                    int i = RawBackend.steadyExecutionCounts.get(node).intValue();

                    //i don't know, the prepeek could be really large, so just in case
                    //include it.  Make the buffer big enough to hold 
                    buffersize = 
                        Math.max
                        ((RawBackend.steadyExecutionCounts.get(node).intValue() - 1) * 
                         filter.getPopInt() + filter.getPeekInt(), prepeek);
                }
                else //not ratematching and we do not have a circular buffer
                    buffersize = maxpeek;

                //define the simple index variable
                JVariableDefinition simpleIndexVar = 
                    new JVariableDefinition(null, 
                                            0, 
                                            CStdType.Integer,
                                            simpleIndex,
                                            new JIntLiteral(-1));
        
                //remember the JVarDef for latter (in the raw main function)
                localVariables.simpleIndex = simpleIndexVar;
        
                block.addStatement
                    (new JVariableDeclarationStatement(null,
                                                       simpleIndexVar,
                                                       null));
            }
            else { //filter with remaing items on the buffer after initialization 
                //see Mgordon's thesis for explanation (Code Generation Section)
                if (KjcOptions.ratematch)
                    buffersize = 
                        Util.nextPow2(Math.max((RawBackend.steadyExecutionCounts.get(node).intValue() - 1) * 
                                               filter.getPopInt() + filter.getPeekInt(), prepeek) + remaining);
                else
                    buffersize = Util.nextPow2(maxpeek + remaining);
            }
        
            int dim = (filter.getInputType().isArrayType()) ? 
                ((CArrayType)filter.getInputType()).getDims().length + 1
                : 1;

            JVariableDefinition recvBufVar = 
                new JVariableDefinition(null, 
                                        at.dms.kjc.Constants.ACC_FINAL, //?????????
                                        new CArrayType(filter.getInputType(), 
                                                       dim /* dimension */ ,
                                                       bufferDims(filter, filter.getInputType(), buffersize)),
                                        recvBuffer,
                                        null);
        
        
            //the size of the buffer 
            JVariableDefinition recvBufferSizeVar = 
                new JVariableDefinition(null, 
                                        at.dms.kjc.Constants.ACC_FINAL, //?????????
                                        CStdType.Integer,
                                        recvBufferSize,
                                        new JIntLiteral(buffersize));
        
            //the size of the buffer 
            JVariableDefinition recvBufferBitsVar = 
                new JVariableDefinition(null, 
                                        at.dms.kjc.Constants.ACC_FINAL, //?????????
                                        CStdType.Integer,
                                        recvBufferBits,
                                        new JIntLiteral(buffersize - 1));
        
            //the receive buffer index (start of the buffer)
            JVariableDefinition recvBufferIndexVar = 
                new JVariableDefinition(null, 
                                        0, 
                                        CStdType.Integer,
                                        recvBufferIndex,
                                        new JIntLiteral(-1));
        
            //the index to the end of the receive buffer)
            JVariableDefinition recvIndexVar = 
                new JVariableDefinition(null, 
                                        0, 
                                        CStdType.Integer,
                                        recvIndex,
                                        new JIntLiteral(-1));

            JVariableDefinition[] indices = 
                {        
                    recvBufferSizeVar,
                    recvBufferBitsVar,
                    recvBufferIndexVar,
                    recvIndexVar
                };
        
            localVariables.recvBuffer = recvBufVar;
            localVariables.recvBufferSize = recvBufferSizeVar;
            localVariables.recvBufferBits = recvBufferBitsVar;
            localVariables.recvBufferIndex = recvBufferIndexVar;
            localVariables.recvIndex = recvIndexVar;

            block.addStatement
                (new JVariableDeclarationStatement(null,
                                                   indices,
                                                   null));

            block.addStatement
                (new JVariableDeclarationStatement(null,
                                                   recvBufVar, 
                                                   null));
        
        }
    
        //if we are rate matching, create the output buffer with its 
        //index
        if (KjcOptions.ratematch && filter.getPushInt() > 0) {
            int steady = RawBackend.steadyExecutionCounts.
                          get(Layout.getNode(Layout.getTile(filter))).intValue();
        
            //define the send buffer index variable
            JVariableDefinition sendBufferIndexVar = 
                new JVariableDefinition(null, 
                                        0, 
                                        CStdType.Integer,
                                        sendBufferIndex,
                                        new JIntLiteral(-1));
        
            localVariables.sendBufferIndex = sendBufferIndexVar;
            block.addStatement
                (new JVariableDeclarationStatement(null,
                                                   sendBufferIndexVar,
                                                   null));
            //define the send buffer
        
            JExpression[] dims = new JExpression[1];
            //the size of the output array is number of executions in steady *
            // number of items pushed * size of item
            dims[0] = new JIntLiteral(steady * filter.getPushInt() * 
                                      Util.getTypeSize(filter.getOutputType()));

            JVariableDefinition sendBufVar = 
                new JVariableDefinition(null, 
                                        at.dms.kjc.Constants.ACC_FINAL, //?????????
                                        new CArrayType(filter.getOutputType(), 
                                                       1 /* dimension */ ,
                                                       dims),
                                        sendBuffer,
                                        null);

            localVariables.sendBuffer = sendBufVar;
            block.addStatement
                (new JVariableDeclarationStatement(null,
                                                   sendBufVar,
                                                   null));
        }
    
    
        //print the declarations for the array indices for pushing and popping
        //if this filter deals with arrays
        if (filter.getInputType().isArrayType()) {
            //the number of vars for receiving is equal to the dimensionality of the
            //input array
            int inputDim = 
                ((CArrayType)filter.getInputType()).getArrayBound();
        
            localVariables.ARRAY_INDEX = new JVariableDefinition[inputDim];
        
            //create enough index vars as max dim
            for (int i = 0; i < inputDim; i++) {
                JVariableDefinition arrayIndexVar = 
                    new JVariableDefinition(null, 
                                            0, 
                                            CStdType.Integer,
                                            ARRAY_INDEX + i,
                                            null);
                //remember the array index vars
                localVariables.ARRAY_INDEX[i] = arrayIndexVar;
        
                block.addStatement
                    (new JVariableDeclarationStatement(null,
                                                       arrayIndexVar, 
                                                       null));
            }
        }
    }
    
    private boolean isSimple(SIRFilter filter) 
    {
        if (noBuffer(filter))
            return false;
    
        if (filter.getPeekInt() == filter.getPopInt() &&
            remaining == 0 &&
            (!(filter instanceof SIRTwoStageFilter) ||
             ((SIRTwoStageFilter)filter).getInitPop() ==
             ((SIRTwoStageFilter)filter).getInitPeek()))
            return true;
        return false;
    }
    

    //convert the peek and pop expressions for a filter into
    //buffer accesses, do this for all functions just in case helper
    //functions call peek or pop
    private void convertCommExps(SIRFilter filter, boolean simple,
                                 LocalVariables localVars) 
    {
        SLIRReplacingVisitor convert;
    
        if (simple) {
            convert = 
                new ConvertCommunicationSimple(localVars);
        }
        else
            convert = new ConvertCommunication(localVars);
    
        JMethodDeclaration[] methods = filter.getMethods();
        for (int i = 0; i < methods.length; i++) {
            //iterate over the statements and call the ConvertCommunication
            //class to convert peek, pop
            for (ListIterator it = methods[i].getStatementIterator();
                 it.hasNext(); ){
                ((JStatement)it.next()).accept(convert);
            }
        }
    }

    private boolean noBuffer(SIRFilter filter) 
    {
        //always need a buffer for rate matching.
        //      if (KjcOptions.ratematch)
        //  return false;
        
        if (filter.getPeekInt() == 0 &&
            (!(filter instanceof SIRTwoStageFilter) ||
             (((SIRTwoStageFilter)filter).getInitPeekInt() == 0)))
            return true;
        return false;       
    }
    
            
    // private void defineLocalVars(JBlock block,
    //               LocalVariables localVariables
    

    private void rawMainFunction(FlatNode node, JBlock statements,
                                 LocalVariables localVariables) 
    {
        SIRFilter filter = (SIRFilter)node.contents;
    
        //generate the code to define the local variables
        //defineLocalVars(statements, localVariables);

        //create the params list, for some reason 
        //calling toArray() on the list breaks a later pass
        List paramList = filter.getParams();
        JExpression[] paramArray;
        if (paramList == null || paramList.size() == 0)
            paramArray = new JExpression[0];
        else
            paramArray = (JExpression[])paramList.toArray(new JExpression[0]);
    
        //if standalone, add a field for the iteration counter...
        JFieldDeclaration iterationCounter = null;
        if (KjcOptions.standalone) {
            iterationCounter = 
                new JFieldDeclaration(new JVariableDefinition(0,
                                                              CStdType.Integer, 
                                                              FlatIRToC.MAINMETHOD_COUNTER,
                                                              new JIntLiteral(-1)));
            filter.addField(iterationCounter);
        }
    

        //add the call to the init function
        statements.addStatement
            (new 
             JExpressionStatement(null,
                                  new JMethodCallExpression
                                  (null,
                                   new JThisExpression(null),
                                   filter.getInit().getName(),
                                   paramArray),
                                  null));
    
        //add the call to initWork
        if (filter instanceof SIRTwoStageFilter) {
            SIRTwoStageFilter two = (SIRTwoStageFilter)filter;
            JBlock body = 
                (JBlock)ObjectDeepCloner.deepCopy
                (two.getInitWork().getBody());

            //add the code to receive the items into the buffer
            statements.addStatement
                (makeForLoop(receiveCode(filter, filter.getInputType(), 
                                         localVariables),
                             localVariables.exeIndex,
                             new JIntLiteral(two.getInitPeekInt())));
        
            //now inline the init work body
            statements.addStatement(body);
            //if a simple filter, reset the simpleIndex
            if (isSimple(filter)) {
                statements.addStatement
                    (new JExpressionStatement(null,
                                              (new JAssignmentExpression
                                               (null,
                                                new JLocalVariableExpression
                                                (null, localVariables.simpleIndex),
                                                new JIntLiteral(-1))), null));
            }
        }
    
        
        
        if (initFire - 1 > 0) {
            //add the code to collect enough data necessary to fire the 
            //work function for the first time
        
            if (bottomPeek > 0) {
                statements.addStatement
                    (makeForLoop(receiveCode(filter, filter.getInputType(),
                                             localVariables),
                                 localVariables.exeIndex,
                                 new JIntLiteral(bottomPeek)));
            }
        
            //add the calls for the work function in the initialization stage
            statements.addStatement(generateInitWorkLoop
                                    (filter, localVariables));
        }

        //add the code to collect all data produced by the upstream filter 
        //but not consumed by this filter in the initialization stage
        if (remaining > 0) {
            statements.addStatement
                (makeForLoop(receiveCode(filter, filter.getInputType(),
                                         localVariables),
                             localVariables.exeIndex,
                             new JIntLiteral(remaining))); 
        }

        if (!IMEMEstimation.TESTING_IMEM) {
            //add a call to raw_init2 only if not testing imem
            statements.addStatement(new JExpressionStatement(null,
                                                             new JMethodCallExpression
                                                             (null, 
                                                              new JThisExpression(null),
                                                              SwitchCode.SW_SS_TRIPS,
                                                              new JExpression[0]),
                                                             null));
        }
    

        if (RawBackend.FILTER_DEBUG_MODE) {
            statements.addStatement
                (new SIRPrintStatement(null,
                                       new JStringLiteral(null, filter.getName() + " Starting Steady-State\\n"),
                                       null));
        }
    
        //add the call to the work function
        statements.addStatement(generateSteadyStateLoop(filter,
                                                        localVariables));
    
    
    }

    //generate the loop for the work function firings in the 
    //initialization schedule
    JStatement generateInitWorkLoop(SIRFilter filter, 
                                    LocalVariables localVariables) 
    {
        JStatement innerReceiveLoop = 
            makeForLoop(receiveCode(filter, filter.getInputType(),
                                    localVariables),
                        localVariables.exeIndex,
                        new JIntLiteral(filter.getPopInt()));
    
        JExpression isFirst = 
            new JEqualityExpression(null,
                                    false,
                                    new JLocalVariableExpression
                                    (null, 
                                     localVariables.exeIndex1),
                                    new JIntLiteral(0));
        JStatement ifStatement = 
            new JIfStatement(null,
                             isFirst,
                             innerReceiveLoop,
                             null, 
                             null);
    
        JBlock block = new JBlock(null, new JStatement[0], null);

        //if a simple filter, reset the simpleIndex
        if (isSimple(filter)){
            block.addStatement
                (new JExpressionStatement(null,
                                          (new JAssignmentExpression
                                           (null,
                                            new JLocalVariableExpression
                                            (null, localVariables.simpleIndex),
                                            new JIntLiteral(-1))), null));
        }
    
        //add the if statement
        block.addStatement(ifStatement);
    
        //clone the work function and inline it
        JBlock workBlock = 
            (JBlock)ObjectDeepCloner.deepCopy(filter.getWork().getBody());
    
        //if we are in debug mode, print out that the filter is firing
        if (RawBackend.FILTER_DEBUG_MODE) {
            block.addStatement
                (new SIRPrintStatement(null,
                                       new JStringLiteral(null, filter.getName() + " firing (init).\\n"),
                                       null));
        }
    
        block.addStatement(workBlock);
    
        //return the for loop that executes the block init - 1
        //times
        return makeForLoop(block, localVariables.exeIndex1, 
                           new JIntLiteral(initFire - 1));
    }
    
    private int lcm(int x, int y) { // least common multiple
        int v = x, u = y;
        while (x != y)
            if (x > y) {
                x -= y; v += u;
            } else {
                y -= x; u += v;
            }
        return (u+v)/2;
    }


    JStatement generateRateMatchSteadyState(SIRFilter filter,
                                            LocalVariables localVariables) 
    {
        JBlock block = new JBlock(null, new JStatement[0], null);
        int steady = RawBackend.steadyExecutionCounts.
                      get(Layout.getNode(Layout.getTile(filter))).intValue();

        //reset the simple index
        if (isSimple(filter)){
            block.addStatement
                (new JExpressionStatement(null,
                                          (new JAssignmentExpression
                                           (null,
                                            new JLocalVariableExpression
                                            (null, localVariables.simpleIndex),
                                            new JIntLiteral(-1))), null));
        }
        
    
        //should be at least peek - pop items in the buffer, so
        //just receive pop * steady in the buffer and we can
        //run for an entire steady state
        block.addStatement
            (makeForLoop(receiveCode(filter, filter.getInputType(),
                                     localVariables),
                         localVariables.exeIndex,
                         new JIntLiteral(filter.getPopInt() * steady)));
        if (filter.getPushInt() > 0) {
            //reset the send buffer index
            block.addStatement
                (new JExpressionStatement(null,
                                          new JAssignmentExpression(null,
                                                                    new JLocalVariableExpression
                                                                    (null, localVariables.sendBufferIndex),
                                                                    new JIntLiteral(-1)),
                                          null));
        }
    
        //now, run the work function steady times...
        JBlock workBlock = 
            (JBlock)ObjectDeepCloner.
            deepCopy(filter.getWork().getBody());

        //convert all of the push expressions in the steady state work block into
        //stores to the output buffer
        workBlock.accept(new RateMatchConvertPush(localVariables));

        //if we are in debug mode, print out that the filter is firing
        if (RawBackend.FILTER_DEBUG_MODE) {
            block.addStatement
                (new SIRPrintStatement(null,
                                       new JStringLiteral(null, filter.getName() + " firing.\\n"),
                                       null));
        }

        //add the cloned work function to the block
        block.addStatement
            (makeForLoop(workBlock, localVariables.exeIndex,
                         new JIntLiteral(steady)));
    
        //now add the code to push the output buffer onto the static network and 
        //reset the output buffer index
        //    for (steady*push*typesize)
        //        push(__SENDBUFFER__[++ __SENDBUFFERINDEX__])
        if (filter.getPushInt() > 0) {
        
            SIRPushExpression pushExp =  new SIRPushExpression
                (new JArrayAccessExpression
                 (null, 
                  new JLocalVariableExpression
                  (null,
                   localVariables.sendBuffer),
                  new JLocalVariableExpression
                  (null,
                   localVariables.exeIndex)));
        
            pushExp.setTapeType(CommonUtils.getBaseType(filter.getOutputType()));
        
            JExpressionStatement send = new JExpressionStatement(null, pushExp, null);
        
            block.addStatement
                (makeForLoop(send, localVariables.exeIndex,
                             new JIntLiteral(steady * filter.getPushInt() * 
                                             Util.getTypeSize(filter.getOutputType()))));
       
        }
    

        //if we are in decoupled mode do not put the work function in a for loop
        //and add the print statements
        if (KjcOptions.decoupled) {
            block.addStatementFirst
                (new SIRPrintStatement(null, 
                                       new JIntLiteral(0),
                                       null));
            block.addStatement(block.size(), 
                               new SIRPrintStatement(null, 
                                                     new JIntLiteral(1),
                                                     null));
            return block;
        }
    
        //return the infinite loop
        return new JWhileStatement(null, 
                                   new JBooleanLiteral(null, true),
                                   block, 
                                   null);
    
    }

    //generate the code for the steady state loop
    JStatement generateSteadyStateLoop(SIRFilter filter, 
                                       LocalVariables localVariables) 
    {
    
        //is we are rate matching generate the appropriate code
        if (KjcOptions.ratematch) 
            return generateRateMatchSteadyState(filter, localVariables);

        JBlock block = new JBlock(null, new JStatement[0], null);

        //reset the simple index
        if (isSimple(filter)){
            block.addStatement
                (new JExpressionStatement(null,
                                          (new JAssignmentExpression
                                           (null,
                                            new JLocalVariableExpression
                                            (null, localVariables.simpleIndex),
                                            new JIntLiteral(-1))), null));
        }
        
    
        //add the statements to receive pop items into the buffer
        block.addStatement
            (makeForLoop(receiveCode(filter, filter.getInputType(),
                                     localVariables),
                         localVariables.exeIndex,
                         new JIntLiteral(filter.getPopInt())));

    

        JBlock workBlock = 
            (JBlock)ObjectDeepCloner.
            deepCopy(filter.getWork().getBody());

        //if we are in debug mode, print out that the filter is firing
        if (RawBackend.FILTER_DEBUG_MODE) {
            block.addStatement
                (new SIRPrintStatement(null,
                                       new JStringLiteral(null, filter.getName() + " firing.\\n"),
                                       null));
        }

        //add the cloned work function to the block
        block.addStatement(workBlock);
        //  }
    
        //  return block;
    
        //if we are in decoupled mode do not put the work function in a for loop
        //and add the print statements
        if (KjcOptions.decoupled) {
            block.addStatementFirst
                (new SIRPrintStatement(null, 
                                       new JIntLiteral(0),
                                       null));
            block.addStatement(block.size(), 
                               new SIRPrintStatement(null, 
                                                     new JIntLiteral(1),
                                                     null));
            return block;
        }
    
        return new JWhileStatement(null, 
                                   KjcOptions.standalone ?  
                                   (JExpression) new JPostfixExpression(null, 
                                                                        Constants.OPE_POSTDEC, 
                                                                        new JFieldAccessExpression(new JThisExpression(null), 
                                                                                                   FlatIRToC.MAINMETHOD_COUNTER)) :
                                   (JExpression)new JBooleanLiteral(null, true),
                                   block, 
                                   null);
    }

    JStatement receiveCode(SIRFilter filter, CType type, LocalVariables localVariables) {
        if (noBuffer(filter)) 
            return null;

        //the name of the method we are calling, this will
        //depend on type of the pop, by default set it to be the scalar receive
        String receiveMethodName = receiveMethod;

        JBlock statements = new JBlock(null, new JStatement[0], null);
    
        //if it is not a scalar receive change the name to the appropriate 
        //method call, from struct.h
        if (type.isArrayType()) {
            return arrayReceiveCode(filter,(CArrayType) type, localVariables);
        }
        else if (type.isClassType()) {
            receiveMethodName = structReceiveMethodPrefix  + type.toString();
        }

        //create the array access expression to access the buffer 
        JArrayAccessExpression arrayAccess = 
            new JArrayAccessExpression(null,
                                       new JLocalVariableExpression
                                       (null,
                                        localVariables.recvBuffer),
                                       bufferIndex(filter,
                                                   localVariables));
    
        //put the arrayaccess in an array...
        JExpression[] bufferAccess = 
            {new JParenthesedExpression(null,
                                        arrayAccess)};
     
        //the method call expression, for scalars, flatIRtoC
        //changes this into c code thatw will perform the receive...
        JExpression exp =
            new JMethodCallExpression(null,  new JThisExpression(null),
                                      receiveMethodName,
                                      bufferAccess);

        //return a statement
        return new JExpressionStatement(null, exp, null);
    }
    
    //generate the code to receive the array into the buffer
    JStatement arrayReceiveCode(SIRFilter filter, CArrayType type, LocalVariables localVariables) {
        //get the dimensionality of the input buffer
        int dim = type.getDims().length + 1;
        //make sure there are enough indices
        if (localVariables.ARRAY_INDEX.length < (dim - 1))
            Utils.fail("Error generating array receive code");
        //generate the first (outermost array access)
        JArrayAccessExpression arrayAccess = 
            new JArrayAccessExpression(null, 
                                       new JLocalVariableExpression(null, 
                                                                    localVariables.recvBuffer),
                                       bufferIndex(filter, localVariables));
        //generate the remaining array accesses
        for (int i = 0; i < dim - 1; i++) 
            arrayAccess = new JArrayAccessExpression(null, arrayAccess,
                                                     new JLocalVariableExpression
                                                     (null, localVariables.ARRAY_INDEX[i]));
    
        //now place the array access in a method call to alert flatirtoc that it is a 
        //static receive
        JExpression[] args = new JExpression[1];
        args[0] = arrayAccess;
    
        JMethodCallExpression methodCall = 
            new JMethodCallExpression(null, 
                                      new JThisExpression(null),
                                      receiveMethod,
                                      args);
        //now generate the nested for loops 

        //get the dimensions of the array as set by kopi2sir
        JExpression[] dims = type.getDims();
        JStatement stmt = new JExpressionStatement(null,
                                                   methodCall,
                                                   null);
        for (int i = dims.length - 1; i >= 0; i--) 
            stmt = makeForLoop(stmt, localVariables.ARRAY_INDEX[i],
                               dims[i]);
    
        return stmt;       
    }
    
    //return the buffer access expression for the receive code
    //depends if this is a simple filter
    private JExpression bufferIndex(SIRFilter filter, 
                                    LocalVariables localVariables) 
    {
        if (isSimple(filter)) {
            return new JLocalVariableExpression
                (null, 
                 localVariables.exeIndex);
        }
        else {
            //create the increment of the index var
            JPrefixExpression bufferIncrement = 
                new JPrefixExpression(null, 
                                      OPE_PREINC,
                                      new JLocalVariableExpression
                                      (null,localVariables.recvIndex));
            /*
            //create the modulo expression
            JModuloExpression indexMod = 
            new JModuloExpression(null, bufferIncrement, 
            new JLocalVariableExpression
            (null,
            localVariables.recvBufferSize));
            */
        
            //create the modulo expression
            JBitwiseExpression indexAnd = 
                new JBitwiseExpression(null, 
                                       OPE_BAND,
                                       bufferIncrement, 
                                       new JLocalVariableExpression
                                       (null,
                                        localVariables.recvBufferBits));
        
            return indexAnd;
        }
    }
    
    
    /**
     * Returns a for loop that uses field <var> to count
     * <count> times with the body of the loop being <body>.  If count
     * is non-positive, just returns empty (!not legal in the general case)
     */
    public static JStatement makeForLoop(JStatement body,
                                         JLocalVariable var,
                                         JExpression count) {
        if (body == null)
            return new JEmptyStatement(null, null);
    
        // if count==0, just return empty statement
        if (count instanceof JIntLiteral) {
            int intCount = ((JIntLiteral)count).intValue();
            if (intCount<=0) {
                // return empty statement
                return new JEmptyStatement(null, null);
            }
            if (intCount==1) { 
                // return body
                return body;
            }
        }

        // make init statement - assign zero to <var>.  We need to use
        // an expression list statement to follow the convention of
        // other for loops and to get the codegen right.
        JExpression initExpr[] = {
            new JAssignmentExpression(null,
                                      new JLocalVariableExpression(null,
                                                                   var),
                                      new JIntLiteral(0)) };
        JStatement init = new JExpressionListStatement(null, initExpr, null);

        // make conditional - test if <var> less than <count>
        JExpression cond = 
            new JRelationalExpression(null,
                                      Constants.OPE_LT,
                                      new JLocalVariableExpression(null,
                                                                   var),
                                      count);
        JExpression incrExpr = 
            new JPostfixExpression(null, 
                                   Constants.OPE_POSTINC, 
                                   new JLocalVariableExpression(null,
                                                                var));
        JStatement incr = 
            new JExpressionStatement(null, incrExpr, null);

        return new JForStatement(null, init, cond, incr, body, null);
    }
  
    class ConvertCommunicationSimple extends SLIRReplacingVisitor 
    {
        LocalVariables localVariables;
    
    
        public ConvertCommunicationSimple(LocalVariables locals) 
        {
            localVariables = locals;
        }
    
        //for pop expressions convert to the form
        // (recvBuffer[++recvBufferIndex % recvBufferSize])
        public Object visitPopExpression(SIRPopExpression oldSelf,
                                         CType oldTapeType) {
            assert oldSelf.getNumPop() == 1: "need to update code here for multiple pop";
            // do the super
            SIRPopExpression self = 
                (SIRPopExpression)
                super.visitPopExpression(oldSelf, oldTapeType);

            //create the increment of the index var
            JPrefixExpression increment = 
                new JPrefixExpression(null, 
                                      OPE_PREINC,
                                      new JLocalVariableExpression
                                      (null, 
                                       localVariables.simpleIndex));
        
            //create the array access expression
            JArrayAccessExpression bufferAccess = 
                new JArrayAccessExpression(null,
                                           new JLocalVariableExpression
                                           (null,
                                            localVariables.recvBuffer),
                                           increment);

            //return the parenthesed expression
            return new JParenthesedExpression(null,
                                              bufferAccess);
        }
    
        //convert peek exps into:
        // (recvBuffer[(recvBufferIndex + (arg) + 1) mod recvBufferSize])
        public Object visitPeekExpression(SIRPeekExpression oldSelf,
                                          CType oldTapeType,
                                          JExpression oldArg) {
            // do the super
            SIRPeekExpression self = 
                (SIRPeekExpression)
                super.visitPeekExpression(oldSelf, oldTapeType, oldArg);


            JAddExpression argIncrement = 
                new JAddExpression(null, self.getArg(), new JIntLiteral(1));
        
            JAddExpression index = 
                new JAddExpression(null,
                                   new JLocalVariableExpression
                                   (null, 
                                    localVariables.simpleIndex),
                                   argIncrement);

            //create the array access expression
            JArrayAccessExpression bufferAccess = 
                new JArrayAccessExpression(null,
                                           new JLocalVariableExpression
                                           (null,
                                            localVariables.recvBuffer),
                                           index);

            //return the parenthesed expression
            return new JParenthesedExpression(null,
                                              bufferAccess);
        
        }
    
    }
    
    

    class ConvertCommunication extends SLIRReplacingVisitor 
    {
        LocalVariables localVariables;
    
    
        public ConvertCommunication(LocalVariables locals) 
        {
            localVariables = locals;
        }
    
        //for pop expressions convert to the form
        // (recvBuffer[++recvBufferIndex % recvBufferSize])
        public Object visitPopExpression(SIRPopExpression self,
                                         CType tapeType) {
      
            //create the increment of the index var
            JPrefixExpression bufferIncrement = 
                new JPrefixExpression(null, 
                                      OPE_PREINC,
                                      new JLocalVariableExpression
                                      (null, 
                                       localVariables.recvBufferIndex));
        
            JBitwiseExpression indexAnd = 
                new JBitwiseExpression(null, 
                                       OPE_BAND,
                                       bufferIncrement, 
                                       new JLocalVariableExpression
                                       (null,
                                        localVariables.recvBufferBits));
            /*
            //create the modulo expression
            JModuloExpression indexMod = 
            new JModuloExpression(null, bufferIncrement, 
            new JLocalVariableExpression
            (null,
            localVariables.recvBufferSize));
            */
        
            //create the array access expression
            JArrayAccessExpression bufferAccess = 
                new JArrayAccessExpression(null,
                                           new JLocalVariableExpression
                                           (null,
                                            localVariables.recvBuffer),
                                           indexAnd);

            //return the parenthesed expression
            return new JParenthesedExpression(null,
                                              bufferAccess);
        }
    
        //convert peek exps into:
        // (recvBuffer[(recvBufferIndex + (arg) + 1) mod recvBufferSize])
        public Object visitPeekExpression(SIRPeekExpression oldSelf,
                                          CType oldTapeType,
                                          JExpression oldArg) {
            // do the super
            SIRPeekExpression self = 
                (SIRPeekExpression)
                super.visitPeekExpression(oldSelf, oldTapeType, oldArg);
        

            //create the index calculation expression
            JAddExpression argIncrement = 
                new JAddExpression(null, self.getArg(), new JIntLiteral(1));
            JAddExpression index = 
                new JAddExpression(null,
                                   new JLocalVariableExpression
                                   (null, 
                                    localVariables.recvBufferIndex),
                                   argIncrement);
        
            JBitwiseExpression indexAnd = 
                new JBitwiseExpression(null, 
                                       OPE_BAND,
                                       index, 
                                       new JLocalVariableExpression
                                       (null,
                                        localVariables.recvBufferBits));
     
            /*
            //create the mod expression
            JModuloExpression indexMod = 
            new JModuloExpression(null, index,
            new JLocalVariableExpression
            (null,
            localVariables.recvBufferSize));
            */

            //create the array access expression
            JArrayAccessExpression bufferAccess = 
                new JArrayAccessExpression(null,
                                           new JLocalVariableExpression
                                           (null,
                                            localVariables.recvBuffer),
                                           indexAnd);

            //return the parenthesed expression
            return new JParenthesedExpression(null,
                                              bufferAccess);
        }
    
    }

    //this pass will convert all push statements to a method call expression
    //so that in FlatIRtoC we can recognize the call and replace it with a store to
    //the output buffer instead of a send onto the network...
    class RateMatchConvertPush extends SLIRReplacingVisitor 
    {
        LocalVariables localVariables;
    
    
        public RateMatchConvertPush(LocalVariables localVariables) 
        {
            this.localVariables = localVariables;
        }
  
        /**
         * Visits a push expression.
         */
        public Object visitPushExpression(SIRPushExpression self,
                                          CType tapeType,
                                          JExpression arg) {
            JExpression newExp = (JExpression)arg.accept(this);
            return new JAssignmentExpression
                (null,
                 new JArrayAccessExpression(null, 
                                            new JLocalVariableExpression
                                            (null, localVariables.sendBuffer),
                                            new JPrefixExpression(null, OPE_PREINC,
                                                                  new JLocalVariableExpression
                                                                  (null, 
                                                                   localVariables.sendBufferIndex))),
                 newExp);

        
        
            /*
              JExpression[] args = new JExpression[1];
              args[0] = newExp;
        
              JMethodCallExpression ratematchsend = 
              new JMethodCallExpression(null, new JThisExpression(null),
              RawExecutionCode.rateMatchSendMethod,
              args);
        
              return ratematchsend;
            */
        }
    }
}

