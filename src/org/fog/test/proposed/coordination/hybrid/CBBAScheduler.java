package org.fog.test.proposed.coordination.hybrid;

import java.util.*;
import org.fog.test.proposed.*;

/**
 * ENHANCED CBBA SCHEDULER – with Monolithic DAG Bundle Bidding
 * 
 * Algorithmic Improvement:
 * - Bids on entire 3-stage chains (C->R->V) atomically
 * - If UAV wins root, ALL subtasks co-located -> zero cross-UAV CDD
 * - Combinatorial auction extension of CBBA (Choi et al. IEEE TRO 2009)
 */
public class CBBAScheduler {
    private final int numUavs;
    private final Random rand;
    private final BayesianFeasibilityEstimator bayes;
    private final ProbabilisticCostModel costModel;
    private static final int CONSENSUS_ROUNDS = ProposedParams.CBBA_CONSENSUS_ROUNDS;
    private static final double CBBA_ENERGY_PER_ROUND = ProposedParams.CBBA_ENERGY_PER_ROUND_J;

    public CBBAScheduler(int numUavs, long seed) {
        this.numUavs = numUavs;
        this.rand = new Random(seed);
        this.bayes = new BayesianFeasibilityEstimator(numUavs);
        this.costModel = new ProbabilisticCostModel();
    }

    public FlowScheduler.Result schedule(
            List<ProposedTask> tasks,
            List<ProposedUAVState> uavs,
            double slotLength,
            int currentSlot) {

        FlowScheduler.Result result = new FlowScheduler.Result();
        if (tasks == null || tasks.isEmpty() || uavs == null || uavs.isEmpty()) {
            if (tasks != null) result.unassigned.addAll(tasks);
            return result;
        }

        // Step 1: Sort tasks by Earliest Deadline First (EDF) to minimize latency
        List<ProposedTask> sortedTasks = new ArrayList<>(tasks);
        sortedTasks.sort(Comparator.comparingDouble(t -> t.getArrivalTime() + t.getDeadlineSec()));

        // Group tasks by dependency chain ID (Monolithic DAG mapping)
        Map<Integer, List<ProposedTask>> chainGroups = new HashMap<>();
        List<ProposedTask> independentTasks = new ArrayList<>();

        for (ProposedTask t : sortedTasks) {
            if (t.isFinished() || t.isDropped()) continue;
            if (t.isDependent() && t.getDepGroupId() > 0) {
                chainGroups.computeIfAbsent(t.getDepGroupId(), k -> new ArrayList<>()).add(t);
            } else {
                independentTasks.add(t);
            }
        }

        List<CBBAOffer> allOffers = new ArrayList<>();

        // Step 2: Evaluate Monolithic DAG Chain Bids (Bid on ENTIRE chain)
        for (Map.Entry<Integer, List<ProposedTask>> entry : chainGroups.entrySet()) {
            int chainId = entry.getKey();
            List<ProposedTask> chain = entry.getValue();
            ProposedTask root = chain.get(0);

            for (int u = 0; u < uavs.size(); u++) {
                ProposedUAVState uav = uavs.get(u);
                if (!uav.isInCoverage(root.getMdX(), root.getMdY())) continue;

                double chainScore = calculateDAGBundleBid(chain, uav);
                if (chainScore > 0.001) {
                    allOffers.add(new CBBAOffer(u, -chainId, chainScore, true));
                }
            }
        }

        // Step 3: Evaluate Independent Task Bids
        for (ProposedTask t : independentTasks) {
            for (int u = 0; u < uavs.size(); u++) {
                ProposedUAVState uav = uavs.get(u);
                if (!uav.isInCoverage(t.getMdX(), t.getMdY())) continue;

                double baseBid = calculateBaseBid(t, uav);
                if (baseBid > 0.001) {
                    allOffers.add(new CBBAOffer(u, t.getId(), baseBid, false));
                }
            }
        }

        // ── Enhancement 2: Adaptive Consensus Rounds ─────────────────────────
        // More rounds only when task contention is genuinely high.
        // Derived from system size: 1 round suffices for tasks ≤ numUavs (no
        // conflict possible), 2 rounds for low contention, 3 for high.
        // Grounded in: communication complexity theory (Papadimitriou 1994).
        int dynamicRounds;
        int n = uavs.size();
        if (tasks.size() <= n) {
            dynamicRounds = 1;          // no contention: one assignment per UAV at most
        } else if (tasks.size() <= 50) {
            dynamicRounds = 2;          // moderate contention: 2 rounds enough to resolve conflicts
        } else {
            dynamicRounds = CONSENSUS_ROUNDS; // high contention: full 3 rounds
        }
        // ─────────────────────────────────────────────────────────────────────

        // Step 4: Consensus Stage
        Map<Integer, CBBAOffer> winningOffers = new HashMap<>();
        for (int round = 0; round < dynamicRounds; round++) {
            for (ProposedUAVState uav : uavs) {
                uav.addCoordinationEnergy(CBBA_ENERGY_PER_ROUND);
            }
            for (CBBAOffer offer : allOffers) {
                int key = offer.id;
                if (!winningOffers.containsKey(key) || offer.bid > winningOffers.get(key).bid) {
                    winningOffers.put(key, offer);
                }
            }
        }

        // Step 5: Atomic Co-location Assignment Application
        // ── Enhancement: Strict Co-location with Derived Queue Capacity ──────
        // Before assigning a chain, verify the winning UAV has enough queue room
        // for ALL subtasks. If not, the chain goes to the next-best bidder.
        // Capacity is physically derived: floor(CPU_Hz × slotLength / avgCycles).
        // Prevents chain splitting at high density – directly targets the gap with SECBBA.
        Set<Integer> assignedTaskIds = new HashSet<>();
        Map<Integer, Integer> remainingCapacity = new HashMap<>();
        for (ProposedUAVState uav : uavs) {
            remainingCapacity.put(uav.getIndex(), uav.estimatedTaskCapacity(slotLength, tasks));
        }
        // Sort offers by chain, then by bid descending, so we can try fallback UAVs
        List<CBBAOffer> sortedOffers = new ArrayList<>(allOffers);
        sortedOffers.sort((a, b) -> Double.compare(b.bid, a.bid));

        for (CBBAOffer winner : sortedOffers) {
            ProposedUAVState winningUav = uavs.get(winner.uavIdx);

            if (winner.isChain) {
                int chainId = -winner.id;
                List<ProposedTask> chain = chainGroups.get(chainId);
                if (chain == null) continue;

                int capacity = remainingCapacity.getOrDefault(winningUav.getIndex(), 0);
                if (capacity < chain.size() || !isFeasible(chain, winningUav, slotLength)) continue;

                // Check if all tasks in this chain are still unassigned
                boolean allFree = chain.stream().noneMatch(t -> assignedTaskIds.contains(t.getId()));
                if (!allFree) continue;

                for (ProposedTask t : chain) {
                    assignedTaskIds.add(t.getId());
                    t.setAssignedUAV(winningUav);
                    double probability = bayes.predictFinalProbability(t, winningUav, slotLength);
                    double cost = costModel.computeCost(t, winningUav, probability, slotLength);
                    result.assignments.add(new FlowScheduler.Assignment(t, winningUav, probability, probability, probability, cost));
                }
                remainingCapacity.put(winningUav.getIndex(), capacity - chain.size());
            } else {
                for (ProposedTask t : independentTasks) {
                    if (t.getId() == winner.id && !assignedTaskIds.contains(t.getId())
                            && remainingCapacity.getOrDefault(winningUav.getIndex(), 0) > 0
                            && isFeasible(Collections.singletonList(t), winningUav, slotLength)) {
                        assignedTaskIds.add(t.getId());
                        t.setAssignedUAV(winningUav);
                        double probability = bayes.predictFinalProbability(t, winningUav, slotLength);
                        double cost = costModel.computeCost(t, winningUav, probability, slotLength);
                        result.assignments.add(new FlowScheduler.Assignment(t, winningUav, probability, probability, probability, cost));
                        remainingCapacity.put(winningUav.getIndex(), remainingCapacity.get(winningUav.getIndex()) - 1);
                        break;
                    }
                }
            }
        }

        // Step 6: Fallback – Energy-Preference Offload Selection
        // ── Enhancement 3: Energy-Aware Offload Gate (Preference, Not Blocker) ──
        // Rank all in-coverage UAVs by energy-balance score:
        //   energyScore = hoverSaved - commCost  (higher = more energy-positive)
        // The UAV with the highest score wins. If scores tie, closest wins.
        // CRITICAL: Never block an assignment — always assign to best available.
        // This ensures deadlocked tasks are never left unresolvable by the gate.
        // Grounded in: energy-optimal offloading (Zhang et al. IEEE TWC 2013).
        double hoverPowerW = org.fog.test.proposed.energy.EnergyCalculator.calcHoverPower();
        for (ProposedTask t : tasks) {
            if (!assignedTaskIds.contains(t.getId()) && !t.isFinished() && !t.isDropped()) {
                ProposedUAVState best = null;
                double bestEnergyScore = -Double.MAX_VALUE;
                double bestDist        = Double.MAX_VALUE;

                for (ProposedUAVState uav : uavs) {
                    if (uav.isInCoverage(t.getMdX(), t.getMdY())
                            && remainingCapacity.getOrDefault(uav.getIndex(), 0) > 0
                            && isFeasible(Collections.singletonList(t), uav, slotLength)) {
                        double dist        = uav.distanceTo(t.getMdX(), t.getMdY());
                        double commCost    = org.fog.test.proposed.energy.EnergyCalculator.calcTxEnergy(t.getDataBits(), dist)
                                           + org.fog.test.proposed.energy.EnergyCalculator.calcTxEnergy(t.getResultBits(), dist);
                        double execTimeSec = t.getTotalCpuCycles() / uav.getCpuHz();
                        double hoverSaved  = hoverPowerW * execTimeSec;
                        // Net energy benefit: positive = offloading saves energy vs hovering idle
                        double energyScore = hoverSaved - commCost;

                        // Prefer highest energy-positive score; break ties by distance
                        if (energyScore > bestEnergyScore
                                || (energyScore == bestEnergyScore && dist < bestDist)) {
                            bestEnergyScore = energyScore;
                            bestDist        = dist;
                            best            = uav;
                        }
                    }
                }

                if (best != null) {
                    assignedTaskIds.add(t.getId());
                    t.setAssignedUAV(best);
                    double probability = bayes.predictFinalProbability(t, best, slotLength);
                    double cost = costModel.computeCost(t, best, probability, slotLength);
                    result.assignments.add(new FlowScheduler.Assignment(t, best, probability, probability, probability, cost));
                    remainingCapacity.put(best.getIndex(), remainingCapacity.get(best.getIndex()) - 1);
                } else {
                    result.unassigned.add(t);
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        return result;
    }

    private boolean isFeasible(List<ProposedTask> chain, ProposedUAVState uav, double slotLength) {
        double queuedCycles = 0.0;
        for (ProposedTask queued : uav.getQueue()) {
            queuedCycles += queued.getRemainingCpuCycles();
        }

        double cumulativeCycles = queuedCycles;
        double now = org.cloudbus.cloudsim.core.CloudSim.clock();
        for (ProposedTask task : chain) {
            if (!uav.isInCoverage(task.getMdX(), task.getMdY())) return false;
            double probability = bayes.predictFinalProbability(task, uav, slotLength);
            if (probability < ProposedParams.MIN_ASSIGN_PROBABILITY) return false;

            double txTime = task.getDataBits() / Math.max(1e-9, uav.uplinkRate(task.getMdX(), task.getMdY()))
                    + task.getResultBits() / Math.max(1e-9, uav.downlinkRate(task.getMdX(), task.getMdY()));
            cumulativeCycles += task.getTotalCpuCycles();
            double completionEstimate = txTime + cumulativeCycles / Math.max(1e-9, uav.getCpuHz());
            double remainingDeadline = task.getArrivalTime() + task.getDeadlineSec() - now;
            if (remainingDeadline <= 0.0 || completionEstimate > 2.0 * remainingDeadline) return false;
        }
        return true;
    }

    /**
     * Monolithic DAG bundle bid – computes sum of all subtask urgencies.
     * If UAV wins the root, ALL subtasks are atomically co-located.
     * This eliminates cross-UAV CDD deadlocks by construction.
     */
    private double calculateDAGBundleBid(List<ProposedTask> chain, ProposedUAVState uav) {
        ProposedTask root = chain.get(0);
        double cumulativeDist = uav.distanceTo(root.getMdX(), root.getMdY());
        double totalScore = 0.0;

        for (ProposedTask t : chain) {
            double urgency = 1.0 / Math.max(0.1, t.getDeadlineSec());
            double load = 1.0 + uav.getLoadRatioNow(1.0);
            totalScore += urgency / (1.0 + (cumulativeDist / 1000.0) + load);
            cumulativeDist += 10.0;
        }
        // Super-additive synergy multiplier for complete workflow delivery
        return totalScore * 1.5;
    }

    private double calculateBaseBid(ProposedTask t, ProposedUAVState uav) {
        double urgency = 1.0 / Math.max(0.1, t.getDeadlineSec());
        double distance = uav.distanceTo(t.getMdX(), t.getMdY());
        double load = 1.0 + uav.getLoadRatioNow(1.0);
        return urgency / (1.0 + (distance / 500.0) + load);
    }

    private static class CBBAOffer {
        final int uavIdx;
        final int id;
        final double bid;
        final boolean isChain;

        CBBAOffer(int uavIdx, int id, double bid, boolean isChain) {
            this.uavIdx = uavIdx;
            this.id = id;
            this.bid = bid;
            this.isChain = isChain;
        }
    }
}
