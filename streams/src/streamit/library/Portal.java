/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.library;

import streamit.scheduler2.SDEPData;
import streamit.scheduler2.constrained.NoPathException;
import streamit.library.iriter.Iterator;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A portal that messages can be sent to.  For the StreamIt compiler,
 * the set of messages that can be sent should be defined in an
 * interface.  A class derived from <code>Portal</code> and
 * implementing the interface whose name ends in "Portal" should be
 * defined; that class is the portal object.  Receiver objects should
 * also implement the interface.
 *
 * @version $Id: Portal.java,v 1.10 2006-01-09 23:15:26 madrake Exp $
 */
public abstract class Portal
{
    protected ArrayList receivers; //List of Filters
    private int minLat,maxLat;
    /**
     * Stream -> SDEPInfo.  Maps a given sender to the SDEP data for
     * each receiver in <receivers>.
     */
    private HashMap SDEPCache;
    /**
     * Constrained scheduler, run on toplevel stream.
     */
    private streamit.scheduler2.constrained.Scheduler scheduler;
    
    public Portal() {
	this.receivers = new ArrayList();
	this.SDEPCache = new HashMap();
    }

    public void setAnyLatency() // default to zero latency
    {
	setLatency(0,0);
	/*
        throw new UnsupportedOperationException
            ("StreamIt Java library does not support setting AnyLatency yet");
	*/
    }
    
    public void setMaxLatency(int b) // default to zero minimum latency
    {
	setLatency(0,b);
	/*
        throw new UnsupportedOperationException
            ("StreamIt Java library does not support setting MaxLatency yet");
	*/
    }

    public void setLatency(int a, int b)
    {
	//System.out.println("Setting Latency: "+a+", "+b);
	minLat=a;
	maxLat=b;
    }

    public void regReceiver(Object o)
    {
	//System.out.println("Registering Receiver: "+o);
	receivers.add(o);
    }

    /**
     * Enqueues a message to <handlerName> method in all receivers of
     * this, from <sender>, and with arguemnts <args>.
     */
    public void enqueueMessage(Stream sender, String handlerName, Object[] args) {
	// setup scheduler if it's not ready yet (have to do it here
	// instead of constructor because scheduler depends on
	// structure of stream graph, which is not ready at the time
	// this is constructed.)
	if (this.scheduler==null) {
	    this.scheduler = streamit.scheduler2.constrained.Scheduler.createForSDEP(new Iterator(Stream.toplevel));
	}
	// get SDEP to receivers
	SDEPInfo sdep = getSDEP(sender);
	// for each receiver...
	for (int i=0; i<receivers.size(); i++) {
	    Stream receiver = (Stream)receivers.get(i);
	    // make message -- for now just send with maximum latency
	    // to make it easiest to schedule
	    Message m;
	    if (sdep.isDownstream[i]) {
		// schedule downstream messages using DstPhase4Src
		m = new Message(sdep.data[i].getDstPhase4SrcPhase(sender.getNumExecutions()+maxLat+1),
				handlerName, args);
	    } else {
		// schedule upstream messages messages using SrcPhase4Dst
		m = new Message(sdep.data[i].getSrcPhase4DstPhase(sender.getNumExecutions()+maxLat+1),
				handlerName, args);
	    }
	    //System.err.println("Enqueuing message <" + handlerName + "> for deliver at time " + m.getDeliveryTime() + " in " + receiver);
	    // enqueue message
	    receiver.enqueueMessage(m);
	}
    }

    /**
     * Returns SDEP data from sender to each receiver.
     */
    private SDEPInfo getSDEP(Stream sender) {
	if (SDEPCache.containsKey(sender)) {
	    return (SDEPInfo)SDEPCache.get(sender);
	} else {
	    // make placeholders for data and direction info
	    SDEPData[] data = new SDEPData[receivers.size()];
	    boolean[] isDownstream = new boolean[receivers.size()];
	    // for each receiver...
	    for (int i=0; i<receivers.size(); i++) {
		Stream receiver = (Stream)receivers.get(i);
		if (scheduler.isDownstreamPath(new Iterator(sender), new Iterator(receiver))) {
		    // if downstream path, compute dependences downstream
		    try {
			data[i] = scheduler.computeSDEP(new Iterator(sender),
							new Iterator(receiver));
		    } catch (NoPathException e) {
			// should not happen because we checked that there is downstream path
			e.printStackTrace();
		    }
		    isDownstream[i] = true;
		} else if (scheduler.isUpstreamPath(new Iterator(sender), new Iterator(receiver))) {
		    // if upstream path, compute dependences upstream
		    try {
			data[i] = scheduler.computeSDEP(new Iterator(receiver),
							new Iterator(sender));
		    } catch (NoPathException e) {
			// should not happen because we checked that there is downstream path
			e.printStackTrace();
		    }
		    isDownstream[i] = false;
		} else {
		    // otherwise, fail -- no path between sender and receiver
		    new RuntimeException("No path between message sender and receiver in stream graph.")
			.printStackTrace();
		}
	    }
	    SDEPInfo result = new SDEPInfo(data, isDownstream);
	    SDEPCache.put(sender, result);
	    return result;
	}
    }
    
    /**
     * For wrapping primitives into objects, so that message senders
     * can have uniform interface for queueing up messaging arguments.
     */
    public Byte wrapInObject(byte b) { 
	return new Byte(b);
    }
    public Boolean wrapInObject(boolean b) { 
	return new Boolean(b); 
    }
    public Character wrapInObject(char c) { 
	return new Character(c); 
    }
    public Float wrapInObject(float f) { 
	return new Float(f); 
    }
    public Double wrapInObject(double d) { 
	return new Double(d);
    }
    public Integer wrapInObject(int i) { 
	return new Integer(i); 
    }
    public Long wrapInObject(long l) {
	return new Long(l);
    }
    public Object wrapInObject(Object o) {
	// make copy of arrays so that it mimicks pass-by-value.  We
	// should support more types (this is all that is needed so
	// far.)  We could also use serialization and unserialization
	// to be more general (e.g., for arrays of user-defined struct
	// types).
	if (o instanceof int[]) {
	    return Operator.copyIntArray1D((int[])o);
	} else if (o instanceof float[]) {
	    return Operator.copyFloatArray1D((float[])o);
	} else if (o instanceof int[][]) {
	    return Operator.copyIntArray2D((int[][])o);
	} else if (o instanceof float[][]) {
	    return Operator.copyFloatArray2D((float[][])o);
  } else if (o instanceof int[][][]) {
	    return Operator.copyIntArray3D((int[][][])o);
	} else {
	    return o;
	}
    }
    public Short wrapInObject(short s) {
	return new Short(s);
    }

    /**
     * Just a bundle of the SDEP data and whether or not a given
     * message travels upstream or downstream.
     */
    class SDEPInfo {
	// table of dependence info
	public SDEPData[] data;
	// true iff the receiver is downstream of the sender
	public boolean[] isDownstream;
	
	public SDEPInfo(SDEPData[] data, boolean[] isDownstream) {
	    this.data = data;
	    this.isDownstream = isDownstream;
	}
    }
    
}

