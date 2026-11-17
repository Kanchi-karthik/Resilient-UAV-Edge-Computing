package org.fog.test.proposed.coordination.secbba;

import org.fog.test.proposed.ProposedTask;
import org.fog.test.proposed.ProposedUAVState;

/**
 * SECBBATask - Extends ProposedTask with CBBA/SECBBA-specific fields.
 * 
 * Based on Yu et al., "Distributed Task Allocation and Trajectory Planning for 
 * Heterogeneous UAV Swarms in Multi-Constraint Environments", Aerospace 2026.
 * 
 * Each task maintains:
 *  - bid: Marginal gain / utility score calculated by UAVs.
 *  - winningUAV: Index of the current winning UAV for this task.
 *  - winningBid: The highest winning bid acknowledged by consensus.
 *  - requiredUavCount: Number of UAVs needed (1 for independent, >1 for multi-UAV dependent).
 */
public class SECBBATask extends ProposedTask {

    private double bid = 0.0;
    private int winningUAV = -1;
    private double winningBid = 0.0;
    private int requiredUavCount = 1;

    public SECBBATask(ProposedTask task) {
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
        // Multi-UAV requirement if part of dependent multi-stage pipeline
        this.requiredUavCount = (task.getDepGroupId() > 0) ? 2 : 1;
    }

    public double getBid() { return bid; }
    public void setBid(double bid) { this.bid = bid; }

    public int getWinningUAV() { return winningUAV; }
    public void setWinningUAV(int uav) { this.winningUAV = uav; }

    public double getWinningBid() { return winningBid; }
    public void setWinningBid(double bid) { this.winningBid = bid; }

    public int getRequiredUavCount() { return requiredUavCount; }
    public void setRequiredUavCount(int count) { this.requiredUavCount = count; }

    /**
     * Calculates Diminishing Marginal Gain (DMG) bid for UAV i bidding on this task.
     * Eq. (17)-(19) in Yu et al. (Aerospace 2026).
     */
    public double calculateBid(ProposedUAVState uav, double slotLength) {
        double distance = uav.distanceTo(this.getMdX(), this.getMdY());
        double urgency = 100.0 / (1.0 + Math.max(0.1, this.getDeadlineSec()));
        double loadFactor = 1.0 + uav.getLoadRatioNow(slotLength);
        double distFactor = 1.0 + (distance / 1000.0);
        return urgency / (distFactor * loadFactor);
    }
}
