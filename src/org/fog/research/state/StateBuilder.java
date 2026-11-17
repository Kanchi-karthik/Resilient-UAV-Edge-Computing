package org.fog.research.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fog.research.environment.MobileDevice;
import org.fog.research.environment.UAVEntity;
import org.fog.research.utils.Vector2D;
import org.fog.research.utils.Vector3D;

public class StateBuilder {

    public static ObservationState buildObservation(UAVEntity uav, List<MobileDevice> allMds) {
        Vector3D uavPos = uav.getPosition();
        double radius = uav.getCoverageRadius();
        
        List<Integer> observedIds = new ArrayList<>();
        List<Vector2D> observedPositions = new ArrayList<>();
        
        for (MobileDevice md : allMds) {
            Vector2D mdPos = md.getPosition();
            double dist = uavPos.distanceTo2D(mdPos.getX(), mdPos.getY());
            if (dist <= radius) {
                observedIds.add(md.getId());
                observedPositions.add(mdPos);
            }
        }
        
        double currentLoadMips = uav.getHost().getUtilizationOfCpu() * uav.getHost().getTotalMips();
        
        return new ObservationState(
            uav.getId(),
            uavPos,
            uav.getRemainingEnergy(),
            uav.getTaskQueue().size(),
            currentLoadMips,
            observedIds,
            observedPositions
        );
    }

    public static GlobalState buildGlobalState(List<UAVEntity> uavs, List<MobileDevice> mds, double currentTime) {
        Map<Integer, Vector3D> uavPositions = new HashMap<>();
        Map<Integer, Double> uavEnergies = new HashMap<>();
        Map<Integer, Integer> uavQueueSizes = new HashMap<>();
        Map<Integer, Vector2D> mdPositions = new HashMap<>();

        for (UAVEntity uav : uavs) {
            uavPositions.put(uav.getId(), uav.getPosition());
            uavEnergies.put(uav.getId(), uav.getRemainingEnergy());
            uavQueueSizes.put(uav.getId(), uav.getTaskQueue().size());
        }

        for (MobileDevice md : mds) {
            mdPositions.put(md.getId(), md.getPosition());
        }

        return new GlobalState(uavPositions, uavEnergies, uavQueueSizes, mdPositions, currentTime);
    }
}
