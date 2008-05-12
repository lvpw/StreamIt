package at.dms.kjc.tilera;

import at.dms.kjc.*;
import at.dms.kjc.backendSupport.*;
import at.dms.kjc.sir.*;
import at.dms.kjc.slicegraph.*;

public class TileraBackend {
    public static Scheduler scheduler;
    public static TileraChip chip;
    public static TileraBackEndFactory backEndBits;
    
    public static void run(SIRStream str,
                           JInterfaceDeclaration[] interfaces,
                           SIRInterfaceTable[] interfaceTables,
                           SIRStructure[]structs,
                           SIRHelper[] helpers,
                           SIRGlobal global) {
	System.out.println("Entry to Tilera Backend...");
        
	setScheduler();
        chip = new TileraChip(KjcOptions.tilera, KjcOptions.tilera);     

        // The usual optimizations and transformation to slice graph
        CommonPasses commonPasses = new CommonPasses();
        // perform standard optimizations.
        commonPasses.run(str, interfaces, interfaceTables, structs, helpers, global, chip.size());
        // perform some standard cleanup on the slice graph.
        commonPasses.simplifySlices();
        // Set schedules for initialization, prime-pump (if KjcOptions.spacetime), and steady state.
        SpaceTimeScheduleAndSlicer graphSchedule = commonPasses.scheduleSlices();
        scheduler.setGraphSchedule(graphSchedule);
        // slicer contains information about the Slice graph used by dumpGraph
        Slicer slicer = commonPasses.getSlicer();

        scheduler.runLayout();
        backEndBits = new TileraBackEndFactory(chip);
        backEndBits.setLayout(scheduler);
        
        //now convert to Kopi code plus channels.  
        backEndBits.getBackEndMain().run(graphSchedule, backEndBits);
                
	System.exit(0);
    }
    
    /**
     * Set the scheduler field to the correct leaf class that implements a scheduling 
     * policy.
     */
    private static void setScheduler() {
        if (KjcOptions.partitioner.equals("tmd")) {
            scheduler = new TMD();
        } else if (KjcOptions.partitioner.equals("smd")) {
            scheduler = new SMD();
        } else {
            System.err.println("Unknown Scheduler Type!");
            System.exit(1);
        }
    }
}
