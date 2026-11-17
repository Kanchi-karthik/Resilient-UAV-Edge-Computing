package org.fog.test.baseline;
import org.fog.test.baseline.ProposedTask;
import org.fog.test.baseline.ProposedUAVState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
/**
 * Utility methods shared by all baseline schedulers.
 * All methods are purely deterministic — no probabilities used.
 */
public class BaselineSchedulingUtils {
    private BaselineSchedulingUtils() {}
    /**
     * Estimated total service time for a task on a UAV (seconds):
     *   = transmission time (up + down) + computation time + queue wait time
     */
    public static double estimatedServiceTime(ProposedTask task,
                                              ProposedUAVState uav,
                                              double slotLength) {
        double upRate   = uav.uplinkRate(task.getMdX(),   task.getMdY());
        double downRate = uav.downlinkRate(task.getMdX(), task.getMdY());
        double txTime   = task.getDataBits()   / Math.max(1e-9, upRate)
                        + task.getResultBits() / Math.max(1e-9, downRate);
        double compTime = task.getTotalCpuCycles() / Math.max(1e-9, uav.getCpuHz());
        // Queue wait: existing queued CPU cycles / CPU speed
        double queueWait = 0.0;
        for (ProposedTask qt : uav.getQueue()) {
            queueWait += qt.getRemainingCpuCycles() / Math.max(1e-9, uav.getCpuHz());
        }
        return txTime + compTime + queueWait;
    }
    /**
     * Conservative capacity estimate: how many tasks (of the current batch's
     * mean size) can this UAV still process in one slot?
     */
    public static int capacityGuess(ProposedUAVState uav,
                                    double slotLength,
                                    List<ProposedTask> tasks) {
        double slotCycles  = uav.getCpuHz() * slotLength;
        double queueCycles = 0.0;
        for (ProposedTask qt : uav.getQueue()) {
            queueCycles += qt.getRemainingCpuCycles();
        }
        double freeCycles = Math.max(0.0, slotCycles - queueCycles);
        double meanCycles = 1e8; // default
        if (tasks != null && !tasks.isEmpty()) {
            double sum = 0.0;
            for (ProposedTask t : tasks) sum += t.getTotalCpuCycles();
            meanCycles = Math.max(1.0, sum / tasks.size());
        }
        return Math.max(1, (int) Math.floor(freeCycles / meanCycles));
    }
    /**
     * True iff the UAV covers the task's MD location.
     */
    public static boolean isReachable(ProposedTask task, ProposedUAVState uav) {
        return uav.isInCoverage(task.getMdX(), task.getMdY());
    }
    /**
     * Return candidate UAVs for a task, sorted by ascending estimated
     * service time (best = fastest first). Excludes out-of-coverage UAVs.
     */
    public static List<ProposedUAVState> rankedUavsForTask(ProposedTask task,
                                                            List<ProposedUAVState> uavs,
                                                            double slotLength) {
        List<ProposedUAVState> cands = new ArrayList<>();
        for (ProposedUAVState u : uavs) {
            if (isReachable(task, u)) cands.add(u);
        }
        cands.sort(Comparator.comparingDouble(
                u -> estimatedServiceTime(task, u, slotLength)));
        return cands;
    }
    /**
     * Fitness function used by GA and PSO:
     *   sum over tasks assigned: priority_weight / service_time
     *   Capacity-overflow and deadline-violation penalties are subtracted.
     */
    public static double mgaFitness(List<ProposedTask> tasks,
                                    int[] gene,
                                    List<ProposedUAVState> uavs,
                                    double slotLength) {
        if (gene == null) return -1e9;
        int[] loads = new int[uavs.size()];
        int[] caps  = new int[uavs.size()];
        for (int j = 0; j < uavs.size(); j++) {
            caps[j] = Math.max(1, capacityGuess(uavs.get(j), slotLength, tasks));
        }
        double fitness = 0.0;
        for (int i = 0; i < gene.length; i++) {
            int uj = gene[i];
            if (uj < 0 || uj >= uavs.size()) continue;
            ProposedTask    t = tasks.get(i);
            ProposedUAVState u = uavs.get(uj);
            if (!isReachable(t, u)) { fitness -= 0.5; continue; }
            if (loads[uj] >= caps[uj]) { fitness -= 0.3; continue; }
            loads[uj]++;
            double svc    = estimatedServiceTime(t, u, slotLength);
            double slack  = t.getDeadlineSec() - svc;
            if (slack < 0) {
                fitness -= 0.4;   // deadline penalty
            } else {
                // Reward: normalised slack + load balance term
                double normSlack   = Math.min(1.0, slack / Math.max(1e-9, t.getDeadlineSec()));
                double loadBalance = 1.0 - (double) loads[uj] / caps[uj];
                double energyBonus = u.getResidualEnergyRatio();
                fitness += 0.50 * normSlack
                         + 0.25 * loadBalance
                         + 0.25 * energyBonus;
            }
        }
        return fitness;
    }
}
