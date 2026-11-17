package org.fog.test.proposed.coordination.wu2025;

import org.fog.test.proposed.ProposedTask;

/**
 * WuTask - Extended for Wu et al. algorithm (Scientific Reports 2025).
 * 
 * Includes timing margins, arrival angles, and holding loop metrics.
 */
public class WuTask extends ProposedTask {

    private double executionTime = 0.0;
    private double deadlineMargin = 0.0;
    private int assignedUAVIndex = -1;
    private boolean inDeadlockCycle = false;
    private double arrivalAngle = 0.0;

    public WuTask(ProposedTask task) {
        super(task.getDataBits(), task.getResultBits(),
              task.getTotalCpuCycles(), task.getDeadlineSec(),
              task.getMdX(), task.getMdY(), task.getArrivalTime());
        this.setDependency(task.getDepGroupId(), task.getDepSeq());
        this.setReleasable(task.isReleasable());
        this.setPriority(task.getPriority());
        this.setDummy(task.isDummy());
        this.setState(task.currentState, task.getArrivalTime());
        this.readyTime = task.readyTime;
        this.assignedTime = task.assignedTime;
        this.startExecTime = task.startExecTime;

        this.executionTime = task.getTotalCpuCycles() / 1e9;
        this.deadlineMargin = task.getDeadlineSec() - executionTime;
    }

    public double getExecutionTime() { return executionTime; }
    public void setExecutionTime(double time) { this.executionTime = time; }

    public double getDeadlineMargin() { return deadlineMargin; }
    public void setDeadlineMargin(double margin) { this.deadlineMargin = margin; }

    public int getAssignedUAVIndex() { return assignedUAVIndex; }
    public void setAssignedUAVIndex(int idx) { this.assignedUAVIndex = idx; }

    public boolean isInDeadlockCycle() { return inDeadlockCycle; }
    public void setInDeadlockCycle(boolean inCycle) { this.inDeadlockCycle = inCycle; }

    public double getArrivalAngle() { return arrivalAngle; }
    public void setArrivalAngle(double angle) { this.arrivalAngle = angle; }

    public double getUrgency() {
        return 1.0 / (Math.max(0.1, this.deadlineMargin) + 0.1);
    }
}
