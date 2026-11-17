package org.fog.test.baseline;
import org.fog.test.baseline.ProposedTask;
import org.fog.test.baseline.ProposedUAVState;
import java.util.*;
/**
 * Modified Genetic Algorithm (MGA) Baseline Scheduler.
 *
 * Chromosome: int[] of length |tasks|
 *   gene[i] = UAV index assigned to task i, or -1 (unassigned)
 *
 * Fitness (BaselineSchedulingUtils.mgaFitness):
 *   Rewards deadline slack and load balance; penalises capacity overflow
 *   and deadline violations. NO probability estimates.
 *
 * Operators:
 *   – Greedy seeding: first individual = greedy solution
 *   – Cut-and-splice crossover (two different cut points)
 *   – Per-gene mutation: re-assign to a random feasible UAV
 *   – Repair: remove genes that violate coverage
 *   – Elite preservation: top ELITE_K individuals survive
 *
 * Multi-era restart: best individual from each era seeds the next.
 */
public class MGABaselineScheduler implements BaselineScheduler {
    private static final int    POP_SIZE    = 30;
    private static final int    ERAS        = 4;
    private static final int    GENERATIONS = 20;
    private static final int    TOURNAMENT  = 4;
    private static final int    ELITE_K     = 3;
    private final Random rand;
    public MGABaselineScheduler(long seed) {
        this.rand = new Random(seed);
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
        // Seed from greedy
        int[] template = greedyTemplate(tasks, uavs, slotLength);
        int[] bestGene = template.clone();
        double bestFit = BaselineSchedulingUtils.mgaFitness(tasks, bestGene, uavs, slotLength);
        for (int era = 0; era < ERAS; era++) {
            List<int[]> pop = initPop(template, tasks, uavs, slotLength);
            for (int gen = 0; gen < GENERATIONS; gen++) {
                // Evaluate
                double[] fit = new double[pop.size()];
                for (int i = 0; i < pop.size(); i++) {
                    fit[i] = BaselineSchedulingUtils.mgaFitness(tasks, pop.get(i), uavs, slotLength);
                    if (fit[i] > bestFit) { bestFit = fit[i]; bestGene = pop.get(i).clone(); }
                }
                // Elite selection
                List<int[]> next = new ArrayList<>();
                Integer[] order = argsortDesc(fit);
                for (int k = 0; k < Math.min(ELITE_K, pop.size()); k++) next.add(pop.get(order[k]).clone());
                // Fill rest via crossover + mutation
                while (next.size() < POP_SIZE) {
                    int[] p1 = tournament(pop, tasks, uavs, slotLength);
                    int[] p2 = tournament(pop, tasks, uavs, slotLength);
                    int[][] children = cutAndSplice(p1, p2, n);
                    for (int[] child : children) {
                        mutate(child, tasks, uavs, slotLength);
                        repair(child, tasks, uavs);
                        next.add(child);
                        if (next.size() >= POP_SIZE) break;
                    }
                }
                pop = next;
            }
            template = bestGene.clone(); // seed next era from best
        }
        // Decode best chromosome
        int[] capLeft = new int[uavs.size()];
        for (int j = 0; j < uavs.size(); j++) {
            capLeft[j] = BaselineSchedulingUtils.capacityGuess(uavs.get(j), slotLength, tasks);
        }
        boolean[] assigned = new boolean[n];
        for (int i = 0; i < n; i++) {
            int uj = bestGene[i];
            if (uj < 0 || uj >= uavs.size()) continue;
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
    // ── Chromosome operations ─────────────────────────────────────────────────
    private int[] greedyTemplate(List<ProposedTask> tasks,
                                  List<ProposedUAVState> uavs,
                                  double slotLength) {
        int[] gene = new int[tasks.size()];
        Arrays.fill(gene, -1);
        Result r = new GreedyBaselineScheduler().schedule(tasks, uavs, slotLength);
        for (BaselineScheduler.Assignment a : r.assignments) {
            int ti = tasks.indexOf(a.task);
            int uj = uavs.indexOf(a.uav);
            if (ti >= 0 && uj >= 0) gene[ti] = uj;
        }
        return gene;
    }
    private List<int[]> initPop(int[] template,
                                 List<ProposedTask> tasks,
                                 List<ProposedUAVState> uavs,
                                 double slotLength) {
        List<int[]> pop = new ArrayList<>();
        pop.add(template.clone());
        while (pop.size() < POP_SIZE) {
            int[] g = template.clone();
            int changes = Math.max(1, g.length / 4);
            for (int c = 0; c < changes; c++) {
                int i = rand.nextInt(g.length);
                List<ProposedUAVState> cands =
                        BaselineSchedulingUtils.rankedUavsForTask(tasks.get(i), uavs, slotLength);
                if (cands.isEmpty()) { g[i] = -1; }
                else {
                    int pick = rand.nextInt(cands.size() + 1);
                    g[i] = (pick == cands.size()) ? -1 : uavs.indexOf(cands.get(pick));
                }
            }
            pop.add(g);
        }
        return pop;
    }
    private int[] tournament(List<int[]> pop,
                              List<ProposedTask> tasks,
                              List<ProposedUAVState> uavs,
                              double slotLength) {
        int[] best = null; double bestFit = -Double.MAX_VALUE;
        for (int i = 0; i < Math.min(TOURNAMENT, pop.size()); i++) {
            int[] c = pop.get(rand.nextInt(pop.size()));
            double f = BaselineSchedulingUtils.mgaFitness(tasks, c, uavs, slotLength);
            if (f > bestFit) { bestFit = f; best = c; }
        }
        return best.clone();
    }
    private int[][] cutAndSplice(int[] p1, int[] p2, int n) {
        if (n <= 1) return new int[][]{ p1.clone(), p2.clone() };
        int cut1 = 1 + rand.nextInt(n - 1);
        int cut2 = 1 + rand.nextInt(n - 1);
        int[] c1 = new int[n]; int[] c2 = new int[n];
        for (int i = 0; i < cut1; i++) c1[i] = p1[i];
        for (int i = cut1; i < n; i++) c1[i] = p2[(i - cut1 + cut2) % n];
        for (int i = 0; i < cut2; i++) c2[i] = p2[i];
        for (int i = cut2; i < n; i++) c2[i] = p1[(i - cut2 + cut1) % n];
        return new int[][]{ c1, c2 };
    }
    private void mutate(int[] gene, List<ProposedTask> tasks,
                        List<ProposedUAVState> uavs, double slotLength) {
        double pm = Math.min(0.25, 2.0 / Math.max(1, gene.length));
        for (int i = 0; i < gene.length; i++) {
            if (rand.nextDouble() < pm) {
                List<ProposedUAVState> cands =
                        BaselineSchedulingUtils.rankedUavsForTask(tasks.get(i), uavs, slotLength);
                if (cands.isEmpty()) { gene[i] = -1; }
                else {
                    int r = rand.nextInt(cands.size() + 1);
                    gene[i] = (r == cands.size()) ? -1 : uavs.indexOf(cands.get(r));
                }
            }
        }
    }
    private void repair(int[] gene, List<ProposedTask> tasks, List<ProposedUAVState> uavs) {
        for (int i = 0; i < gene.length; i++) {
            int uj = gene[i];
            if (uj < 0 || uj >= uavs.size()) continue;
            if (!BaselineSchedulingUtils.isReachable(tasks.get(i), uavs.get(uj))) gene[i] = -1;
        }
    }
    private Integer[] argsortDesc(double[] arr) {
        Integer[] idx = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(arr[b], arr[a]));
        return idx;
    }
}
