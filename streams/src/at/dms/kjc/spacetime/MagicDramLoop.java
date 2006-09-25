package at.dms.kjc.spacetime;

import at.dms.util.Utils;
import at.dms.kjc.flatgraph2.*;
import java.util.LinkedList;
import java.util.Iterator;

public class MagicDramLoop extends MagicDramInstruction 
{
    private int tripCount;
    private LinkedList<MagicDramInstruction> ins;

    public MagicDramLoop()
    {
        ins = new LinkedList<MagicDramInstruction>();
        tripCount = 0;
    }
    
    public MagicDramLoop(int tc, LinkedList<MagicDramInstruction> insList) 
    {
        tripCount = tc;
        ins = insList;
    }

    public void addIns(MagicDramInstruction in) 
    {
        if (in instanceof MagicDramLoop) 
            Utils.fail("Cannot have nested loop in magic dram loop");
        ins.add(in);
    }
    
    public void setTripCount(int tc) 
    {
        tripCount = tc;
    }

    public String toC() 
    {
        StringBuffer sb = new StringBuffer();
        sb.append("for (index = 0; index < " + tripCount + "; index++) {\n");
        Iterator<MagicDramInstruction> it = ins.iterator();
        while (it.hasNext()) {
            MagicDramInstruction in = it.next();
            if (in instanceof MagicDramLoop)
                Utils.fail("Cannot have nested loop in magic dram loop");
            sb.append(in.toC());
        }
        sb.append("}\n");
        return sb.toString();
    }
}
