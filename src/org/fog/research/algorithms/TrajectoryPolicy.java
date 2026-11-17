package org.fog.research.algorithms;

import java.util.List;
import java.util.Map;
import org.fog.research.utils.Action;
import org.fog.research.environment.UAVEntity;
import org.fog.research.environment.MobileDevice;

public interface TrajectoryPolicy {
    Map<Integer, Action> selectActions(
        List<UAVEntity> uavs, 
        List<MobileDevice> mds, 
        double currentTime,
        double areaX,
        double areaY
    );
}
