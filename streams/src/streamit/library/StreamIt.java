package streamit;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;
import streamit.scheduler2.iriter.Iterator;
import streamit.scheduler2.minlatency.StreamFactory;
import streamit.scheduler2.base.StreamInterface;
import streamit.scheduler2.ScheduleBuffers;
import streamit.scheduler2.Schedule;
import streamit.scheduler2.ScheduleOptimizer;

/**
 * Main class
 */
public class StreamIt extends Pipeline
{
    int numExecutions = 0;
    boolean printdot = false;
    boolean printsched = false;

    int uncompressedSize = 0;
    int totalSize = 0;
    HashMap sizeMap = new HashMap();
    HashSet usefulSet = new HashSet();
    public static int totalBuffer = 0;

    Vector integers = new Vector();

    Integer getInteger(int i)
    {
        int size = integers.size();
        while (size < i + 1)
        {
            integers.add(new Integer(size));
            size++;
        }

        return (Integer)integers.get(i);
    }

    public int computeSize(Object s, boolean top)
    {
        if (sizeMap.get(s) != null)
        {
            //System.out.print (".");
            return 0;
        }

        {
            ASSERT(s instanceof Schedule);

            int index = sizeMap.size();
            sizeMap.put(s, getInteger(index));

            Schedule sched = (Schedule)s;

            if (sched.isBottomSchedule())
                return 0;

            int size = sched.getNumPhases();
            for (int nSched = 0; nSched < sched.getNumPhases(); nSched++)
            {
                size += computeSize(sched.getSubSched(nSched), top);
            }

            return size;
        }

        /*
        
        int index = sizeMap.size ();
        
        sizeMap.put(s,getInteger(index));
        if (s instanceof Schedule)
        {
            Schedule sched = (Schedule) s;
            ASSERT (sched.getNumReps() == 1);
            if (sched.isBottomSchedule()) 
            {
                usefulSet.add (sched);
                //System.out.print("X");
        if (printsched) {
            System.out.println ("$" + index + " = " + sched.getStream().getObject() + "." + sched.getWorkFunc());
        }
                if (top) totalSize++;
                return;
            }
        
            int size = 0;
        
            Integer lastIndx = null;
            int nSched;
            
            boolean useful = false;
            
            HashMap pos2phase = new HashMap ();
            
            for (nSched = 0; nSched < sched.getNumPhases();nSched++)
            {
                Object next = sched.getSubSched (nSched);
                computeSize (next, top && sched.getNumPhases () == 1);
                
                Integer pIdx = (Integer)sizeMap.get(next);
                ASSERT (pIdx);
                pos2phase.put(getInteger(nSched), pIdx);
                
                if (!useful && usefulSet.contains(next)) 
                {
                    useful = true;
                    usefulSet.add (sched);
                }
            }
            
            {
                Integer other = (Integer)sizeMap.get(pos2phase);
                if (other != null)
                {
                    sizeMap.put (s,other);
            if (printsched) {
        	System.out.println ("$" + index + " = $" + other.intValue());
            }
                    return;
                } else {
                    sizeMap.put(pos2phase, getInteger (index));
                }
            }
        
        if (printsched) {
        System.out.print ("$" + index + " = ");
        }
        
            int reps = 0;
            for (nSched = 0; useful && nSched < sched.getNumPhases(); nSched++)
            {
                Object next = sched.getSubSched (nSched);
                
                Integer nextIndx = (Integer) sizeMap.get(next);
                
                if (nextIndx != lastIndx && reps != 0)
                {
            if (printsched) {
        	System.out.print ("{");
        	if (reps > 1) System.out.print (reps + " ");
        	System.out.print ("$" + lastIndx.intValue() + "}");
            }
                    reps = 0;
                }
                
                if (lastIndx != nextIndx) 
                {
                    size++;
                }
                lastIndx = nextIndx;
                
                reps++;
            }
            
            if (lastIndx != null)
            {
        if (printsched) {
            System.out.print ("{");
            if (reps > 1) System.out.print (reps + " ");
            System.out.println ("$" + lastIndx.intValue() + "}");
        }
            } else {
        if (printsched) {
            System.out.println ("{}");
        }
            }
            
            
            if (size > 1) 
                totalSize += size;
        }
        */
    }

    void runSchedule(Object schedule, Object function)
    {
        if (schedule instanceof Operator)
        {
            numExecutions++;
            if (numExecutions == 10000)
            {
                if (printdot)
                    System.out.print(".");
                numExecutions = 0;
            }
            Operator oper = (Operator)schedule;
            int filterPop, filterPush;
            if (oper instanceof Filter)
            {
                Filter f = (Filter)oper;
                f.executeNextPhase((String)function);
            }
            else if (
                oper instanceof SplitJoin || oper instanceof FeedbackLoop)
            {
                ASSERT(function instanceof Operator);
                ((Operator)function).work();
            }
            else
                ASSERT(false);

        }
    }
    void runSchedule(Object schedule, int nTimes)
    {
        if (schedule instanceof Schedule)
        {
            Schedule repSchedule = (Schedule)schedule;

            for (; nTimes > 0; nTimes--)
            {
                if (repSchedule.isBottomSchedule())
                {
                    runSchedule(
                        repSchedule.getStream().getObject(),
                        repSchedule.getWorkFunc());
                }
                else
                {
                    int nSched;
                    for (nSched = 0;
                        nSched < repSchedule.getNumPhases();
                        nSched++)
                    {
                        runSchedule(
                            repSchedule.getSubSched(nSched),
                            repSchedule.getSubSchedNumExecs(nSched));
                    }
                }
            }

        }
        else
            ASSERT(false);
    }

    /* removing this to force people to pass arguments
    public void run ()
    {
        run(null);
    }
    */

    // just a runtime hook to run the stream
    public void run(String args[])
    {
        boolean scheduledRun = true;
        boolean printGraph = false;
        boolean doRun = true;
        boolean schedsingleapp = false;
        int nIters = -1;
        float fracPhaseExec = 0;

        // read the args:
        if (args != null)
        {
            int length = args.length;
            int index;
            for (index = 0; index < length; index++)
            {
                if (args[index].equals("-nosched"))
                {
                    scheduledRun = false;
                }
                else if (args[index].equals("-printgraph"))
                {
                    printGraph = true;
                }
                else if (args[index].equals("-i"))
                {
                    index++;
                    nIters = Integer.valueOf(args[index]).intValue();
                }
                else if (args[index].equals("-printdot"))
                {
                    printdot = true;
                }
                else if (args[index].equals("-printsched"))
                {
                    printsched = true;
                }
                else if (args[index].equals("-norun"))
                {
                    doRun = false;
                }
                else if (args[index].equals("-phasefrac"))
                {
                    index++;
                    fracPhaseExec = Float.valueOf(args[index]).floatValue();
                }
                else if (args[index].equals("-schedsingleapp"))
                {
                    schedsingleapp = true;
                }
                else
                {
                    ERROR("Unrecognized argument: " + args[index] + ".");
                }
            }
        }

        setupOperator();

        ASSERT(
            getInputChannel() == null,
            "The toplevel stream can't have any input or output channels,\n"
                + "but in this program there is an input to the first filter.");
        ASSERT(
            getOutputChannel() == null,
            "The toplevel stream can't have any input or output channels,\n"
                + "but in this program there is an output of the last filter.");

        // setup the scheduler
        if (printGraph)
        {
            ASSERT(
                false,
                "Graph printing is not implemented yet.  When I'm done with a certain amount\n"
                    + "of the scheduler, I can start using the iterators to do this properly.");
        }

        if (!doRun)
            System.exit(0);

        // setup the scheduler
        if (scheduledRun)
        {
            {
                HashSet x = new HashSet(), y = new HashSet();
                Integer a = new Integer(9), b = new Integer(9);
                ASSERT(a != b);
                x.add(a);
                y.add(b);
                ASSERT(x.equals(y));
                HashSet z = new HashSet();
                z.add(a);
                ASSERT(z.contains(b));
                z.add(x);
                ASSERT(z.contains(y));

                HashMap i = new HashMap();
                i.put(x, a);
                ASSERT(i.get(y));
                i.put(a, x);
                ASSERT(i.get(b));

                //System.out.println ("OK");
                //System.exit (0);

                Vector a1 = new Vector();
                Vector a2 = new Vector();

                for (int r = 0; r < 6; r++)
                {
                    a1.add(new Integer(r));
                    a2.add(new Integer(r));
                }

                i.put(a1, null);
                ASSERT(i.containsKey(a2));
            }
            // not implemented yet. waiting for the scheduler to
            // be done.
            System.out.println("phaseFrac = " + fracPhaseExec);
            Iterator selfIter = new streamit.iriter.Iterator(this);
            streamit.scheduler2.base.StreamFactory factory = new StreamFactory(fracPhaseExec);
            StreamInterface selfStream = factory.newFrom(selfIter, null);
            selfStream.computeSchedule();
            Schedule initSched = selfStream.getInitSchedule();
            Schedule steadySched = selfStream.getSteadySchedule();

            ScheduleBuffers buffers = new ScheduleBuffers(selfIter);
            buffers.computeBuffersFor(initSched);
            buffers.computeBuffersFor(steadySched);

            // write equals and hashCode functions for operators!
            // this will solve all equality problems.

            /*
            scheduler = new SimpleHierarchicalScheduler ();
            
            SchedStream stream;
            stream = (SchedStream) constructSchedule ();
            ASSERT (stream);
            
            scheduler.useStream (stream);
            scheduler.computeSchedule ();
            */

            // setup the buffer lengths for the stream setup here:
            setupBufferLengths(buffers);

            ScheduleOptimizer opt =
                new ScheduleOptimizer(initSched, steadySched);
            steadySched = opt.getOptimizedSteadySched();
            initSched = opt.getOptimizedInitSched();

            opt = new ScheduleOptimizer(initSched, steadySched);
            steadySched = opt.getOptimizedSteadySched();
            initSched = opt.getOptimizedInitSched();

            if (printsched)
            {
                System.out.print("[");
            }
            totalSize = this.computeSize(initSched, true);
            if (printsched)
            {
                System.out.println("]");
                System.out.println("[");
            }
            totalSize += this.computeSize(steadySched, true);

            int mlSize = totalSize;
            int mlBuffer = totalBuffer;

            if (schedsingleapp) {
                totalSize = 0;
                totalBuffer = 0;
                selfIter = new streamit.iriter.Iterator(this);
                factory = new streamit.scheduler2.singleappearance.StreamFactory();
                selfStream =
                    factory.newFrom(selfIter, null);
                selfStream.computeSchedule();
                initSched = selfStream.getInitSchedule();
                steadySched = selfStream.getSteadySchedule();

                buffers = new ScheduleBuffers(selfIter);
                buffers.computeBuffersFor(initSched);
                buffers.computeBuffersFor(steadySched);

                setupBufferLengths(buffers);

                opt =
                    new ScheduleOptimizer(initSched, steadySched);
                steadySched = opt.getOptimizedSteadySched();
                initSched = opt.getOptimizedInitSched();

                opt = new ScheduleOptimizer(initSched, steadySched);
                steadySched = opt.getOptimizedSteadySched();
                initSched = opt.getOptimizedInitSched();

                if (printsched)
                {
                    System.out.print("[");
                }
                totalSize = this.computeSize(initSched, true);
                if (printsched)
                {
                    System.out.println("]");
                    System.out.println("[");
                }
                totalSize += this.computeSize(steadySched, true);
            }

            int sasSize = totalSize;
            int sasBuffer = totalBuffer;

            if (printsched)
            {
                System.out.println("]");
                System.out.println("!nodex = " + selfStream.getNumNodes());
                System.out.println(
                    "!node firings = " + selfStream.getNumNodeFirings());
                if (schedsingleapp) 
                {
                    System.out.println("!sas sched size = " + sasSize);
                    System.out.println("!sas buff size = " + sasBuffer);
                } else {
                    System.out.println ("!");
                    System.out.println ("!");
                }
                
                System.out.println("!ml sched size = " + mlSize);
                System.out.println("!ml buff size = " + mlBuffer);
            }
            
            // run the init schedule:
            if (nIters != 0)
                runSchedule(initSched, 1);

            //nIters = 0;

            // and run the steady schedule forever:
            while (nIters != 0)
            {
                runSchedule(steadySched, 1);
                if (printdot)
                    System.out.print("*");
                if (nIters > 0)
                    nIters--;
            }
        }
        else
        {
            while (true)
            {
                runSinks();
                drainChannels();
            }
        }
    }
}
