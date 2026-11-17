package org.fog.research.state;

import java.util.Map;
import org.fog.research.utils.Vector2D;
import org.fog.research.utils.Vector3D;

public class GlobalState {
    private final Map<Integer, Vector3D> uavPositions;
    private final Map<Integer, Double> uavEnergies;
    private final Map<Integer, Integer> uavQueueSizes;
    private final Map<Integer, Vector2D> mdPositions;
    private final double timestamp;

    public GlobalState(Map<Integer, Vector3D> uavPositions, Map<Integer, Double> uavEnergies, 
                       Map<Integer, Integer> uavQueueSizes, Map<Integer, Vector2D> mdPositions, 
                       double timestamp) {
        this.uavPositions = uavPositions;
        this.uavEnergies = uavEnergies;
        this.uavQueueSizes = uavQueueSizes;
        this.mdPositions = mdPositions;
        this.timestamp = timestamp;
    }

    public Map<Integer, Vector3D> getUavPositions() {
        return uavPositions;
    }

    public Map<Integer, Double> getUavEnergies() {
        return uavEnergies;
    }

    public Map<Integer, Integer> getUavQueueSizes() {
        return uavQueueSizes;
    }

    public Map<Integer, Vector2D> getMdPositions() {
        return mdPositions;
    }

    public double getTimestamp() {
        return timestamp;
    }
}
