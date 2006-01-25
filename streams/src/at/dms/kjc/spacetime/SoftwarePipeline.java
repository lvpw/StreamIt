package at.dms.kjc.spacetime;

import at.dms.kjc.flatgraph2.*;

public class SoftwarePipeline {
    private SoftwarePipeline() {}

    public static void pipeline(SpaceTimeSchedule sched,Trace[] traces,Trace[] io) {
        int globalPrimePump=0;
        Trace[][][] schedule=sched.getSchedule();
        for(int i=0;i<traces.length;i++) {
            Trace trace=traces[i];
            int[] pos=sched.getPosition(trace);
            Trace[] tile=schedule[pos[0]][pos[1]];
            int num=pos[2];
            InputTraceNode input=(InputTraceNode)trace.getHead();
            //System.out.println(trace+" "+input.getSources().length);
            Edge[] srcs=input.getSources();
            int oldPrimePump=1;
            for(int j=0;j<srcs.length;j++) {
                OutputTraceNode src=srcs[j].getSrc();
                if(!((FilterTraceNode)src.getPrevious()).isPredefined()) {
                    Trace srcTrace=src.getParent();
                    boolean found=false;
                    int srcPrimePump=srcTrace.getPrimePump();
                    if(oldPrimePump==1)
                        oldPrimePump=srcPrimePump;
                    else
                        assert oldPrimePump==srcPrimePump:"Case Not Supported Yet "+trace;
                    if(trace.depends(srcTrace)) { //Hack to get rid of loops
                        found=true;
                    } else
                        for(int k=num-1;k>=0;k--)
                            if(tile[k]==srcTrace)
                                found=true;
                    if(found) {
                        trace.setPrimePump(srcPrimePump);
                    } else if(sched.getPosition(srcTrace)[2]<sched.getPosition(trace)[2]) {
                        trace.addDependency(srcTrace);
                        trace.setPrimePump(srcPrimePump);
                    } else {
                        srcTrace.addDependency(trace);
                        globalPrimePump++;
                        trace.setPrimePump(srcPrimePump-1);
                    }
                }
            }
        }
        for(int i=0;i<traces.length;i++) {
            Trace trace=traces[i];
            trace.setPrimePump(trace.getPrimePump()+globalPrimePump);
        }
        for(int i=0;i<io.length;i++) {
            Trace trace=io[i];
            InputTraceNode input=(InputTraceNode)trace.getHead();
            FilterTraceNode filter=(FilterTraceNode)input.getNext();
            OutputTraceNode output=(OutputTraceNode)filter.getNext();
            PredefinedContent node=(PredefinedContent)filter.getFilter();
            if(node instanceof InputContent) {
                //int[] weights=output.getWeights();
                //assert weights.length==1:"Case Not Supprted Yet";
                Edge[][] dests=output.getDests();
                assert dests.length==1:"Case Not Supprted Yet";
                Edge[] edges=dests[0];
                assert edges.length==1:"Case Not Supprted Yet";
                trace.setPrimePump(edges[0].getDest().getParent().getPrimePump());
            } else if(node instanceof OutputContent) {
                //int[] weights=input.getWeights();
                //assert weights.length==1:"Case Not Supprted Yet";
                Edge[] edges=input.getSources();
                assert edges.length==1:"Case Not Supprted Yet";
                trace.setPrimePump(edges[0].getSrc().getParent().getPrimePump());
            } else
                throw new AssertionError("Predefined filter neither input nor output");
        }
    }
}
