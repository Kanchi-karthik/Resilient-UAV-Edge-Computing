package org.fog.test.proposed.coordination.wu2025;

import org.fog.test.proposed.ProposedTask;
import org.fog.test.proposed.ProposedUAVState;

import java.util.*;

/**
 * WuGeneticAlgorithm - Distributed GA with DFS Deadlock Filtering (Scientific Reports 2025).
 */
public class WuGeneticAlgorithm {

    private final Random rand;
    private final int popSize = 40;
    private final int maxGen = 60;
    private final double pc = 0.6;
    private final double pm = 0.5;

    public WuGeneticAlgorithm(long seed) {
        this.rand = new Random(seed);
    }

    public WuChromosome solve(List<WuTask> tasks, List<ProposedUAVState> uavs, double slotLength) {
        int length = tasks.size();
        if (length == 0) return null;

        List<WuChromosome> pop = initPopulation(length, tasks, uavs.size());

        for (int gen = 0; gen < maxGen; gen++) {
            // Evaluate fitness and filter deadlocks using DFS
            for (WuChromosome ch : pop) {
                if (WuTimingGraphDFS.checkChromosomeDeadlock(ch)) {
                    ch.deadlocked = true;
                    ch.fitness = 0.0001; // Deadlock penalty
                } else {
                    ch.deadlocked = false;
                    ch.fitness = evaluateFitness(ch, tasks, uavs, slotLength);
                }
            }

            // Elite preservation
            pop.sort((a, b) -> Double.compare(b.fitness, a.fitness));
            List<WuChromosome> nextGen = new ArrayList<>();
            nextGen.add(pop.get(0).clone());
            if (pop.size() > 1) nextGen.add(pop.get(1).clone());

            // Selection & Crossover
            List<WuChromosome> selected = tournamentSelect(pop);
            for (int i = 0; i < selected.size() - 1; i += 2) {
                if (rand.nextDouble() < pc) {
                    List<WuChromosome> children = crossover(selected.get(i), selected.get(i + 1));
                    nextGen.addAll(children);
                } else {
                    nextGen.add(selected.get(i).clone());
                    nextGen.add(selected.get(i + 1).clone());
                }
            }

            // Mutation
            for (WuChromosome ch : nextGen) {
                if (rand.nextDouble() < pm) {
                    mutate(ch, uavs.size());
                }
            }

            // Keep pop size
            while (nextGen.size() > popSize) {
                nextGen.remove(nextGen.size() - 1);
            }
            pop = nextGen;
        }

        // Final evaluation and sort
        for (WuChromosome ch : pop) {
            if (WuTimingGraphDFS.checkChromosomeDeadlock(ch)) {
                ch.deadlocked = true;
                ch.fitness = 0.0001;
            } else {
                ch.deadlocked = false;
                ch.fitness = evaluateFitness(ch, tasks, uavs, slotLength);
            }
        }
        pop.sort((a, b) -> Double.compare(b.fitness, a.fitness));
        return pop.get(0);
    }

    private List<WuChromosome> initPopulation(int length, List<WuTask> tasks, int numUavs) {
        List<WuChromosome> pop = new ArrayList<>();
        for (int i = 0; i < popSize; i++) {
            WuChromosome ch = new WuChromosome(length);
            for (int j = 0; j < length; j++) {
                ch.targetId[j] = tasks.get(j).getDepGroupId();
                ch.taskType[j] = (tasks.get(j).getDepSeq() % 3) + 1;
                ch.pathAngle[j] = rand.nextDouble() * 360.0;
                ch.uavId[j] = rand.nextInt(numUavs);
            }
            pop.add(ch);
        }
        return pop;
    }

    private double evaluateFitness(WuChromosome ch, List<WuTask> tasks, List<ProposedUAVState> uavs, double slotLength) {
        double totalDist = 0.0;
        double completionBonus = 0.0;
        int[] uavTasks = new int[uavs.size()];

        for (int i = 0; i < ch.targetId.length; i++) {
            int uavIdx = ch.uavId[i];
            if (uavIdx < 0 || uavIdx >= uavs.size()) continue;

            ProposedUAVState u = uavs.get(uavIdx);
            WuTask t = tasks.get(i);
            double d = u.distanceTo(t.getMdX(), t.getMdY());
            totalDist += d;
            uavTasks[uavIdx]++;

            if (u.isInCoverage(t.getMdX(), t.getMdY())) {
                completionBonus += 50.0;
            }
        }

        // Load balance factor
        double variance = 0.0;
        double mean = (double) ch.targetId.length / uavs.size();
        for (int count : uavTasks) {
            variance += Math.pow(count - mean, 2);
        }

        return (10000.0 / (totalDist + 1.0)) + completionBonus - (variance * 2.0);
    }

    private List<WuChromosome> tournamentSelect(List<WuChromosome> pop) {
        List<WuChromosome> selected = new ArrayList<>();
        int tournamentSize = 3;
        for (int i = 0; i < pop.size(); i++) {
            WuChromosome best = null;
            for (int j = 0; j < tournamentSize; j++) {
                WuChromosome cand = pop.get(rand.nextInt(pop.size()));
                if (best == null || cand.fitness > best.fitness) {
                    best = cand;
                }
            }
            selected.add(best != null ? best.clone() : pop.get(0).clone());
        }
        return selected;
    }

    private List<WuChromosome> crossover(WuChromosome p1, WuChromosome p2) {
        int length = p1.targetId.length;
        int cut = rand.nextInt(length);
        WuChromosome c1 = p1.clone();
        WuChromosome c2 = p2.clone();

        for (int i = cut; i < length; i++) {
            c1.uavId[i] = p2.uavId[i];
            c2.uavId[i] = p1.uavId[i];
            c1.pathAngle[i] = p2.pathAngle[i];
            c2.pathAngle[i] = p1.pathAngle[i];
        }
        return Arrays.asList(c1, c2);
    }

    private void mutate(WuChromosome ch, int numUavs) {
        int idx = rand.nextInt(ch.targetId.length);
        int type = rand.nextInt(3);
        if (type == 0) {
            ch.pathAngle[idx] = rand.nextDouble() * 360.0; // Heading variation
        } else if (type == 1) {
            ch.uavId[idx] = rand.nextInt(numUavs);          // UAV variation
        } else {
            int other = rand.nextInt(ch.targetId.length);   // Task order variation
            int tmpU = ch.uavId[idx]; ch.uavId[idx] = ch.uavId[other]; ch.uavId[other] = tmpU;
            double tmpA = ch.pathAngle[idx]; ch.pathAngle[idx] = ch.pathAngle[other]; ch.pathAngle[other] = tmpA;
        }
    }
}
