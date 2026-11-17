package org.fog.research.state;

import java.util.List;
import org.fog.research.utils.Vector2D;
import org.fog.research.utils.Vector3D;

public class ObservationState {
    private final int uavId;
    private final Vector3D uavPosition;
    private final double remainingEnergy;
    private final int queueSize;
    private final double currentLoadMips;
    private final List<Integer> observedMdIds;
    private final List<Vector2D> observedMdPositions;

    public ObservationState(int uavId, Vector3D uavPosition, double remainingEnergy, 
                            int queueSize, double currentLoadMips, 
                            List<Integer> observedMdIds, List<Vector2D> observedMdPositions) {
        this.uavId = uavId;
        this.uavPosition = uavPosition;
        this.remainingEnergy = remainingEnergy;
        this.queueSize = queueSize;
        this.currentLoadMips = currentLoadMips;
        this.observedMdIds = observedMdIds;
        this.observedMdPositions = observedMdPositions;
    }

    public int getUavId() {
        return uavId;
    }

    public Vector3D getUavPosition() {
        return uavPosition;
    }

    public double getRemainingEnergy() {
        return remainingEnergy;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public double getCurrentLoadMips() {
        return currentLoadMips;
    }

    public List<Integer> getObservedMdIds() {
        return observedMdIds;
    }

    public List<Vector2D> getObservedMdPositions() {
        return observedMdPositions;
    }
}
