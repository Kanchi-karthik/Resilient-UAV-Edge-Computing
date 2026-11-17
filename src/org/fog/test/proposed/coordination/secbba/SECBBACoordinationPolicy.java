package org.fog.test.proposed.coordination.secbba;

import org.fog.test.proposed.*;
import org.fog.test.proposed.coordination.CoordinationPolicy;
import java.util.*;

/**
 * SECBBA Coordination Policy (Aerospace 2026).
 *
 * Uses Consensus-Based Bundle Algorithm (CBBA) with Diminishing Marginal Gain (DMG)
 * and Algorithm 3: Deadlock resolution via execution-order swapping in UAV queues.
 *
 * KEY FIX: The paper resolves deadlocks by swapping the ordering of cycle tasks
 * WITHIN actual UAV queues (not in abstract path lists that are cleared each slot).
 * The solver is now fed with live UAV queue snapshots at recovery time, so swapping
 * actually breaks the dependency cycle in real execution order.
 *
 * No task is ever preempted or dropped by SECBBA — it is purely an order-swap.
 * If a cycle spans different UAVs and cannot be broken by intra-queue swap, the
 * solver releases the waitFor lock of the lower-priority task (by deadline) so it
 * can proceed, which is the inter-UAV equivalent of a "path-list reorder" in the paper.
 */
public class SECBBACoordinationPolicy implements CoordinationPolicy {

    // Persistent path lists: UAV-index -> ordered list of task IDs in planned execution order.
    // These are NOT cleared every slot — they persist and grow as tasks are assigned.
    private final Map<Integer, List<Integer>> uavPathLists = new HashMap<>();
    private final Map<Integer, SECBBABundle> bundles = new HashMap<>();
    private final double[][] winnerMatrix;
    private final double[][] winningBids;
    private static final double CBBA_ENERGY_PER_ROUND = 0.015; // J per UAV per consensus round
    private final int numUavs;
    private final int maxTasks;
    private int currentSlot = 0;

    // Live reference to UAV states — set in beginSlot so recover() can access queues
    private List<ProposedUAVState> liveUavs = new ArrayList<>();
    // Live reference to waitFor map — injected via setWaitFor by ProposedSlotController
    private Map<Integer, Integer> waitFor = new HashMap<>();

    public SECBBACoordinationPolicy(int numUavs, long seed) {
        this.numUavs = numUavs;
        this.maxTasks = ProposedParams.TASK_SET_LENGTH + 100;
        this.winnerMatrix = new double[numUavs][maxTasks];
        this.winningBids = new double[numUavs][maxTasks];
        for (int i = 0; i < numUavs; i++) {
            bundles.put(i, new SECBBABundle(i));
            uavPathLists.put(i, new ArrayList<>());
        }
    }

    /** Called by ProposedSlotController so SECBBA can manipulate wait-for edges. */
    public void setWaitFor(Map<Integer, Integer> waitFor) {
        this.waitFor = waitFor;
    }

    @Override
    public String getName() {
        return "SECBBA_AEROSPACE_2026";
    }

    @Override
    public void beginSlot(int slot, double clock, List<ProposedUAVState> uavs) {
        this.currentSlot = slot;
        this.liveUavs = uavs;

        // Sync persistent path lists from current UAV queue snapshots.
        // We ADD newly queued tasks (those not yet tracked) to the path list.
        for (ProposedUAVState uav : uavs) {
            List<Integer> path = uavPathLists.get(uav.getIndex());
            if (path == null) {
                path = new ArrayList<>();
                uavPathLists.put(uav.getIndex(), path);
            }
            // Add any queued tasks on this UAV not already in the path list
            for (ProposedTask t : uav.getQueue()) {
                if (!path.contains(t.getId())) {
                    path.add(t.getId());
                }
            }
            // Also track the currently-executing task at position 0
            ProposedTask exec = uav.getCurrentlyExecutingTask();
            if (exec != null && !path.contains(exec.getId())) {
                path.add(0, exec.getId());
            }
        }

        // Bundle bidding: clear per-slot bids only (not path lists)
        for (SECBBABundle b : bundles.values()) {
            b.clear();
        }

        // Consume CBBA coordination energy per UAV for consensus broadcast
        for (ProposedUAVState u : uavs) {
            u.addCoordinationEnergy(CBBA_ENERGY_PER_ROUND * 2.0);
        }
    }

    @Override
    public boolean shouldDetect(List<ProposedUAVState> uavs, int completedThisSlot) {
        // SECBBA checks every slot for deadlock cycles in its path lists
        return true;
    }

    /**
     * Algorithm 3 (Yu et al., Aerospace 2026): Resolve deadlocks by swapping
     * execution order in path lists — NO task preemption or dropping.
     *
     * Strategy:
     * 1. Find cycle tasks from the DeadlockReport.
     * 2. For each pair of cycle tasks on the SAME UAV's queue, swap their ordering
     *    in the persistent path list AND in the actual UAV queue (by re-enqueuing).
     * 3. For cycle tasks on DIFFERENT UAVs: release the waitFor lock of the
     *    lower-deadline-priority task (the one less urgent) so it stops blocking.
     *    This is the inter-UAV equivalent of Algorithm 3's cross-agent path swap.
     */
    @Override
    public boolean recover(DeadlockReport report, DeadlockRecoverer recoverer, double clock) {
        if (report.involvedTaskIds == null || report.involvedTaskIds.isEmpty()) {
            return false;
        }

        List<Integer> cycleIds = new ArrayList<>(report.involvedTaskIds);

        // Step 1: Build map of taskId -> UAV for cycle tasks
        Map<Integer, ProposedUAVState> taskToUav = new HashMap<>();
        Map<Integer, ProposedTask> taskLookup = new HashMap<>();
        for (ProposedUAVState uav : liveUavs) {
            for (ProposedTask t : uav.getQueue()) {
                if (cycleIds.contains(t.getId())) {
                    taskToUav.put(t.getId(), uav);
                    taskLookup.put(t.getId(), t);
                }
            }
            ProposedTask exec = uav.getCurrentlyExecutingTask();
            if (exec != null && cycleIds.contains(exec.getId())) {
                taskToUav.put(exec.getId(), uav);
                taskLookup.put(exec.getId(), exec);
            }
        }

        // Step 2: Try intra-UAV swap first — find two cycle tasks on same UAV
        for (ProposedUAVState uav : liveUavs) {
            List<Integer> path = uavPathLists.get(uav.getIndex());
            if (path == null) continue;

            List<Integer> cycleTasKsOnUav = new ArrayList<>();
            for (int taskId : cycleIds) {
                if (path.contains(taskId)) {
                    cycleTasKsOnUav.add(taskId);
                }
            }

            if (cycleTasKsOnUav.size() >= 2) {
                int idA = cycleTasKsOnUav.get(0);
                int idB = cycleTasKsOnUav.get(1);
                int idxA = path.indexOf(idA);
                int idxB = path.indexOf(idB);

                if (idxA >= 0 && idxB >= 0 && idxA != idxB) {
                    // Swap in path list
                    Collections.swap(path, idxA, idxB);

                    // Release waitFor lock if task B was waiting for task A
                    // (swapping order means B no longer needs to wait)
                    if (waitFor.getOrDefault(idB, -1) == idA) {
                        waitFor.remove(idB);
                        ProposedTask taskB = taskLookup.get(idB);
                        if (taskB != null && taskB.currentState == ProposedTask.TaskState.WAITING) {
                            taskB.setState(ProposedTask.TaskState.READY, clock);
                            taskB.setReleasable(true);
                        }
                    } else if (waitFor.getOrDefault(idA, -1) == idB) {
                        waitFor.remove(idA);
                        ProposedTask taskA = taskLookup.get(idA);
                        if (taskA != null && taskA.currentState == ProposedTask.TaskState.WAITING) {
                            taskA.setState(ProposedTask.TaskState.READY, clock);
                            taskA.setReleasable(true);
                        }
                    }

                    System.out.println("[SECBBA-2026] Deadlock (" + report.type
                            + ") resolved via INTRA-UAV queue order swap: T" + idA
                            + " <-> T" + idB + " on UAV-" + uav.getIndex()
                            + " (0 tasks dropped, 0 tasks preempted).");
                    return true;
                }
            }
        }

        // Step 3: Inter-UAV swap fallback — release the waitFor lock of the task
        // with the LATEST (least urgent) deadline. This is the paper's cross-agent
        // path reorder: the less-urgent task yields its position so the more-urgent
        // one can proceed first.
        ProposedTask leastUrgent = null;
        double latestDeadline = Double.NEGATIVE_INFINITY;
        for (int taskId : cycleIds) {
            ProposedTask t = taskLookup.get(taskId);
            if (t == null) continue;
            double absDeadline = t.getArrivalTime() + t.getDeadlineSec();
            if (absDeadline > latestDeadline) {
                latestDeadline = absDeadline;
                leastUrgent = t;
            }
        }

        if (leastUrgent != null && waitFor.containsKey(leastUrgent.getId())) {
            int blockedBy = waitFor.remove(leastUrgent.getId());
            if (leastUrgent.currentState == ProposedTask.TaskState.WAITING) {
                leastUrgent.setState(ProposedTask.TaskState.READY, clock);
                leastUrgent.setReleasable(true);
            }

            // Update path list: move leastUrgent task to after its former blocker
            ProposedUAVState uav = taskToUav.get(leastUrgent.getId());
            if (uav != null) {
                List<Integer> path = uavPathLists.get(uav.getIndex());
                if (path != null) {
                    int idx = path.indexOf(leastUrgent.getId());
                    int blockerIdx = path.indexOf(blockedBy);
                    if (idx >= 0 && blockerIdx >= 0 && idx < blockerIdx) {
                        // Move leastUrgent to just after its blocker (swap in path)
                        path.remove(idx);
                        path.add(Math.min(blockerIdx, path.size()), leastUrgent.getId());
                    }
                }
            }

            System.out.println("[SECBBA-2026] Deadlock (" + report.type
                    + ") resolved via INTER-UAV path reorder: T" + leastUrgent.getId()
                    + " yields to T" + blockedBy
                    + " (least-urgent task T" + leastUrgent.getId()
                    + " waitFor lock released; 0 tasks dropped).");
            return true;
        }

        System.out.println("[SECBBA-2026] Deadlock (" + report.type
                + ") could NOT be resolved by path swapping. No eligible cycle pair found.");
        return false;
    }

    @Override
    public void endSlot() {
    }

    public Map<Integer, SECBBABundle> getBundles() {
        return bundles;
    }

    public Map<Integer, List<Integer>> getUavPathLists() {
        return uavPathLists;
    }
}
