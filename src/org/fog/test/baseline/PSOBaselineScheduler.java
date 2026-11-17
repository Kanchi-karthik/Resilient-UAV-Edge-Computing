package org.fog.test.baseline;
import org.fog.test.baseline.ProposedTask;
import org.fog.test.baseline.ProposedUAVState;
import java.util.*;
/**
 * Particle Swarm Optimisation (PSO) Baseline Scheduler.
 *
 * Each particle represents a complete task-to-UAV assignment:
 *   position[i] ∈ {-1, 0, 1, …, U-1}  (integer, -1 = unassigned)
 *   velocity[i] ∈ ℝ  (continuous; rounded to nearest int on update)
 *
 * PSO update rule (Clerc-Kennedy constriction variant):
 *   v_i(t+1) = χ * [w*v_i(t) + c1*r1*(pBest_i - x_i) + c2*r2*(gBest_i - x_i)]
 *   x_i(t+1) = clamp(round(x_i(t) + v_i(t+1)), -1, U-1)
 *
 * Fitness: BaselineSchedulingUtils.mgaFitness (deadline-slack + load balance;
 *          NO probability estimates).
 *
 * Seeding: particle 0 = greedy solution; rest = random feasible assignments.
 * Repair: genes that violate coverage are reset to -1 after each update.
 */
public class PSOBaselineScheduler implements BaselineScheduler {
    private static final int    SWARM_SIZE = 30;
    private static final int    ITERATIONS = 30;
    private static final double W          = 0.72;    // inertia
    private static final double C1         = 1.49;    // cognitive
    private static final double C2         = 1.49;    // social
    private static final double V_MAX      = 3.0;     // velocity clamp
    private final Random rand;
    public PSOBaselineScheduler(long seed) {
        this.rand = new Random(seed);
    }
    private static class Particle {
        int[]    pos;
        double[] vel;
        int[]    pBest;
        double   pBestFit;
        Particle(int n) {
            pos      = new int[n];
            vel      = new double[n];
            pBest    = new int[n];
            pBestFit = -Double.MAX_VALUE;
        }
    }
    @Override
    public Result schedule(List<ProposedTask> tasks,
                           List<ProposedUAVState> uavs,
                           double slotLength) {
        Result result = new Result();
        if (tasks == null || tasks.isEmpty() || uavs == null || uavs.isEmpty()) {
            if (tasks != null) result.unassigned.addAll(tasks);
            return result;
        }
        int n = tasks.size();
        int U = uavs.size();
        // Greedy seed for particle 0
        int[] greedySeed = greedyTemplate(tasks, uavs, slotLength);
        // Initialise swarm
        List<Particle> swarm      = new ArrayList<>();
        int[]          gBest      = null;
        double         gBestFit   = -Double.MAX_VALUE;
        for (int s = 0; s < SWARM_SIZE; s++) {
            Particle p = new Particle(n);
            if (s == 0) {
                p.pos = greedySeed.clone();
            } else {
                randomInit(p.pos, tasks, uavs, slotLength);
            }
            for (int i = 0; i < n; i++) {
                p.vel[i] = (rand.nextDouble() - 0.5) * 2.0;
            }
            double fit = BaselineSchedulingUtils.mgaFitness(tasks, p.pos, uavs, slotLength);
            p.pBestFit = fit;
            p.pBest    = p.pos.clone();
            if (fit > gBestFit) { gBestFit = fit; gBest = p.pos.clone(); }
            swarm.add(p);
        }
        // PSO main loop
        for (int iter = 0; iter < ITERATIONS; iter++) {
            for (Particle p : swarm) {
                for (int i = 0; i < n; i++) {
                    double r1 = rand.nextDouble();
                    double r2 = rand.nextDouble();
                    p.vel[i] = W * p.vel[i]
                             + C1 * r1 * (p.pBest[i] - p.pos[i])
                             + C2 * r2 * (gBest[i]   - p.pos[i]);
                    p.vel[i] = Math.max(-V_MAX, Math.min(V_MAX, p.vel[i]));
                    int newPos = (int) Math.round(p.pos[i] + p.vel[i]);
                    p.pos[i]  = Math.max(-1, Math.min(U - 1, newPos));
                }
                repair(p.pos, tasks, uavs);
                double fit = BaselineSchedulingUtils.mgaFitness(tasks, p.pos, uavs, slotLength);
                if (fit > p.pBestFit) { p.pBestFit = fit; p.pBest = p.pos.clone(); }
                if (fit > gBestFit)   { gBestFit   = fit; gBest   = p.pos.clone(); }
            }
        }
        // Decode global best
        int[] capLeft = new int[U];
        for (int j = 0; j < U; j++) {
            capLeft[j] = BaselineSchedulingUtils.capacityGuess(uavs.get(j), slotLength, tasks);
        }
        boolean[] assigned = new boolean[n];
        for (int i = 0; i < n; i++) {
            int uj = gBest[i];
            if (uj < 0 || uj >= U) continue;
            ProposedTask    t = tasks.get(i);
            ProposedUAVState u = uavs.get(uj);
            if (capLeft[uj] <= 0 || !BaselineSchedulingUtils.isReachable(t, u)) continue;
            double svc = BaselineSchedulingUtils.estimatedServiceTime(t, u, slotLength);
            if (svc > 2.0 * t.getDeadlineSec()) continue;
            capLeft[uj]--;
            assigned[i] = true;
            t.setAssignedUAV(u);
            t.setPredictedCost(svc);
            result.assignments.add(new Assignment(t, u, svc));
        }
        for (int i = 0; i < n; i++) {
            if (!assigned[i]) result.unassigned.add(tasks.get(i));
        }
        return result;
    }
    // ── Helpers ───────────────────────────────────────────────────────────────
    private int[] greedyTemplate(List<ProposedTask> tasks,
                                  List<ProposedUAVState> uavs,
                                  double slotLength) {
        int[] gene = new int[tasks.size()];
        Arrays.fill(gene, -1);
        BaselineScheduler.Result r =
                new GreedyBaselineScheduler().schedule(tasks, uavs, slotLength);
        for (BaselineScheduler.Assignment a : r.assignments) {
            int ti = tasks.indexOf(a.task);
            int uj = uavs.indexOf(a.uav);
            if (ti >= 0 && uj >= 0) gene[ti] = uj;
        }
        return gene;
    }
    private void randomInit(int[] pos, List<ProposedTask> tasks,
                            List<ProposedUAVState> uavs, double slotLength) {
        Arrays.fill(pos, -1);
        for (int i = 0; i < tasks.size(); i++) {
            List<ProposedUAVState> cands =
                    BaselineSchedulingUtils.rankedUavsForTask(tasks.get(i), uavs, slotLength);
            if (!cands.isEmpty()) {
                int pick = rand.nextInt(cands.size() + 1);
                pos[i]   = (pick == cands.size()) ? -1 : uavs.indexOf(cands.get(pick));
            }
        }
    }
    private void repair(int[] pos, List<ProposedTask> tasks, List<ProposedUAVState> uavs) {
        for (int i = 0; i < pos.length; i++) {
            int uj = pos[i];
            if (uj < 0 || uj >= uavs.size()) continue;
            if (!BaselineSchedulingUtils.isReachable(tasks.get(i), uavs.get(uj))) pos[i] = -1;
        }
    }
}
