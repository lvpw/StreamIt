package at.dms.kjc.raw;

import java.util.HashMap;

public abstract class Simulator {
    public static HashMap initSchedules;
    public static HashMap steadySchedules;
    
    public static HashMap initJoinerCode;
    public static HashMap steadyJoinerCode;

    public abstract void simulate(FlatNode top);
}
