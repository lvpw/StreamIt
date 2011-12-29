package at.dms.kjc.sir.lowering.partition;

import java.util.LinkedList;
import java.util.Map;

import at.dms.kjc.CType;
import at.dms.kjc.JExpression;
import at.dms.kjc.JFieldDeclaration;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.GraphFlattener;
import at.dms.kjc.sir.EmptyAttributeStreamVisitor;
import at.dms.kjc.sir.SIRContainer;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRIdentity;
import at.dms.kjc.sir.SIRJoinType;
import at.dms.kjc.sir.SIRJoiner;
import at.dms.kjc.sir.SIROperator;
import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRSplitJoin;
import at.dms.kjc.sir.SIRSplitType;
import at.dms.kjc.sir.SIRSplitter;
import at.dms.kjc.sir.SIRStream;
import at.dms.kjc.sir.lowering.ConstantProp;
import at.dms.kjc.sir.lowering.fission.StatelessDuplicate;
import at.dms.kjc.sir.lowering.fusion.FusePipe;
import at.dms.kjc.sir.lowering.fusion.FuseSplit;
import at.dms.kjc.sir.lowering.fusion.Lifter;

public class GreedyPartitioner {
    /**
     * The toplevel stream we're operating on.
     */
    private SIRStream str;
    /**
     * Most recent work estimate for this.
     */
    private WorkEstimate work;
    /**
     * The target number of tiles this partitioner is going for.
     */
    private int target;
    /**
     * Whether or not joiners need tiles.
     */
    private boolean joinersNeedTiles;
    
    //How many times to attempt fissing till use up all space
    private final int MAX_FISS_ITER=5;

    public GreedyPartitioner(SIRStream str, WorkEstimate work, int target, boolean joinersNeedTiles) {
        this.str = str;
        this.target = target;
        this.work = work;
        this.joinersNeedTiles = joinersNeedTiles;
    }

    public void toplevelFission(int startingTileCount) {
        int count = startingTileCount;
        for(int i=0;i<MAX_FISS_ITER;i++) {
            fissAll();
            //System.out.print("count tiles again... ");
            count = Partitioner.countTilesNeeded(str, joinersNeedTiles);
            //System.out.println("done:"+count);
            if(count>=target) {
                break;
            } else {
                // redo work estimate
                this.work = WorkEstimate.getWorkEstimate(str);
            }
        }
        if(count>target) {
            fuseAll();
        }
    }
    
    public void toplevelFusion() {
        fuseAll();
    }

    /*************************************************************************/

    private void aggressiveFiss() {
        // get work sorted by work
        WorkList sorted=work.getSortedFilterWork();
        //System.out.println("Sorted:"+sorted);
        //int lowestFissable=-1;
        //Find smallest fissable filter
        for(int i=sorted.size()-1;i>=0;i--) {
            SIRFilter filter=sorted.getFilter(i);
            if(StatelessDuplicate.isFissable(filter)) {
                GraphFlattener flattener = new GraphFlattener(str);
                int dec=tilesForFission(filter, flattener, 2);
                target-=dec;
                if(fuseAll()<=target) {
                    if(StatelessDuplicate.isFissable(filter)) {
                        StatelessDuplicate.doit(filter, 2);
                        ConstantProp.propagateAndUnroll(filter.getParent());
                    }
                }
                target+=dec;
                break;
            }
        }
        //fuseAll();
    }

    /**
     * Do all the fission we can.
     */
    private void fissAll() {
        // get work sorted by work
        WorkList sorted = work.getSortedFilterWork();
        //System.out.println("Sorted:"+sorted);
        // keep track of the position of who we've tried to split
        int pos = sorted.size()-1;

        while (pos>=0) {
            if((sorted.getFilter(pos) instanceof SIRIdentity)||
               (sorted.getFilter(pos).getIdent().startsWith("FileWriter"))) { //Hack to not fuse file writer
                pos--;
                continue;
            }
            // make raw flattener for latest version of stream graph
            GraphFlattener flattener = new GraphFlattener(str);
            // get work at <pos>
            long work = sorted.getWork(pos);
            // make a list of candidates that have the same amount of work
            LinkedList<SIRFilter> candidates = new LinkedList<SIRFilter>();
            //System.out.println("Trying:"+pos+" "+sorted.getFilter(pos));
            while (pos>=0 && sorted.getWork(pos)==work) {
                candidates.add(sorted.getFilter(pos));
                pos--;
            }
            // try fissing the candidates
            if (canFiss(candidates, flattener)) {
                doFission(candidates);
            } /*else {
              // if illegal, quit
              break;
              }*/
        }
    }
    
    /**
     * Returns whether or not it's legal to fiss candidates.
     */
    private boolean canFiss(LinkedList<SIRFilter> candidates, GraphFlattener flattener) {
        // count the number of new tiles required
        int newTiles = 0;
        // go through the candidates and count tiles, check that can
        // be duplicated
        for (int i=0; i<candidates.size(); i++) {
            SIRFilter filter = candidates.get(i);
            // check fissable
            if (!StatelessDuplicate.isFissable(filter)) {
                return false;
            }
            // count new copies of the filter
            newTiles += tilesForFission(filter, flattener, 2);
        }
        // return whether or not there's room for candidates
        return (Partitioner.countTilesNeeded(str, flattener, joinersNeedTiles)+newTiles<=target);
    }
    
    /**
     * Returns the number of NEW tiles that will be required under the
     * current configuration of 'str' if 'filter' is split into 'ways'
     * copies.
     */
    private int tilesForFission(SIRFilter filter, GraphFlattener flattener, int ways) {
        int result = 0;
        // add tiles for the copies of <filter>
        result += ways-1;

        if (joinersNeedTiles) {
            // add a tile for the joiner if it's not followed by a joiner
            // or a nullshow
            FlatNode successor = flattener.getFlatNode(filter).getEdges()[0];
            if (successor!=null && !(successor.contents instanceof SIRJoiner)) {
                result += 1;
            }
        }

        return result;
    }

    /**
     * Split everyone in 'candidates' two ways.
     */
    private void doFission(LinkedList<SIRFilter> candidates) {
        for (int i=0; i<candidates.size(); i++) {
            SIRFilter filter = candidates.get(i);
            StatelessDuplicate.doit(filter, 2);
            // constant prop through new filters
            ConstantProp.propagateAndUnroll(filter.getParent());
            //FieldProp.doPropagate((lowPass.getParent()));
        }
    }

    /**
     * Do all the fusion we have to do until we're within our target
     * number of filters.
     */
    private int fuseAll() {
        // how many tiles we have occupied
        int count = 0;
        // whether or not we're done trying to fuse
        boolean done = false;
        int aggressive=0;
        do {
            // get how many tiles we have
            count = Partitioner.countTilesNeeded(str, joinersNeedTiles);
            System.out.println("  Partitioner detects " + count + " tiles.");
            if (count>target) {
                //boolean tried=false;
                // make a fresh work estimate
                this.work = WorkEstimate.getWorkEstimate(str);
                // get the containers in the order of work for filters
                // immediately contained
                WorkList list = work.getSortedContainerWork();
                //System.out.println(list);
                // work up through this list until we fuse something
                for (int i=0; i<list.size(); i++) {
                    SIRContainer cont = list.getContainer(i);
                    if (cont instanceof SIRSplitJoin) {
                        //System.out.println("trying to fuse " + cont.size() + "-way split " + ((SIRSplitJoin)cont).getName());
                        if(aggressive==0) {
                            // skip this container if it has an identity child
                            boolean attempt=true;
                            for(int j=0;j<cont.size();j++) {
                                if (cont.get(j) instanceof SIRIdentity) {
                                    attempt=false;
                                }
                            }
                            if(attempt) {
                                SIRStream newstr = FuseSplit.semiFuse((SIRSplitJoin)cont);
                                // replace toplevel stream if we're replacing it
                                if (cont==str) {
                                    str = newstr;
                                }
                                // if we fused something, quit loop
                                if (newstr!=cont) {
                                    aggressive=0;
                                    break;
                                }
                            }
                        } else if(aggressive==1) {
                            // skip this container if it has nothing but identity children
                            boolean attempt=false;
                            for(int j=0;j<cont.size();j++) {
                                if (!(cont.get(j) instanceof SIRIdentity)) {
                                    attempt=true;
                                }
                            }
                            if(attempt) {
                                SIRStream newstr = FuseSplit.semiFuse((SIRSplitJoin)cont);
                                // replace toplevel stream if we're replacing it
                                if (cont==str) {
                                    str = newstr;
                                }
                                // if we fused something, quit loop
                                if (newstr!=cont) {
                                    aggressive=0;
                                    break;
                                }
                            }
                        } else {
                            // otherwise, always fuse if we can
                            SIRStream newstr = FuseSplit.semiFuse((SIRSplitJoin)cont);
                            // replace toplevel stream if we're replacing it
                            if (cont==str) {
                                str = newstr;
                            }
                            // if we fused something, quit loop
                            if (newstr!=cont) {
                                aggressive=0;
                                //tried=true;
                                break;
                            }
                        }
                    } else if (cont instanceof SIRPipeline) {
                        //System.out.println("trying to fuse " + (count-target) + " from " 
                        //+ cont.size() + "-long pipe " + ((SIRPipeline)cont).getName());
                        int elim=0;
                        if(cont.size()<=2) {
                            elim = FusePipe.fuse((SIRPipeline)cont, count-target);
                
                            Lifter.eliminatePipe((SIRPipeline)cont);
                        } else {
                            long best=Long.MAX_VALUE;
                            int index=0;
                            SIRStream cur=cont.get(0);
                            for(int j=0;j<cont.size()-1;j++) {
                                SIRStream next=cont.get(j+1);
                                if((cur instanceof SIRFilter)&&(next instanceof SIRFilter)) {
                                    long newWork=work.getWork((SIRFilter)cont.get(j))+work.getWork((SIRFilter)cont.get(j+1));
                                    if(newWork<best) {
                                        best=newWork;
                                        index=j;
                                    }
                                }
                                cur=next;
                            }
                            if(best!=Long.MAX_VALUE) {
                                elim = FusePipe.fuseTwo((SIRPipeline)cont, index);
                            }
                        }
                        // try lifting
                        if (elim!=0) {
                            //System.out.println("FUSED: "+elim);
                            aggressive=0;
                            break;
                        }
                    }
                    // if we made it to the end of the last loop, then
                    // we're done trying to fuse (we didn't reach target.)
                    if (i==list.size()-1) { 
                        if(aggressive>=2)
                            done=true;
                        else
                            //if(!tried)
                            aggressive++;
                    }
                }
            }
        } while (count>target && !done);
        return count;
    }

    /**
     * Assuming that 'str' is the product of a greedy (or greedier)
     * partitioning, produce a mapping from SIROperator's to N hosts.
     *
     * The result is saved in the argument 'map'.
     *
     * Currently this is very primitive, should be made smarter.
     */
    public static void makePartitionMap(SIRStream str, Map<SIROperator,Integer> map, int N) {
        // first get all the siroperators
        final LinkedList operators = new LinkedList();
        str.accept(new EmptyAttributeStreamVisitor() {
                @Override
				public Object visitFilter(SIRFilter self,
                                          JFieldDeclaration[] fields,
                                          JMethodDeclaration[] methods,
                                          JMethodDeclaration init,
                                          JMethodDeclaration work,
                                          CType inputType, CType outputType) {
                    operators.add(self);
                    return self;
                }

                @Override
				public Object visitSplitter(SIRSplitter self,
                                            SIRSplitType type,
                                            JExpression[] weights) {
                    operators.add(self);
                    return self;
                }
    
                @Override
				public Object visitJoiner(SIRJoiner self,
                                          SIRJoinType type,
                                          JExpression[] weights) {
                    operators.add(self);
                    return self;
                }
                
            });
        SIROperator[] ops = (SIROperator[])operators.toArray(new SIROperator[0]);

        // then distribute operators "evenly" (and in order) across partitions
        int curOperator = 0;
        int curPartition = 0;
        while (curOperator<ops.length && curPartition<N) {
            // assign operator to the current partition until the
            // progress in assigning operators exceeds the progress in
            // the available partitions
            while (true) {
                double opsProgress = (double)(curOperator+1) / (double)ops.length;
                double parProgress = (double)(curPartition+1) / (double)N;
                if (opsProgress <= parProgress && curOperator < ops.length) {
                    map.put(ops[curOperator], new Integer(curPartition));
                    curOperator++;
                } else {
                    break;
                }
            }
            curPartition++;
        }
    }
}

