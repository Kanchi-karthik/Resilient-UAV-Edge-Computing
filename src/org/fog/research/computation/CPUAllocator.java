package org.fog.research.computation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;

public class CPUAllocator {

    public static Map<Integer, Double> allocateCPU(UAVEntity uav, List<ResearchTask> activeTasks) {
        Map<Integer, Double> allocations = new HashMap<>();
        if (activeTasks.isEmpty()) {
            return allocations;
        }
        
        // Eq. (16c): Sum of allocated CPU frequencies must not exceed total CPU capacity
        double totalMips = uav.getHost().getTotalMips();
        double share = totalMips / activeTasks.size(); // Fair share allocation
        
        for (ResearchTask task : activeTasks) {
            // Eq. (16d): Each allocation is non-negative
            allocations.put(task.getTaskId(), share);
        }
        return allocations;
    }
}
