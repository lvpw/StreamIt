package at.dms.kjc.sir.lowering.partition;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Map;

import at.dms.kjc.KjcOptions;
import at.dms.kjc.sir.SIRContainer;
import at.dms.kjc.sir.SIRFilter;
import at.dms.kjc.sir.SIRStream;

/**
 * A wrapper for a linked list to save ourself a lot of casting with
 * work entries.
 */
public class WorkList extends java.util.LinkedList {

    public WorkList(Collection c) {
        super(c);
    }

    /**
     * Gets total work at position <pre>i</pre>.
     */
    public long getWork(int i) {
        return ((WorkInfo)((Map.Entry)super.get(i)).getValue()).getTotalWork();
    }

    /**
     * Gets filter at position <pre>i</pre>.
     */
    public SIRFilter getFilter(int i) {
        return (SIRFilter)((Map.Entry)super.get(i)).getKey();
    }

    /**
     * Gets container at position <pre>i</pre>.
     */
    public SIRContainer getContainer(int i) {
        return (SIRContainer)((Map.Entry)super.get(i)).getKey();
    }

    /**
     * Write the contents of this to filename <pre>filename</pre>.
     */
    public void writeToFile(String filename) {
        PrintStream out = null;
        try {
            out = new PrintStream(new FileOutputStream(filename));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // first get max-length string so we justify the work column
        int max = 0;
        for (int i=size()-1; i>=0; i--) {
            SIRStream str = (SIRStream)((Map.Entry)super.get(i)).getKey();
            if (max<str.getIdent().length()) {
                max = str.getIdent().length();
            }
        }
        // then print, justified
        String title1 = "Filter";
        out.print(title1);
        for (int i=title1.length(); i<max; i++) {
            out.print(" ");
        }
        out.print("\t" + "Reps" + "\t" + "Unit Work (Estimated)" + "\t" + "Total Work (Estimated)");
        if (KjcOptions.simulatework) {
            out.println("\t" + "Unit Work (Measured)" + "\t" + "Total Work (Measured)" + "\t" + "Estimated/Measured");
        } else {
            out.println();
        }
        for (int i=size()-1; i>=0; i--) {
            SIRStream str = (SIRStream)((Map.Entry)super.get(i)).getKey();
            WorkInfo workInfo = (WorkInfo)((Map.Entry)super.get(i)).getValue();
            out.print(str.getIdent());
            for (int j=str.getIdent().length(); j<max; j++) {
                out.print(" ");
            }
            out.print("\t" + workInfo.getReps() + "\t" + workInfo.getInexactUnitWork() + "\t" + (workInfo.getReps()*workInfo.getInexactUnitWork()));
            if (KjcOptions.simulatework) {
                out.println("\t" + workInfo.getUnitWork() + "\t" + workInfo.getTotalWork() +
                            "\t" + (((float)workInfo.getInexactUnitWork())/((float)workInfo.getUnitWork())) );
            } else {
                out.println();
            }
        }
        out.close();
    }
}
