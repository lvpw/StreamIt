// $Header: /afs/csail.mit.edu/group/commit/reps/projects/streamit/cvsroot/streams/src/at/dms/kjc/cluster/GenerateMakefile.java,v 1.7 2006-07-21 19:42:36 dimock Exp $
package at.dms.kjc.cluster;

import java.io.FileWriter;

import at.dms.kjc.KjcOptions;
import at.dms.kjc.common.CodegenPrintWriter;
import at.dms.kjc.sir.SIRHelper;




/**
 * Generate Makefile.cluster for cluster back end.
 * 
 * <p>Output program is "a.out" unless --output switch provides name.</p>
 *
 * @author Janis
 *
 */
public class GenerateMakefile {
   /**
    *  Generate Makefile.cluster for cluster back end.
    *  
    *  <p>Gets number of threads for a NodeEnumerator.
    *  Gets helper functions from parameter: if a helper function is
    *  native, assumes that there is already a .o file for it.
    *  </p>
    * @param helpers Helper functions
    */
    public static void generateMakefile(SIRHelper[] helpers) {

        int threadNumber = NodeEnumerator.getNumberOfNodes();

        CodegenPrintWriter p = new CodegenPrintWriter();
        String executablename = KjcOptions.output == null? "a.out": KjcOptions.output;
        
        p.newLine();
        p.print("LIB_CLUSTER = $(STREAMIT_HOME)/library/cluster\n");

        p.newLine();    
        //p.print("CC = gcc34\n"); // gcc
        p.print("CCFLAGS = -O3\n");

        p.newline();
        p.println("# Unsupported target machines");
        p.print("CC_IA64 = ecc\n");
        p.print("CC_ARM = /u/janiss/bin/arm343 #arm-linux-gcc\n");
        p.print("CCFLAGS_IA64 = -O3\n");
        p.print("CCFLAGS_ARM = -O3\n");

        p.newLine();
        p.println("NAMES = \\");
    
        {
            int i;
            for (i = 0; i < threadNumber - 1; i++) {
                p.print("\tthread"+i+" \\");
                p.newLine();
            }
            p.print("\tthread"+i);
            p.newLine();
        }

        p.newLine();

        p.print("SOURCES = $(NAMES:%=%.cpp)\n");
        p.print("OBJS = $(NAMES:%=%.o)\n");

        p.newLine();    
        p.print("OBJS_IA64 = $(NAMES:%=%_ia64.o)\n");
        p.print("OBJS_ARM = $(NAMES:%=%_arm.o)\n");

        p.newLine();

        
        p.println("all: " + executablename);
        p.newLine();


        p.print("clean:\n");
        p.println("\trm -f master*.o fusion*.o global*.o thread*.o combined_threads.o" + executablename);
        p.newLine();

        p.print("combined_threads.cpp : ");
        if (KjcOptions.standalone) {
            p.print("fusion.cpp ");
        } else {
            p.print("master.cpp global.cpp ");
        }
        p.println(" ${SOURCES}");
        p.println("\t@${STREAMIT_HOME}/misc/concat_cluster_threads_cpp.pl $^");
        p.newline();
        
//        if (KjcOptions.standalone) {
//            p.print(executablename + ": fusion.o ");
//        } else {
//            p.print(executablename + ": master.o global.o ");
//        }
        p.print(executablename + " : combined_threads.o ");
        for (int y = 0; y < helpers.length; y++) {
            if (helpers[y].isNative()) {
                p.print(helpers[y].getIdent() + ".o ");
            }
        }
        //p.print("$(OBJS)\);
        p.newline();
        // link against FFTW if we need an FFT library
        String fftLib = (at.dms.kjc.sir.linear.frequency.LEETFrequencyReplacer.didTransform ?
                         " -lsrfftw -lsfftw" : "");
        p.print("\t$(CXX) $(CCFLAGS) -o $@ $^ -L$(LIB_CLUSTER) -lpthread -lcluster -lstdc++" + fftLib + "\n");
        p.newLine();
        
        // =============== %.o : %.cpp
    
        p.print("%.o: %.cpp fusion.h cluster.h global.h\n");
        p.print("\t$(CXX) $(CCFLAGS) -I$(LIB_CLUSTER) -c -o $@ $<\n");
        p.newLine();

        p.newLine();

        
        p.println("# Unsupported target machines");
        p.println("ia64: " + executablename + "_ia64");
        p.newLine();
        p.println("arm: " + executablename + "_arm");
        p.newLine();

         if (KjcOptions.standalone) {
            p.print(executablename + "_ia64: fusion_ia64.o ");
        } else {
            p.print(executablename + "_ia64: master_ia64.o ");
        }
        p.println("$(OBJS_IA64)");
        p.print("\t$(CC_IA64) $(CCFLAGS_IA64) -o $@ $^ -L$(LIB_CLUSTER) -lpthread -lcluster_ia64" + fftLib + "\n");
        p.newLine();

//        // =============== %_ia64.o : %.cpp

        p.print("%_ia64.o: %.cpp fusion.h cluster.h\n");
        p.print("\t$(CC_IA64) $(CCFLAGS_IA64) -I$(LIB_CLUSTER) -c -o $@ $<\n");
        p.newLine();



   
        // =============== fusion_arm

        p.print(executablename + "_arm: fusion_arm.o $(OBJS_ARM)\n");
        p.print("\tar r objects_arm.a $^\n");
        p.print("\tranlib objects_arm.a\n");
        p.print("\t$(CC_ARM) $(CCFLAGS_ARM) -o $@ objects_arm.a -L$(LIB_CLUSTER) -lstdc++ -lm -lcluster_arm" + fftLib + " #-lpthread\n");

        p.newLine();

        // =============== %_arm.o : %.cpp

        p.print("%_arm.o: %.cpp fusion.h cluster.h\n");
        p.print("\t$(CC_ARM) $(CCFLAGS_ARM) -I$(LIB_CLUSTER) -c -o $@ $<\n");
        p.newLine();



        try {
            FileWriter fw = new FileWriter("Makefile.cluster");
            fw.write(p.getString());
            fw.close();
        }
        catch (Exception e) {
            System.err.println("Unable to write Makefile");
        }   
    }
}
