package org.fog.research.baseline;
import org.fog.research.baseline.ProposedTask;
import org.fog.research.baseline.ProposedUAVState;
import java.util.ArrayList;
import java.util.List;
/**
 * Common interface for all baseline schedulers.
 * Baselines must NOT use probability estimates — they use deterministic
 * heuristics (service time estimates, load, distance, energy).
 */
public interface BaselineScheduler {
    Result schedule(List<ProposedTask> tasks,
                    List<ProposedUAVState> uavs,
                    double slotLength);
    // ── Shared result types ──────────────────────────────────────────────────
    class Assignment {
        public final ProposedTask    task;
        public final ProposedUAVState uav;
        public final double          estimatedServiceTime;
        public Assignment(ProposedTask task,
                          ProposedUAVState uav,
                          double estimatedServiceTime) {
            this.task                 = task;
            this.uav                  = uav;
            this.estimatedServiceTime = estimatedServiceTime;
        }
    }
    class Result {
        public final List<Assignment>   assignments = new ArrayList<>();
        public final List<ProposedTask> unassigned  = new ArrayList<>();
    }
}
