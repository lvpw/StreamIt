package streamit.scheduler.singleappearance;

/* $Id: Pipeline.java,v 1.5 2002-07-16 02:18:48 karczma Exp $ */

import java.util.Map;
import java.util.HashMap;
import streamit.scheduler.iriter./*persistent.*/
PipelineIter;
import streamit.scheduler.hierarchical.StreamInterface;
import streamit.scheduler.base.StreamFactory;
import streamit.scheduler.Schedule;
import streamit.scheduler.hierarchical.PhasingSchedule;

/**
 * This class implements a single-appearance algorithm for creating
 * schedules.
 * 
 * @version 2
 * @author  Michal Karczmarek
 */

public class Pipeline extends streamit.scheduler.hierarchical.Pipeline
{
    public Pipeline(PipelineIter iterator, StreamFactory factory)
    {
        super(iterator, factory);
    }

    public void computeSchedule()
    {
        // first compute schedules for all my children
        {
            int nChild;
            for (nChild = 0; nChild < getNumChildren(); nChild++)
            {
                getChild(nChild).computeSchedule();
            }
        }

        // now go through all the children again and figure out how many
        // data each child needs to be executed to fill up the buffers
        // enough to initialize the entire pipeline without producing
        // any data - this means that the last child doesn't get executed
        // even once
        // I do this by iterating from end to beggining and computing the
        // number of elements that each child needs to produce
        int numExecutionsForInit[] = new int[getNumChildren()];
        {
            int neededByNext = 0;

            // go through the children from last to first
            int nChild = getNumChildren() - 1;
            for (; nChild >= 0; nChild--)
            {
                StreamInterface child = getHierarchicalChild(nChild);

                int producesForInit = child.getInitPush();
                int producesPerIter = child.getSteadyPush();
                int numItersInit;
                if (producesPerIter != 0)
                {
                    // this child actually produces some data - this is
                    // the common case
                    numItersInit =
                        ((neededByNext - producesForInit)
                            + producesPerIter
                            - 1)
                            / producesPerIter;
                    if (numItersInit < 0)
                        numItersInit = 0;
                }
                else
                {
                    // this child does not produce any data
                    // make sure that consumedByPrev is 0 (otherwise
                    // I cannot execute the next child, 'cause it will
                    // never get any input)
                    ASSERT(neededByNext == 0);

                    // there will be no cycles executed for initialization
                    // of children downstream
                    numItersInit = 0;
                }

                // associate the child with the number of executions required
                // by the child to fill up the pipeline
                numExecutionsForInit[nChild] = numItersInit;

                // and figure out how many data this particular child
                // needs to initialize the pipeline.  this is complicated
                // by the fact that there are essentially two separate
                // init stages, and because the extra peek on one of these 
                // might be different from the steady-state peek
                // explanation in notebook, 02/07/02
                {
                    // compute the two-stage-init peek and pop values
                    int pop_i =
                        child.getInitPop()
                            + numItersInit * child.getSteadyPop();
                    int peek_i =
                        MAX(
                            child.getInitPeek(),
                            child.getInitPop()
                                + numItersInit * child.getSteadyPop()
                                + (child.getSteadyPeek()
                                    - child.getSteadyPop()));

                    // now save how much data is needed before peek adjust:
                    neededByNext = peek_i;

                    // and perform the peek adjust
                    neededByNext
                        += MAX(
                            (child.getSteadyPeek() - child.getSteadyPop())
                                - (peek_i - pop_i),
                            0);

                }
            }
        }

        // compute an initialization schedule
        // now that I know how many times each child needs to be executed,
        // it should be easy to compute this - just execute each stream
        // an appropriate number of times.
        // In this approach, I do not care about the size of the buffers, and
        // may in fact be enlarging them much more than necessary
        {
            // the entire schedule will be just a single phase, so I need
            // to wrap it in a unique PhasingSchedule myself
            PhasingSchedule initStage;
            initStage = new PhasingSchedule(this);

            int nChild;
            for (nChild = 0; nChild < getNumChildren(); nChild++)
            {
                StreamInterface child = getHierarchicalChild(nChild);
                // add the initialization schedule:
                initStage.appendPhase(child.getPhasingInitSchedule());

                // add the steady schedule an appropriate number of times:
                {
                    int n;
                    for (n = 0; n < numExecutionsForInit[nChild]; n++)
                    {
                        initStage.appendPhase(
                            child.getPhasingSteadySchedule());
                    }
                }
            }

            // now append the one init phase to my initialization phase
            if (initStage.getNumPhases() != 0)
                addInitScheduleStage(initStage);
        }

        // compute the steady state schedule
        // this one is quite easy - I go through the children and execute them
        // an appropriate number of times
        {
            PhasingSchedule steadyPhase;
            steadyPhase = new PhasingSchedule(this);

            int nChild;
            for (nChild = 0; nChild < getNumChildren(); nChild++)
            {
                StreamInterface child = getHierarchicalChild(nChild);

                int n;
                for (n = 0; n < getChildNumExecs(nChild); n++)
                {
                    steadyPhase.appendPhase(child.getPhasingSteadySchedule());
                }
            }

            addSteadySchedulePhase(steadyPhase);
        }
    }
}
