package org.fog.research.environment;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.entities.Tuple;
import org.fog.research.utils.TaskStatus;

public class ResearchTask extends Tuple {
    private final int taskId;
    private final int mobileDeviceId;
    private final long inputBits;
    private final long outputBits;
    private final long cpuCycles;
    private final double latencyToleranceSec;
    private final double generationTime;
    
    private TaskStatus researchTaskStatus;
    private int assignedUavId = -1;
    private double finishTime = -1.0;
    private double txLatency = 0.0;

    public double getTxLatency() {
        return txLatency;
    }

    public void setTxLatency(double txLatency) {
        this.txLatency = txLatency;
    }

    public ResearchTask(String appId, int taskId, int mobileDeviceId, 
                        long inputBits, long outputBits, long cpuCycles, 
                        double latencyToleranceSec, double generationTime) {
        super(appId, taskId, Tuple.UP, cpuCycles, 1, Math.max(inputBits / 8, 1), Math.max(outputBits / 8, 1), 
              new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
        
        this.taskId = taskId;
        this.mobileDeviceId = mobileDeviceId;
        this.inputBits = inputBits;
        this.outputBits = outputBits;
        this.cpuCycles = cpuCycles;
        this.latencyToleranceSec = latencyToleranceSec;
        this.generationTime = generationTime;
        this.researchTaskStatus = TaskStatus.QUEUED;
    }

    public int getTaskId() {
        return taskId;
    }

    public int getMobileDeviceId() {
        return mobileDeviceId;
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

    public double getGenerationTime() {
        return generationTime;
    }

    public TaskStatus getResearchTaskStatus() {
        return researchTaskStatus;
    }

    public void setResearchTaskStatus(TaskStatus researchTaskStatus) {
        this.researchTaskStatus = researchTaskStatus;
    }

    public int getAssignedUavId() {
        return assignedUavId;
    }

    public void setAssignedUavId(int assignedUavId) {
        this.assignedUavId = assignedUavId;
    }

    public double getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(double finishTime) {
        this.finishTime = finishTime;
    }

    public boolean isExpired(double currentTime) {
        return (currentTime - generationTime) > latencyToleranceSec;
    }
}
