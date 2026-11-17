package org.fog.research.environment;

public class TaskDemand {

    private final int taskId;
    private final int mdId;
    private final long inputBits;
    private final long outputBits;
    private final long cpuCycles;
    private final double latencyToleranceSec;

    public TaskDemand(int taskId, int mdId, long inputBits, long outputBits, long cpuCycles, double latencyToleranceSec) {
        this.taskId = taskId;
        this.mdId = mdId;
        this.inputBits = inputBits;
        this.outputBits = outputBits;
        this.cpuCycles = cpuCycles;
        this.latencyToleranceSec = latencyToleranceSec;
    }

    public int getTaskId() {
        return taskId;
    }

    public int getMdId() {
        return mdId;
    }

    public long getInputBits() {
        return inputBits;
    }

    public long getOutputBits() {
        return outputBits;
    }

    public long getCpuCycles() {
        return cpuCycles;
    }

    public double getLatencyToleranceSec() {
        return latencyToleranceSec;
    }
}
