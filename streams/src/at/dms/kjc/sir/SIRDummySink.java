package at.dms.kjc.sir;

import at.dms.kjc.CClassType;
import at.dms.kjc.CStdType;
import at.dms.kjc.CType;
import at.dms.kjc.Constants;
import at.dms.kjc.JBlock;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JFieldDeclaration;
import at.dms.kjc.JFormalParameter;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JStatement;

/**
 * This represents a StreamIT filter that just pushes 1 for int type and 1.0f for float.
 */
public class SIRDummySink extends SIRPredefinedFilter implements Cloneable, Constants {

    /**
	 * 
	 */
	private static final long serialVersionUID = 6606322438117430551L;

	/**
     * No argument constructor, FOR AUTOMATIC CLONING ONLY.
     */
    private SIRDummySink() {
        super();
    }

    public SIRDummySink(CType type) 
    {
        super(null,
              "DummySink",
              /* fields */ JFieldDeclaration.EMPTY(), 
              /* methods */ JMethodDeclaration.EMPTY(),
              new JIntLiteral(1), new JIntLiteral(1), new JIntLiteral(0),
              /* input type */ type,
              /* output type */ CStdType.Void);
        setup(type);
    }
    
    /**
     * Set the input type and output type to t
     * also sets the work function and init function
     */
    private void setup(CType t) {
        assert t.isFloatingPoint() || t.isOrdinal() :
            "Constructing a SIRDummySink with a non-scalar type.";
    
        this.setPeek(new JIntLiteral(1));
        this.setPop(new JIntLiteral(1));
        this.setPush(new JIntLiteral(0));
    
        this.setInputType(t);
        this.setOutputType(CStdType.Void);
    
        // work function
        JStatement work1body[] = new JStatement[1];
        work1body[0] =  
            new JExpressionStatement
            (null, new SIRPopExpression(t), null);

    
        JBlock work1block = new JBlock(/* tokref   */ null,
                                       /* body     */ work1body,
                                       /* comments */ null);    
    
        JMethodDeclaration workfn =  new JMethodDeclaration( /* tokref     */ null,
                                                             /* modifiers  */ at.dms.classfile.Constants.ACC_PUBLIC,
                                                             /* returntype */ CStdType.Void,
                                                             /* identifier */ "work",
                                                             /* parameters */ JFormalParameter.EMPTY,
                                                             /* exceptions */ CClassType.EMPTY,
                                                             /* body       */ work1block,
                                                             /* javadoc    */ null,
                                                             /* comments   */ null);
        setWork(workfn);

        // init function
        JBlock initblock = new JBlock(/* tokref   */ null,
                                      /* body     */ new JStatement[0],
                                      /* comments */ null);
        setInit(SIRStream.makeEmptyInit());
    }
    
}


