package at.dms.kjc.rstream;

import at.dms.kjc.sir.*;
import at.dms.kjc.*;

/**
 * A file reader class that implements a file reader as a normal
 * (non-predefined) filter in the SIR graph.  The filter calls
 * fopen in its init function and then fscanf in its work
 * function.  This can be fused by the partitioner.
 *
 * @author Michael Gordon
 */

public class FileReader extends SIRFilter 
{

    private static int uniqueID = 0;

    //be careful about names clashing...
    /**
     * Returns the fully constructed FileReader based on *sirFR*
     * @param sirFR The SIRFileReader we are replacing
     */
    public FileReader(SIRFileReader sirFR) 
    {
        String fileVar = "__file__" + uniqueID++;
    
        //set I/O rates
        int  pushrate = 1;
        this.setParent(sirFR.getParent());
        this.setPush(pushrate);
        this.setPeek(0);
        this.setPop(0);
        this.setIdent("FileReader" + uniqueID);
        this.setInputType(CStdType.Void);
        this.setOutputType(sirFR.getOutputType());

        //create fields
        JFieldDeclaration file = 
            new JFieldDeclaration(null,
                                  new JVariableDefinition(null,
                                                          0,
                                                          CStdType.Integer,
                                                          fileVar,
                                                          null),
                                  null, null);
        this.addField(file);
    
        //create init function
        JBlock initBlock = new JBlock(null, new JStatement[0], null);
        //create the file open command
        JExpression[] params = {
            new JStringLiteral(null, sirFR.getFileName()),
            new JStringLiteral(null, "r")
        };
        
        JMethodCallExpression fopen = 
            new JMethodCallExpression(null, new JThisExpression(null),
                                      "fopen", params);
        //assign to the file handle
        JAssignmentExpression fass = 
            new JAssignmentExpression(null, 
                                      new JFieldAccessExpression(null,
                                                                 new JThisExpression(null),
                                                                 file.getVariable().getIdent()),
                                      fopen);
    
        initBlock.addStatement(new JExpressionStatement(null, fass, null));
        //set this as the init function...
        this.setInit(new JMethodDeclaration(null,
                                            at.dms.kjc.Constants.ACC_PUBLIC,
                                            CStdType.Void,
                                            "init_fileread" + uniqueID ,
                                            JFormalParameter.EMPTY,
                                            CClassType.EMPTY,
                                            initBlock,
                                            null,
                                            null));
    
        //create work function
        JBlock workBlock = new JBlock(null, new JStatement[0], null);
    
        JVariableDefinition value = new JVariableDefinition(null,
                                                            0,
                                                            sirFR.getOutputType(),
                                                            "__value__" + uniqueID,
                                                            null);
        workBlock.addStatement
            (new JVariableDeclarationStatement(null, value, null));
    
        /* RMR { rather than use fscanf, use fread to be consistent
         * with the other backends which read in binary mode
         *
         *
         * old code follows
         *
         *

         //create a temp variable to hold the value we are reading
         JExpression[] fscanfParams = new JExpression[3];
         //create the params for fscanf
         fscanfParams[0] = new JFieldAccessExpression(null, new JThisExpression(null),
                                                      file.getVariable().getIdent());
         fscanfParams[1] = new JStringLiteral(null,
                                              sirFR.getOutputType().isFloatingPoint() ?
                                              "%f\\n" : "%d\\n");
         fscanfParams[2] = new JLocalVariableExpression(null, value);

         //fscanf call
         JMethodCallExpression fread = 
             new JMethodCallExpression(null, new JThisExpression(null),
                                       Names.fscanf,
                                       fscanfParams);        
         *
         *
         * new code follows
         *
         */

        // create the params for fread(&variable, sizeof(type), 1, file)
        JExpression[] freadParams = new JExpression[4];
    
        // the first parameter: &(variable); treat the & operator as a function call
        JExpression[] addressofParameters = new JExpression[1];
    
        addressofParameters[0] = new JLocalVariableExpression(null, value);
    
        JMethodCallExpression addressofCall =
            new JMethodCallExpression(null, Names.addressof, addressofParameters);
    
        freadParams[0] = addressofCall;
    
        // the second parameter: the call to sizeof(type)
        JExpression[] sizeofParameters = new JExpression[1];
    
        sizeofParameters[0] = 
            new JLocalVariableExpression(null, 
                                         new JVariableDefinition(null, 0,
                                                                 CStdType.Integer,
                                                                 (sirFR.getOutputType().isFloatingPoint() ? 
                                                                  "float" : "int"),
                                                                 null));
    
        JMethodCallExpression sizeofCall =
            new JMethodCallExpression(null, Names.sizeof, sizeofParameters);
    
        freadParams[1] = sizeofCall;
    
        // the third parameter: read one element at a time
        freadParams[2] = new JIntLiteral(pushrate);
    
        // the last parameter: the file pointer
        freadParams[3] = new JFieldAccessExpression(null, new JThisExpression(null),
                                                    file.getVariable().getIdent());
    
        JMethodCallExpression fread = 
            new JMethodCallExpression(null, new JThisExpression(null),
                                      Names.fread,
                                      freadParams);
    
        /* } RMR */
    
        workBlock.addStatement(new JExpressionStatement(null, fread, null));
    
        SIRPushExpression push = 
            new SIRPushExpression(new JLocalVariableExpression(null, value), 
                                  sirFR.getOutputType());
        
        workBlock.addStatement(new JExpressionStatement(null, push, null));

        this.setWork(new JMethodDeclaration(null,
                                            at.dms.kjc.Constants.ACC_PUBLIC,
                                            CStdType.Void,
                                            "work_fileread" + uniqueID ,
                                            JFormalParameter.EMPTY,
                                            CClassType.EMPTY,
                                            workBlock,
                                            null,
                                            null));
    }
}
