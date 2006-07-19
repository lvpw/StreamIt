package at.dms.kjc.sir.lowering.fission;

import at.dms.kjc.*;
import at.dms.util.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.fusion.*;
import java.util.*;

/**
 * This class splits a stateless filter with arbitrary push/pop/peek
 * ratios into a duplicate/round-robin split-join of a user-specified
 * width.
 */
public class StatelessDuplicate {
    /**
     * Toggle that indicates whether a round-robing splitter should be
     * used in the resulting splitjoin if pop==peek for the filter
     * we're fissing.  This seems like it would be a good idea, but it
     * turns out to usually be faster to duplicate all data and
     * decimate at the nodes.
     */
    private static final boolean USE_ROUNDROBIN_SPLITTER = true;
    
    /**
     * The filter we're duplicating.
     */
    private SIRFilter origFilter;
    
    /**
     * The number of repetitions to duplicate
     */
    private int reps;

    /**
     * The desired ratio of work between the fissed children.
     * Invariant: workRatio.length = reps.
     */
    private int[] workRatio;

    /**
     * The list of resulting filters
     */
    private LinkedList newFilters;
    
    private StatelessDuplicate(SIRFilter origFilter, int reps, int[] workRatio) {
        this.origFilter = origFilter;
        this.reps = reps;
        this.workRatio = workRatio;
        this.newFilters = new LinkedList();
    }

    /**
     * Duplicates <filter> into a <reps>-way SplitJoin and replaces
     * the filter with the new construct in the parent.  The new
     * filters will be load-balanced according to 'workRatio'.  For
     * example, if workRatio = {1, 2}, then the second child will do
     * twice as much work as the first child.
     *
     * Requires workRatio.length == reps.
     */
    public static SIRSplitJoin doit(SIRFilter origFilter, int reps, int[] workRatio) {
        if (isFissable(origFilter)) {
            return new StatelessDuplicate(origFilter, reps, workRatio).doit();
        } else {
            Utils.fail("Trying to split an un-fissable filter: " + origFilter);
            return null;
        }
    }
    /**
     * Duplicates <filter> into a <reps>-way SplitJoin and replaces
     * the filter with the new construct in the parent.  The resulting
     * children will all do the same amount of work.
     */
    public static SIRSplitJoin doit(SIRFilter origFilter, int reps) {
        // init workRatio to {1, 1, ..., 1}
        int[] workRatio = new int[reps];
        for (int i=0; i<reps; i++) {
            workRatio[i] = 1;
        }
        return doit(origFilter, reps, workRatio);
    }

    /**
     * Checks whether or not <filter> can be fissed.
     */
    public static boolean isFissable(SIRFilter filter) {
        //do not fiss sinks
        if (filter.getPushInt() == 0)
            return false;

        // check that it does not have mutable state
        if (hasMutableState(filter)) {
            return false;
        }
    
        //Hack to prevent fissing file writers
        if(filter.getIdent().startsWith("FileWriter"))
            return false;

        //Don't fiss identities
        if(filter instanceof SIRIdentity)
            return false;

        // don't split a filter with a feedbackloop as a parent, just
        // as a precaution, since feedbackloops are hard to schedule
        // when stuff is blowing up in the body
        SIRContainer[] parents = filter.getParents();
        for (int i=0; i<parents.length; i++) {
            if (parents[i] instanceof SIRFeedbackLoop) {
                return false;
            }
        }
        // We don't yet support fission of two-stage filters that peek.
        if (filter instanceof SIRTwoStageFilter) {
            SIRTwoStageFilter twoStage = (SIRTwoStageFilter)filter;
            if (twoStage.getInitPopInt()>0) {
                return false;
            }
        }

        //This seems to break fission too
        if(filter.getPopInt()==0)
            return false;

        return true;
    }

    /**
     * Returns whether or not <filter> has mutable state.  This is
     * equivalent to checking if there are any assignments to fields
     * outside of the init function.
     */
    public static boolean hasMutableState(final SIRFilter filter) {
        // whether or not we are on the LHS of an assignment
        final boolean[] inAssignment = { false };
        // whether or not we have found any mutable state
        final boolean[] foundMutable = { false };

        // visit all methods except <init>
        JMethodDeclaration[] methods = filter.getMethods();
        JMethodDeclaration init = filter.getInit();
        for(int i=0;i<methods.length;i++)
            if(methods[i]!=init)
                methods[i].accept(new SLIREmptyVisitor() {
                        // wrap visit to <left> with some book-keeping
                        // to indicate that it is on the LHS of an assignment
                        private void wrapVisit(JExpression left) {
                            // in case of nested assignment
                            // expressions, remember the old value of <inAssignment>
                            boolean old = inAssignment[0];
                            inAssignment[0] = true;
                            left.accept(this);
                            inAssignment[0] = old;
                        }

                        public void visitCompoundAssignmentExpression(JCompoundAssignmentExpression self,
                                                                      int oper,
                                                                      JExpression left,
                                                                      JExpression right) {
                            wrapVisit(left);
                            right.accept(this);
                        }

                        public void visitAssignmentExpression(JAssignmentExpression self,
                                                              JExpression left,
                                                              JExpression right) {
                            wrapVisit(left);
                            right.accept(this);
                        }

                        public void visitFieldExpression(JFieldAccessExpression self,
                                                         JExpression left,
                                                         String ident) {
                            // if we are in assignment, mark that there is mutable state
                            if (inAssignment[0]) {
                                foundMutable[0] = true;
                            }

                            super.visitFieldExpression(self, left, ident);
                        }
                        
                    });

        return foundMutable[0];
    }

    /**
     * Carry out the duplication on this instance.
     */
    private SIRSplitJoin doit() {
        // make new filters
        for (int i=0; i<reps; i++) {
            newFilters.add(makeDuplicate(i));
        }

        // make result
        SIRSplitJoin result = new SIRSplitJoin(origFilter.getParent(), origFilter.getIdent() + "_Fiss");

        // replace in parent
        origFilter.getParent().replace(origFilter, result);

        // make an init function
        JMethodDeclaration init = makeSJInit(result);

        // create the splitter
        if (USE_ROUNDROBIN_SPLITTER && origFilter.getPeekInt()==origFilter.getPopInt()) {
            // without peeking, it's a round-robin..
            // assign split weights according to workRatio and pop rate of filter
            JExpression[] splitWeights = new JExpression[reps];
            for (int i=0; i<reps; i++) {
                splitWeights[i] = new JIntLiteral(workRatio[i] * origFilter.getPopInt());
            }
            result.setSplitter(SIRSplitter.createWeightedRR(result, splitWeights));
        } else {
            // with peeking, it's just a duplicate splitter
            result.setSplitter(SIRSplitter.
                               create(result, SIRSplitType.DUPLICATE, reps));
        }

        // create the joiner
        if (origFilter.getPushInt() > 0) {
            // assign join weights according to workRatio and push rate of filter
            JExpression[] joinWeights = new JExpression[reps];
            for (int i=0; i<reps; i++) {
                joinWeights[i] = new JIntLiteral(workRatio[i] * origFilter.getPushInt());
            }
            result.setJoiner(SIRJoiner.createWeightedRR(result, joinWeights));
        } else {
            result.setJoiner(SIRJoiner.create(result,
                                              SIRJoinType.NULL, 
                                              reps));
            // rescale the joiner to the appropriate width
            result.rescale();
        }

        // set the init function
        result.setInit(init);

        /*
        // propagate constants through the new stream, to resolve
        // arguments to init functions
        SIRContainer toplevel = result.getParent();
        while (toplevel.getParent()!=null) {
        toplevel = toplevel.getParent();
        }
        ConstantProp.propagateAndUnroll(toplevel);
        */
        return result;
    }

    /**
     * Returns an init function for the containing splitjoin.
     */
    private JMethodDeclaration makeSJInit(SIRSplitJoin sj) {
        // start by cloning the original init function, so we can get
        // the signature right
        JMethodDeclaration result = (JMethodDeclaration)
            ObjectDeepCloner.deepCopy(origFilter.getInit());
        // get the parameters
        List params = sj.getParams();
        // now build up the body as a series of calls to the sub-streams
        LinkedList bodyList = new LinkedList();
        for (ListIterator it = newFilters.listIterator(); it.hasNext(); ) {
            // build up the argument list
            LinkedList args = new LinkedList();
            for (ListIterator pit = params.listIterator(); pit.hasNext(); ) {
                args.add((JExpression)pit.next());
            }
            // add the child and the argument to the parent
            sj.add((SIRStream)it.next(), args);
        }
        // replace the body of the init function with statement list
        // we just made
        result.setBody(new JBlock(null, bodyList, null));
        // return result
        return result;
    }

    /**
     * Makes the <i>'th duplicate filter in this.
     */
    private SIRFilter makeDuplicate(int i) {
        // start by cloning the original filter, and copying the state
        // into a new filter.
        SIRFilter cloned = (SIRFilter)ObjectDeepCloner.deepCopy(origFilter);
        // if there is no peeking, then we can returned <cloned>.
        if (USE_ROUNDROBIN_SPLITTER && origFilter.getPeekInt()==origFilter.getPopInt()) {
            return cloned;
        } else {
            // wrap work function with pop statements for extra items
            wrapWorkFunction(cloned, i);
            // return result
            return cloned;
        }
    }

    // For the i'th child in the fissed splitjoin, does two things:
    // 
    // 1. wraps the work function in a loop corresponding to the
    // workRatio of the filter
    //
    // 2. adds pop statements before and after the loop to eliminate
    // items destined for other filters
    // 
    // Also adjusts the I/O rates to match this behavior.  Note that
    // this function only applies to duplication utilizing a duplicate
    // splitter.
    private void wrapWorkFunction(SIRFilter filter, int i) {
        JMethodDeclaration work = filter.getWork();
        // if we have a workRatio of 0, we'll get a class cast
        // exception from JEmptyStatement to JBlock on the next line
        assert workRatio[i] > 0 : "Require workRatio > 0 in horizontal fission.";
        // wrap existing work function according to its work ratio
        work.setBody((JBlock)Utils.makeForLoop(work.getBody(), workRatio[i]));

        // calculate the pops that should come before and after
        int popsBefore = 0;
        int popsAfter = 0;
        for (int j=0; j<i; j++) {
            popsBefore += workRatio[j] * filter.getPopInt();
        }
        for (int j=i+1; j<reps; j++) {
            popsAfter += workRatio[j] * filter.getPopInt();
        }

        // add pop statements to beginning and end of filter
        work.getBody().addStatementFirst(makePopLoop(popsBefore));
        work.getBody().addStatement(makePopLoop(popsAfter));

        // SET NEW I/O RATES FOR FILTER:
        // pop items destined for any splitjoins
        filter.setPop(getTotalPops());
        // push items according to work ratio
        filter.setPush(workRatio[i] * origFilter.getPushInt());
        // peek the same as the pop rate, unless the last execution
        // peeked even further
        filter.setPeek(Math.max(getTotalPops(),
                                popsBefore+(workRatio[i]-1)*origFilter.getPopInt()+origFilter.getPeekInt()));
    }

    /**
     * Returns a loop that pops <i> items.
     */
    private JStatement makePopLoop(int i) {
        JStatement[] popStatement = 
            { new JExpressionStatement(null, new SIRPopExpression(), null) } ;
        // wrap it in a block and a for loop
        JBlock popBlock = new JBlock(null, popStatement, null);
        return Utils.makeForLoop(popBlock, i);
    }

    /**
     * Returns the total number of items popped by the fissed filters.
     */
    private int totalPops = 0;
    private int getTotalPops() {
        // memoize the sum
        if (totalPops > 0) return totalPops;
        // otherwise compute it
        for (int i=0; i<reps; i++) {
            totalPops += workRatio[i] * origFilter.getPopInt();
        }
        return totalPops;
    }
}
