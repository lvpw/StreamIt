package at.dms.kjc.spacetime;

import java.util.Vector;
import at.dms.kjc.spacetime.switchIR.*;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class SwitchCodeStore {
    protected RawTile parent;
    private Vector steadySwitchIns;
    private Vector initSwitchIns;
    private Vector commAddrIns;
    private static final String LABEL_PREFIX="L_";
    private static int labelId=0;

    public SwitchCodeStore(RawTile parent) {
        this.parent = parent;
        initSwitchIns = new Vector();
        steadySwitchIns = new Vector();
        commAddrIns = new Vector();
    }

    public void appendCommAddrIns(SwitchIns ins) 
    {
        parent.setSwitches();
        commAddrIns.add(ins);
    }

    public void appendIns(SwitchIns ins, boolean init) {
        //this tile has switch code
        parent.setSwitches();
        if (init) 
            initSwitchIns.add(ins);
        else
            steadySwitchIns.add(ins);
    }
    
    public void appendIns(int i, SwitchIns ins, boolean init) 
    {
        //this tile has switch code
        parent.setSwitches();
        if (init) 
            initSwitchIns.add(i, ins);
        else
            steadySwitchIns.add(i, ins);
    }
    

    public int size(boolean init) {
        return init ? initSwitchIns.size() : steadySwitchIns.size();
    }

    public SwitchIns getIns(int i, boolean init) {
        return (init) ? (SwitchIns)initSwitchIns.get(i) : 
            (SwitchIns)steadySwitchIns.get(i);
    }

    public Label getFreshLabel() {
        return new Label(LABEL_PREFIX+(labelId++));
    }
    
    public SwitchIns getCommAddrIns(int i) 
    {
        return (SwitchIns)commAddrIns.get(i);
    }
    
    public int commAddrSize()
    {
        return commAddrIns.size();
    }
    
    /*
      public static void generateSwitchCode(ComputeNode source, ComputeNode[] dests,
      int stage, String comment)
      {
      if (stage == 0) 
      source.getRawChip().getTile(i).getSwitchCode().appendCommAddrIns(new Comment(comment));
      if (stage == 1) 
      source.getRawChip().getTile(i).getSwitchCode().appendIns(new Comment(comment), true);
      if (stage == 2)
      source.getRawChip().getTile(i).getSwitchCode().appendIns(new Comment(comment), false);
      generateSwitchCode(source, dests, stage);
      }
    */


    //create the header of a switch loop on all the tiles in <tiles> and 
    //first send mult from the compute processor to the switch
    //returns map of tile->Label
    public static HashMap switchLoopHeader(HashSet tiles, int mult, boolean init, boolean primePump) 
    {
        assert mult > 1;
        HashMap labels = new HashMap();
        Iterator it = tiles.iterator();
        while (it.hasNext()) {
            RawTile tile = (RawTile)it.next();
            Util.sendConstFromTileToSwitch(tile, mult - 1, init, primePump, SwitchReg.R2);
            //add the label
            Label label = new Label();
            tile.getSwitchCode().appendIns(label, (init || primePump)); 
            //remember the label
            labels.put(tile, label);
        }
        return labels;
    }
    
    //create the trailer of the loop for all tiles in the key set of <lables>
    //labels maps RawTile->label
    public static void switchLoopTrailer(HashMap labels, boolean init, boolean primePump) 
    {
        Iterator tiles = labels.keySet().iterator();
        while (tiles.hasNext()) {
            RawTile tile = (RawTile)tiles.next();
            Label label = (Label)labels.get(tile);
            //add the branch back
            BnezdIns branch = new BnezdIns(SwitchReg.R2, SwitchReg.R2, 
                                           label.getLabel());
            tile.getSwitchCode().appendIns(branch, (init || primePump));
        }
    }
    
    
    //return a list of all the raw tiles used in routing from source to dests
    public static HashSet getTilesInRoutes(ComputeNode source, ComputeNode[] dests) 
    {
        HashSet tiles = new HashSet();

        for (int i = 0; i < dests.length; i++) {
            ComputeNode dest = dests[i];

            LinkedList route = Router.getRoute(source, dest);

            Iterator it = route.iterator();
            while (it.hasNext()) {
                ComputeNode current = (ComputeNode)it.next();
                if (current instanceof RawTile) {
                    tiles.add(current);
                }
            }
        }
        return tiles;
    }
    

    /**
       give a source and an array of dests, generate the code to 
       route an item from the sourc to the dests and place the
       place the switch instruction in the appropriate stage's instruction
       vector based on the <stage> argument:
       0: commaddr
       1: init / prime pump
       2: steady stage
    **/
    public static void generateSwitchCode(ComputeNode source, ComputeNode[] dests,
                                          int stage)
    {
        RouteIns[] ins = new RouteIns[source.getRawChip().getXSize() *
                                      source.getRawChip().getYSize()];
    
        for (int i = 0; i < dests.length; i++) {
            ComputeNode dest = dests[i];
        
            LinkedList route = Router.getRoute(source, dest);
            //append the dest again to the end of route 
            //so we can place the item in the processor queue
            route.add(dest);
            Iterator it = route.iterator();
            if (!it.hasNext()) {
                System.err.println("Warning sending item to itself");
                continue;
            }
        
            ComputeNode prev = (ComputeNode)it.next();
            ComputeNode current = prev;
            ComputeNode next;
        
            while (it.hasNext()) {
                next = (ComputeNode)it.next();
                SpaceTimeBackend.println("    Route on " + current + ": " + prev + "->" + next);
                //only add the instructions to the raw tile
                if (current instanceof RawTile) {
                    //create the route instruction if it does not exist
                    if (ins[((RawTile)current).getTileNumber()] == null)
                        ins[((RawTile)current).getTileNumber()] = new RouteIns((RawTile)current);
                    //add this route to it
                    //RouteIns will take care of the duplicate routes 
                    ins[((RawTile)current).getTileNumber()].addRoute(prev, next);
                }
                prev = current;
                current = next;
            }
        }
        //add the non-null instructions
        for (int i = 0; i < ins.length; i++) {
            if (ins[i] != null) { 
                if (stage == 0) 
                    source.getRawChip().getTile(i).getSwitchCode().appendCommAddrIns(ins[i]);
                if (stage == 1) 
                    source.getRawChip().getTile(i).getSwitchCode().appendIns(ins[i], true);
                if (stage == 2)
                    source.getRawChip().getTile(i).getSwitchCode().appendIns(ins[i], false);
            }
        }
    }
    
    /**
     * Generate code on the switch neigboring <dev> to disregard the input from
     * the dev for <words> words.
     * 
     * @param dev
     * @param words
     * @param init
     */
    public static void disregardIncoming(IODevice dev, int words, boolean init) 
    {
        assert words < RawChip.cacheLineWords : "Should not align more than cache-line size";
        //get the neighboring tile 
        RawTile neighbor = dev.getNeighboringTile();
        //generate instructions to disregard the items and place 
        //them in the right vector
        for (int i = 0; i < words; i++) {
            MoveIns ins = new MoveIns(SwitchReg.R1,
                                      SwitchIPort.getIPort(neighbor.getRawChip().getDirection(neighbor, dev)));
            neighbor.getSwitchCode().appendIns(ins, init);
        }
    }
    
    /**
     * Generate code on the switch neighboring <dev> to send dummy values to <dev> for
     * <words> words. 
     * 
     * @param dev
     * @param words
     * @param init
     */
    public static void dummyOutgoing(IODevice dev, int words, boolean init) 
    {
        assert words < RawChip.cacheLineWords : "Should not align more than cache-line size";
        //get the neighboring tile 
        RawTile neighbor = dev.getNeighboringTile();
        for (int i = 0; i < words; i++) {
            RouteIns route = new RouteIns(neighbor);
            route.addRoute(SwitchReg.R1 , dev);
            neighbor.getSwitchCode().appendIns(route, init);
        }
    }
    

    public void appendComment(boolean init, String str) 
    {
        appendIns(new Comment(str), init);
    }
    
    
    /* 
       public void addCommAddrRoute(RawTile dest) 
       {
       LinkedList route = Router.getRoute(parent, dest);
       //append the dest again to the end of route 
       //so we can place the item in the processor queue
       route.add(dest);
    
       Iterator it = route.iterator();
    
       if (!it.hasNext()) {
       System.err.println("Warning sending item to itself");
       return;
       }
    
       RawTile prev = (RawTile)it.next();
       RawTile current = prev;
       RawTile next;
    
       while (it.hasNext()) {
       next = (RawTile)it.next();
       //generate the route on 
       RouteIns ins = new RouteIns(current);
       ins.addRoute(prev, next);
       current.getSwitchCode().appendCommAddrIns(ins);
        
       prev = current;
       current = next;
       }
       }*/
}
