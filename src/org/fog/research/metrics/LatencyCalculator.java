package org.fog.research.metrics;

import java.util.List;
import org.fog.research.environment.ResearchTask;

public class LatencyCalculator {

    public static double calculateAverageLatencySec(List<ResearchTask> completedTasks) {
        if (completedTasks.isEmpty()) {
            return 0.0;
        }
        double totalLatency = 0.0;
        for (ResearchTask task : completedTasks) {
            totalLatency += (task.getFinishTime() - task.getGenerationTime());
        }
        return totalLatency / completedTasks.size();
    }

    public static double calculateAverageDelaySec(List<ResearchTask> completedTasks) {
        if (completedTasks.isEmpty()) {
            return 0.0;
        }
        double totalDelay = 0.0;
        for (ResearchTask task : completedTasks) {
            double latency = task.getFinishTime() - task.getGenerationTime();
            // Actual CPU time spent processing
            double executionDuration = task.getActualCPUTime();
            double delay = Math.max(0.0, latency - executionDuration);
            totalDelay += delay;
        }
        return totalDelay / completedTasks.size();
    }
}
