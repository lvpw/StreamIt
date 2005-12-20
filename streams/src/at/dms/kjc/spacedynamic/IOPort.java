package at.dms.kjc.spacedynamic;

import at.dms.util.Utils;
import at.dms.kjc.*;

/*******************************************************************************
 * This class represents an IOPort on the chip in which devices can be connected
 * to, it extends ComputeNode for easy routing.
 ******************************************************************************/
public class IOPort extends ComputeNode {
    // the port number of this io device
    private int port;

    // the device attached to this port
    private IODevice device;

    public IOPort(RawChip chip, int index) {
        super(chip);
        device = null;
        port = index;
        assert !(port < 0 || port >= (2 * rawChip.getXSize() + 2 * rawChip
                .getYSize())) : "invalid port number for io device";
        this.port = port;
        // set the x and y coordinates
        if (port >= 0 && port < rawChip.getXSize()) {
            Y = -1;
            X = port;
        } else if (port >= rawChip.getXSize()
                && port < (rawChip.getXSize() + rawChip.getYSize())) {
            X = rawChip.getXSize();
            Y = port - rawChip.getXSize();
        } else if (port >= (rawChip.getXSize() + rawChip.getYSize())
                && port < (2 * rawChip.getXSize() + rawChip.getYSize())) {
            X = (rawChip.getXSize() - 1)
                    - (port - (rawChip.getXSize() + rawChip.getYSize()));
            Y = rawChip.getYSize();
        } else if (port >= (2 * rawChip.getXSize() + rawChip.getYSize())
                && port < (2 * rawChip.getXSize() + 2 * rawChip.getYSize())) {
            X = -1;
            Y = (rawChip.getYSize() - 1)
                    - (port - (2 * rawChip.getXSize() + rawChip.getYSize()));
        }
        getNeighboringTile().addIOPort(this);
    }

    /** should only be called by RawChip * */
    public void addDevice(IODevice device) {
        this.device = device;
    }

    public IODevice getDevice() {
        return device;
    }

    public boolean hasDevice() {
        return device != null;
    }

    public void removeDevice() {
        device = null;
    }

    public int getPortNumber() {
        return port;
    }

    /** Get the raw tile that this IOPort is connected to */
    public RawTile getNeighboringTile() {
        if (Y == -1)
            return rawChip.getTile(X, 0);
        if (X == -1)
            return rawChip.getTile(0, Y);
        if (X == rawChip.getXSize())
            return rawChip.getTile(X - 1, Y);
        if (Y == rawChip.getYSize())
            return rawChip.getTile(X, Y - 1);
        assert false : "invalid x, y coordinate for streaming dram";
        return null;
    }
    
    /**
     * Get the direction from the neighboring tile to this 
     * 
     * @return 2 = west, 3 = south, 4 = east, 5 = north
     */
    public int getDirectionFromTile() {
        if (Y == -1)
            return 5;
        if (X == -1)
            return 2;
        if (X == rawChip.getXSize())
            return 4;
        if (Y == rawChip.getYSize())
            return 3;
        assert false : "invalid x, y coordinate for streaming dram";
        return -1;
    }
}
