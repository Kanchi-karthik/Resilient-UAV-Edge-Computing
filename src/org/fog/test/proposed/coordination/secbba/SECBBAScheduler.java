package org.fog.test.proposed.coordination.secbba;

import org.fog.test.proposed.ProposedTask;
import org.fog.test.proposed.ProposedUAVState;

import java.util.*;

/**
 * SECBBAScheduler - Full Sequentially Extended CBBA workflow.
 * 
 * Implements:
 *  1. Bundle Construction (Algorithm 1)
 *  2. Consistency Conflict Resolution (Algorithm 2)
 *  3. Deadlock Detection & Resolution via Swapping (Algorithm 3)
 */
public class SECBBAScheduler {

    private final int numUavs;
    private final Random rand;
    private final Map<Integer, SECBBABundle> bundles = new HashMap<>();
    private final Map<Integer, SECBBATask> taskMap = new HashMap<>();
    private static final double CBBA_COMM_ENERGY_PER_ROUND = 0.015; // J per UAV per consensus round

    public SECBBAScheduler(int numUavs, long seed) {
        this.numUavs = numUavs;
        this.rand = new Random(seed);
        for (int i = 0; i < numUavs; i++) {
            bundles.put(i, new SECBBABundle(i));
        }
    }

    public Map<Integer, List<ProposedTask>> schedule(
            List<ProposedTask> proposedTasks,
            List<ProposedUAVState> uavs,
            double slotLength,
            int currentSlot) {

        Map<Integer, List<ProposedTask>> assignment = new HashMap<>();
        for (int i = 0; i < uavs.size(); i++) {
            assignment.put(i, new ArrayList<>());
        }

        if (proposedTasks.isEmpty() || uavs.isEmpty()) return assignment;

        // Convert ProposedTasks to SECBBATasks
        List<SECBBATask> tasks = new ArrayList<>();
        taskMap.clear();
        for (ProposedTask pt : proposedTasks) {
            SECBBATask st = new SECBBATask(pt);
            tasks.add(st);
            taskMap.put(st.getId(), st);
        }

        // 1. Reset Bundles for active scheduling round
        for (int i = 0; i < numUavs; i++) {
            bundles.get(i).clear();
        }

        // 2. Phase 1: Bundle Construction (Algorithm 1)
        for (int u = 0; u < numUavs; u++) {
            ProposedUAVState uav = uavs.get(u);
            SECBBABundle b = bundles.get(u);

            for (SECBBATask t : tasks) {
                if (t.isFinished() || t.isDropped()) continue;
                if (!uav.isInCoverage(t.getMdX(), t.getMdY())) continue;

                double bid = t.calculateBid(uav, slotLength);
                if (bid > 0.01) {
                    b.addTask(t.getId(), bid, b.getPath().size());
                    if (bid > t.getWinningBid()) {
                        t.setWinningBid(bid);
                        t.setWinningUAV(u);
                    }
                }
            }
        }

        // 3. Phase 2: Consensus Conflict Resolution (Algorithm 2)
        // 3 rounds of peer neighbor consensus
        for (int round = 0; round < 3; round++) {
            for (int u = 0; u < numUavs; u++) {
                ProposedUAVState sender = uavs.get(u);
                sender.addCoordinationEnergy(CBBA_COMM_ENERGY_PER_ROUND);

                List<ProposedUAVState> neighbors = sender.getNeighbors();
                if (neighbors.isEmpty()) continue;

                ProposedUAVState receiver = neighbors.get(rand.nextInt(neighbors.size()));
                SECBBABundle senderBundle = bundles.get(u);
                SECBBABundle receiverBundle = bundles.get(receiver.getIndex());

                for (int taskId : senderBundle.getBundle()) {
                    SECBBATask task = taskMap.get(taskId);
                    if (task != null) {
                        double senderBid = senderBundle.getBidAt(senderBundle.getBundle().indexOf(taskId));
                        if (senderBid > task.getWinningBid()) {
                            task.setWinningBid(senderBid);
                            task.setWinningUAV(u);
                            receiverBundle.removeTask(taskId);
                        }
                    }
                }
            }
        }

        // 4. Phase 3: Deadlock Detection & Order-Swapping (Algorithm 3)
        boolean deadlockResolved = SECBBADeadlockSolver.resolveDeadlocksBySwapping(bundles, tasks);
        if (deadlockResolved) {
            System.out.println("[SECBBA] Deadlock detected and successfully resolved via path swapping.");
        }

        // 5. Phase 4: Assign tasks according to final conflict-free bundles
        for (SECBBATask t : tasks) {
            int winningUAV = t.getWinningUAV();
            if (winningUAV >= 0 && winningUAV < uavs.size()) {
                t.setAssignedUAV(uavs.get(winningUAV));
                assignment.get(winningUAV).add(t);
            }
        }

        return assignment;
    }

    public Map<Integer, SECBBABundle> getBundles() {
        return bundles;
    }
}
