package streamit.iriter;

import streamit.Filter;

public class FilterIter
    extends streamit.misc.DestroyedClass
    implements streamit.scheduler2.iriter.FilterIter
{
    FilterIter(Filter _filter)
    {
        filter = _filter;
    }

    Filter filter;
    final String workName = "work";
    
    public Object getObject ()
    {
        return filter;
    }

    public streamit.scheduler2.iriter.Iterator getUnspecializedIter()
    {
        return new Iterator(filter);
    }
    
    public int getNumInitStages ()
    {
        if (filter.isMultiPhaseStyle())
        {
            return filter.getNumInitPhases();
        } else {
            // if not using the multi-phase style, cannot have any init phases!
            return 0;
        }
    }
    
    public int getInitPeekStage (int stage)
    {
        ASSERT (filter.isMultiPhaseStyle());
        return filter.getInitPeekStage (stage);
    }

    public int getInitPopStage (int stage)
    {
        ASSERT (filter.isMultiPhaseStyle());
        return filter.getInitPopStage (stage);
    }
    
    public int getInitPushStage (int stage)
    {
        ASSERT (filter.isMultiPhaseStyle());
        return filter.getInitPushStage (stage);
    }
    
    public Object getInitFunctionStage (int stage)
    {
        ASSERT (filter.isMultiPhaseStyle());
        return filter.getInitFunctionStageName (stage);
    }
    
    public int getNumWorkPhases ()
    {
        if (filter.isMultiPhaseStyle())
        {
            return filter.getNumSteadyPhases();
        } else {
            // if not using the multi-phase style, must have exactly one phase!
            return 1;
        }
    }
    
    public int getPeekPhase (int phase)
    {
        if (filter.isMultiPhaseStyle())
        {
            return filter.getSteadyPeekPhase(phase);
        } else {
            // if not using the multi-phase style, must have exactly one phase!
            ASSERT (phase == 0);
            return filter.peekCount;
        }
    }
    
    public int getPopPhase (int phase)
    {
        if (filter.isMultiPhaseStyle())
        {
            return filter.getSteadyPopPhase(phase);
        } else {
            // if not using the multi-phase style, must have exactly one phase!
            ASSERT (phase == 0);
            return filter.popCount;
        }
    }
    
    public int getPushPhase (int phase)
    {
        if (filter.isMultiPhaseStyle())
        {
            return filter.getSteadyPushPhase(phase);
        } else {
            // if not using the multi-phase style, must have exactly one phase!
            ASSERT (phase == 0);
            return filter.pushCount;
        }
    }

    public Object getWorkFunctionPhase (int phase)
    {
        if (filter.isMultiPhaseStyle())
        {
            return filter.getSteadyFunctionPhaseName(phase);
        } else {
            // if not using the multi-phase style, must have exactly one phase!
            ASSERT (phase == 0);
            return workName;
        }
    }
    
    public boolean equals(Object other)
    {
        if (!(other instanceof FilterIter)) return false;
        FilterIter otherFilter = (FilterIter) other;
        return otherFilter.getObject() == this.getObject();
    }
    
    public int hashCode()
    {
        return filter.hashCode();
    }
}