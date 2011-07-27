package at.dms.kjc.slir;

import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;

public class LevelizeSliceGraph {
    private Filter[] topSlices;
    private HashMap<Filter, Integer> levelMap;
    private Filter[][] levels;
    
    public LevelizeSliceGraph(Filter[] topSlices) {
        this.topSlices = topSlices;
        levelMap = new HashMap<Filter, Integer>();
        calcLevels();
    }
    
    public Filter[][] getLevels() {
        return levels;
    }
    
    /**
     * Return the level this slice occupies.
     */
    public int getLevel(Filter slice) {
        return levelMap.get(slice);
    }
    
    /**
     * Return the size of the level for this slice.  The total number of tiles
     * occupied by the level.
     */
    public int levelSize(Filter slice) {
        return levels[levelMap.get(slice)].length;
    }
    
    private void calcLevels() {
        LinkedList<LinkedList<Filter>> levelsList = new LinkedList<LinkedList<Filter>>();
        HashSet<Filter> visited = new HashSet<Filter>();
        LinkedList<Filter> queue = new LinkedList<Filter>();
        
        //add the top slices and set their level
        for (int i = 0; i < topSlices.length; i++) {
            queue.add(topSlices[i]);
            levelMap.put(topSlices[i], 0);
        }
        
        while (!queue.isEmpty()) {
            Filter slice = queue.removeFirst();
            if (!visited.contains(slice)) {
                visited.add(slice);
                for (Edge destEdge : slice.getTail().getDestSet(SchedulingPhase.STEADY)) {
                    Filter current = destEdge.getDest().getParent();
                    if (!visited.contains(current)) {
                        // only add if all sources has been visited
                        boolean addMe = true;
                        int maxParentLevel = 0;
                        for (Edge sourceEdge : current.getHead().getSourceSet(SchedulingPhase.STEADY)) {
                            if (!visited.contains(sourceEdge.getSrc().getParent())) {
                                addMe = false;
                                break;
                            }
                            //remember the max parent level
                            if (levelMap.get(sourceEdge.getSrc().getParent()).intValue() > maxParentLevel)
                                maxParentLevel = levelMap.get(sourceEdge.getSrc().getParent()).intValue();
                        }   
                        if (addMe) {
                            levelMap.put(current, maxParentLevel + 1);
                            queue.add(current);
                        }
                    }
                }
                //add the slice to the appropriate level
                int sliceLevel = levelMap.get(slice);
                if (levelsList.size() <= sliceLevel) {
                    int levelsToAdd = sliceLevel - levelsList.size() + 1;
                    for (int i = 0; i < levelsToAdd; i++);
                        levelsList.add(new LinkedList<Filter>());
                }
                levelsList.get(sliceLevel).add(slice);
            }
        }
        
        //set the multi-dim array for the levels from the linkedlist of lists 
        levels = new Filter[levelsList.size()][];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = new Filter[levelsList.get(i).size()];
            for (int j = 0; j < levels[i].length; j++) 
                levels[i][j] = levelsList.get(i).get(j);
        }
    }
    
}
