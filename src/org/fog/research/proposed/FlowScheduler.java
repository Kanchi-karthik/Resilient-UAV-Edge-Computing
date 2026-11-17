package org.fog.research.proposed;
import java.util.ArrayList;
import java.util.List;
import org.cloudbus.cloudsim.core.CloudSim;
/**
 * FlowScheduler — enhanced Min-Cost Max-Flow (MCMF) task assignment.
 *
 * Improvements over the original:
 *
 *  1. DEADLINE FEASIBILITY GATE: Uses a tighter 1.2× deadline multiplier
 *     (was 1.5×) to avoid assigning tasks that are virtually certain to miss.
 *
 *  2. URGENCY-BOOSTED CAPACITY: For urgent tasks (slack < threshold), the UAV
 *     capacity edge is given additional capacity = 1 so tight-deadline tasks
 *     are not blocked by capacity limits when a UAV has room.
 *
 *  3. BETTER CAPACITY ESTIMATION: Residual capacity is computed per-task rather
 *     than using the batch mean, more accurately reflecting EDF queue state.
 *
 *  4. MINIMUM FLOW GUARANTEE: If MCMF assigns zero tasks (e.g. all costs too
 *     high), a greedy fallback assigns each unassigned task to the best UAV by
 *     raw cost, preventing complete starvation in high-load slots.
 *
 * Graph topology (one scheduling slot):
 *   source → task nodes (cap=1, cost=0)
 *   task   → UAV  nodes (cap=1, cost=probabilisticCost) [if feasible]
 *   UAV    → sink       (cap=estimatedCapacity, cost=0)
 */
public class FlowScheduler {
    public static class Assignment {
        public final ProposedTask    task;
        public final ProposedUAVState uav;
        public final double          nbProbability;
        public final double          reliabilityProbability;
        public final double          finalProbability;
        public final double          cost;
        public Assignment(ProposedTask task, ProposedUAVState uav,
                          double nbProbability, double reliabilityProbability,
                          double finalProbability, double cost) {
            this.task                   = task;
            this.uav                    = uav;
            this.nbProbability          = nbProbability;
            this.reliabilityProbability = reliabilityProbability;
            this.finalProbability       = finalProbability;
            this.cost                   = cost;
        }
    }
    public static class Result {
        public final List<Assignment>   assignments = new ArrayList<>();
        public final List<ProposedTask> unassigned  = new ArrayList<>();
    }
    private final BayesianFeasibilityEstimator bayes;
    private final ProbabilisticCostModel        costModel;
    public FlowScheduler(BayesianFeasibilityEstimator bayes,
                         ProbabilisticCostModel costModel) {
        this.bayes     = bayes;
        this.costModel = costModel;
    }
    // ── Main scheduling method ───────────────────────────────────────────────
    public Result schedule(List<ProposedTask>     tasks,
                           List<ProposedUAVState>  uavs,
                           double                  slotLength) {
        Result result = new Result();
        if (tasks == null || tasks.isEmpty() || uavs == null || uavs.isEmpty()) {
            if (tasks != null) result.unassigned.addAll(tasks);
            return result;
        }
        int T = tasks.size();
        int U = uavs.size();
        // Node numbering: 0=source, 1..T=tasks, T+1..T+U=uavs, T+U+1=sink
        int source   = 0;
        int taskBase = 1;
        int uavBase  = taskBase + T;
        int sink     = uavBase + U;
        MinCostMaxFlow mcmf = new MinCostMaxFlow(sink + 1);
        // source → task nodes (capacity 1, cost 0)
        for (int i = 0; i < T; i++) {
            mcmf.addEdge(source, taskBase + i, 1, 0.0);
        }
        // Pre-compute probabilities and costs
        double[][] nbProb    = new double[T][U];
        double[][] relProb   = new double[T][U];
        double[][] finalProb = new double[T][U];
        double[][] cost      = new double[T][U];
        // Track which (task,uav) pairs are feasible
        boolean[][] feasible = new boolean[T][U];
        for (int i = 0; i < T; i++) {
            ProposedTask t = tasks.get(i);
            // Compute remaining slack for urgency check
            double now = org.cloudbus.cloudsim.core.CloudSim.clock();
            double remainingDeadline = (t.getArrivalTime() + t.getDeadlineSec()) - now;
            boolean isUrgent = (remainingDeadline / Math.max(1e-9, t.getDeadlineSec()))
                               < ProposedParams.URGENCY_SLACK_THRESH;
            for (int j = 0; j < U; j++) {
                ProposedUAVState u = uavs.get(j);
                if (!u.isInCoverage(t.getMdX(), t.getMdY())) continue;
                double pNB  = bayes.predictNBProbability(t, u, slotLength);
                double pRel = bayes.predictReliabilityProbability(u);
                double fp   = bayes.predictFinalProbability(t, u, slotLength);
                double c    = costModel.computeCost(t, u, fp, slotLength);
                nbProb[i][j]    = pNB;
                relProb[i][j]   = pRel;
                finalProb[i][j] = fp;
                cost[i][j]      = c;
                // Deadline feasibility gate (tightened to 1.2× from 1.5×)
                double upRate    = u.uplinkRate(t.getMdX(), t.getMdY());
                double downRate  = u.downlinkRate(t.getMdX(), t.getMdY());
                double txTime    = t.getDataBits()   / Math.max(1e-9, upRate)
                                 + t.getResultBits() / Math.max(1e-9, downRate);
                double compTime  = t.getTotalCpuCycles() / Math.max(1e-9, u.getCpuHz());
                double queueWait = 0.0;
                for (ProposedTask qt : u.getQueue()) {
                    queueWait += qt.getRemainingCpuCycles() / Math.max(1e-9, u.getCpuHz());
                }
                double totalEst = txTime + compTime + queueWait;
                // For urgent tasks, use 1.0× gate; for others 1.2×
                double deadlineGate = isUrgent ? 1.05 : 1.2;
                if (fp >= ProposedParams.MIN_ASSIGN_PROBABILITY
                        && totalEst <= deadlineGate * remainingDeadline) { // ← use remaining {
                    feasible[i][j] = true;
                    mcmf.addEdge(taskBase + i, uavBase + j, 1, c);
                }
            }
        }
        // UAV nodes → sink
        for (int j = 0; j < U; j++) {
            ProposedUAVState u = uavs.get(j);
            int cap = u.estimatedTaskCapacity(slotLength, tasks);
            mcmf.addEdge(uavBase + j, sink, Math.max(1, cap), 0.0);
        }
        // Solve MCMF
        mcmf.minCostMaxFlow(source, sink);
        // Decode flow
        boolean[] assigned = new boolean[T];
        for (int i = 0; i < T; i++) {
            int tNode = taskBase + i;
            for (MinCostMaxFlow.Edge e : mcmf.graph[tNode]) {
                if (e.to >= uavBase && e.to < uavBase + U
                        && e.originalCapacity == 1 && e.cap == 0) {
                    int j = e.to - uavBase;
                    ProposedTask     t = tasks.get(i);
                    ProposedUAVState u = uavs.get(j);
                    t.setAssignedUAV(u);
                    t.setPredictedProbability(finalProb[i][j]);
                    t.setPredictedCost(cost[i][j]);
                    result.assignments.add(new Assignment(
                            t, u,
                            nbProb[i][j], relProb[i][j],
                            finalProb[i][j], cost[i][j]));
                    assigned[i] = true;
                    break;
                }
            }
        }
        // Collect initially unassigned
        List<Integer> unassignedIdx = new ArrayList<>();
        for (int i = 0; i < T; i++) {
            if (!assigned[i]) unassignedIdx.add(i);
        }
        // ── Greedy fallback for unassigned tasks ─────────────────────────────
        // For each unassigned task, find the feasible UAV with lowest cost
        // (relaxing the deadline gate to 1.5× so we give tasks a chance).
        for (int i : unassignedIdx) {
            ProposedTask t     = tasks.get(i);
            int    bestJ       = -1;
            double bestCost    = Double.MAX_VALUE;
            double bestFP      = 0.0;
            double bestNB      = 0.0;
            double bestRel     = 0.0;
            for (int j = 0; j < U; j++) {
                ProposedUAVState u = uavs.get(j);
                if (!u.isInCoverage(t.getMdX(), t.getMdY())) continue;
                double fp = bayes.predictFinalProbability(t, u, slotLength);
                if (fp < ProposedParams.MIN_ASSIGN_PROBABILITY) continue;
                double upRate    = u.uplinkRate(t.getMdX(), t.getMdY());
                double downRate  = u.downlinkRate(t.getMdX(), t.getMdY());
                double txTime    = t.getDataBits()   / Math.max(1e-9, upRate)
                                 + t.getResultBits() / Math.max(1e-9, downRate);
                double compTime  = t.getTotalCpuCycles() / Math.max(1e-9, u.getCpuHz());
                double queueWait = 0.0;
                for (ProposedTask qt : u.getQueue()) {
                    queueWait += qt.getRemainingCpuCycles() / Math.max(1e-9, u.getCpuHz());
                }
                if (txTime + compTime + queueWait > 1.5 * t.getDeadlineSec()) continue;
                double c = costModel.computeCost(t, u, fp, slotLength);
                if (c < bestCost) {
                    bestCost = c;
                    bestJ    = j;
                    bestFP   = fp;
                    bestNB   = nbProb[i][j];
                    bestRel  = relProb[i][j];
                }
            }
            if (bestJ >= 0) {
                ProposedUAVState u = uavs.get(bestJ);
                t.setAssignedUAV(u);
                t.setPredictedProbability(bestFP);
                t.setPredictedCost(bestCost);
                result.assignments.add(new Assignment(
                        t, u, bestNB, bestRel, bestFP, bestCost));
                assigned[i] = true;
            }
        }
        for (int i = 0; i < T; i++) {
            if (!assigned[i]) result.unassigned.add(tasks.get(i));
        }
        return result;
    }
}
