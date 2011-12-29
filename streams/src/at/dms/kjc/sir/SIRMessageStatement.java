package at.dms.kjc.sir;

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
 * Message Sending Statement.
 *
 * This statement sends a message to a portal.
 */
public class SIRMessageStatement extends JStatement {

    /**
     * The portal that is the target of the message.
     */
    private JExpression portal;
    /**
     * The name of the interface the portal corresponds to.
     */
    private String iname;
    /**
     * The name of the method to invoke in the portal.
     */
    private String ident;
    /**
     * The arguments to the method.
     */
    private JExpression[] args;
    /**
     * The latency with which the message should be delivered.
     */
    private SIRLatency latency;

    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Construct a node in the parsing tree
     */
    public SIRMessageStatement(JExpression portal, String iname, String ident, JExpression[] args, SIRLatency latency) {
        super(null, null);

        this.portal = portal;
        this.iname = iname;
        this.ident = ident;
        this.args = args;
        this.latency = latency;
    }

    
    /**
     * Construct a node in the parsing tree
     */
    public SIRMessageStatement() {
        super(null, null);

        this.portal = null;
        this.iname = null;
        this.ident = null;
        this.args = null;
        this.latency = null;
    }

    public JExpression getPortal() {
        return portal;
    }
    public String getInterfaceName() {
        return iname;
    }
    public String getMessageName() {
        return ident;
    }
    public SIRLatency getLatency() {
        return latency;
    }
    public JExpression[] getArgs() {
        return args;
    }

    public void setPortal (JExpression p) {
        this.portal = p;
    }
    public void setInterfaceName (String iname) {
        this.iname = iname;
    }
    public void setMessageName (String ident) {
        this.ident = ident;
    }
    public void setArgs (JExpression[] a) {
        this.args = a;
    }
    public void setLatency (SIRLatency l) {
        this.latency = l;
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
     * Generates a sequence of bytescodes - NOT SUPPORTED YET.
     */
    public void genCode(CodeSequence code) {
        at.dms.util.Utils.fail("Codegen of SIR nodes not supported yet.");
    }

    /**
     * Accepts the specified attribute visitor.
     * @param   p               the visitor
     */
    public Object accept(AttributeVisitor p) {
        if (p instanceof SLIRAttributeVisitor) {
            return ((SLIRAttributeVisitor)p).visitMessageStatement(this,
                                                                   portal,
                                                                   iname,
                                                                   ident,
                                                                   args,
                                                                   latency);
        } else {
            return this;
        }
    }

    /**
     * Accepts the specified visitor.
     */
    public void accept(KjcVisitor p) {
        if (p instanceof SLIRVisitor) {
            ((SLIRVisitor)p).visitMessageStatement(this, 
                                                   portal,
                                                   iname,
                                                   ident,
                                                   args,
                                                   latency);
        } else {
            // otherwise, visit the arguments
            for (int i=0; i<args.length; i++) {
                args[i].accept(p);
            }
        }
    }


    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    public Object deepClone() {
        at.dms.kjc.sir.SIRMessageStatement other = new at.dms.kjc.sir.SIRMessageStatement();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.sir.SIRMessageStatement other) {
        super.deepCloneInto(other);
        other.portal = (at.dms.kjc.JExpression)at.dms.kjc.AutoCloner.cloneToplevel(this.portal);
        other.iname = (java.lang.String)at.dms.kjc.AutoCloner.cloneToplevel(this.iname);
        other.ident = (java.lang.String)at.dms.kjc.AutoCloner.cloneToplevel(this.ident);
        other.args = (at.dms.kjc.JExpression[])at.dms.kjc.AutoCloner.cloneToplevel(this.args);
        other.latency = (at.dms.kjc.sir.SIRLatency)at.dms.kjc.AutoCloner.cloneToplevel(this.latency);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
