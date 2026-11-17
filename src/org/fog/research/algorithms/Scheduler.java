package org.fog.research.algorithms;

import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;

public interface Scheduler {
    Map<Integer, Integer> schedule(
        List<ResearchTask> tasks,
        List<UAVEntity> uavs,
        Map<Integer, Map<Integer, Double>> uploadRates,
        Map<Integer, Map<Integer, Double>> downloadRates,
        double currentTime
    );
}
