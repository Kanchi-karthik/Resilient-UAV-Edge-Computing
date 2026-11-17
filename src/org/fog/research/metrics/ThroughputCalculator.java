package org.fog.research.metrics;

import java.util.List;
import org.fog.research.environment.ResearchTask;

public class ThroughputCalculator {

    public static double calculateTotalThroughputBps(List<ResearchTask> completedTasks, double totalTimeSec) {
        if (totalTimeSec <= 0 || completedTasks.isEmpty()) {
            return 0.0;
        }
        long totalVolumeBits = 0;
        for (ResearchTask task : completedTasks) {
            totalVolumeBits += task.getInputBits() + task.getOutputBits();
        }
        return (double) totalVolumeBits / totalTimeSec;
    }
}
