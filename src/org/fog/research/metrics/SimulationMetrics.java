package org.fog.research.metrics;

public class SimulationMetrics {

    private final double throughputBps;
    private final double averageLatencySec;
    private final int completedTasks;
    private final double averageDelaySec;
    private final double successRate;
    
    private final double energyConsumed;
    private final double batteryLeftPct;
    private final int totalTasks;
    private final int failedTasks;
    private final int queuedTasks;
    private final int executingTasks;

    // Derived and paper-specific metrics
    private final double loadBalancingIndex;
    private final double coverageRatio;
    private final double avgUavCpuLoad;
    private final double energyPerCompletedTask;
    private final double throughputPerUavBps;
    private final double completionRatio;
    private final double avgCommDelaySec;
    private final double avgQueueDelaySec;

    public SimulationMetrics(
            double throughputBps,
            double averageLatencySec,
            int completedTasks,
            double averageDelaySec,
            double successRate,
            double energyConsumed,
            double batteryLeftPct,
            int totalTasks,
            int failedTasks,
            int queuedTasks,
            int executingTasks,
            double loadBalancingIndex,
            double coverageRatio,
            double avgUavCpuLoad,
            double energyPerCompletedTask,
            double throughputPerUavBps,
            double completionRatio,
            double avgCommDelaySec,
            double avgQueueDelaySec) {
        this.throughputBps = throughputBps;
        this.averageLatencySec = averageLatencySec;
        this.completedTasks = completedTasks;
        this.averageDelaySec = averageDelaySec;
        this.successRate = successRate;
        this.energyConsumed = energyConsumed;
        this.batteryLeftPct = batteryLeftPct;
        this.totalTasks = totalTasks;
        this.failedTasks = failedTasks;
        this.queuedTasks = queuedTasks;
        this.executingTasks = executingTasks;
        this.loadBalancingIndex = loadBalancingIndex;
        this.coverageRatio = coverageRatio;
        this.avgUavCpuLoad = avgUavCpuLoad;
        this.energyPerCompletedTask = energyPerCompletedTask;
        this.throughputPerUavBps = throughputPerUavBps;
        this.completionRatio = completionRatio;
        this.avgCommDelaySec = avgCommDelaySec;
        this.avgQueueDelaySec = avgQueueDelaySec;
    }

    public double getThroughputBps() {
        return throughputBps;
    }

    public double getAverageLatencySec() {
        return averageLatencySec;
    }

    public int getCompletedTasks() {
        return completedTasks;
    }

    public double getAverageDelaySec() {
        return averageDelaySec;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public double getEnergyConsumed() {
        return energyConsumed;
    }

    public double getBatteryLeftPct() {
        return batteryLeftPct;
    }

    public int getTotalTasks() {
        return totalTasks;
    }

    public int getFailedTasks() {
        return failedTasks;
    }

    public int getQueuedTasks() {
        return queuedTasks;
    }

    public int getExecutingTasks() {
        return executingTasks;
    }

    public double getLoadBalancingIndex() {
        return loadBalancingIndex;
    }

    public double getCoverageRatio() {
        return coverageRatio;
    }

    public double getAvgUavCpuLoad() {
        return avgUavCpuLoad;
    }

    public double getEnergyPerCompletedTask() {
        return energyPerCompletedTask;
    }

    public double getThroughputPerUavBps() {
        return throughputPerUavBps;
    }

    public double getCompletionRatio() {
        return completionRatio;
    }

    public double getAvgCommDelaySec() {
        return avgCommDelaySec;
    }

    public double getAvgQueueDelaySec() {
        return avgQueueDelaySec;
    }

    private java.util.List<org.fog.research.environment.UAVEntity> rawUavs;
    private java.util.List<org.fog.research.environment.ResearchTask> rawTasks;
    private java.util.Map<Integer, Double> uavDistances;
    private java.util.Map<Integer, Integer> uavCoverageTime;

    public void setRawUavs(java.util.List<org.fog.research.environment.UAVEntity> rawUavs) { this.rawUavs = rawUavs; }
    public java.util.List<org.fog.research.environment.UAVEntity> getRawUavs() { return rawUavs; }

    public void setRawTasks(java.util.List<org.fog.research.environment.ResearchTask> rawTasks) { this.rawTasks = rawTasks; }
    public java.util.List<org.fog.research.environment.ResearchTask> getRawTasks() { return rawTasks; }

    public void setUavDistances(java.util.Map<Integer, Double> uavDistances) { this.uavDistances = uavDistances; }
    public java.util.Map<Integer, Double> getUavDistances() { return uavDistances; }

    public void setUavCoverageTime(java.util.Map<Integer, Integer> uavCoverageTime) { this.uavCoverageTime = uavCoverageTime; }
    public java.util.Map<Integer, Integer> getUavCoverageTime() { return uavCoverageTime; }
}
