package streamit.scheduler2.hierarchical;

/* $Id: StreamInterface.java,v 1.6 2002-12-02 23:54:09 karczma Exp $ */

import streamit.scheduler2.Schedule;

/**
 * This interface provides the required functional interface for
 * all hierarchical scheduling objects.  Hierarchical scheduling
 * objects have the property that the schedule they produce is 
 * entirely contained by the object.  A hierarchical scheduling
 * object provides information about the particular phasing
 * schedule, such as number of data input/output by each phase, etc.
 * 
 * I have to make this an interface instead of a class because
 * Java doesn't have multiple inheritance.  Argh!
 * 
 * @version 2
 * @author  Michal Karczmarek
 */

public interface StreamInterface
    extends streamit.scheduler2.base.StreamInterface
{
    /**
     * Returns the object that has ability to consume/peek data
     * from the top of the stream.  For Pipeline this would be
     * the top stream in the pipeline.  For all other objects, this
     * would be self (splits and joins are considered parts of the
     * stream objects).
     */
    public streamit.scheduler2.base.StreamInterface getTop();

    /**
     * Returns the object that has ability to consume/peek data
     * from the top of the stream.  For Pipeline this would be
     * the bottom stream in the pipeline.  For all other objects, this
     * would be self (splits and joins are considered parts of the
     * stream objects).
     */
    public streamit.scheduler2.base.StreamInterface getBottom();

    /**
     * Return the number of phases that this StreamInterface's steady 
     * schedule has.
     * @return number of phases in this StreamInterface's schedule
     */
    public int getNumSteadyPhases();

    /**
     * Return the number of data that a particular phase of this
     * StreamInterface's steady schedule peeks.
     * @return amount by which a phase of this StreamInterface peeks
     */
    public int getSteadyPhaseNumPeek(int phase);

    /**
     * Return the number of data that a particular phase of this
     * StreamInterface's steady schedule pops.
     * @return amount by which a phase of this StreamInterface pops
     */
    public int getSteadyPhaseNumPop(int phase);

    /**
     * Return the number of data that a particular phase of this
     * StreamInterface's steady schedule pushes.
     * @return amount by which a phase of this StreamInterface push
     */
    public int getSteadyPhaseNumPush(int phase);

    /**
     * Return the phasing steady state schedule associated with this object.
     * @return phasing stready state schedule
     */
    public PhasingSchedule getPhasingSteadySchedule();

    /**
     * Return a phase of the steady state schedule.
     * @return a phase of the steady state schedule
     */
    public PhasingSchedule getSteadySchedulePhase(int phase);

    /**
     * Add a phase to the steady schedule
     */
    public void addSteadySchedulePhase(PhasingSchedule newPhase);

    /**
     * Return the number of stages that this StreamInterface's 
     * initialization schedule has.
     * @return number of stages in this StreamInterface's 
     * initialization schedule
     */
    public int getNumInitStages();

    /**
     * Return the number of data that a particular stage of this
     * StreamInterface's initialization schedule peeks.
     * @return amount by which a stage of this StreamInterface'
     * initialization schedule peeks
     */
    public int getInitStageNumPeek(int stage);

    /**
     * Return the number of data that a particular stage of this
     * StreamInterface's initialization schedule pops.
     * @return amount by which a stage of this StreamInterface's
     * initialization schedule pops
     */
    public int getInitStageNumPop(int stage);

    /**
     * Return the number of data that a particular stage of this
     * StreamInterface's initialization schedule pushes.
     * @return amount of data which a stage of this StreamInterface's
     * initialization schedule pushes
     */
    public int getInitStageNumPush(int stage);

    /**
     * Return the phasing initialization schedule associated with this 
     * object.
     * @return phasing intitialization schedule
     */
    public PhasingSchedule getPhasingInitSchedule();

    /**
     * Return a stage of the initialization schedule.
     * @return a stage of the initialization schedule
     */
    public PhasingSchedule getInitScheduleStage(int stage);
    
    /**
     * Thesis hack
     */
    //int getScheduleSize (PhasingSchedule sched);
}