package at.dms.kjc.sir;

import at.dms.kjc.*;
import at.dms.compiler.*;

/**
 * Create Portal Expression.
 *
 * This expression is a place holder for a particular portal
 */
public class SIRCreatePortal extends JExpression {
    public SIRCreatePortal() {
	super(null); 
    }


    
    /**
     * @return the type of this expression
     */
    public CType getType() {
	return CStdType.Void;
    }

    
    public void genCode(CodeSequence code, boolean discardValue) {
	at.dms.util.Utils.fail("Codegen of SIR nodes not supported yet.");
    }

    /**
     * Accepts the specified attribute visitor.
     * @param   p               the visitor
     */
    public Object accept(AttributeVisitor p) {
	if (p instanceof SLIRAttributeVisitor) {
	    return ((SLIRAttributeVisitor)p).visitCreatePortalExpression(this);
	} else {
	    return this;
	}
    }

    public void accept(KjcVisitor p) {
	if (p instanceof SLIRVisitor) {
	    ((SLIRVisitor)p).visitCreatePortalExpression(this);
	} else {
	    // otherwise, do nothing... this node appears in the body of
	    // work functions, so a KjcVisitor might find it, but doesn't
	    // have anything to do to it.
	}
    }

    public JExpression analyse(CExpressionContext context) throws PositionedError {
	at.dms.util.Utils.fail("Analysis of SIR nodes not supported yet.");
	return this;
    }

}

