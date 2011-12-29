package at.dms.kjc.sir.lowering.partition;

import at.dms.kjc.sir.SIRStream;

/**
 * Identity transform on a stream graph.
 */

public final class IdentityTransform extends IdempotentTransform {

    public IdentityTransform() {
        super();
    }

    /**
     * Perform the transform on 'str' and return new stream.
     */
    public SIRStream doMyTransform(SIRStream str) {
        return str;
    }

    public String toString() {
        return "Identity Transform, #" + id;
    }
}
