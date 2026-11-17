package org.fog.test.baseline;
import org.fog.test.baseline.ProposedTask;
import org.fog.test.baseline.ProposedUAVState;
import java.util.*;
/**
 * Gale-Shapley Stable Matching Baseline.
 *
 * Classic many-to-one Deferred Acceptance algorithm:
 *   – PROPOSER side: tasks
 *   – ACCEPTOR side: UAVs  (each has a capacity = estimatedTaskCapacity)
 *
 * Task preference over UAVs:
 *   Rank UAVs by ascending estimated service time (fastest first),
 *   breaking ties by ascending load ratio, then descending energy ratio.
 *   NO probability estimates used.
 *
 * UAV preference over tasks (for rejection during contention):
 *   Rank held tasks by ascending service time (shortest job first),
 *   breaking ties by ascending deadline (tightest first).
 *   When a UAV is over-capacity it rejects the least-preferred held task.
 *
 * Algorithm terminates when no free task has a UAV left on its preference list.
 * Result is a stable matching: no (task, UAV) pair would mutually prefer
 * each other over their current match.
 */
public class GaleShapleyBaselineScheduler implements BaselineScheduler {
    @Override
    public Result schedule(List<ProposedTask> tasks,
                           List<ProposedUAVState> uavs,
                           double slotLength) {
        Result result = new Result();
        if (tasks == null || tasks.isEmpty() || uavs == null || uavs.isEmpty()) {
            if (tasks != null) result.unassigned.addAll(tasks);
            return result;
        }
        // ── Build task preference lists ──────────────────────────────────────
        Map<ProposedTask, List<ProposedUAVState>> taskPrefs   = new LinkedHashMap<>();
        Map<ProposedTask, Integer>                nextPropIdx = new LinkedHashMap<>();
        for (ProposedTask t : tasks) {
            List<ProposedUAVState> prefs = new ArrayList<>();
            for (ProposedUAVState u : uavs) {
                if (!BaselineSchedulingUtils.isReachable(t, u)) continue;
                double svc = BaselineSchedulingUtils.estimatedServiceTime(t, u, slotLength);
                // Only include UAV if it can plausibly finish within deadline
                if (svc <= 2.0 * t.getDeadlineSec()) prefs.add(u);
            }
            // Sort: fastest first, then least loaded, then most energy
            prefs.sort(Comparator
                .comparingDouble((ProposedUAVState u) ->
                        BaselineSchedulingUtils.estimatedServiceTime(t, u, slotLength))
                .thenComparingDouble(u -> u.getLoadRatioNow(slotLength))
                .thenComparingDouble(u -> -u.getResidualEnergyRatio())); // most energy first
            taskPrefs.put(t, prefs);
            nextPropIdx.put(t, 0);
        }
        // ── UAV capacities and held sets ──────────────────────────────────────
        Map<ProposedUAVState, Integer>          cap   = new LinkedHashMap<>();
        Map<ProposedUAVState, List<ProposedTask>> held = new LinkedHashMap<>();
        for (ProposedUAVState u : uavs) {
            cap.put(u,  BaselineSchedulingUtils.capacityGuess(u, slotLength, tasks));
            held.put(u, new ArrayList<>());
        }
        // ── Deferred Acceptance ───────────────────────────────────────────────
        Deque<ProposedTask> free = new ArrayDeque<>(tasks);
        while (!free.isEmpty()) {
            ProposedTask task = free.pollFirst();
            List<ProposedUAVState> prefs = taskPrefs.get(task);
            int idx = nextPropIdx.get(task);
            // Task exhausted all preferences → will be unassigned
            if (idx >= prefs.size()) continue;
            ProposedUAVState target = prefs.get(idx);
            nextPropIdx.put(task, idx + 1);
            int targetCap = cap.getOrDefault(target, 0);
            if (targetCap <= 0) {
                // UAV has zero capacity; try next preference
                free.addFirst(task);
                continue;
            }
            // Tentatively hold this task
            List<ProposedTask> heldList = held.get(target);
            heldList.add(task);
            // Sort held tasks by UAV preference (shortest service time first,
            // tightest deadline as tiebreak) — NO probabilities
            heldList.sort(Comparator
                    .comparingDouble((ProposedTask x) ->
                            BaselineSchedulingUtils.estimatedServiceTime(x, target, slotLength))
                    .thenComparingDouble(ProposedTask::getDeadlineSec));
            // Reject any task beyond capacity
            while (heldList.size() > targetCap) {
                ProposedTask rejected = heldList.remove(heldList.size() - 1);
                // Rejected task re-proposes to its next preference
                if (nextPropIdx.get(rejected) < taskPrefs.get(rejected).size()) {
                    free.addLast(rejected);
                }
                // else: rejected with no more preferences → unassigned
            }
        }
        // ── Collect results ───────────────────────────────────────────────────
        Set<ProposedTask> matched = new HashSet<>();
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : held.get(u)) {
                double svc = BaselineSchedulingUtils.estimatedServiceTime(t, u, slotLength);
                t.setAssignedUAV(u);
                t.setPredictedCost(svc);
                // Probability field left at default 0 — baselines don't use it
                result.assignments.add(new Assignment(t, u, svc));
                matched.add(t);
            }
        }
        for (ProposedTask t : tasks) {
            if (!matched.contains(t)) result.unassigned.add(t);
        }
        return result;
    }
}
