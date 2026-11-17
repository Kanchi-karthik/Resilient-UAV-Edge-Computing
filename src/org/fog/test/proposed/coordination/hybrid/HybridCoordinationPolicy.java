package org.fog.test.proposed.coordination.hybrid;

import java.util.List;
import org.fog.test.proposed.DeadlockRecoverer;
import org.fog.test.proposed.DeadlockReport;
import org.fog.test.proposed.FlowScheduler;
import org.fog.test.proposed.ProposedTask;
import org.fog.test.proposed.ProposedUAVState;
import org.fog.test.proposed.coordination.CoordinationPolicy;

/**
 * Proposed Enhanced Hybrid Coordination Policy (2025-2026):
 * Combines CBBA-based DAG Task Bundling, Dynamic Event-Triggered Detection,
 * and Energy-Aware Token Ring Recovery.
 */
public final class HybridCoordinationPolicy implements CoordinationPolicy {
    private final EventTriggerPolicy trigger = new EventTriggerPolicy();
    private final TokenRing tokenRing;
    private final CBBAScheduler cbbaScheduler;
    private boolean triggerFiredThisSlot = false;
    private double currentTime = 0.0;
    private int currentSlot = 0;

    public HybridCoordinationPolicy(List<ProposedUAVState> uavs, long seed) {
        this.tokenRing = new TokenRing(uavs);
        this.cbbaScheduler = new CBBAScheduler(uavs.size(), seed);
    }

    public HybridCoordinationPolicy(List<ProposedUAVState> uavs) {
        this(uavs, 7L);
    }

    @Override
    public String getName() {
        return "HYBRID";
    }

    public TokenRing getTokenRing() {
        return tokenRing;
    }

    public CBBAScheduler getCbbaScheduler() {
        return cbbaScheduler;
    }

    public void forceTrigger() {
        trigger.forceTrigger();
    }

    public void setWaitFor(java.util.Map<Integer, Integer> waitFor) {
        trigger.setWaitFor(waitFor);
    }


    @Override
    public void beginSlot(int slot, double clock, List<ProposedUAVState> uavs) {
        this.currentSlot = slot;
        this.currentTime = clock;
        this.triggerFiredThisSlot = false;
    }

    @Override
    public boolean shouldDetect(List<ProposedUAVState> uavs, int completedThisSlot) {
        triggerFiredThisSlot = trigger.shouldTrigger(uavs, completedThisSlot, currentTime);
        return triggerFiredThisSlot;
    }

    @Override
    public boolean recover(DeadlockReport report, DeadlockRecoverer recoverer, double clock) {
        boolean success = tokenRing.executeRecovery(report, recoverer, clock);
        if (success) {
            double latency = clock - report.detectionTime;
            tokenRing.getTokenState().updateAverageLatency(latency);
        }
        return success;
    }

    @Override
    public void endSlot() {
        tokenRing.circulateToken(triggerFiredThisSlot);
    }

    /**
     * Schedule tasks using decentralized CBBA auction.
     */
    public FlowScheduler.Result scheduleTasks(
            List<ProposedTask> tasks,
            List<ProposedUAVState> uavs,
            double slotLength,
            int currentSlot) {
        return cbbaScheduler.schedule(tasks, uavs, slotLength, currentSlot);
    }
}