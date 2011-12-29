package at.dms.kjc.sir.lowering.partition;

import at.dms.kjc.sir.SIRPipeline;
import at.dms.kjc.sir.SIRStream;

/**
 * Removes *matching* synchronization in this pipeline.
 */
public final class RemoveSyncTransform extends IdempotentTransform {
    public RemoveSyncTransform() {
        super();
    }

    /**
     * Perform the transform on <str> and return new stream.
     */
    public SIRStream doMyTransform(SIRStream str) {
        assert str instanceof SIRPipeline;
        boolean ok = RefactorSplitJoin.removeMatchingSyncPoints((SIRPipeline)str);
        assert ok: "Remove matching sync failed.";
        return str;
    }

    public String toString() {
        return "Remove matching sync transform, #" + id;
    }
}
