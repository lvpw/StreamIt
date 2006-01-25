package at.dms.kjc.sir.lowering.partition.linear;

import java.util.*;
import java.io.*;

import at.dms.kjc.*;
import at.dms.util.*;
import at.dms.kjc.iterator.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.kjc.sir.lowering.fusion.*;
import at.dms.kjc.sir.lowering.fission.*;
import at.dms.kjc.sir.lowering.partition.*;

class LDPConfigSplitJoin extends LDPConfigContainer {

    public LDPConfigSplitJoin(SIRSplitJoin sj, LinearPartitioner partitioner) {
        super(sj, partitioner, wrapInArray(sj.size()), 1);
        assert sj.getRectangularHeight()==1:
            "Require sj's with height of 1 now.";
    }
    
    /**
     * Wraps <i> in a 1-element array
     */
    private static int[] wrapInArray(int i) {
        int[] result = { i };
        return result;
    }

    protected LDPConfig childConfig(int x, int y) {
        assert y==0: "Looking for y=" + y + " in LDPConfigSplitJoin.get";
        return partitioner.getConfig(cont.get(x));
    }
}
