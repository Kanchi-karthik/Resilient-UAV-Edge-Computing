package org.fog.research.reward;

import java.util.List;
import java.util.Map;
import org.fog.research.environment.ResearchTask;
import org.fog.research.utils.TaskStatus;

public class RewardCalculator {

    // Eq. (18): r = \sum \Omega_{u_k}
    // Calculates the sum of throughputs of all active tasks in this time slot.
    public static double calculateSlotReward(List<ResearchTask> activeTasks, Map<Integer, Double> taskThroughputRatesBps) {
        double totalThroughputBps = 0.0;
        for (ResearchTask task : activeTasks) {
            if (task.getResearchTaskStatus() == TaskStatus.TRANSMITTING || 
                task.getResearchTaskStatus() == TaskStatus.EXECUTING) {
                double rate = taskThroughputRatesBps.getOrDefault(task.getTaskId(), 0.0);
                totalThroughputBps += rate;
            }
        }
        return totalThroughputBps;
    }

    // Calculates reward based directly on UAV associations and throughput map
    public static double calculateUavThroughputReward(Map<Integer, Double> uavThroughputs) {
        double totalThroughput = 0.0;
        for (Double tp : uavThroughputs.values()) {
            totalThroughput += tp;
        }
        return totalThroughput;
    }
}
