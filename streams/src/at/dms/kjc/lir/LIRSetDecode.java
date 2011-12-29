package at.dms.kjc.lir;

import at.dms.kjc.JExpression;
import at.dms.kjc.SLIRVisitor;

/**
 * This registers the function in the stream that takes the internal
 * representation of a black box and decodes it into a form that the
 * runtime system can use.
 */
public class LIRSetDecode extends LIRNode {

    /**
     * The encode function.
     */
    private LIRFunctionPointer decode;

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Construct a node.
     */
    public LIRSetDecode(JExpression streamContext,
                        LIRFunctionPointer decode) {
        super(streamContext);
        this.decode = decode;
    }

    @Override
	public void accept(SLIRVisitor v) {
        v.visitSetDecode(this, this.getStreamContext(), this.decode);
    }
}
