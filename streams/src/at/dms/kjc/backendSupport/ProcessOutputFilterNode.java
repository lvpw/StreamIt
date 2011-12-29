package at.dms.kjc.backendSupport;

import java.util.Map;
import java.util.WeakHashMap;

import at.dms.kjc.CArrayType;
import at.dms.kjc.CClassType;
import at.dms.kjc.CStdType;
import at.dms.kjc.JAddExpression;
import at.dms.kjc.JArrayAccessExpression;
import at.dms.kjc.JArrayInitializer;
import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JBlock;
import at.dms.kjc.JBreakStatement;
import at.dms.kjc.JEmptyStatement;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JFieldAccessExpression;
import at.dms.kjc.JFieldDeclaration;
import at.dms.kjc.JFormalParameter;
import at.dms.kjc.JIfStatement;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JModuloExpression;
import at.dms.kjc.JPrefixExpression;
import at.dms.kjc.JRelationalExpression;
import at.dms.kjc.JStatement;
import at.dms.kjc.JSwitchGroup;
import at.dms.kjc.JSwitchLabel;
import at.dms.kjc.JSwitchStatement;
import at.dms.kjc.JVariableDeclarationStatement;
import at.dms.kjc.JVariableDefinition;
import at.dms.kjc.slir.InternalFilterNode;
import at.dms.kjc.slir.IntraSSGEdge;
import at.dms.kjc.slir.OutputNode;
import at.dms.kjc.slir.SchedulingPhase;

/**
 * Create kopi code for an {@link at.dms.kjc.slir.OutputNode}.
 * @author dimock
 *
 */
public class ProcessOutputFilterNode {
    /** set of filters for which we have written basic code. */
    // uses WeakHashMap to be self-cleaning, but now have to insert some value.
    protected static Map<InternalFilterNode,Boolean>  basicCodeWritten = new WeakHashMap<InternalFilterNode,Boolean>();

    protected OutputNode outputNode;
    protected SchedulingPhase whichPhase;
    protected BackEndFactory backEndBits;
    protected CodeStoreHelper splitter_code;
    protected ComputeNode location;
    protected ComputeCodeStore codeStore;
    
    /**
     * Constructor
     * @param outputNode    the OutputSliceNode that may need code generated.
     * @param whichPhase   a scheduling phase {@link SchedulingPhase}
     * @param backEndBits  a BackEndFactory to access layout, etc.
     */
    public ProcessOutputFilterNode(OutputNode outputNode, 
            SchedulingPhase whichPhase, BackEndFactory backEndBits) {
        this.outputNode = outputNode;
        this.whichPhase = whichPhase;
        this.backEndBits = backEndBits;
        setLocationAndCodeStore();
    }
    
    /**
     * Create code for a OutputSliceNode.
     */
    public void processOutputSliceNode() {
        doit();
    }
    
    protected void setLocationAndCodeStore() {
        location = backEndBits.getLayout().getComputeNode(outputNode);
        assert location != null;
        codeStore = location.getComputeCode();
    }
    
    protected void doit() {
        // No code generated for outputNode if there is not needed.
        if (! backEndBits.sliceHasDownstreamChannel(outputNode.getParent())) {
            return;
        }
        
        setSplitterCode();

        switch (whichPhase) {
        case PREINIT:
            standardPreInitProcessing();
            additionalPreInitProcessing();
            break;
        case INIT:
            standardInitProcessing();
            additionalInitProcessing();
            break;
        case PRIMEPUMP:
            standardPrimePumpProcessing();
            additionalPrimePumpProcessing();
            break;
        case STEADY:
            standardSteadyProcessing();
            additionalSteadyProcessing();
            break;
        }
    }
    
    protected void setSplitterCode() {
        splitter_code = CodeStoreHelper.findHelperForSliceNode(outputNode);
        if (splitter_code == null) {
            splitter_code = getSplitterCode(outputNode,backEndBits);
        }
    }
    
    protected void standardPreInitProcessing() {
        
    }
    
    protected void additionalPreInitProcessing() {
        
    }
    
    protected void standardInitProcessing() {
        
    }
    
    protected void additionalInitProcessing() {
        
    }
    
    protected void standardPrimePumpProcessing() {
        
    }
    
    protected void additionalPrimePumpProcessing() {
        
    }
    
    protected void standardSteadyProcessing() {
        // helper has now been used for the last time, so we can write the basic code.
        // write code deemed useful by the helper into the corrrect ComputeCodeStore.
        // write only once if multiple calls for steady state.
        if (!basicCodeWritten.containsKey(outputNode)) {
            codeStore.addFields(splitter_code.getFields());
            codeStore.addMethods(splitter_code.getUsefulMethods());
            basicCodeWritten.put(outputNode,true);
        }
    }
    
    protected void additionalSteadyProcessing() {
        
    }
    
    /**
         * Create fields and code for a splitter, as follows.
         * Do not create a splitter if all weights are 0: this code
         * fails rather than creating nonsensical kopi code.
         * <pre>
    splitter as a state machine, driven off arrays:
    
    void push_1_1(T v) {fprintf(stderr, "push_1_1 %d\n", v);}
    void push_3_1(T v) {fprintf(stderr, "push_3_1 %d\n", v);}
    void push_3_2(T v) {fprintf(stderr, "push_3_2 %d\n", v);}
    
    / *
     Splitter as state machine.
     Need arity (3 here), max duplication (2 here).
     Special case if arity == 1: always call push_N_M directly.
     * /
    
    static int splitter_N_dim1 = 3-1;
    static int splitter_N_weight = 0;
    
    static inline void splitter_N (T val) {
      static void (*pushes[3][2])(T) = {
        {push_1_1, NULL},       / * edge * /
        {NULL, NULL},       / * 0-weight edge * /
        {push_3_1, push_3_2}    / * duplicating edge * /
      };
    
      static const int weights[3] = {1, 0, 4};
      int dim2;
    
      while (splitter_N_weight == 0) {
          splitter_N_dim1 = (splitter_N_dim1 + 1) % 3;
          splitter_N_weight = weights[splitter_N_dim1];
      }
    
      dim2 = 0;
      while (dim2 < 2 && pushes[splitter_N_dim1][dim2] != NULL) {
        pushes[splitter_N_dim1][dim2++](val);
      }
      splitter_N_weight--;
    }
    
    
    splitter as actually implemented:
    
    / * inline the array stuff, 
     * remove 0-weight edge 
     * /
    static int splitter_N_unrolled_edge = 2 - 1;
    static int splitter_N_unrolled_weight = 0;
    
    static inline void splitter_N_unrolled(T val) {
    
      static const int weights[2] = {1-1,4-1};
    
      if (--splitter_N_unrolled_weight < 0) {
        splitter_N_unrolled_edge = (splitter_N_unrolled_edge + 1) % 2;
        splitter_N_unrolled_weight = weights[splitter_N_unrolled_edge];
      }
    
      switch (splitter_N_unrolled_edge) {
      case 0:
        push_1_1(val);
        break;
      case 1:
        push_3_1(val); 
        push_3_2(val);
        break;
      }
    }
    
         </pre>
         * @param splitter
         * @return
         */
        private static void makeSplitterCode(OutputNode splitter, 
                BackEndFactory backEndBits, CodeStoreHelper helper) {
            String splitter_name = "_splitter_" + ProcessFilterWorkNode.getUid();
            String splitter_method_name =  splitter_name + splitter.getPrevFilter().getWorkNodeContent().getName();

            // size is number of edges with non-zero weight.
            int size = 0;
            for (int w : splitter.getWeights(SchedulingPhase.STEADY)) {
                if (w != 0) {size++;}
            }
            
            assert size > 0 : "asking for code generation for null splitter: " + splitter_method_name;
            
            String edge_name = splitter_name + "_edge";
            String weight_name = splitter_name + "_weight";
    
            String param_name = "arg";
            JFormalParameter arg = new JFormalParameter(splitter.getType(),param_name);
            JExpression argExpr = new JLocalVariableExpression(arg);
            
            JVariableDefinition edgeVar = new JVariableDefinition(
                    at.dms.kjc.Constants.ACC_STATIC,
                    CStdType.Integer,
                    edge_name,
                    new JIntLiteral(size - 1));
            
            JFieldDeclaration edgeDecl = new JFieldDeclaration(edgeVar);
            JFieldAccessExpression edgeExpr = new JFieldAccessExpression(edge_name);
            
            JVariableDefinition weightVar = new JVariableDefinition(
                    at.dms.kjc.Constants.ACC_STATIC,
                    CStdType.Integer,
                    weight_name,
                    new JIntLiteral(0));
    
            JFieldDeclaration weightDecl = new JFieldDeclaration(weightVar);
            JFieldAccessExpression weightExpr = new JFieldAccessExpression(weight_name);
            
            JIntLiteral[] weightVals = new JIntLiteral[size];
            {
                int i = 0;
                for (int w : splitter.getWeights(SchedulingPhase.STEADY)) {
                    if (w != 0) {
                        weightVals[i++] = new JIntLiteral(w - 1);
                    }
                }
            }
            
            JVariableDefinition weightsArray = new JVariableDefinition(
                    at.dms.kjc.Constants.ACC_STATIC | at.dms.kjc.Constants.ACC_FINAL,  // static const in C
                    new CArrayType(CStdType.Integer,
                            1, new JExpression[]{new JIntLiteral(size)}),
                    "weights",
                    new JArrayInitializer(weightVals));
            JLocalVariableExpression weightsExpr = new JLocalVariableExpression(weightsArray);
            
            JStatement next_edge_weight_stmt = new JIfStatement(null,
                    new JRelationalExpression(at.dms.kjc.Constants.OPE_LT,
                            new JPrefixExpression(null,
                                    at.dms.kjc.Constants.OPE_PREDEC,
                                    weightExpr),
                            new JIntLiteral(0)),
                    new JBlock(new JStatement[]{
                            new JExpressionStatement(new JAssignmentExpression(
                                    edgeExpr,
                                    new JModuloExpression(null,
                                            new JAddExpression(
                                                    edgeExpr,
                                                    new JIntLiteral(1)),
                                            new JIntLiteral(size)))),
                            new JExpressionStatement(new JAssignmentExpression(
                                    weightExpr,
                                    new JArrayAccessExpression(weightsExpr,
                                            edgeExpr)
                                    ))
                    }),
                    new JEmptyStatement(),
                    null);
    
            
            JSwitchGroup[] cases = new JSwitchGroup[size]; // fill in later.
            JStatement switch_on_edge_stmt = new JSwitchStatement(null,
                    edgeExpr,
                    cases,
                    null);
            
            {
                int i = 0;
                for (int j = 0; j < splitter.getWeights(SchedulingPhase.STEADY).length; j++) {
                    if (splitter.getWeights(SchedulingPhase.STEADY)[j] != 0) {
                        IntraSSGEdge[] edges = splitter.getDests(SchedulingPhase.STEADY)[j];
                        JStatement[] pushes = new JStatement[edges.length + 1];
                        for (int k = 0; k < edges.length; k++) {
                            pushes[k] = new JExpressionStatement(
                                new JMethodCallExpression(
                                        backEndBits.getChannel(edges[k]).pushMethodName(),
                                    new JExpression[]{argExpr}));
                        }
                        pushes[edges.length] = new JBreakStatement(null,null,null);
                        
                        cases[i] = new JSwitchGroup(null,
                                new JSwitchLabel[]{new JSwitchLabel(null,new JIntLiteral(i))},
                                pushes);
                        i++;
                    }
                }
            }
    
            JMethodDeclaration splitter_method = new JMethodDeclaration(
                    null, 
                    at.dms.kjc.Constants.ACC_STATIC | at.dms.kjc.Constants.ACC_INLINE,
                    CStdType.Void,
                    splitter_method_name,
                    new JFormalParameter[]{arg},
                    new CClassType[]{},
                    new JBlock(),
                    null, null);
            
            JBlock splitter_block = splitter_method.getBody();
            
            splitter_block.addStatement(
                    new JVariableDeclarationStatement(
                            new JVariableDefinition[]{weightsArray}));
            splitter_block.addStatement(next_edge_weight_stmt);
            splitter_block.addStatement(switch_on_edge_stmt);
            
            
            helper.setFields(new JFieldDeclaration[]{edgeDecl, weightDecl});
            helper.setMethods(new JMethodDeclaration[]{splitter_method});
        }

        
        /**
         * Get code for a splitter.
         * If code not yet made, then makes it.
         * @param splitter
         * @param backEndBits
         * @return
         */
        public static  CodeStoreHelper getSplitterCode(OutputNode splitter, BackEndFactory backEndBits) {
            CodeStoreHelper splitter_code = CodeStoreHelper.findHelperForSliceNode(splitter);
            if (splitter_code == null) {
                splitter_code = backEndBits.getCodeStoreHelper(splitter);
                if (backEndBits.sliceNeedsSplitterCode(splitter.getParent())) {
                    makeSplitterCode(splitter,backEndBits,splitter_code);
                }
                CodeStoreHelper.addHelperForSliceNode(splitter,splitter_code);
            }
            return splitter_code;
        }


}

