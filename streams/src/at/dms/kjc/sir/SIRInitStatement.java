package at.dms.kjc.sir;

import java.util.*;
import at.dms.kjc.*;
import at.dms.compiler.*;

/**
 * Init Statement.
 *
 * This statement represents a call to an init function of a
 * sub-stream.  It should take the place of any add(...) statement in
 * StreaMIT syntax.  The arguments to the constructor of the
 * sub-stream should be the <args> in here.
 */
public class SIRInitStatement extends JStatement {

    /**
     * The arguments to the init function. (all are JExpressions)
     */
    protected List args;
    /**
     * The stream structure to initialize.
     */
    private SIRStream target;

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Construct a node in the parsing tree
     */
    public SIRInitStatement(List args, 
			    SIRStream str) {
	super(null, null);
	this.args = new Vector(args);
	this.target = str;
    }
    
    public SIRInitStatement(SIRStream str) {
	this(null, str);
    }
    
    /**
     * Construct a node in the parsing tree
     */
    public SIRInitStatement() {
	super(null, null);

	this.args = null;
	this.target = null;
    }
    
    public void setArgs(LinkedList args) {
	this.args = args;
    }

    public List getArgs() {
	return this.args;
    }

    public void setTarget(SIRStream s) {
	this.target = s;
    }

    public SIRStream getTarget() {
	return this.target;
    }

    // ----------------------------------------------------------------------
    // SEMANTIC ANALYSIS
    // ----------------------------------------------------------------------

    /**
     * Analyses the statement (semantically) - NOT SUPPORTED YET.
     */
    public void analyse(CBodyContext context) throws PositionedError {
	at.dms.util.Utils.fail("Analysis of SIR nodes not supported yet.");
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Accepts the specified visitor.
     */
    public void accept(KjcVisitor p) {
	if (p instanceof SLIRVisitor) {
	    ((SLIRVisitor)p).visitInitStatement(this, target);
	} else {
	    // otherwise, visit children
	    for (int i=0; i<args.size(); i++) {
		((JExpression)args.get(i)).accept(p);
	    }
	}
    }


    /**
     * Accepts the specified attribute visitor.
     * @param   p               the visitor
     */
    public Object accept(AttributeVisitor p) {
	if (p instanceof SLIRAttributeVisitor) {
	    return ((SLIRAttributeVisitor)p).visitInitStatement(this, 
								target);
	} else {
	    return this;
	}
    }

    /**
     * Generates a sequence of bytescodes - NOT SUPPORTED YET.
     */
    public void genCode(CodeSequence code) {
	at.dms.util.Utils.fail("Codegen of SIR nodes not supported yet.");
    }
}





