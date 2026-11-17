package org.fog.test.proposed.coordination.wu2025;

import org.fog.test.proposed.ProposedTask;
import org.fog.test.proposed.ProposedUAVState;

import java.util.*;

/**
 * WuScheduler - Integrated Task Assignment and Trajectory Planning (Scientific Reports 2025).
 */
public class WuScheduler {

    private final WuGeneticAlgorithm ga;
    private static final double GA_COMM_ENERGY_PER_SLOT = 0.045; // J per UAV per GA run

    public WuScheduler(long seed) {
        this.ga = new WuGeneticAlgorithm(seed);
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

        // Energy consumption for GA coordination and chromosome migration
        for (ProposedUAVState u : uavs) {
            u.addCoordinationEnergy(GA_COMM_ENERGY_PER_SLOT);
        }

        // Convert to WuTasks
        List<WuTask> tasks = new ArrayList<>();
        for (ProposedTask pt : proposedTasks) {
            tasks.add(new WuTask(pt));
        }

        // Solve via Distributed GA with DFS deadlock filtering
        WuChromosome best = ga.solve(tasks, uavs, slotLength);

        if (best != null && !best.deadlocked) {
            for (int i = 0; i < best.targetId.length; i++) {
                if (i < tasks.size()) {
                    int uavIdx = best.uavId[i];
                    WuTask t = tasks.get(i);
                    if (uavIdx >= 0 && uavIdx < uavs.size()) {
                        t.setAssignedUAV(uavs.get(uavIdx));
                        assignment.get(uavIdx).add(t);
                    }
                }
            }
        }

        return assignment;
    }
}
