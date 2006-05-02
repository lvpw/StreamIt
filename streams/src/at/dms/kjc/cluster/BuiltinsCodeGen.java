package at.dms.kjc.cluster;

import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.sir.*;
import at.dms.kjc.CType;
import at.dms.util.Utils;
import java.util.*;
//import at.dms.kjc.JMethodDeclaration;

class BuiltinsCodeGen {

    /**
     * Create code for the work function of a built-in filter.
     * 
     * @param filter  The filter that the work function is being built for
     * @param selfID  A unique id assigned in FlatIRToC
     * @param p       A printer to output the generated code
     */
    static void predefinedFilterWork(SIRPredefinedFilter filter, int selfID,
                                     CodegenPrintWriter p) {
        // Caller has printed function name and an iteration parameter ____n
        // Generate loop to execute body of this function ____n times,
        // In loop generate specialized code for function.
        p.print("void " + ClusterUtils.getWorkName(filter, selfID)
                + "(int ____n) {");
        p.indent();
        p.newLine();
        p.indent();
        //        p.println("// predefinedFilterWork " + filter.getName());

        // SIRFileReader
        if (filter instanceof SIRFileReader) {
            genFileReaderWork((SIRFileReader)filter,selfID,p);
            // SIRFileWriter
        } else if (filter instanceof SIRFileWriter) {
            genFileWriterWork((SIRFileWriter)filter,selfID,p);
            // SIRIdentity
        } else if (filter instanceof SIRIdentity) {
            p.println("for (; 0 < ____n; ____n--) {");
            p.indent();
            p.println("  " + ClusterUtils.pushName(selfID) + "("
                      + ClusterUtils.popName(selfID) + "());");
            p.outdent();
            p.print("}"); // end of for loop.
        } else if (filter instanceof SIRDummySink
                   || filter instanceof SIRDummySource) {
            // DummySource and SummySink do not appear in any of our
            // application code.  Are they part of the language?
            // TODO: get right exception for unimplemented.
            throw new Error("Unsupported predefined filter "
                            + filter.getName());
        } else {
            // TODO: get right unchecked exception for unextended code...
            throw new Error("Unknown predefined filter " + filter.getName());
        }
        p.newLine();
        p.outdent(); // end of method body
        p.outdent(); // end of method definition
        p.print("}");
        p.newLine();
        p.newLine();

    }

    // utility routines for framing functions (only parameterless functions 
    // for now)

    private static void startParameterlessFunction(String return_type,
                                                   String function_name, CodegenPrintWriter p) {
        p.print(return_type);
        p.println(" " + function_name + "() {");
        p.indent();
    }

    private static void endFunction(CodegenPrintWriter p) {
        p.outdent();
        p.println("}");
        p.newLine();
    }

    // utility routines for FileReader and FileWriter.
    
    private static String fpName(SIRFilter f) {
        // assuming that a filter manages at most one C- stype file
        // pointer, what is that file pointer named?
        return f.getName() + "__fp";
    }

    private static String bitsToGoName(SIRFilter f) {
        // assuming that a filter manages at most one C- stype file
        // pointer, what is that file pointer named?
        return f.getName() + "__bits_to_go";
    }

    private static String theBitsName(SIRFilter f) {
        // assuming that a filter manages at most one C- stype file
        // pointer, what is that file pointer named?
        return f.getName() + "__the_bits";
    }

    /**
     * Create code for the init function of built-in filter
     * 
     * @param filter
     *            The filter for which we are creating init function
     * @param return_type
     *            The return type for the init function
     * @param function_name
     *            The name for the init function
     * @param selfID
     *            The unique identifier that FlatIRToCluster has assigned to the
     *            function
     * @param cleanupCode
     *            A list of statements run at "clean-up" time to which this
     *            code can add requests to close files, etc.
     * @param p
     *            The printer for outputting code for the function
     */
    static void predefinedFilterInit(SIRPredefinedFilter filter,
                                     CType return_type, String function_name, int selfID,
                                     List/*String*/ cleanupCode,CodegenPrintWriter p) {

        // No wrapper code around init since may need to create defns at file
        // level. We assume that the code generator will continue to
        // generate code for init before code for work, so scope of
        // any file-level code will include the work function.

        //        p.println("// predefinedFilterInit " + filter.getName()
        //      + " " + filter.getInputType().toString() + " -> " 
        //      + filter.getOutputType().toString());

        if (filter instanceof SIRFileReader) {
            genFileReaderInit((SIRFileReader)filter, return_type, 
                              function_name, selfID, cleanupCode, p);
        } else if (filter instanceof SIRFileWriter) {
            genFileWriterInit((SIRFileWriter)filter, return_type, 
                              function_name, selfID, cleanupCode, p);
        } else if (filter instanceof SIRIdentity 
                   || filter instanceof SIRDummySink
                   || filter instanceof SIRDummySource) {
            // all of these have filters produce empty init functions.
            startParameterlessFunction(ClusterUtils.CTypeToString(return_type),
                                       function_name, p);
            endFunction(p);
        } else if (filter instanceof SIRDummySink) {
            // TODO:  get right exception for unimplemented.
            throw new Error("Unsupported predefined filter "
                            + filter.getName()); 
        } else if (filter instanceof SIRDummySource) {
            throw new Error("Unsupported predefined filter "
                            + filter.getName());
        } else {
            // TODO:  get right unchecked exception for unextended code...
            throw new Error("Unknown predefined filter " + filter.getName());
        }
    }
   
    // should be able to change this to longer numeric type for less
    // frequent I/O if we have endian-ness correct.
    private static final String bits_type = "unsigned char";
    
    /*
     * File reader code currently works by using fread.
     * 
     * The File* is declared outside any function / method.
     * 
     * This code follows the model in the library of stalling (not pushing)
     * at end of file (detected by fread returning 0).
     * 
     */
    private static void genFileReaderWork(SIRFileReader filter, 
                                          int selfID,
                                          CodegenPrintWriter p) {

        String theType = "" + filter.getOutputType();
        // dispatch to special routine for bit type
        if (theType.equals("bit")) {
            genFileReaderWorkBit(filter, selfID, p);
            return;
        }

        // get source and destination of incoming stream
        NetStream out = RegisterStreams.getFilterOutStream(filter);
        int s = out.getSource();
        int d = out.getDest();

        // template code to generate, using symbolic free vars above.
        String template = 
            "\n  int __index;" + "\n" +
            "  for (__index=0; __index < ____n; __index++) {" + "\n" +
	    "    PUSH(FileReader_read<"+theType+">(__file_descr__"+selfID+"));\n" +
	    "  }\n";

	/*
            "  #ifdef FUSED" + "\n" +
            "    #ifdef NOMOD" + "\n" +
            "      // read directly into buffer" + "\n" +
            "      if (fread(&(BUFFER[HEAD]), sizeof(BUFFER[HEAD]), ____n, FILEREADER)) {" + "\n" +
            "        HEAD+=____n;" + "\n" +
            "      }" + "\n" +
            "    #else" + "\n" +
            "      // wraparound buffer, might have to read in two pieces (but never" + "\n" +
            "      // more, since we would overwrote what we already wrote on this call)" + "\n" +
            "      if (HEAD+____n <= __BUF_SIZE_MASK) {" + "\n" +
            "          // no overflow" + "\n" +
            "          if (fread(&(BUFFER[HEAD]), sizeof(BUFFER[HEAD]), ____n, FILEREADER)) {" + "\n" +
            "             HEAD+=____n;" + "\n" +
            "          }" + "\n" +
            "      } else {" + "\n" +
            "          // overflow, need two pieces" + "\n" +
            "          int piece1 = __BUF_SIZE_MASK - HEAD;" + "\n" +
            "          int piece2 = ____n - piece1;" + "\n" +
            "          if (fread(&(BUFFER[HEAD]), sizeof(BUFFER[HEAD]), piece1, FILEREADER)) {" + "\n" +
            "              if (fread(&(BUFFER[0]), sizeof(BUFFER[HEAD]), piece2, FILEREADER)) {" + "\n" +
            "                HEAD+=____n;" + "\n" +
            "                HEAD&=__BUF_SIZE_MASK;" + "\n" +
            "              }" + "\n" +
            "          }" + "\n" +
            "      }" + "\n" +
            "     #endif" + "\n" +
            "  #else" + "\n" +
            "    // read as a block" + "\n" +
            "    TYPE __buffer[____n];" + "\n" +
            "    int __index;" + "\n" +
            "    if (fread(__buffer, sizeof(__buffer[0]), ____n, FILEREADER)) {" + "\n" +
            "      for (__index=0; __index < ____n; __index++) {" + "\n" +
            "       // should move push out of loop, but not clear if mult-push implemented yet" + "\n" +
            "       PUSH(__buffer[__index]);" + "\n" +
            "      }" + "\n" +
            "    }" + "\n" +
            "  #endif";
	*/

        // set values of free variables
        String TYPE = theType;
        String FILEREADER = fpName(filter);
        String FUSED = "__FUSED_" + s + "_" + d;
        String NOMOD = "__NOMOD_" + s + "_" + d;
        String BUFFER = "BUFFER_" + s + "_" + d;
        String HEAD = "HEAD_" + s + "_" + d;
        String BUF_SIZE_MASK = "__BUF_SIZE_MASK_" + s + "_" + d;
        String PUSH = "__push__" + selfID;

        // replace templates with correct values
        template = Utils.replaceAll(template, "TYPE", TYPE);
        template = Utils.replaceAll(template, "FILEREADER", FILEREADER);
        template = Utils.replaceAll(template, "FUSED", FUSED);
        template = Utils.replaceAll(template, "NOMOD", NOMOD);
        template = Utils.replaceAll(template, "BUFFER", BUFFER);
        template = Utils.replaceAll(template, "HEAD", HEAD);
        template = Utils.replaceAll(template, "BUF_SIZE_MASK", BUF_SIZE_MASK);
        template = Utils.replaceAll(template, "PUSH", PUSH);

        // output code
        p.println(template);
    }

    /*
     * There is special case code for FileReader<bit> since individual
     * bits can not be read in by any system routine that I know.
     * 
     * With most bit streams that I (A.D.) have seen, the order is
     * little-endian in words, low-to-high bit in bytes.
     * This code -- matching Matt's implementation for the library
     * is a bit odd: endian-ness depends on fread / fwrite -- my
     * laziness, should always be little-endian -- but bits are read
     * and written from high to low.  So byte 0b10011111
     * is read as 1, 0, 0, 1, 1, 1, 1, 1 and is written back as
     * 0b10011111.  This looks a little odd if a bit stream does not
     * end on a byte boundary: writing stream 1,0,1,1 results in 0b10110000 
     * 
     * If we change the number of bits bufferred from 1 byte to a larger
     * integer type, we will have to worry about endian-ness on disk.
     * This code was designed for .BMP files, which are little-endian.
     * on disk.  We will also have to worry about file lengths: Unix allows
     * file lengths in bytes, this will involve some extra code for not writing
     * an excessive number of bytes on the last write, causing the file lengths
     * to be dependent on the size of 'bits_type'.
     **/
    private static void genFileReaderWorkBit(SIRFileReader filter, 
                                             int selfID,
                                             CodegenPrintWriter p) {

        // haven't bothered to do buffered file input on top of the
        // bit input (it is possible but seems a little complicated)
        System.err.println("PERFORMANCE WARNING:  FileReader<bit> reads only 1 byte" +
                           "  at a time; faster to use FileWriter<int>, which is buffered.");

        String theType = "" + filter.getOutputType();
        // wrap in loop
        p.println("for (; 0 < ____n; ____n--) {");
        p.indent();
        
        // the bit type is special since you can not just read or
        // write a bit.  It requires bufferring in some larger
        // integer type.
        String bits_to_go = bitsToGoName(filter);
        String the_bits = theBitsName(filter);
            
        p.println("static " + bits_type + " " + the_bits + " = 0;");
        p.println("static int " + bits_to_go + " = 0;");
        p.newline();
        p.println("if (" + bits_to_go + " == 0) {");
        p.indent();
        p.println("if (fread(" + "&"+ the_bits + ", " 
                  + "sizeof(" + the_bits + "),"
                  + " " + "1, " 
                  + fpName(filter) + ")) {");
        p.indent();
        p.println(bits_to_go + " = 8 * sizeof("+ the_bits + ");");
        // identical to code fragment below ///////////////////////
        p.println(ClusterUtils.pushName(selfID) 
                  + "((" + the_bits +" & (1 << (sizeof(" + the_bits + ") * 8 - 1))) ? 1 : 0);");
        p.println(the_bits + " <<= 1;");
        p.println(bits_to_go + "--;");
        // end identical to code fragment below ////////////////////
        p.outdent();
        p.println("}");
        p.outdent();
        p.println("} else {");
        p.indent();
        // identical to code fragment above /////////////////////////
        p.println(ClusterUtils.pushName(selfID) 
                  + "((" + the_bits +" & (1 << (sizeof(" + the_bits + ") * 8 - 1))) ? 1 : 0);");
        p.println(the_bits + " <<= 1;");
        p.println(bits_to_go + "--;");
        // end identical to code fragment above /////////////////////
        p.outdent();
        p.println("}");

        // close loop
        p.outdent();
        p.print("}"); // end of for loop.
    }
    
    /*
     * When generating (no) code for init routine, also generate File*
     * and close() for file.
     */
    private static void genFileReaderInit(SIRFileReader fr,
                                          CType return_type, String function_name, int selfID,
                                          List/*String*/ cleanupCode,CodegenPrintWriter p) {

	int id = NodeEnumerator.getSIROperatorId(fr);
        String theType = "" + fr.getOutputType();
        // dispatch to special routine for bit type
        if (theType.equals("bit")) {
	    p.print("FILE* " + fpName(fr) + ";");
	    p.newLine();
	    p.newLine();
        }

        startParameterlessFunction(ClusterUtils.CTypeToString(return_type),
                                   function_name, p);

        if (theType.equals("bit")) {
	    p.print(fpName(fr) + " = fopen(\"" + fr.getFileName()
		    + "\", \"r\");");
	    p.newLine();
	    p.print("assert (" + fpName(fr) + ");");
	    p.newLine();
	} else {
	    p.print("__file_descr__"+id+" = FileReader_open(\""+fr.getFileName()+"\");");
	    p.newLine();
	    p.print("assert (__file_descr__"+id+");");
	    p.newLine();
	}

	endFunction(p);

        String closeName = ClusterUtils.getWorkName(fr, selfID)
            + "__close"; 
                                  
        startParameterlessFunction("void", closeName, p);

	if (theType.equals("bit")) {
	    p.println("fclose(" + fpName(fr) + ");");
	} else {
	    p.println("FileReader_close(__file_descr__"+id+");");
	}
        
	endFunction(p);
        
        cleanupCode.add(closeName+"();\n");

    }
    

    /*
     * File writer code currently works by using fwrite.
     * 
     * The File* is declared outside any function / method.
     * 
     * There is special case code for FileReader<bit> since individual
     * bits can not be read in by any system routine that I know.
     * The current bits and the count of unprocessed bits are created
     * outside of 
     */
    private static void genFileWriterWork(SIRFileWriter fw, int selfID,
                                          CodegenPrintWriter p) {
        String theType = "" + fw.getInputType();
        if (theType.equals("bit")) {
            // the bit type is special since you can not just read or
            // write a bit.  It requires buffering in some larger
            // integer type.
            String bits_to_go = bitsToGoName(fw);
            String the_bits = theBitsName(fw);
            
            p.println("unsigned char __buffer[____n/8+1];");
            p.println("int __index = 0;");
            p.println("for (; 0 < ____n; ____n--) {");
            p.indent();

            p.println(the_bits + " = (" + bits_type + ") ((" + the_bits 
                      + " << 1) | (" + ClusterUtils.popName(selfID) 
                      + "() & 1));");
            p.println(bits_to_go + "--;");
            p.println("if (" + bits_to_go + " == 0) {");
            p.indent();
            p.println("__buffer[__index++] = FileWriter__5_4__the_bits;");
            p.println(the_bits + " = 0;");
            p.println(bits_to_go + " = 8 * sizeof(" + the_bits + ");");
            p.outdent();
            p.println("}");
            p.outdent();
            p.println("}");
            p.println("fwrite(__buffer, "
                      + "sizeof(" + the_bits +"), " + "__index, " + fpName(fw)
                      + ");");
        } else {
            // not a bit type.  write directly to file without needing to buffer bits.
	    
	    p.println("\n  int __index;" + "\n" +
		      "  for (__index=0; __index < ____n; __index++) {" + "\n" +
		      "    FileWriter_write<"+theType+">(__file_descr__"+selfID+", __pop__"+selfID+"());\n" +
		      "  }\n");

	    /*
            NetStream in = RegisterStreams.getFilterInStream(fw);
            // source and destination of incoming stream
            int s = in.getSource();
            int d = in.getDest();
            
            p.println("#ifdef __FUSED_" + s + "_" + d);
            p.indent(); 
            {
                p.println("fwrite(&(BUFFER_" + s + "_" + d + "[TAIL_" + s + "_" + d + "]), " + 
                          "sizeof(BUFFER_" + s + "_" + d + "[TAIL_" + s + "_" + d + "]), " +
                          "____n, " + fpName(fw) + ");");
                p.println("TAIL_" + s + "_" + d + "+=____n;");
            }
            p.outdent();
            p.println("#else");
            p.indent();
            {
                p.println("fwrite(&(__pop_buf__" + selfID + "[__tail__" + selfID + "]), " +
                          "sizeof(__pop_buf__" + selfID + "[__tail__" + selfID + "]), " +
                          "____n, " + fpName(fw) + ");");
                p.println("__tail__" + selfID + "+=____n;");
            }
            p.outdent();
            p.println("#endif");
	    */
        }
    }
    
    /*
     * When generating (no) code for init routine, also generate File*
     * and close() for file.
     */
    private static void genFileWriterInit(SIRFileWriter fw,
                                          CType return_type, String function_name, int selfID,
                                          List/*String*/ cleanupCode,CodegenPrintWriter p) {

	int id = NodeEnumerator.getSIROperatorId(fw);

        String theType = "" + fw.getInputType();
        String bits_to_go = bitsToGoName(fw);
        String the_bits = theBitsName(fw);

        if (theType.equals("bit")) {
	    p.println("FILE* " + fpName(fw) + ";");
	}

        if (theType.equals("bit")) {
            // the bit type is special since you can not just read or
            // write a bit.  It requires buffering in some larger
            // integer type.

            p.println(bits_type + " " + the_bits + " = 0;");
            p.println("int " + bits_to_go + " = 8 * sizeof(" + the_bits + ");");
        }
        p.newLine();

        startParameterlessFunction(ClusterUtils.CTypeToString(return_type),
                                   function_name, p);

        if (theType.equals("bit")) {
	    p.println(fpName(fw) + " = fopen(\"" + fw.getFileName()
		      + "\", \"w\");");
	    p.println("assert (" + fpName(fw) + ");");
	} else {
	    p.print("__file_descr__"+id+" = FileWriter_open(\""+fw.getFileName()+"\");");
	    p.newLine();
	    p.print("assert (__file_descr__"+id+");");
	    p.newLine();
	}

        endFunction(p);

        String closeName = ClusterUtils.getWorkName(fw, selfID)
            + "__close"; 
               
        startParameterlessFunction("void", closeName, p);
        if (theType.equals("bit")) {
            p.println("if (" + bits_to_go + " != 8 * sizeof(" 
                      + the_bits + ")) {");
            p.indent();
            p.println(the_bits + " = " + the_bits + " << " + bits_to_go + ";");
            p.println("fwrite(" + "&" + the_bits + ", " 
                      + "sizeof(" + the_bits +"), " + "1, " + fpName(fw)
                      + ");");
            p.outdent();
            p.println("}");
        } 

        if (theType.equals("bit")) {
	    p.println("fclose(" + fpName(fw) + ");");
	} else {
	    p.println("FileWriter_flush(__file_descr__"+id+");");
	    p.println("FileWriter_close(__file_descr__"+id+");");
	}

        endFunction(p);
        
        cleanupCode.add(closeName+"();\n");
    }
    

}
