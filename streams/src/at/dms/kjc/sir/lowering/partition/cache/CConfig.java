package at.dms.kjc.sir.lowering.partition.cache;

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

abstract class CConfig implements Cloneable {
    /**
     * The partitioner this is part of.
     */
    protected CachePartitioner partitioner;

    protected CConfig(CachePartitioner partitioner) {
        this.partitioner = partitioner;
    }

    abstract public boolean getPeek();

    /**
     * Returns information about the config if everything is fused.
     */

    abstract protected FusionInfo getFusionInfo();

    // number of tiles needed for the Config to satisfy cache constraints

    abstract protected int numberOfTiles();

    /**
     * Return the bottleneck work if this config is fit on <pre>tileLimit</pre>
     * tiles.  <pre>nextToJoiner</pre> is 1 iff this is next to a downstream
     * joiner under the current arrangement.
     */
    abstract protected CCost get(int tileLimit);

    /**
     * Traceback through a pre-computed optimal solution, keeping
     * track of new partitions in <pre>partitions</pre> and adding to current
     * partition <pre>curPartition</pre>, and returning new stream.
     */
    abstract public SIRStream traceback(LinkedList<PartitionRecord> partitions, PartitionRecord curPartition, int tileLimit, SIRStream str);

    /**
     * For debugging.
     */
    abstract public void printArray(int numTiles);

}

