package streamit.scheduler1;

import java.math.BigInteger;

public abstract class SchedStream extends SchedObject
{
    SchedStream (Object stream)
    {
        super (stream);
    }

    private int consumes = -1, produces = -1, peeks = -1;

    public void setProduction (int p)
    {
        ASSERT (p >= 0);
        produces = p;
    }

    public int getProduction ()
    {
        ASSERT (produces >= 0);
        return produces;
    }

    public void setConsumption (int c)
    {
        ASSERT (c >= 0);
        ASSERT (peeks == -1 ^ c <= peeks);
        consumes = c;
        if (peeks < 0) peeks = c;
    }

    public int getConsumption ()
    {
        ASSERT (consumes >= 0);
        return consumes;
    }

    public void setPeekConsumption (int p)
    {
        ASSERT (p >= 0);
        ASSERT (consumes <= p);
        peeks = p;
    }

    public int getPeekConsumption ()
    {
        ASSERT (peeks >= 0);
        return peeks;
    }

    /**
     * This field holds the previous stream - be it a filter, pipeline,
     * splitjoin or loop
     */
    private SchedStream prevStream = null;

    /**
     * Gets the previous stream
     */
    public SchedStream getPrevStream ()
    {
        return prevStream;
    }

    /**
     * Sets the previous stream
     */
    public void setPrevStream (SchedStream stream)
    {
        ASSERT (stream != null && prevStream == null);

        prevStream = stream;
    }

    // This section computes a steady-state schedule for children of the stream
    private BigInteger numExecutions;

    public BigInteger getNumExecutions ()
    {
        return numExecutions;
    }

    public void setNumExecutions (BigInteger n)
    {
        numExecutions = n;
    }

    public void multNumExecutions (BigInteger mult)
    {
        // make sure that mutliplying by something > 0
        ASSERT (mult.compareTo (BigInteger.ZERO) == 1);

        numExecutions = numExecutions.multiply (mult);
    }

    public void divNumExecutions (BigInteger div)
    {
        // make sure that dividing by something > 0
        ASSERT (div.compareTo (BigInteger.ZERO) == 1);
        ASSERT (numExecutions.mod (div).equals (BigInteger.ZERO));

        numExecutions = numExecutions.divide (div);
    }

    /**
     * Compute a steady schedule.
     * A steady schedule is defined as a schedule that does not change
     * the amount of data buffered between filters when executed.
     * It can change where the (active) data is in the buffer.
     */
    abstract public void computeSteadySchedule ();

    private String streamName = null;

    public String getStreamName ()
    {
        if (streamName == null)
            {
                streamName =  "(" + getConsumption () + ", " + getPeekConsumption () + ") " +
                    super.getStreamName () + "*" + getNumExecutions ().toString () +
                    " (" + getProduction () + ")";
            }

        return streamName;
    }
}
