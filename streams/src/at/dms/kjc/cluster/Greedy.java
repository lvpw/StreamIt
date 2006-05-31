
package at.dms.kjc.cluster;

import at.dms.kjc.flatgraph.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.raw.*;
import at.dms.kjc.CType;
import java.lang.*;
import java.util.*;

/*
 * A greedy implementation of a constrained scheduler!
 * (This should actually be optimal in number of phases generated!)
 */

class Greedy {

    HashMap iteration; // SIROperator -> Integer
    HashMap credit; // SIROperator -> Integer
    HashMap queue_size; // Vector -> Integer

    HashMap phase_last_seen; // Vector -> Integer
    Vector phases; // Vcetor of Vectors

    DiscoverSchedule sched; 

    int num;
    int ph;

    int phase_num;
    int loop_start;

    int combine_ptr;

    Greedy(DiscoverSchedule sched) {

        if (ClusterBackend.debugPrint)
            System.out.println("============== Greedy ===============");

        this.sched = sched;

        phase_last_seen = new HashMap();
        phases = new Vector();
        phase_num = 0;
    
        combine_ptr = 0;

        init();

        if (ClusterBackend.debugPrint)
            System.out.println("============== Greedy ===============");

    }
    
    void init() {

        credit = new HashMap();
        iteration = new HashMap();
        queue_size = new HashMap();

        num = NodeEnumerator.getNumberOfNodes();
        //System.out.println("Number of nodes: "+num);

        for (int y = 0; y < num; y++) {
            SIROperator oper = NodeEnumerator.getOperator(y);

            credit.put(oper, new Integer(-1));
            iteration.put(oper, new Integer(0));

            Vector out = RegisterStreams.getNodeOutStreams(oper);

            for (int z = 0; z < out.size(); z++) {
                NetStream ns = (NetStream)out.get(z);
                SIROperator dst = NodeEnumerator.getOperator(ns.getDest());
                queue_size.put(makeVector(oper, dst), new Integer(0));
            }
        }

        ph = sched.getNumberOfPhases();
        //System.out.println("Number of phases: "+ph);

        for (int y = 0; y < ph; y++) {
            HashSet p = sched.getAllOperatorsInPhase(y);
            //System.out.println("phase "+y+" has size: "+p.size());

            Iterator it = p.iterator();

            while (it.hasNext()) {
                SIROperator oper = (SIROperator)it.next();
                if (oper instanceof SIRFilter) {
                    SIRFilter src = (SIRFilter)oper;
                    HashSet cons = LatencyConstraints.getOutgoingConstraints((SIRFilter)oper);
                    Iterator ci = cons.iterator();
                    while (ci.hasNext()) {
                        LatencyConstraint c = (LatencyConstraint)ci.next();
                        SIRFilter dst = (SIRFilter)c.getReceiver();
                        boolean down = LatencyConstraints.isMessageDirectionDownstream(src, dst);
                        int init_c = c.getSourceInit();
                        //System.out.println("Constraint from: "+src+" to: "+dst+(down?" DOWN":" UP")+" init-credit: "+init_c);

                        if (down) {
                            credit.put(dst, new Integer(0));
                        } else {
                            credit.put(dst, new Integer(init_c));
                        }
                    }
                }
            }
        }

    
    } 

    static Vector makeVector(SIROperator src, SIROperator dst) {
        Vector v = new Vector();
        v.add(src);
        v.add(dst);
        return v;
    }


    static int getPeek(SIROperator oper, int id) {
        assert(id >= 0);
        if (oper instanceof SIRFilter) {
            assert(id == 0);
            return ((SIRFilter)oper).getPeekInt();
        }
        return getPop(oper, id);
    }

    static int getPop(SIROperator oper, int id) {
        assert(id >= 0);
        if (oper instanceof SIRFilter) {
            assert(id == 0);
            return ((SIRFilter)oper).getPopInt();
        }
        if (oper instanceof SIRSplitter) {
            assert(id == 0);
            SIRSplitter s = (SIRSplitter)oper;
            if (s.getType().isDuplicate()) return 1;
            return ((SIRSplitter)oper).getSumOfWeights();
        }
        if (oper instanceof SIRJoiner) {
            SIRJoiner joiner = (SIRJoiner)oper;
            assert(id < joiner.getWays());
            return joiner.getWeight(id);
        }
        assert(1 == 0);
        return 0;
    }

    static int getPush(SIROperator oper, int id) {
        assert(id >= 0);
        if (oper instanceof SIRFilter) {
            assert(id == 0);
            return ((SIRFilter)oper).getPushInt();
        }
        if (oper instanceof SIRJoiner) {
            assert(id == 0);
            return ((SIRJoiner)oper).getSumOfWeights();
        }
        if (oper instanceof SIRSplitter) {
            SIRSplitter s = (SIRSplitter)oper;
            assert(id < s.getWays());
            return s.getWeight(id);
        }
        assert(1 == 0);
        return 0;
    }

    int nextPhase() {

        Vector phase = new Vector();

        if (ClusterBackend.debugPrint)
            System.out.println("-------------------------------------");

        for (int y = 0; y < ph; y++) {
            HashSet p = sched.getAllOperatorsInPhase(y);
            Iterator it = p.iterator();
            while (it.hasNext()) {
                SIROperator oper = (SIROperator)it.next();
                int id = NodeEnumerator.getSIROperatorId(oper);
                int steady_count = ((Integer)ClusterBackend.steadyExecutionCounts.get(NodeEnumerator.getFlatNode(id))).intValue();

                int _iter = ((Integer)iteration.get(oper)).intValue();
                int _credit = ((Integer)credit.get(oper)).intValue();

                int exec = steady_count;

                if (_credit >= 0 && _iter + steady_count > _credit) {
                    exec = _credit - _iter; 
                }

                int input = 2000000000;

                Vector in = RegisterStreams.getNodeInStreams(oper);
                Vector out = RegisterStreams.getNodeOutStreams(oper);
        
                for (int z = 0; z < in.size(); z++) {
                    NetStream ns = (NetStream)in.get(z);
                    SIROperator src = NodeEnumerator.getOperator(ns.getSource());       
                    int qsize = ((Integer)queue_size.get(makeVector(src, oper))).intValue();
                    int peek = getPeek(oper, z);
                    int pop = getPop(oper, z);
                    if (peek < pop) peek = pop;
                    int extra = peek - pop;

                    int can = (qsize-extra)/pop;
                    if (can < 0) can = 0;
                    if (can < input) input = can;
                }

                if (input < exec) exec = input;

                //System.out.println("CREDIT = "+_credit+" INPUT = "+input+" EXEC = "+exec);
        
                for (int z = 0; z < in.size(); z++) {
                    NetStream ns = (NetStream)in.get(z);
                    SIROperator src = NodeEnumerator.getOperator(ns.getSource());       
                    int qsize = ((Integer)queue_size.get(makeVector(src, oper))).intValue();
                    qsize -= getPop(oper, z) * exec;
                    queue_size.put(makeVector(src, oper), 
                                   new Integer(qsize));
                }

                for (int z = 0; z < out.size(); z++) {
                    NetStream ns = (NetStream)out.get(z);
                    SIROperator dst = NodeEnumerator.getOperator(ns.getDest());     
                    int qsize = ((Integer)queue_size.get(makeVector(oper, dst))).intValue();
                    qsize += getPush(oper, z) * exec;
                    queue_size.put(makeVector(oper, dst), 
                                   new Integer(qsize));
                }

                if (ClusterBackend.debugPrint)
                    System.out.println(oper.getName()+" Exec = "+exec+"/"+steady_count);

                phase.add(new Integer(exec)); // push exec count to phase vector

                for (int z = 0; z < exec; z++, _iter++) {
                    if (!(oper instanceof SIRFilter)) continue;
            
                    HashSet cons = LatencyConstraints.getOutgoingConstraints((SIRFilter)oper);
                    Iterator ci = cons.iterator();
                    while (ci.hasNext()) {

                        //System.out.println("latency constraint");

                        LatencyConstraint c = (LatencyConstraint)ci.next();
                        SIRFilter dst = (SIRFilter)c.getReceiver();
                        boolean down = LatencyConstraints.isMessageDirectionDownstream((SIRFilter)oper, (SIRFilter)dst);
                        int init_c = c.getSourceInit();
            
                        if (down) {
                
                            if (_iter >= init_c) {
                
                                int s_steady = c.getSourceSteadyExec();
                                int cycle = (_iter - init_c) / s_steady;
                                int offs = (_iter - init_c) % s_steady;
                
                                int dep = c.getDependencyData(offs);

                                //System.out.println("dep = "+dep);
                                //System.out.println("c = "+cycle);
                                //System.out.println("o = "+offs);

                                if (dep > 0) {
                                    int cc = dep + cycle * c.getDestSteadyExec();
                                    credit.put(dst, new Integer(cc));
                                    if (ClusterBackend.debugPrint)
                                        System.out.println("Send credit: "+cc);
                                }
                            }
                
                        } else {
                
                            int s_steady = c.getSourceSteadyExec();
                            int cycle = _iter / s_steady;
                            int offs = _iter % s_steady;
                
                            int dep = c.getDependencyData(offs);
                            if (dep > 0) {
                                int cc = dep + cycle * c.getDestSteadyExec();
                                credit.put(dst, new Integer(cc));
                                if (ClusterBackend.debugPrint)
                                    System.out.println("Send credit: "+cc);
                            }
                        }
                    }
                }

                iteration.put(oper, new Integer(_iter));
            }
        }   

        boolean match = false;
        int ratio = -1;

        if (phase_last_seen.containsKey(phase)) {
            int last = ((Integer)phase_last_seen.get(phase)).intValue();
        
            match = true;

            int node_id = 0;
            for (int y = 0; y < ph; y++) {
                HashSet p = sched.getAllOperatorsInPhase(y);
                Iterator it = p.iterator();
                while (it.hasNext()) {
                    SIROperator oper = (SIROperator)it.next();
                    int id = NodeEnumerator.getSIROperatorId(oper);
                    int steady_count = ((Integer)ClusterBackend.steadyExecutionCounts.get(NodeEnumerator.getFlatNode(id))).intValue();
            
                    int sum = 0;

                    for (int z = last; z < phase_num; z++) {
                        sum += ((Integer)((Vector)phases.get(z)).get(node_id)).intValue();
                    }
        
                    if (sum % steady_count == 0 &&
                        sum / steady_count >= 1) {
                        if (ratio >= 1 && sum / steady_count != ratio) match = false;
                        if (ratio < 1) ratio = sum / steady_count;
                    } else {
                        match = false;
                    }

                    node_id++;
                }
            }

            if (ClusterBackend.debugPrint)
                System.out.println("---- have seen phase at: "+last+" ----");

            if (match) {
                loop_start = last;
                if (ClusterBackend.debugPrint)
                    System.out.println("---- A loop contains "+ratio+" steady states ----");
            }

        }

        if (!match) {
            phases.add(phase);
            phase_last_seen.put(phase, new Integer(phase_num));
            phase_num++;
        }

        if (ClusterBackend.debugPrint)
            System.out.println("-------------------------------------");

        if (match) return ratio; else return 0; 

    }

    void combineInit() {

        int node_id;

        if (combine_ptr + 1 >= loop_start) return;

        init();

        if (ClusterBackend.debugPrint) {
            System.out.println("-------------------------------------");
            System.out.println("----------- COMBINING ---------------");
            System.out.println("-------------------------------------");
        }

        for (int curr = 0; curr < combine_ptr; curr++) {

            node_id = 0;

            for (int y = 0; y < ph; y++) {
                HashSet p = sched.getAllOperatorsInPhase(y);
                Iterator it = p.iterator();
                while (it.hasNext()) {
                    SIROperator oper = (SIROperator)it.next();
                    int id = NodeEnumerator.getSIROperatorId(oper);
                    int exec = ((Integer)((Vector)phases.get(curr)).get(node_id)).intValue();

                    int _iter = ((Integer)iteration.get(oper)).intValue();

                    Vector in = RegisterStreams.getNodeInStreams(oper);
                    Vector out = RegisterStreams.getNodeOutStreams(oper);

                    for (int z = 0; z < in.size(); z++) {
                        NetStream ns = (NetStream)in.get(z);
                        SIROperator src = NodeEnumerator.getOperator(ns.getSource());       
                        int qsize = ((Integer)queue_size.get(makeVector(src, oper))).intValue();
                        qsize -= getPop(oper, z) * exec;
                        queue_size.put(makeVector(src, oper), 
                                       new Integer(qsize));
                    }
            
                    for (int z = 0; z < out.size(); z++) {
                        NetStream ns = (NetStream)out.get(z);
                        SIROperator dst = NodeEnumerator.getOperator(ns.getDest());     
                        int qsize = ((Integer)queue_size.get(makeVector(oper, dst))).intValue();
                        qsize += getPush(oper, z) * exec;
                        queue_size.put(makeVector(oper, dst), 
                                       new Integer(qsize));
                    }

                    for (int z = 0; z < exec; z++, _iter++) {
                        if (!(oper instanceof SIRFilter)) continue;
            
                        HashSet cons = LatencyConstraints.getOutgoingConstraints((SIRFilter)oper);
                        Iterator ci = cons.iterator();
                        while (ci.hasNext()) {
                
                            //System.out.println("latency constraint");
                
                            LatencyConstraint c = (LatencyConstraint)ci.next();
                            SIRFilter dst = (SIRFilter)c.getReceiver();
                            boolean down = LatencyConstraints.isMessageDirectionDownstream((SIRFilter)oper, (SIRFilter)dst);
                            int init_c = c.getSourceInit();
                
                            if (down) {
                
                                if (_iter >= init_c) {
                    
                                    int s_steady = c.getSourceSteadyExec();
                                    int cycle = (_iter - init_c) / s_steady;
                                    int offs = (_iter - init_c) % s_steady;
                    
                                    int dep = c.getDependencyData(offs);
                    
                                    if (dep > 0) {
                                        int cc = dep + cycle * c.getDestSteadyExec();
                                        credit.put(dst, new Integer(cc));
                                        if (ClusterBackend.debugPrint)
                                            System.out.println("Send credit: "+cc);
                                    }
                                }
                
                            } else {
                
                                int s_steady = c.getSourceSteadyExec();
                                int cycle = _iter / s_steady;
                                int offs = _iter % s_steady;
                
                                int dep = c.getDependencyData(offs);
                                if (dep > 0) {
                                    int cc = dep + cycle * c.getDestSteadyExec();
                                    credit.put(dst, new Integer(cc));
                                    if (ClusterBackend.debugPrint)
                                        System.out.println("Send credit: "+cc);
                                }
                            }
                        }
                    }

                    iteration.put(oper, new Integer(_iter));


                }

        

            }    
        }



        boolean success = true;

        node_id = 0;

        Vector new_phase = new Vector();

        for (int y = 0; y < ph; y++) {
            HashSet p = sched.getAllOperatorsInPhase(y);
            Iterator it = p.iterator();
            while (it.hasNext()) {
                SIROperator oper = (SIROperator)it.next();
                int id = NodeEnumerator.getSIROperatorId(oper);

                int steady_count = ((Integer)((Vector)phases.get(combine_ptr)).get(node_id)).intValue() +
                    ((Integer)((Vector)phases.get(combine_ptr+1)).get(node_id)).intValue();

                node_id++;

                int _iter = ((Integer)iteration.get(oper)).intValue();
                int _credit = ((Integer)credit.get(oper)).intValue();

                int exec = steady_count;

                if (_credit >= 0 && _iter + steady_count > _credit) {
                    exec = _credit - _iter; 
                }

                int input = 2000000000;

                Vector in = RegisterStreams.getNodeInStreams(oper);
                Vector out = RegisterStreams.getNodeOutStreams(oper);
        
                for (int z = 0; z < in.size(); z++) {
                    NetStream ns = (NetStream)in.get(z);
                    SIROperator src = NodeEnumerator.getOperator(ns.getSource());       
                    int qsize = ((Integer)queue_size.get(makeVector(src, oper))).intValue();
                    int peek = getPeek(oper, z);
                    int pop = getPop(oper, z);
                    if (peek < pop) peek = pop;
                    int extra = peek - pop;

                    int can = (qsize-extra)/pop;
                    if (can < 0) can = 0;
                    if (can < input) input = can;
                }

                if (input < exec) exec = input;

                //System.out.println("CREDIT = "+_credit+" INPUT = "+input+" EXEC = "+exec);
        
                for (int z = 0; z < in.size(); z++) {
                    NetStream ns = (NetStream)in.get(z);
                    SIROperator src = NodeEnumerator.getOperator(ns.getSource());       
                    int qsize = ((Integer)queue_size.get(makeVector(src, oper))).intValue();
                    qsize -= getPop(oper, z) * exec;
                    queue_size.put(makeVector(src, oper), 
                                   new Integer(qsize));
                }

                for (int z = 0; z < out.size(); z++) {
                    NetStream ns = (NetStream)out.get(z);
                    SIROperator dst = NodeEnumerator.getOperator(ns.getDest());     
                    int qsize = ((Integer)queue_size.get(makeVector(oper, dst))).intValue();
                    qsize += getPush(oper, z) * exec;
                    queue_size.put(makeVector(oper, dst), 
                                   new Integer(qsize));
                }

                if (ClusterBackend.debugPrint)
                    System.out.println(oper.getName()+" Exec = "+exec+"/"+steady_count);

                if (exec < steady_count) success = false;

                new_phase.add(new Integer(exec)); // push exec count to phase vector

                for (int z = 0; z < exec; z++, _iter++) {
                    if (!(oper instanceof SIRFilter)) continue;
            
                    HashSet cons = LatencyConstraints.getOutgoingConstraints((SIRFilter)oper);
                    Iterator ci = cons.iterator();
                    while (ci.hasNext()) {

                        //System.out.println("latency constraint");

                        LatencyConstraint c = (LatencyConstraint)ci.next();
                        SIRFilter dst = (SIRFilter)c.getReceiver();
                        boolean down = LatencyConstraints.isMessageDirectionDownstream((SIRFilter)oper, (SIRFilter)dst);
                        int init_c = c.getSourceInit();
            
                        if (down) {
                
                            if (_iter >= init_c) {
                
                                int s_steady = c.getSourceSteadyExec();
                                int cycle = (_iter - init_c) / s_steady;
                                int offs = (_iter - init_c) % s_steady;
                
                                int dep = c.getDependencyData(offs);

                                //System.out.println("dep = "+dep);
                                //System.out.println("c = "+cycle);
                                //System.out.println("o = "+offs);

                                if (dep > 0) {
                                    int cc = dep + cycle * c.getDestSteadyExec();
                                    credit.put(dst, new Integer(cc));
                                    if (ClusterBackend.debugPrint)
                                        System.out.println("Send credit: "+cc);
                                }
                            }
                
                        } else {
                
                            int s_steady = c.getSourceSteadyExec();
                            int cycle = _iter / s_steady;
                            int offs = _iter % s_steady;
                
                            int dep = c.getDependencyData(offs);
                            if (dep > 0) {
                                int cc = dep + cycle * c.getDestSteadyExec();
                                credit.put(dst, new Integer(cc));
                                if (ClusterBackend.debugPrint)
                                    System.out.println("Send credit: "+cc);
                            }
                        }
                    }
                }

                iteration.put(oper, new Integer(_iter));
            }
        }
    
        if (ClusterBackend.debugPrint)
            System.out.println(success?"SUCCESS":"FAILED");

        if (success) {
            phases.set(combine_ptr, new_phase);
            phases.remove(combine_ptr+1);
            loop_start--;

        } else {
            combine_ptr++;
        }
    }

}
