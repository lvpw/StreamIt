package at.dms.kjc.sir.linear;

import java.util.*;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
//import at.dms.kjc.sir.linear.*;
//import at.dms.kjc.sir.linear.transform.*;
//import at.dms.kjc.iterator.*;


/**
 * The LinearRedundancyAnalyzer tries to determine redundant computations
 * across the firings of filters. Possibly (in the future) this information can
 * be used to optimize performance even more.
 **/

public class LinearRedundancyAnalyzer {
    // the information that we are going to keep is a mapping from filter
    // to redundancy information.
    HashMap filtersToRedundancy;

    /**
     * Main entry point for redundancy analysis. Gets passed a
     * LinearAnalyzer and creates a new LinearRedundancyAnalyzer
     * based on that.
     **/
    public LinearRedundancyAnalyzer(LinearAnalyzer la) {
	this.filtersToRedundancy = new HashMap();
	Iterator filterIterator = la.getFilterIterator();
	while(filterIterator.hasNext()) {
	    SIRStream filter = (SIRStream)filterIterator.next();
	    LinearFilterRepresentation filterRep = la.getLinearRepresentation(filter);
	    // make a new linear redundancy for this filter
	    LinearRedundancy filterRedundancy = new LinearRedundancy(filterRep);
	    // add the redundancy to our data structure
	    this.filtersToRedundancy.put(filter, filterRedundancy);
	}
    }



    /**
     * Prints out the internal state of this analyzer for debugging purposes.
     **/
    public String toString() {
	String returnString = "";
	returnString += "Linear Redundancy Analysis:\n";
	Iterator keyIter = this.filtersToRedundancy.keySet().iterator();
	while(keyIter.hasNext()) {
	    Object key = keyIter.next();
	    Object val = this.filtersToRedundancy.get(key);
	    returnString += "key: " + key + "\n";
	    returnString += "val: " + val + "\n";
	}
	returnString += "end.";
	return returnString;
    }
    


    
	    
	

}


