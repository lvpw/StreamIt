package at.dms.kjc.sir;

import at.dms.kjc.sir.lowering.Propagator;
import at.dms.kjc.lir.LIRStreamType;
import at.dms.kjc.sir.lowering.LoweringConstants;
import at.dms.kjc.*;
import at.dms.util.*;

/**
 * This represents a StreaMIT filter that reads from a data source.
 */
public class SIRFileReader extends SIRPredefinedFilter implements Cloneable {
    /**
     * The filename of the data source.
     */
    private JExpression fileName;

    public SIRFileReader() {
	super(null,
	      "FileReader",
	      /* fields */ JFieldDeclaration.EMPTY(),
	      /* methods */ JMethodDeclaration.EMPTY(),
	      new JIntLiteral(null, 0),
	      new JIntLiteral(null, 0),
	      new JIntLiteral(null, 1),
	      CStdType.Void, null);
	this.fileName = new JStringLiteral("");
    }

    public void setFileName(JExpression fileName) {
	this.fileName = fileName;
    }

    public String getFileName() {
	if (!(fileName instanceof JStringLiteral)) {
	    System.err.println("Error:  have not yet resolved filename for filereader.\n" +
			       "        the filename expression is " + fileName);
	    new RuntimeException().printStackTrace();
	    System.exit(1);
	}
	return ((JStringLiteral)fileName).stringValue();
    }

    public void propagatePredefinedFields(Propagator propagator) {
	JExpression newFilename = (JExpression)fileName.accept(propagator);
	if (newFilename!=null && newFilename!=fileName) {
	    fileName = newFilename;
	}
    }

/** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

/** Returns a deep clone of this object. */
public Object deepClone() {
  at.dms.kjc.sir.SIRFileReader other = new at.dms.kjc.sir.SIRFileReader();
  at.dms.kjc.AutoCloner.register(this, other);
  deepCloneInto(other);
  return other;
}

/** Clones all fields of this into <other> */
protected void deepCloneInto(at.dms.kjc.sir.SIRFileReader other) {
  super.deepCloneInto(other);
  other.fileName = (at.dms.kjc.JExpression)at.dms.kjc.AutoCloner.cloneToplevel(this.fileName);
}

/** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}


