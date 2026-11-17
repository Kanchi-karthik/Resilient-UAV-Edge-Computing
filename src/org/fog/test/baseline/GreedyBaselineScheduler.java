package org.fog.test.baseline;
import org.fog.test.baseline.ProposedTask;
import org.fog.test.baseline.ProposedUAVState;
import java.util.*;
/**
 * Greedy Baseline Scheduler.
 *
 * Tasks are sorted by Earliest Deadline First (EDF), with arrival time
 * as tiebreak.  For each task the UAV with the lowest estimated service
 * time is selected, subject to:
 *   – UAV must cover the task's MD location
 *   – UAV must have remaining capacity this slot
 *   – Estimated service time must be ≤ 2× task deadline (lax feasibility)
 *
 * NO probability estimates are used. Selection is purely deterministic
 * based on service time, load, and energy.
 */
public class GreedyBaselineScheduler implements BaselineScheduler {
    @Override
    public Result schedule(List<ProposedTask> tasks,
                           List<ProposedUAVState> uavs,
                           double slotLength) {
        Result result = new Result();
        if (tasks == null || tasks.isEmpty() || uavs == null || uavs.isEmpty()) {
            if (tasks != null) result.unassigned.addAll(tasks);
            return result;
        }
        // EDF ordering
        List<ProposedTask> ordered = new ArrayList<>(tasks);
        ordered.sort(Comparator
                .comparingDouble(ProposedTask::getDeadlineSec)
                .thenComparingDouble(ProposedTask::getArrivalTime));
        // Track capacity and committed service time per UAV this slot
        Map<ProposedUAVState, Integer> capLeft       = new LinkedHashMap<>();
        Map<ProposedUAVState, Double>  reservedTime  = new LinkedHashMap<>();
        for (ProposedUAVState u : uavs) {
            capLeft.put(u,      BaselineSchedulingUtils.capacityGuess(u, slotLength, tasks));
            reservedTime.put(u, 0.0);
        }
        for (ProposedTask t : ordered) {
            ProposedUAVState bestUav       = null;
            double           bestSvc       = Double.POSITIVE_INFINITY;
            double           bestTotalTime = Double.POSITIVE_INFINITY;
            for (ProposedUAVState u : uavs) {
                if (!BaselineSchedulingUtils.isReachable(t, u)) continue;
                if (capLeft.get(u) <= 0) continue;
                double baseSvc   = BaselineSchedulingUtils.estimatedServiceTime(t, u, slotLength);
                double totalTime = baseSvc + reservedTime.get(u);
                // Lax feasibility: accept if total estimated time ≤ 2× deadline
                if (totalTime <= 2.0 * t.getDeadlineSec() && baseSvc < bestSvc) {
                    bestSvc       = baseSvc;
                    bestTotalTime = totalTime;
                    bestUav       = u;
                }
            }
            if (bestUav == null) {
                result.unassigned.add(t);
            } else {
                capLeft.put(bestUav,     capLeft.get(bestUav) - 1);
                reservedTime.put(bestUav, reservedTime.get(bestUav) + bestSvc);
                t.setAssignedUAV(bestUav);
                t.setPredictedCost(bestTotalTime);
                result.assignments.add(new Assignment(t, bestUav, bestTotalTime));
            }
        }
        return result;
    }
}
