package streamit.scheduler2.base;

/* $Id: StreamInterfaceWithSnJ.java,v 1.2 2002-12-02 23:54:07 karczma Exp $ */

/**
 * This interface provides the required functional interface for
 * all scheduling objects with splits and joins.
 * This is an extension of just regular scheduling
 * (as you can see from the extends statement :)
 * Basically, this takes care of getting information about
 * the split and the join in the stream
 * 
 * I have to make this an interface instead of a class because
 * Java doesn't have multiple inheritance.  Argh!
 * 
 * @version 2
 * @author  Michal Karczmarek
 */

public interface StreamInterfaceWithSnJ extends StreamInterface
{
    /**
     * store the amount of data distributed to and 
     * collected by the splitter
     */
    public class SplitFlow extends streamit.misc.DestroyedClass
    {
        SplitFlow(int nChildren)
        {
            pushWeights = new int[nChildren];
        }

        private int pushWeights[];
        private int popWeight = 0;

        void setPopWeight(int weight)
        {
            popWeight = weight;
        }

        void setPushWeight(int nChild, int weight)
        {
            pushWeights[nChild] = weight;
        }

        public int getPopWeight()
        {
            return popWeight;
        }

        public int getPushWeight(int nChild)
        {
            // cannot easily check for being out of bounds
            // if this function throws an out-of-bounds exception, the
            // problem is with the nChild being too large (already 
            // checking for negative values below)
            ASSERT(nChild >= 0);

            return pushWeights[nChild];
        }
    }

    /**
     * store the amount of data distributed to and 
     * collected by the joiner
     */
    public class JoinFlow extends streamit.misc.DestroyedClass
    {
        JoinFlow(int nChildren)
        {
            popWeights = new int[nChildren];
        }

        private int popWeights[];
        private int pushWeight = 0;

        void setPushWeight(int weight)
        {
            pushWeight = weight;
        }

        void setPopWeight(int nChild, int weight)
        {
            popWeights[nChild] = weight;
        }

        public int getPushWeight()
        {
            return pushWeight;
        }

        public int getPopWeight(int nChild)
        {
            // cannot easily check for being out of bounds
            // if this function throws an out-of-bounds exception, the
            // problem is with the nChild being too large (already 
            // checking for negative values below)
            ASSERT(nChild >= 0);

            return popWeights[nChild];
        }
    }

    /**
     * get the flow of the splitter in steady state (all splitter's 
     * phases executed once)
     * @return splitter's steady flow
     */
    public SplitFlow getSteadySplitFlow();

    /**
     * get the flow of the joiner in steady state (all joiner's 
     * phases executed once)
     * @return joiner's steady flow
     */
    public JoinFlow getSteadyJoinFlow();

    /**
     * get the flow of the splitter for a particular phase
     * @return splitter's phase flow
     */
    public SplitFlow getSplitFlow(int nPhase);

    /**
     * get the flow of the joiner for a particular phase
     * @return joiner's phase flow
     */
    public JoinFlow getJoinFlow(int nPhase);
}
