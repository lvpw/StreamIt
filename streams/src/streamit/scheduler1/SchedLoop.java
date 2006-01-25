package streamit.scheduler1;

import java.math.BigInteger;
import java.io.PrintStream;
import streamit.misc.Fraction;

public class SchedLoop extends SchedStream
{
    SchedSplitType split;
    SchedJoinType join;
    SchedStream body, loop;
    int delay;

    BigInteger numSplitExecutions, numJoinExecutions;

    protected SchedLoop (Object stream, SchedJoinType join, SchedStream body, SchedSplitType split, SchedStream loop, int delay)
    {
        super (stream);
        this.join = join;
        this.body = body;
        this.split = split;
        this.loop = loop;
        this.delay = delay;
    }

    public SchedStream getLoopBody ()
    {
        return body;
    }

    public SchedStream getLoopFeedbackPath ()
    {
        return loop;
    }

    public SchedJoinType getLoopJoin ()
    {
        return join;
    }

    public SchedSplitType getLoopSplit ()
    {
        return split;
    }

    public void computeSteadySchedule ()
    {
        // first, initialize the children:
        {
            if (body != null) body.computeSteadySchedule ();
            if (loop != null) loop.computeSteadySchedule ();
            ASSERT (join != null && split != null);
        }

        // now, assuming the body executes once, compute fractions
        // of how many times everything else executes
        {
            BigInteger bodyProd, bodyCons;
            BigInteger loopProd, loopCons;

            if (body != null)
                {
                    bodyProd = BigInteger.valueOf (body.getProduction ());
                    bodyCons = BigInteger.valueOf (body.getConsumption ());
                } else {
                    bodyProd = BigInteger.ONE;
                    bodyCons = BigInteger.ONE;
                }

            if (loop != null)
                {
                    loopProd = BigInteger.valueOf (loop.getProduction ());
                    loopCons = BigInteger.valueOf (loop.getConsumption ());
                } else {
                    loopProd = BigInteger.ONE;
                    loopCons = BigInteger.ONE;
                }

            BigInteger splitProd, splitCons;
            BigInteger joinProd, joinCons;

            // get the feedback production rate and others
            splitProd = BigInteger.valueOf (split.getOutputWeight (1));
            splitCons = BigInteger.valueOf (split.getRoundConsumption ());
            joinCons = BigInteger.valueOf (join.getInputWeight (1));
            joinProd = BigInteger.valueOf (join.getRoundProduction ());

            // calculate all the fractions
            Fraction bodyFrac = new Fraction (BigInteger.ONE, BigInteger.ONE);
            Fraction splitFrac = new Fraction (bodyProd, splitCons).multiply (bodyFrac);
            Fraction loopFrac = new Fraction (splitProd, loopCons).multiply (splitFrac);
            Fraction joinFrac = new Fraction (loopProd, joinCons).multiply (loopFrac);

            // make sure that the rates are self consistant
            if (!joinFrac.multiply (joinProd).divide (bodyCons).equals (bodyFrac))
                {
                    ERROR ("Inconsistant program - cannot be scheduled without growing buffers infinitely!");
                }

            // compute a minimal multiplier for all the fractions
            // s.t. multiplying the fractions by the multiplier will yield
            // all integers
            BigInteger multiplier = bodyFrac.getDenominator ();
            multiplier = multiplier.multiply (splitFrac.getDenominator ().divide (multiplier.gcd (splitFrac.getDenominator ())));
            multiplier = multiplier.multiply (loopFrac.getDenominator ().divide (multiplier.gcd (loopFrac.getDenominator ())));
            multiplier = multiplier.multiply (joinFrac.getDenominator ().divide (multiplier.gcd (joinFrac.getDenominator ())));

            // multiply all the fractions by the multiplier
            bodyFrac = bodyFrac.multiply (multiplier);
            splitFrac = splitFrac.multiply (multiplier);
            loopFrac = loopFrac.multiply (multiplier);
            joinFrac = joinFrac.multiply (multiplier);

            // make sure that all the fractions are integers now
            ASSERT (bodyFrac.getDenominator ().equals (BigInteger.ONE));
            ASSERT (loopFrac.getDenominator ().equals (BigInteger.ONE));
            ASSERT (joinFrac.getDenominator ().equals (BigInteger.ONE));
            ASSERT (splitFrac.getDenominator ().equals (BigInteger.ONE));

            // and now actually set the appropriate multipliers on body and loop
            // and split and join:
            if (body != null) body.setNumExecutions (bodyFrac.getNumerator ());
            if (loop != null) loop.setNumExecutions (loopFrac.getNumerator ());
            numJoinExecutions = joinFrac.getNumerator ();
            numSplitExecutions = splitFrac.getNumerator ();
        }

        // setup my variables that come from SchedStream:
        {
            setNumExecutions (BigInteger.ONE);

            setConsumption (numJoinExecutions.intValue () * join.getInputWeight (0));
            setProduction (numSplitExecutions.intValue () * split.getOutputWeight (0));
        }

        // done
    }

    /**
     * Get the number of times the split is executed in a single steady state
     * execution of this feedback loop.
     */
    public BigInteger getNumSplitExecutions ()
    {
        ASSERT (numSplitExecutions);
        return numSplitExecutions;
    }

    /**
     * Get the number of times the join is executed in a single steady state
     * execution of this feedback loop.
     */
    public BigInteger getNumJoinExecutions ()
    {
        ASSERT (numJoinExecutions);
        return numJoinExecutions;
    }

    /**
     * Get the number of times the body is executed in a single steady state
     * execution of this feedback loop.
     */
    public BigInteger getNumBodyExecutions ()
    {
        ASSERT (body);

        BigInteger result = body.getNumExecutions ();
        ASSERT (result);

        return result;
    }

    /**
     * Get the number of times the loop is executed in a single steady state
     * execution of this feedback loop.
     */
    public BigInteger getNumLoopExecutions ()
    {
        ASSERT (loop);

        BigInteger result = loop.getNumExecutions ();
        ASSERT (result);

        return result;
    }

    /**
     * Get the loop delay, as specified in the program.
     */
    public int getLoopDelay ()
    {
        ASSERT (delay);
        return delay;
    }

    void printDot (PrintStream outputStream)
    {
        // Create a subgraph again...
        print("subgraph cluster_" + getUniqueStreamName () + " {\n", outputStream);
        print("label = \"" + getStreamName () + "\";\n", outputStream);

        // Visit the splitter and joiner.
        getLoopSplit ().printDot (outputStream);
        getLoopJoin ().printDot (outputStream);

        // Visit the body and the loop part.
        getLoopBody ().printDot (outputStream);
        getLoopFeedbackPath ().printDot (outputStream);

        printEdge (getLoopJoin ().getUniqueStreamName (), getLoopBody ().getFirstChild ().getUniqueStreamName (), outputStream);
        printEdge (getLoopBody ().getLastChild ().getUniqueStreamName (), getLoopSplit ().getUniqueStreamName (), outputStream);
        printEdge (getLoopSplit ().getUniqueStreamName (), getLoopFeedbackPath ().getFirstChild ().getUniqueStreamName (), outputStream);
        printEdge (getLoopFeedbackPath ().getLastChild ().getUniqueStreamName (), getLoopJoin ().getUniqueStreamName (), outputStream);

        print("}\n", outputStream);
    }

    SchedObject getFirstChild ()
    {
        return getLoopJoin ().getFirstChild ();
    }

    SchedObject getLastChild ()
    {
        return getLoopSplit ().getLastChild ();
    }
}

