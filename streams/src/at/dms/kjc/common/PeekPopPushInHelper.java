package at.dms.kjc.common;

import at.dms.kjc.CType;
import at.dms.kjc.JExpression;
import at.dms.kjc.SLIREmptyVisitor;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRPeekExpression;
import at.dms.kjc.sir.SIRPopExpression;
import at.dms.kjc.sir.SIRPushExpression;

/**
 * This class will search for all communication expressions outside of the work
 * function of a filter and return true if they exist.
 *
 * @author mgordon
 */
public class PeekPopPushInHelper extends SLIREmptyVisitor {
    private static boolean found;
    
    /** returns true if we find communication statements/expressions
     * outside of the work function (i.e. in a helper function 
     * 
     * @return true if we find a peek, pop, or push in a helper function
     */
    public static boolean check(SIRFilter filter) {
	for (int i = 0; i < filter.getMethods().length; i++) {
	    if (!filter.getMethods()[i].equals(filter.getWork())) {
		found = false;
		filter.getMethods()[i]
		    .accept(new PeekPopPushInHelper());
		if (found)
		    return true;
	    }
	}
	return false;
    }

    @Override
	public void visitPeekExpression(SIRPeekExpression self, CType tapeType,
				    JExpression arg) {
	found = true;
    }

    @Override
	public void visitPopExpression(SIRPopExpression self, CType tapeType) {
	found = true;
    }

    @Override
	public void visitPushExpression(SIRPushExpression self, CType tapeType,
				    JExpression arg) {
	found = true;
	arg.accept(this);
    }
}
