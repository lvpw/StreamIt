package at.dms.kjc.spacetime;

import at.dms.kjc.sir.*;
import at.dms.kjc.flatgraph2.*;
/** 
 *
 **/
public class FilterTraceNode extends TraceNode
{
    private FilterContent filter;
    //private int initMult;
    //private int steadyMult;
    private int x, y;
    private boolean predefined;
    private boolean laidout;

    public FilterTraceNode(FilterContent filter,
                           int x, int y) {
        predefined = (filter instanceof PredefinedContent);
        this.filter = filter;
        this.x = x;
        this.y = y;
        laidout = false;
    }

    public FilterTraceNode(FilterContent filter) {
        predefined = (filter instanceof PredefinedContent);
        this.filter=filter;
        laidout = false;
    }
    
    public boolean isPredefined() 
    {
        return predefined;
    }

    public boolean isAssignedTile() 
    {
        return laidout;
    }

    public FilterContent getFilter() {
        return filter;
    }

    public String toString() {
        return filter.toString() + " [" + x + ", " + y + "]";   
    }
    
    public String toString(RawChip chip) 
    {
        return filter.toString() + " [" + x + ", " + y + "]" + 
            "(" + chip.getTile(x, y).getTileNumber() + ")";   
    }
    
    
    public boolean isFileInput()
    {
        return (filter instanceof FileInputContent);
    }
    
    public boolean isFileOutput() 
    {
        return (filter instanceof FileOutputContent);
    }
}



