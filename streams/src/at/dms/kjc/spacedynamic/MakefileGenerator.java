package at.dms.kjc.spacedynamic;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.sir.lowering.*;
import at.dms.util.Utils;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Iterator;
import java.io.*;


public class MakefileGenerator 
{
    public static final String MAKEFILE_NAME = "Makefile.streamit";
    private static StreamGraph streamGraph;
    private static Layout layout;
    private static RawChip rawChip;
    
    public static void createMakefile(StreamGraph sg) 
    {
	streamGraph = sg;
	layout = sg.getLayout();
	rawChip = sg.getRawChip();

	try {
	    //FileWriter fw = new FileWriter("Makefile");
	    FileWriter fw = new FileWriter(MAKEFILE_NAME);
	    //create a set of all the tiles with code
	    HashSet tiles = new HashSet();
	    tiles.addAll(TileCode.realTiles);
	    tiles.addAll(TileCode.tiles);
	    
	    //remove the tiles assigned to FileReaders
	    //do not generate switchcode for Tiles assigned to file readers
	    //they are just dummy tiles
	    Iterator frs = sg.getFileVisitor().fileNodes.iterator();
	    while (frs.hasNext()) {
		tiles.remove(layout.getTile((FlatNode)frs.next()));
	    }

	    //remove joiners from the hashset if we are in decoupled mode, 
	    //we do not want to simulate joiners
	    if (KjcOptions.decoupled || IMEMEstimation.TESTING_IMEM) 
		removeJoiners(tiles);

	    Iterator tilesIterator = tiles.iterator();
	    
	    fw.write("#-*-Makefile-*-\n\n");
	    /*
	    if (KjcOptions.outputs < 0 &&
		! (KjcOptions.numbers > 0))
		fw.write("LIMIT = TRUE\n"); // need to define limit for SIMCYCLES to matter
	    */
	    
	    if (!IMEMEstimation.TESTING_IMEM) {
		//fw.write("ATTRIBUTES = IMEM_EXTRA_LARGE\n");
		fw.write("BTL-DEVICES += -dram_freq 100\n");
		fw.write("ATTRIBUTES += HWIC\n");
		//add some other stuff
		fw.write("MEMORY_LAYOUT=LEFT_RIGHT_SIDES\n");
		fw.write("BTL-DEVICES += -enable_all_sides_for_dram -dram lhs\n");
	    }
	    
	    //if we are generating number gathering code, 
	    //we do not want to use the default print service...
	    if (KjcOptions.outputs > 0 ||
		KjcOptions.numbers > 0 ||
		KjcOptions.decoupled) {
		fw.write("EXTRA_BTL_ARGS += -magic_instruction\n ");
	    }
	    else {
		fw.write("ATTRIBUTES += USES_PRINT_SERVICE\n");
	    }
	    
	    //fw.write("SIM-CYCLES = 500000\n\n");
	    fw.write("\n");
	    //if we are using the magic network, tell btl
	    if (KjcOptions.magic_net)
		fw.write("EXTRA_BTL_ARGS += " +
			 "-magic_instruction -magic_crossbar C1H1\n");
	    fw.write("include $(TOPDIR)/Makefile.include\n\n");
	    fw.write("RGCCFLAGS += -O3\n\n");
            fw.write("BTL-MACHINE-FILE = fileio.bc\n\n");
	    if (streamGraph.getFileVisitor().foundReader || streamGraph.getFileVisitor().foundWriter)
		createBCFile(true, TileCode.realTiles);
            else
                createBCFile(false, TileCode.realTiles);
	    if (rawChip.getYSize() > 4) {
		fw.write("TILE_PATTERN = 8x8\n\n");
	    }
	    //fix for snake boot race condition
	    fw.write("MULTI_SNAKEBOOT = 0\n\n");
	    
	    fw.write("TILES = ");
	    while (tilesIterator.hasNext()) {
		int tile = 
		    ((RawTile)tilesIterator.next()).getTileNumber();

		if (tile < 10)
		    fw.write("0" + tile + " ");
		else 
		    fw.write(tile + " ");
	    }
	    
	    fw.write("\n\n");
	    
	    tilesIterator = tiles.iterator();
	    while(tilesIterator.hasNext()) {
		int tile = 
		    ((RawTile)tilesIterator.next()).getTileNumber();

		if (tile < 10) 
		    fw.write("OBJECT_FILES_0");
		else
		    fw.write("OBJECT_FILES_");

		fw.write(tile + " = " +
			 "tile" + tile + ".o ");
		
		//make sure that there is some   
		if (!(KjcOptions.magic_net || KjcOptions.decoupled || IMEMEstimation.TESTING_IMEM))
		    fw.write("sw" + tile + ".o");
		
		fw.write("\n");
	    }
	    
	    //use sam's gcc and set the parameters of the tile
	    if (KjcOptions.altcodegen) {
		fw.write
		    ("\nRGCC=/home/pkg/brave_new_linux/0225.btl.rawlib.starbuild/install/slgcc/bin/raw-gcc\n");
		fw.write("\nDMEM_PORTS  = 1\n");
		fw.write("ISSUE_WIDTH = 1\n\n");
		fw.write("EXTRA_BTL_ARGS += -issue_width $(ISSUE_WIDTH) -dmem_ports $(DMEM_PORTS)\n");
		fw.write("RGCCFLAGS += -missue_width=$(ISSUE_WIDTH) -mdmem_ports=$(DMEM_PORTS)\n");
	    }

	    fw.write("\ninclude $(COMMONDIR)/Makefile.all\n\n");
	    fw.write("clean:\n");
	    fw.write("\trm -f *.o\n");
	    fw.write("\trm -f tile*.s\n\n");
	    fw.close();
	}
	catch (Exception e) 
	    {
		System.err.println("Error writing Makefile");
		e.printStackTrace();
	    }
    }
    
    //remove all tiles mapped to joiners from the coordinate hashset *tiles*
    private static void removeJoiners(HashSet tiles) {
	Iterator it = layout.getJoiners().iterator();
	while (it.hasNext()) {
	    tiles.remove(layout.getTile((FlatNode)it.next()));
	}
    }

    private static void createBCFile(boolean hasIO, HashSet mappedTiles) throws Exception 
    {
	FileWriter fw = new FileWriter("fileio.bc");

	if (KjcOptions.magic_net) 
	    fw.write("gTurnOffNativeCompilation = 1;\n");

	fw.write("include(\"<dev/basic.bc>\");\n");

	//workaround for magic instruction support...
	if (KjcOptions.magic_net || KjcOptions.numbers > 0)
	    fw.write("include(\"<dev/magic_instruction.bc>\");\n");
	
	//let the simulation know how many tiles are mapped to 
	//filters or joiners
	fw.write("global gStreamItTilesUsed = " + layout.getTilesAssigned() + ";\n");
	fw.write("global gStreamItTiles = " + rawChip.getTotalTiles() +
		 ";\n");
	fw.write("global gStreamItUnrollFactor = " + KjcOptions.unroll + ";\n");
	fw.write("global streamit_home = getenv(\"STREAMIT_HOME\");\n");

	 //create the function to tell the simulator what tiles are mapped
	fw.write("fn mapped_tile(tileNumber) {\n");
	fw.write("if (");
	Iterator tilesIterator = mappedTiles.iterator();
	//generate the if statement with all the tile numbers of mapped tiles
	while (tilesIterator.hasNext()) {
	    fw.write("tileNumber == " + 
		     ((RawTile)tilesIterator.next()).getTileNumber());
	    if (tilesIterator.hasNext())
		fw.write(" ||\n");
	}
	fw.write(") {return 1; }\n");
	fw.write("return 0;\n");
	fw.write("}\n");
	

	//number gathering code
	if (KjcOptions.numbers > 0 && !IMEMEstimation.TESTING_IMEM) {
	    fw.write("global printsPerCycle = " + KjcOptions.numbers + ";\n");
	    fw.write("global quitAfter = " + 10 + ";\n");
	    fw.write("{\n");
	    fw.write("  local numberpath = malloc(strlen(streamit_home) + 30);\n");
	    fw.write("  sprintf(numberpath, \"%s%s\", streamit_home, \"/include/sd_numbers.bc\");\n");
	    //include the number gathering code and install the device file
	    fw.write("  include(numberpath);\n");
	    //call the number gathering initialization function
	    fw.write("  gather_numbers_init();\n");
	    fw.write("}\n");
	}
	
	fw.write
	    ("global gAUTOFLOPS = 0;\n" +
	     "fn __event_fpu_count(hms)\n" +
	     "{" +
	     "\tlocal instrDynamic = hms.instr_dynamic;\n" +
	     "\tlocal instrWord = InstrDynamic_GetInstrWord(instrDynamic);\n" +
	     "\tif (imem_instr_is_fpu(instrWord))\n" +
	     "\t{\n" +
	     "\t\tAtomicIncrement(&gAUTOFLOPS);\n" +
	     "\t}\n" +
	     "}\n\n" +
	     "EventManager_RegisterHandler(\"issued_instruction\", \"__event_fpu_count\");\n" +

             "fn count_FLOPS(steps)\n" +
             "{\n" +
             "  gAUTOFLOPS = 0;\n" +
             "  step(steps);\n" +
             "  printf(\"// **** count_FLOPS: %4d FLOPS, %4d mFLOPS\\n\",\n" +
             "         gAUTOFLOPS, (250*gAUTOFLOPS)/steps);\n" +
             "}\n" +
             "\n");
	
        if (hasIO){
	    // create preamble
	    fw.write("if (FindFunctionInSymbolHash(gSymbolTable, \"dev_data_transmitter_init\",3) == NULL)\n");
	    fw.write("include(\"<dev/data_transmitter.bc>\");\n\n");
	    
            // create the instrumentation function
            fw.write("// instrumentation code\n");
            fw.write("fn streamit_instrument(val){\n");
            fw.write("  local a;\n"); 
            fw.write("  local b;\n");
            fw.write("  Proc_GetCycleCounter(Machine_GetProc(machine,0), &a, &b);\n");
            fw.write("  //printf(\"cycleHi %X, cycleLo %X\\n\", a, b);\n");
            // use the same format string that generating a printf causes so we can use
            // the same results script;
            fw.write("  printf(\"[00: %08x%08x]: %d\\n\", a, b, val);\n");
            fw.write("}\n\n");
	    

            //create the function to write the data
            fw.write("fn dev_st_port_to_file_size(filename, size, port)\n{\n");
            fw.write("local receive_device_descriptor = hms_new();\n");
            fw.write("// open the file\n  ;");
            fw.write("receive_device_descriptor.fileName = filename;\n  ");
            fw.write("receive_device_descriptor.theFile = fopen(receive_device_descriptor.fileName,\"w\");\n");
            fw.write("verify(receive_device_descriptor.theFile != NULL, \"### Failed to open output file\");\n");
            fw.write("receive_device_descriptor.calc =\n");
            fw.write("& fn(this)\n  {\n");
            fw.write("local theFile = this.theFile;\n");
            fw.write("while (1)\n {\n");
            fw.write("     local value = this.receive();\n");
            fw.write("     fwrite(&value, size, 1, theFile);\n");
            fw.write("     streamit_instrument(value);\n");
            fw.write("     fflush(theFile);\n");
            fw.write("}\n");
            fw.write("};\n");
            fw.write("return dev_data_transmitter_init(\"st_port_to_file\", port,0,receive_device_descriptor);\n");
            fw.write("}");
	}
	//
	fw.write("\n{\n");	
	
	if (KjcOptions.magic_net)
	    fw.write("  addMagicNetwork();\n");

	if (hasIO) {
	    //generate the code for the fileReaders
	    Iterator frs = streamGraph.getFileVisitor().fileReaders.iterator();
	    while (frs.hasNext()) {
		FlatNode node = (FlatNode)frs.next();
		SIRFileReader fr = (SIRFileReader)node.contents;
		fw.write("\tdev_serial_rom_init(\"" + fr.getFileName() +
			 "\", " + getIOPort(layout.getTile(node)) + 
			 ", 1);\n");
	    }
	    //generate the code for the file writers
	    Iterator fws = streamGraph.getFileVisitor().fileWriters.iterator();
	    while (fws.hasNext()) {
		FlatNode node = (FlatNode)fws.next();
		SIRFileWriter sfw = (SIRFileWriter)node.contents;
		int size = getTypeSize(((SIRFileWriter)node.contents).getInputType());
		fw.write("\tdev_st_port_to_file_size(\"" + sfw.getFileName() +
			 "\", " + size + ", " +
			 getIOPort(layout.getTile(node)) + ");\n");
            }
	}
	
	fw.write("\n}\n");
	fw.close();
    }

    
    private static int getTypeSize(CType type) {
    if (type.equals(CStdType.Boolean))
	return 1;
    else if (type.equals(CStdType.Byte))
	return 1;
    else if (type.equals(CStdType.Integer))
	return 4;
    else if (type.equals(CStdType.Short))
	return 4;
    else if (type.equals(CStdType.Char))
	return 1;
    else if (type.equals(CStdType.Float))
	return 4;
    else if (type.equals(CStdType.Long))
	return 4;
    else
	{
	       Utils.fail("Cannot write type to file: " + type);
	}
    return 0;
}

private static int getIOPort(RawTile tile) 
{
    return rawChip.getXSize() + 
	+ tile.getY();
}


}

		
