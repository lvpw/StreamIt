package at.dms.kjc.sir;

import java.util.LinkedList;
import java.util.List;

import at.dms.compiler.PositionedError;
import at.dms.kjc.AttributeVisitor;
import at.dms.kjc.CBodyContext;
import at.dms.kjc.CodeSequence;
import at.dms.kjc.JExpression;
import at.dms.kjc.JStatement;
import at.dms.kjc.KjcVisitor;
import at.dms.kjc.SLIRAttributeVisitor;
import at.dms.kjc.SLIRVisitor;

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
	 * 
	 */
	private static final long serialVersionUID = -4752243843546923319L;
	/**
     * The arguments to the init function. (all are JExpressions)
     */
    protected List<JExpression> args;
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
    public SIRInitStatement(List<JExpression> args, 
                            SIRStream str) {
        super(null, null);
        if (args != null)
            this.args = new LinkedList<JExpression>(args);
        else
            this.args = new LinkedList<JExpression>();
        assert str != null: "SIRInitStatement created with null target";
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
    
    public void setArgs(List<JExpression> args) {
        this.args = args;
    }

    public List<JExpression> getArgs() {
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
    @Override
	public void analyse(CBodyContext context) throws PositionedError {
        at.dms.util.Utils.fail("Analysis of SIR nodes not supported yet.");
    }

    // ----------------------------------------------------------------------
    // CODE GENERATION
    // ----------------------------------------------------------------------

    /**
     * Accepts the specified visitor.
     */
    @Override
	public void accept(KjcVisitor p) {
        if (p instanceof SLIRVisitor) {
            ((SLIRVisitor)p).visitInitStatement(this, target);
        } else {
            // otherwise, visit children
            for (int i=0; i<args.size(); i++) {
                args.get(i).accept(p);
            }
        }
    }


    /**
     * Accepts the specified attribute visitor.
     * @param   p               the visitor
     */
    @Override
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
    @Override
	public void genCode(CodeSequence code) {
        at.dms.util.Utils.fail("Codegen of SIR nodes not supported yet.");
    }

    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.sir.SIRInitStatement other = new at.dms.kjc.sir.SIRInitStatement();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.sir.SIRInitStatement other) {
        super.deepCloneInto(other);
        other.args = (java.util.List)at.dms.kjc.AutoCloner.cloneToplevel(this.args);
        other.target = (at.dms.kjc.sir.SIRStream)at.dms.kjc.AutoCloner.cloneToplevel(this.target);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}





