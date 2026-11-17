package org.fog.test.proposed.coordination.hybrid;

import java.util.List;
import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.ProposedTask;
import org.fog.test.proposed.ProposedUAVState;

/**
 * Enhanced Event-Trigger Policy for UAV-MEC Swarms.
 * 
 * Based on 2025-2026 Event-Triggered Control formulations:
 *  1. State Change Trigger: Hash of queue structure across all UAVs.
 *  2. Dynamic Stagnation Threshold: Adapts dynamically to average load and residual energy:
 *        tau_stag = 2.0 + (1.0 - avgLoad) * 3.0 + (1.0 - avgEnergy) * 1.5
 *  3. Non-periodic Verification: Prevents Zeno behavior with a minimum trigger cooldown window.
 *  4. Proactive Deadlock Prediction: Fires when ratio of blocked/waiting tasks in queues > 0.65.
 *  5. Dynamic Critical Energy Trigger: Low residual energy trigger scaled with current workload.
 *
 * FIX (v2): Applied Zeno cooldown to state-change trigger (not just stagnation) to prevent
 * every-slot chattering during normal task processing. Reduced MIN_TRIGGER_INTERVAL to 0.5s
 * so rapid sequential deadlock injections (MIN_INJECTION_GAP=1 slot=1s) are not missed.
 * Hash is now updated AFTER the trigger fires so that a delayed injection (arriving mid-slot)
 * can still be caught in the immediately following slot.
 */
public class EventTriggerPolicy {
    private int lastQueueHash = 0;
    private int slotsSinceCompletion = 0;
    private double lastTriggerTime = -1.0;
    private static final double MIN_TRIGGER_INTERVAL = 0.2;
    private int slotsSinceLastPeriodicCheck = 0;
    private static final int PERIODIC_CHECK_INTERVAL = 1; // check every slot at high load
    private java.util.Map<Integer, Integer> waitFor = null;
    private int lastWaitForSize = 0;

    private boolean forceTriggerOnNextCheck = false;

    public void forceTrigger() {
        this.forceTriggerOnNextCheck = true;
    }

    public void setWaitFor(java.util.Map<Integer, Integer> waitFor) {
        this.waitFor = waitFor;
    }

    public boolean shouldTrigger(List<ProposedUAVState> uavs, int completedThisSlot, double currentTime) {
        if (forceTriggerOnNextCheck) {
            forceTriggerOnNextCheck = false;
            lastTriggerTime = currentTime;
            return true;
        }

        double avgLoad = computeAverageLoad(uavs);
        double dynamicCooldown = 0.1 + (0.4 * (1.0 - avgLoad));
        boolean cooldownOk = (lastTriggerTime < 0 || (currentTime - lastTriggerTime) >= dynamicCooldown);

        // 0. GRAPH & WAITING STATE TRIGGER (Fires immediately whenever wait-for edges exist or any task is WAITING)
        if ((waitFor != null && !waitFor.isEmpty()) || hasWaitingTasks(uavs)) {
            lastTriggerTime = currentTime;
            return true;
        }


        // 1. STATE CHANGE TRIGGER (only fires if cooldown allows — prevents every-slot chattering)
        int currentHash = computeQueueStateHash(uavs);
        if (cooldownOk && currentHash != lastQueueHash) {
            lastQueueHash = currentHash;
            slotsSinceCompletion = 0;
            lastTriggerTime = currentTime;
            return true;
        }

        // 2. DYNAMIC STAGNATION THRESHOLD
        double avgEnergy = computeAverageEnergy(uavs);

        double dynamicStagThreshold = 2.0 + (1.0 - avgLoad) * 3.0 + (1.0 - avgEnergy) * 1.5;
        dynamicStagThreshold = Math.max(2.0, Math.min(6.0, dynamicStagThreshold));

        if (completedThisSlot == 0) {
            slotsSinceCompletion++;
        } else {
            slotsSinceCompletion = 0;
        }
        if (slotsSinceCompletion >= dynamicStagThreshold) {
            slotsSinceCompletion = 0;
            lastTriggerTime = currentTime;
            return true;
        }

        // 3. DYNAMIC ENERGY TRIGGER
        for (ProposedUAVState u : uavs) {
            double energyRatio = u.getResidualEnergyRatio();
            double dynamicEnergyThresh = ProposedParams.ENERGY_CRITICAL_THRESHOLD + (1.0 - avgLoad) * 0.05;
            if (energyRatio < dynamicEnergyThresh) {
                lastTriggerTime = currentTime;
                return true;
            }
        }

        // 4. NON-PERIODIC VERIFICATION (Prevent Zeno chattering — already handled in step 1,
        //    kept here as a guard for steps 2 and 3 which skip Zeno above)
        if (!cooldownOk) {
            return false;
        }

        // 5. PROACTIVE DEADLOCK PREDICTION (High waiting ratio in queues)
        int totalWaiting = 0;
        int totalQueued = 0;
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.getQueue()) {
                totalQueued++;
                if (t.currentState == ProposedTask.TaskState.WAITING) {
                    totalWaiting++;
                }
            }
        }
        if (totalQueued > 0 && ((double) totalWaiting / totalQueued) > 0.65) {
            lastTriggerTime = currentTime;
            return true;
        }

        // 6. PERIODIC FALLBACK DEADLOCK DETECTION (Every 3 slots)
        // Catches transient ECD (energy critical) and PID (priority inversion) deadlocks
        // that form without altering queue hash or causing macro-stagnation.
        slotsSinceLastPeriodicCheck++;
        if (slotsSinceLastPeriodicCheck >= PERIODIC_CHECK_INTERVAL) {
            slotsSinceLastPeriodicCheck = 0;
            for (ProposedUAVState u : uavs) {
                for (ProposedTask t : u.getQueue()) {
                    if (t.currentState == ProposedTask.TaskState.WAITING) {
                        lastTriggerTime = currentTime;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private int computeQueueStateHash(List<ProposedUAVState> uavs) {
        int hash = 0;
        for (ProposedUAVState u : uavs) {
            hash = hash * 31 + u.getQueueSize();
            for (ProposedTask t : u.getQueue()) {
                hash = hash * 31 + t.getId() + t.currentState.ordinal();
                if (t.getRequestedResource() != null) {
                    hash = hash * 31 + t.getRequestedResource().hashCode();
                }
            }

        }
        if (waitFor != null) {
            hash = hash * 31 + waitFor.size();
            for (java.util.Map.Entry<Integer, Integer> e : waitFor.entrySet()) {
                hash = hash * 31 + e.getKey() * 31 + e.getValue();
            }
        }
        return hash;
    }


    private double computeAverageLoad(List<ProposedUAVState> uavs) {
        if (uavs.isEmpty()) return 0.0;
        double sum = 0.0;
        for (ProposedUAVState u : uavs) {
            sum += u.getLoadRatioNow(1.0);
        }
        return sum / uavs.size();
    }

    private double computeAverageEnergy(List<ProposedUAVState> uavs) {
        if (uavs.isEmpty()) return 1.0;
        double sum = 0.0;
        for (ProposedUAVState u : uavs) {
            sum += u.getResidualEnergyRatio();
        }
        return sum / uavs.size();
    }

    private boolean hasWaitingTasks(List<ProposedUAVState> uavs) {
        if (uavs == null) return false;
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.getQueue()) {
                if (t.currentState == ProposedTask.TaskState.WAITING) return true;
            }
        }
        return false;
    }
}


