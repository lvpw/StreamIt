package at.dms.kjc.flatgraph;

import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.cluster.CodeEstimate;
import java.io.*;
import java.util.*;
import at.dms.kjc.sir.lowering.fission.StatelessDuplicate;
import at.dms.kjc.sir.lowering.partition.WorkEstimate;

/**
 * Dumps a symbolic representation of the flatgraph for interfacing to
 * French collaborators for linear-programming scheduling algorithm.
 */
public class DumpSymbolicGraph implements FlatVisitor
{
    /** The outputs as we create it (both nodes and edges) */
    private StringBuffer nodeBuf, edgeBuf;
    /** The number of nodes and edges we've already output */
    private int nodeId = 0, edgeId = 0;
    /** HashMaps from SIROperator -> Integer for multiplicities */
    private HashMap<FlatNode, Integer> initMults, steadyMults;
    // id number for each SIROperator, starts from 1
    private HashMap<SIROperator,Integer> id;
    
    /**
     * Creates the output file representing the flattened graph and
     * stores it in filename.
     *  
     * This function must be called after 
     * {@link RawBackend#createExecutionCounts} because execution multiplicities 
     * need to be set.
     * 
     * @param str The original SIR graph.
     * @param toplevel The starting node of the FlatNode graph.
     * @param filename The file to write the graph to.
     * @param initExeCounts The multiplicities in the init stage.
     * @param steadyExeCounts The multiplicities in the steady-state stage.
     */
    public void dumpGraph(SIRStream str, FlatNode toplevel, String filename, HashMap<FlatNode, Integer> initExeCounts,
                          HashMap<FlatNode, Integer> steadyExeCounts) 
    {
        nodeBuf = new StringBuffer();
        edgeBuf = new StringBuffer();
        this.initMults = initExeCounts;
        this.steadyMults = steadyExeCounts; 
        this.id = new HashMap<SIROperator,Integer>();
        prepareIds(str);
        toplevel.accept(this, null, true);
        try {
            FileWriter fw = new FileWriter(filename);
            fw.write("node_number:" + nodeId + "\n");
            fw.write(nodeBuf.toString());
            fw.write("edge_number:" + edgeId + "\n");
            fw.write(edgeBuf.toString());
            fw.close();
        }
        catch (Exception e) {
            System.err.println("Could not print flattened graph");
        }
    }

    /**
     * Prepares ID numbers for each filter based on the order they
     * would be executed in a hierarchical schedule.
     */
    private void prepareIds(SIRStream str) {
        final int[] count = { 1 };
        // just number nodes in the order that the visitor hits them,
        // which is the same order that a hierarchical schedule hits
        // them.
        str.accept(new EmptyAttributeStreamVisitor() {
                /* visit a filter */
                public Object visitFilter(SIRFilter self,
                                          JFieldDeclaration[] fields,
                                          JMethodDeclaration[] methods,
                                          JMethodDeclaration init,
                                          JMethodDeclaration work,
                                          CType inputType, CType outputType) {
                    //System.out.println(count[0] + " " + self);
                    id.put(self, new Integer(count[0]++));
                    return self;
                }
                
                /* visit a splitter */
                public Object visitSplitter(SIRSplitter self,
                                            SIRSplitType type,
                                            JExpression[] weights) {
                    //System.out.println(count[0] + " " + self);
                    id.put(self, new Integer(count[0]++));
                    return self;
                }
                
                /* visit a joiner */
                public Object visitJoiner(SIRJoiner self,
                                          SIRJoinType type,
                                          JExpression[] weights) {
                    //System.out.println(count[0] + " " + self);
                    id.put(self, new Integer(count[0]++));
                    return self;
                }
            });
    }

    /**
     * Returns the unique ID number assigned to a given node.
     */
    private int getId(FlatNode node) {
        return id.get(node.contents).intValue();
    }

    /**
     * This function should not be called by the outside world.  It is used
     * by this class to visit each node of the FlatNode graph and create the dot
     * code for connectivity and for the node label. 
     * 
     * @param node The current node we are visiting.
     */
    public void visitNode(FlatNode node) 
    {
        // our node count
        nodeId++;

        if (node.contents instanceof SIRFilter) {
            //we are visiting a filter
            SIRFilter filter = (SIRFilter)node.contents;
        
            // print node info
            int mult = GraphFlattener.getMult(node, false, initMults, steadyMults);
            int pop = filter.getPopInt() * mult;
            int push = filter.getPushInt() * mult;
            int peekMinusPop = filter.getPeekInt() - filter.getPopInt();
            int number = getId(node);
            String name = filter.getIdent();
            boolean stateful = StatelessDuplicate.hasMutableState(filter);
            int sizeOfMutableState =  StatelessDuplicate.sizeOfMutableState(filter);
            int work = WorkEstimate.getWorkEstimate(filter).getWork(filter);
            int codeSize = CodeEstimate.estimateCode(filter);
            // the amount read or written to a file
            int input = 0, output = 0;
            if (filter instanceof SIRFileWriter) {
                output = mult;
                stateful = true;
            }
            if (filter instanceof SIRFileReader) {
                input = mult;
                stateful = true;
            }
            nodeBuf.append("node:\n" +
                           "\tname:" + name + "\n" +
                           "\tnumber:" + number + "\n" +
                           "\tstate:" + (stateful ? "stateful" : "stateless") + "\n" +
                           "\tsizeOfState:" + sizeOfMutableState + "\n" + 
                           "\twork:" + mult*work + "\n" +
                           "\tcodeSize:" + codeSize + "\n" +
                           // their "peek" is our peek-pop
                           "\tpeek:" + peekMinusPop + "\n" +
                           "\tpop:" + pop + "\n" +
                           "\tpush:" + push + "\n" +
                           "\tinput:" + input + "\n" +
                           "\toutput:" + output + "\n");

            // print edge info
            for (int i=0; i<node.getEdges().length; i++) {
                edgeBuf.append("edge:\n" + 
                               "\tnumber:" + (++edgeId)  + "\n" + 
                               "\tsize:" + (push) + "\n" +
                               "\tout:" + getId(node) + "\n" + 
                               "\tin:" + getId(node.getEdges()[i]) + "\n");
            }
        }
        
        // handle splitters
        if (node.contents instanceof SIRSplitter) {
            SIRSplitter splitter = (SIRSplitter)node.contents;
            int mult = GraphFlattener.getMult(node, false, initMults, steadyMults);
            int pop, push;
            if (splitter.getType().isDuplicate()) {
                pop = mult;
                push = mult * node.getEdges().length;
            } else {
                pop = mult * splitter.getSumOfWeights();
                push = mult * splitter.getSumOfWeights();
            }
            // print node
            nodeBuf.append("node:\n" + 
                           "\tname:" + splitter.getName() + "\n" +
                           "\tnumber:" + getId(node) + "\n" +
                           "\tstate:stateless\n" + 
                           "\tsizeOfState:0\n" + 
                           "\twork:0\n" + 
                           "\tcodeSize:0\n" + 
                           // they define "peek" as our peek-pop
                           "\tpeek:0\n" +
                           "\tpop:" + pop + "\n" + 
                           "\tpush:" + push + "\n" +
                           "\tinput:0" + "\n" +
                           "\toutput:0" + "\n");
            
            // do outgoing edges
            for (int i = 0; i < node.getEdges().length; i++) {
                edgeBuf.append("edge:\n" + 
                               "\tnumber:" + (++edgeId)  + "\n" + 
                               "\tsize:" + (mult*node.weights[i]) + "\n" +
                               "\tout:" + getId(node) + "\n" + 
                               "\tin:" + getId(node.getEdges()[i]) + "\n");
            }
        }

        // handle joiners
        if (node.contents instanceof SIRJoiner) {
            SIRJoiner joiner = (SIRJoiner)node.contents;
            int mult = GraphFlattener.getMult(node, false, initMults, steadyMults);
            int pop = mult * joiner.getSumOfWeights();
            int push = mult * joiner.getSumOfWeights();
            // print node
            nodeBuf.append("node:\n" + 
                           "\tname:" + joiner.getName() + "\n" +
                           "\tnumber:" + getId(node) + "\n" +
                           "\tstate:stateless\n" + 
                           "\tsizeOfState:0\n" + 
                           "\twork:0\n" + 
                           "\tcodeSize:0\n" + 
                           // they define "peek" as our peek-pop
                           "\tpeek:0\n" +
                           "\tpop:" + pop + "\n" + 
                           "\tpush:" + push + "\n" +
                           "\tinput:0" + "\n" +
                           "\toutput:0" + "\n");

            // print edge info
            for (int i=0; i<node.getEdges().length; i++) {
                edgeBuf.append("edge:\n" + 
                               "\tnumber:" + (++edgeId)  + "\n" + 
                               "\tsize:" + (mult*node.weights[i]) + "\n" +
                               "\tout:" + getId(node) + "\n" + 
                               "\tin:" + getId(node.getEdges()[i]) + "\n");
            }
        }
    }
}

