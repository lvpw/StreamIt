package at.dms.kjc.sir;

import at.dms.kjc.*;
import at.dms.compiler.*;

/**
 * This represents an interface table.  This encapsulates the
 * relationship between a message-sending interface and a
 * message-receiving class.  It contains the name of the interface and
 * an ordered list of functions corresponding to the interface.
 */
public class SIRInterfaceTable extends JExpression 
{
    /**
     * The interface that the class implements.
     */
    protected CClassType iface;
    
    /**
     * The methods that implement that interface.
     */
    protected JMethodDeclaration[] methods;

    /**
     * The variable declaration containing this interface table.
     */
    protected JLocalVariable vardecl;

    /**
     * Construct a new interface table.
     * @param where    The line of this node in the source code
     * @param iface    The interface being implemented
     * @param methods  The methods that implement it
     */
    public SIRInterfaceTable(TokenReference where,
                             CClassType iface,
                             JMethodDeclaration[] methods)
    {
        super(where);
        this.iface = iface;
        this.methods = methods;
        this.vardecl = null;
    }

    /**
     * Returns the type of this expression.
     */
    public CType getType() 
    {
        return CStdType.Void;
    }

    public JExpression analyse(CExpressionContext context)
    {
        at.dms.util.Utils.fail("Analysis of SIR nodes not supported yet.");
        return this;
    }

    public void genCode(CodeSequence code, boolean discardValue)
    {
        at.dms.util.Utils.fail("Codegen of SIR nodes not supported yet.");
    }

    /**
     * Returns the interface this implements.
     */
    public CClassType getIface()
    {
        return this.iface;
    }
    
    /**
     * Returns the list of methods.
     */
    public JMethodDeclaration[] getMethods()
    {
        return this.methods;
    }

    /**
     * Associates a variable declaration with the table.
     */
    public void setVarDecl(JLocalVariable vardecl)
    {
        this.vardecl = vardecl;
    }
    
    /**
     * Returns the variable declaration for the table.
     */
    public JLocalVariable getVarDecl()
    {
        return this.vardecl;
    }

    /**
     * Accepts the specified visitor.
     */
    public void accept(KjcVisitor p) 
    {
        if (p instanceof SLIRVisitor)
        {
            ((SLIRVisitor)p).visitInterfaceTable(this);
        }
        else
        {
            // Would accept any sub-objects, but there aren't any.
        }
    }
    
    public Object accept(AttributeVisitor p) 
    {
        if (p instanceof SLIRAttributeVisitor)
            return ((SLIRAttributeVisitor)p).visitInterfaceTable(this);
        else
            return this;
    }
            
}

