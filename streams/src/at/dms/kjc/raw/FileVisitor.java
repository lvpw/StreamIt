package at.dms.kjc.raw;

import java.util.HashSet;
import java.util.Iterator;

import at.dms.kjc.flatgraph.FlatNode;
import at.dms.kjc.flatgraph.FlatVisitor;
import at.dms.kjc.sir.SIRFileReader;
import at.dms.kjc.sir.SIRFileWriter;


public class FileVisitor implements FlatVisitor {
    //true if the graph contains a fileReader
    public static boolean foundReader;
    //a hashset containing the flatnodes of the fileReaders
    public static HashSet<Object> fileReaders;
    //true if the graph contains a fileWriter
    public static boolean foundWriter;
    //a hashset containing the flatnodes of the fileWriters
    public static HashSet<Object> fileWriters;
    //a hashset containing the flatnodes of all the file manipulators
    //(both readers and writers
    public static HashSet<Object> fileNodes;

    public static void init(FlatNode top) {
        FileVisitor frv = new FileVisitor();
        top.accept(frv, new HashSet<FlatNode>(), false);
        //add everything to the fileNodes hashset
        RawBackend.addAll(fileNodes, fileReaders);
        RawBackend.addAll(fileNodes, fileWriters);
    }
    
    public FileVisitor() 
    {
        foundReader = false;
        foundWriter = false;
        fileReaders = new HashSet<Object>();
        fileWriters = new HashSet<Object>();
        fileNodes = new HashSet<Object>();
    }
    
    @Override
	public void visitNode (FlatNode node) 
    {
        if (node.contents instanceof SIRFileReader) {
            fileReaders.add(node);
            foundReader = true;
        }
        else if (node.contents instanceof SIRFileWriter) {
            foundWriter = true;
            fileWriters.add(node);
        }
    }

    public static boolean connectedToFR(Coordinate tile) {
        Iterator<Object> frs = fileReaders.iterator();
        while (frs.hasNext()) {
            if (Layout.areNeighbors(tile, Layout.getTile((FlatNode)frs.next()))) 
                return true;
        }
        return false;
    }
}
