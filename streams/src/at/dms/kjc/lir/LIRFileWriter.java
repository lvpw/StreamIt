package at.dms.kjc.lir;

import at.dms.kjc.*;
import at.dms.compiler.*;

/**
 * This represents allocation and initialization of a child stream
 * that is a file writer.  It roughly corresponds to an LIRSetChild,
 * except that the type is a library-supported file writer instead of 
 * an SIR-defined structure.
 *
 *  d->child1 = malloc(sizeof(LIBRARY-FILE-WRITER));
 *  d->child1->c = create_context(d->child1);
 *  register_child(d->c, d->child1->c);
 */
public class LIRFileWriter extends LIRNode {

    /**
     * The name of the child (e.g. child1)
     */
    private String childName;
    /**
     * The name of the file to read from.
     */
    private String fileName;
    
    // ----------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------

    /**
     * Construct a node.
     */
    public LIRFileWriter(JExpression streamContext,
			 String childName,
			 String fileName) {
	super(streamContext);
	this.childName = childName;
	this.fileName = fileName;
    }

    public String getChildName() {
        return childName;
    }

    public String getFileName() {
	return fileName;
    }

    public void accept(SLIRVisitor v)
    {
        v.visitFileWriter(this);
    }
}
