package at.dms.kjc.sir;

import at.dms.kjc.lir.LIRStreamType;
import at.dms.kjc.sir.lowering.LoweringConstants;
import at.dms.kjc.*;
import at.dms.util.*;

/**
 * This represents a StreaMIT filter that has some compiler-defined
 * functionality.  The init and work functions are not implemented by
 * the user.
 */
public class SIRPredefinedFilter extends SIRFilter implements Cloneable {

    public SIRPredefinedFilter() {
	super();
    }

    public SIRPredefinedFilter(SIRContainer parent,
			       String ident,
			       JFieldDeclaration[] fields, 
			       JMethodDeclaration[] methods, 
			       JExpression peek, JExpression pop, JExpression push, 
			       CType inputType, CType outputType) {
	super(parent,
	      ident,
	      fields,
	      methods,
	      peek, pop, push,
	      /* work */ null,
	      /* input type */ inputType,
	      /* output type */ outputType);
    }

    public boolean needsInit() {
	return false;
    }

    public boolean needsWork() {
	return false;
    }

    public String getTypeNameInC() {
        return "ContextContainer";
    }
}


