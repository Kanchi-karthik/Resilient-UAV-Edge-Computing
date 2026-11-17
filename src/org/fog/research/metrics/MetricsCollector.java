package org.fog.research.metrics;

import java.util.ArrayList;
import java.util.List;
import org.fog.research.environment.ResearchTask;
import org.fog.research.environment.UAVEntity;
import org.fog.research.utils.TaskStatus;

public class MetricsCollector {
    private final List<ResearchTask> allTasks;

    public MetricsCollector() {
        this.allTasks = new ArrayList<>();
    }

    public void registerTask(ResearchTask task) {
        allTasks.add(task);
    }

    public List<ResearchTask> getAllTasks() {
        return allTasks;
    }

    public List<ResearchTask> getCompletedTasks() {
        List<ResearchTask> completed = new ArrayList<>();
        for (ResearchTask task : allTasks) {
            if (task.getResearchTaskStatus() == TaskStatus.COMPLETED) {
                completed.add(task);
            }
        }
        return completed;
    }

    public SimulationMetrics collect(double simulationTimeSec, double energyConsumed, double batteryLeftPct, 
                                     int failedCount, int queuedCount, int executingCount,
                                     List<UAVEntity> uavs, double avgCoverage, double avgCpuLoad) {
        List<ResearchTask> completed = getCompletedTasks();
        
        double throughput = ThroughputCalculator.calculateTotalThroughputBps(completed, simulationTimeSec);
        double avgLatency = LatencyCalculator.calculateAverageLatencySec(completed);
        double avgDelay = LatencyCalculator.calculateAverageDelaySec(completed);
        double successRate = CompletedTasksCounter.calculateSuccessRate(allTasks);
        
        // 1. Jain's Fairness Index for Load Balancing
        double sum = 0.0;
        double sumSq = 0.0;
        for (UAVEntity uav : uavs) {
            int count = 0;
            for (ResearchTask t : completed) {
                if (t.getAssignedUavId() == uav.getId()) {
                    count++;
                }
            }
            sum += count;
            sumSq += count * count;
        }
        double loadBalancingIndex = (sumSq > 0.0) ? (sum * sum) / (uavs.size() * sumSq) : 1.0;

        // 2. Derived ratios
        double energyPerCompletedTask = completed.size() > 0 ? energyConsumed / completed.size() : 0.0;
        double throughputPerUavBps = throughput / uavs.size();
        double completionRatio = allTasks.size() > 0 ? (double) completed.size() / allTasks.size() : 0.0;

        // 3. Average communication delay
        double totalCommDelay = 0.0;
        int commCount = 0;
        for (ResearchTask t : completed) {
            if (t.getTxLatency() > 0) {
                totalCommDelay += t.getTxLatency();
                commCount++;
            }
        }
        double avgCommDelay = commCount > 0 ? totalCommDelay / commCount : 0.0;

        // 4. Average queue delay
        double totalQueueDelay = 0.0;
        int queueCount = 0;
        for (ResearchTask t : completed) {
            if (t.getFinishTime() > 0) {
                double cpuLatency = (double) t.getCpuCycles() / 1000.0; // 1000 MIPS CPU speed
                double q = (t.getFinishTime() - t.getGenerationTime()) - t.getTxLatency() - cpuLatency;
                if (q > 0.0) {
                    totalQueueDelay += q;
                    queueCount++;
                }
            }
        }
        double avgQueueDelay = queueCount > 0 ? totalQueueDelay / queueCount : 0.0;

        return new SimulationMetrics(
            throughput, avgLatency, completed.size(), avgDelay, successRate,
            energyConsumed, batteryLeftPct, allTasks.size(), failedCount, queuedCount, executingCount,
            loadBalancingIndex, avgCoverage, avgCpuLoad, energyPerCompletedTask,
            throughputPerUavBps, completionRatio, avgCommDelay, avgQueueDelay
        );
    }
}
