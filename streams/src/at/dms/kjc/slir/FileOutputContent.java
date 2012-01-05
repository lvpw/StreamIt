package at.dms.kjc.slir;

import at.dms.classfile.Constants;
import at.dms.kjc.CClassType;
import at.dms.kjc.CEmittedTextType;
import at.dms.kjc.CStdType;
import at.dms.kjc.CType;
import at.dms.kjc.JAssignmentExpression;
import at.dms.kjc.JBlock;
import at.dms.kjc.JEmittedTextExpression;
import at.dms.kjc.JExpression;
import at.dms.kjc.JExpressionStatement;
import at.dms.kjc.JFieldAccessExpression;
import at.dms.kjc.JFieldDeclaration;
import at.dms.kjc.JFormalParameter;
import at.dms.kjc.JIntLiteral;
import at.dms.kjc.JLocalVariableExpression;
import at.dms.kjc.JMethodCallExpression;
import at.dms.kjc.JMethodDeclaration;
import at.dms.kjc.JStatement;
import at.dms.kjc.JStringLiteral;
import at.dms.kjc.JThisExpression;
import at.dms.kjc.JVariableDefinition;
import at.dms.kjc.KjcOptions;
import at.dms.kjc.common.ALocalVariable;
import at.dms.kjc.sir.SIRFileWriter;
import at.dms.kjc.sir.SIRPopExpression;
import at.dms.kjc.sir.SIRWriter;

/**
 * Predefined FilterContent for file output, expands to implement a FileWriter.
 * @author jasperln
 */
public class FileOutputContent extends OutputContent implements at.dms.kjc.DeepCloneable {
    private String filename; //The filename
    private int outputs; //Expected number of outputs

    /**
     * No argument constructor, FOR AUTOMATIC CLONING ONLY.
     */
    protected FileOutputContent() {
        super();
    }

    /**
     * Copy constructor for FileOutputContent.
     * @param content The FileOutputContent to copy.
     */
    public FileOutputContent(FileOutputContent content) {
        super(content);
        outputs = -1;
        filename=content.filename;
    }

    /**
     * Construct FileInputContent from SIRFileWriter.
     * @param filter The SIRFileWriter used to contruct the FileOutputContent.
     */
    public FileOutputContent(SIRFileWriter filter) {
        super(filter);
        outputs = -1;
        filename=filter.getFileName();
    }
    
    /**
     * Construct FileInputContent from SIRWriter.
     * @param filter The SIRWriter used to contruct the FileOutputContent.
     */
    public FileOutputContent(SIRWriter filter) {
        super(filter);
        System.out.println("**TODO** FileOutputContent(SIRWriter)");
        outputs = -1;
        filename=filter.getFileName();
    }

    /**
     * Returns expected number of outputs.
     */
    public int getOutputs() 
    {
        return outputs;
    }
    
    /**
     * Sets expected number of outputs.
     */
    public void setOutputs(int i) 
    {
        outputs = i;
    }

    /**
     * Returns filename of FileOutputContent.
     */
    public String getFileName() {
        return filename;
    }

    /**
     * Get the type of the file writer
     * . 
     * @return The type.
     */
    public CType getType() {
        return getInputType();
    }
    
    /**
     * Returns if output format of file is floating point.
     */
    public boolean isFP() 
    {
        return getInputType().isFloatingPoint();
    }
    
    JMethodDeclaration closeMethod;
    
    /**
     * Return a statement closing the file.
     * Only valid after calling {@link #createContent()}.
     * @return statement to put in cleanup section of code.
     */
    public JStatement closeFile() {
        JMethodCallExpression close = 
            new JMethodCallExpression(null, new JThisExpression(null), 
                                      closeMethod.getName(), JExpression.EMPTY);
 
        return new JExpressionStatement(close);
    }
    
    /**
     * Create kopi code that when translated to C will manipulate the file.
     * The C file will need to include <stdio.h>
     */
    @Override
	public void createContent() {
        // on entry: 
        // name is set to something reasonable
        // input and output types set correctly
        // bogus init function exists
        // bogus work function exists
        // methods set to the two bogus functions.
        
        System.out.println("**TODO** FileOutputContent.createContent()");
        System.out.println("**TODO** FileOutputContent.createContent() KjcOptions.outputs=" + KjcOptions.outputs);
 
        
        String fileVar = "_fileWriter_" + my_unique_ID;
        
        this.outputType = CStdType.Void;

        JVariableDefinition fileDefn = new JVariableDefinition(null,
                0,
                new CEmittedTextType("FILE *"),
                fileVar,
                null);
        //create fields
        JFieldDeclaration file = 
            new JFieldDeclaration(null,fileDefn, null, null);
        this.addAField(file);
    
        //create init function
        JBlock initBlock = new JBlock(null, new JStatement[0], null);
        //create the file open command
        JExpression[] params = 
            {
                new JStringLiteral(null, getFileName()),
                new JStringLiteral(null, "w")
            };
    
        JMethodCallExpression fopen = 
            new JMethodCallExpression(null, new JThisExpression(null),
                                      "fopen", params);
        //assign to the file handle
        JAssignmentExpression fass = 
            new JAssignmentExpression(null, 
                                      new JFieldAccessExpression(null,
                                                                 new JThisExpression(null),
                                                                 fileDefn.getIdent()),
                                      fopen);
    
        initBlock.addStatement(new JExpressionStatement(null, fass, null));
        
        // do some standard C error checking here.
        // do we need to put this in a separate method to allow subclass to override?
        initBlock.addStatement(new JExpressionStatement(
                new JEmittedTextExpression(new Object[]{
                        "if (",
                        new JFieldAccessExpression(
                                new JThisExpression(null),
                                file.getVariable().getIdent()),
                        " == NULL) { perror(\"error opening "+ filename + "\"); }"
                })));
        //set this as the init function...
        JMethodDeclaration initMethod = new JMethodDeclaration(null,
                Constants.ACC_PUBLIC,
                CStdType.Void,
                "init_filewrite" + my_unique_ID ,
                JFormalParameter.EMPTY,
                CClassType.EMPTY,
                initBlock,
                null,
                null);
        this.initFunction = initMethod;
    
        //create work function
        JBlock workBlock = new JBlock(null, new JStatement[0], null);
    
        SIRPopExpression pop = new SIRPopExpression(getInputType());
        ALocalVariable tmp = ALocalVariable.makeTmp(getInputType());
        workBlock.addStatement(tmp.getDecl());
        workBlock.addStatement(new JExpressionStatement(new JAssignmentExpression(tmp.getRef(),pop)));
    
    
        // RMR { support ascii or binary file operations for reading }
        JMethodCallExpression fileio;

        if (KjcOptions.asciifileio) {
            //the params for the fprintf call
            JExpression[] fprintfParams = new JExpression[3];
            fprintfParams[0] = new JFieldAccessExpression(null, new JThisExpression(null),
                                                          fileDefn.getIdent());
            fprintfParams[1] = new JStringLiteral(null,
                                                  getInputType().isFloatingPoint() ?
                                                  "%f\\n" : "%d\\n");
            fprintfParams[2] = tmp.getRef();
            
            JMethodCallExpression fprintf = 
                new JMethodCallExpression(null, new JThisExpression(null),
                                          "fprintf",
                                          fprintfParams);
            fileio = fprintf;
        }
        else {
            
            // create the params for fwrite(&variable, sizeof(type), 1, file)
            JExpression[] fwriteParams = new JExpression[4];
            
            // the first parameter: &(variable); treat the & operator as a function call
            JExpression[] addressofParameters = new JExpression[1];
            
            addressofParameters[0] = tmp.getRef();
            
            JMethodCallExpression addressofCall =
                new JMethodCallExpression(null, "&", addressofParameters);

            fwriteParams[0] = addressofCall;
            
            // the second parameter: the call to sizeof(type)
            JExpression[] sizeofParameters = new JExpression[1];
            
            sizeofParameters[0] = 
                new JLocalVariableExpression(null, 
                                             new JVariableDefinition(null, 0,
                                                                     CStdType.Integer,
                                                                     (getInputType().isFloatingPoint() ? 
                                                                      "float" : "int"),
                                                                     null));
            
            JMethodCallExpression sizeofCall =
                new JMethodCallExpression(null, "sizeof", sizeofParameters);
            sizeofCall.setTapeType(CStdType.Integer);
            
            fwriteParams[1] = sizeofCall;
            
            // the third parameter: read one element at a time
            fwriteParams[2] = new JIntLiteral(1);
            
            // the last parameter: the file pointer
            fwriteParams[3] = new JFieldAccessExpression(null, new JThisExpression(null),
                                                         fileDefn.getIdent());
            
            JMethodCallExpression fwrite = 
                new JMethodCallExpression(null, new JThisExpression(null),
                                          "fwrite",
                                          fwriteParams);
            fwrite.setTapeType(CStdType.Void);

            fileio = fwrite;
        }

        workBlock.addStatement(new JExpressionStatement(null, fileio, null));
        
        JMethodDeclaration workMethod = new JMethodDeclaration(null,
                Constants.ACC_PUBLIC,
                CStdType.Void,
                "work_filewrite" + my_unique_ID ,
                JFormalParameter.EMPTY,
                CClassType.EMPTY,
                workBlock,
                null,
                null);

        workMethod.setPop(1);
        workMethod.setPeek(1);
        workMethod.setPush(0);
        this.steady = new JMethodDeclaration[]{workMethod};
        
        
        JMethodCallExpression close = 
            new JMethodCallExpression(null, new JThisExpression(null),
                                      "fclose", new JExpression[]{
                new JFieldAccessExpression(null,
                        new JThisExpression(null),
                        fileDefn.getIdent())
            });
        JBlock body = new JBlock();
        body.addStatement(new JExpressionStatement(close));
        
        closeMethod = new JMethodDeclaration(null,
                Constants.ACC_PUBLIC,
                CStdType.Void,
                "close_filewrite" + my_unique_ID ,
                JFormalParameter.EMPTY,
                CClassType.EMPTY,
                body,
                null,
                null);

        // discard old dummy methods and set up the new ones.
        this.setTheMethods(new JMethodDeclaration[]{initMethod,workMethod,closeMethod});
    }


    /** THE FOLLOWING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */

    /** Returns a deep clone of this object. */
    @Override
	public Object deepClone() {
        at.dms.kjc.slir.FileOutputContent other = new at.dms.kjc.slir.FileOutputContent();
        at.dms.kjc.AutoCloner.register(this, other);
        deepCloneInto(other);
        return other;
    }

    /** Clones all fields of this into <pre>other</pre> */
    protected void deepCloneInto(at.dms.kjc.slir.FileOutputContent other) {
        super.deepCloneInto(other);
        other.filename = (java.lang.String)at.dms.kjc.AutoCloner.cloneToplevel(this.filename);
        other.outputs = this.outputs;
        other.closeMethod = (at.dms.kjc.JMethodDeclaration)at.dms.kjc.AutoCloner.cloneToplevel(this.closeMethod);
    }

    /** THE PRECEDING SECTION IS AUTO-GENERATED CLONING CODE - DO NOT MODIFY! */
}
