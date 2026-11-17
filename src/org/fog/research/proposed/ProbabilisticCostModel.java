package org.fog.research.proposed;
/**
 * ProbabilisticCostModel — enhanced with:
 *
 *  1. URGENCY BONUS: Tasks with little remaining slack get a cost reduction,
 *     making MCMF prefer assigning them to capable UAVs before deadline miss.
 *
 *  2. QUEUE-WAIT PENALTY: Full queue wait time (all queued tasks ahead) is
 *     included in the delay ratio — not just this task's txTime + compTime.
 *     EDF execution means the queue wait estimate is more accurate.
 *
 *  3. ENERGY-DEPLETION CLIFF: When a UAV has < 20% residual energy, the
 *     energy term is amplified quadratically to strongly discourage further
 *     assignment to near-depleted UAVs.
 *
 *  Lower cost = preferred by MCMF.
 */
public class ProbabilisticCostModel {
    public double computeCost(ProposedTask     task,
                              ProposedUAVState uav,
                              double           successProbability,
                              double           slotLength) {
        double loadRatio           = uav.getLoadRatioNow(slotLength);
        double residualEnergyRatio = uav.getResidualEnergyRatio();
        double upRate   = uav.uplinkRate(task.getMdX(), task.getMdY());
        double downRate = uav.downlinkRate(task.getMdX(), task.getMdY());
        double txTime   = task.getDataBits()   / Math.max(1e-9, upRate)
                        + task.getResultBits() / Math.max(1e-9, downRate);
        double compTime = task.getTotalCpuCycles() / Math.max(1e-9, uav.getCpuHz());
        // Full queue wait: sum remaining CPU cycles of all tasks ahead in EDF queue
        double queueWait = 0.0;
        for (ProposedTask qt : uav.getQueue()) {
            queueWait += qt.getRemainingCpuCycles() / Math.max(1e-9, uav.getCpuHz());
        }
        double deadline    = Math.max(1e-9, task.getDeadlineSec());
        double delayRatio  = (txTime + compTime + queueWait) / deadline;
        delayRatio = Math.max(0.0, Math.min(2.0, delayRatio));
        // ── Energy-depletion cliff ───────────────────────────────────────────
        // Linear penalty below 100%, quadratic cliff below 20%
        double energyTerm;
        if (residualEnergyRatio < 0.20) {
            // Quadratic: e.g. at 10% residual → (0.10/0.20)^2 = 0.25 → cliff=0.75 bonus
            energyTerm = 1.0 - residualEnergyRatio
                       + Math.pow(1.0 - residualEnergyRatio / 0.20, 2.0);
        } else {
            energyTerm = 1.0 - residualEnergyRatio;
        }
        energyTerm = Math.max(0.0, Math.min(3.0, energyTerm));
        double baseCost =
              ProposedParams.W_COST_PROB   * (1.0 - successProbability)
            + ProposedParams.W_COST_LOAD   * loadRatio
            + ProposedParams.W_COST_ENERGY * energyTerm
            + ProposedParams.W_COST_DELAY  * delayRatio;
        // ── Urgency bonus ────────────────────────────────────────────────────
        // If slack_ratio < URGENCY_SLACK_THRESH, reduce cost so MCMF
        // preferentially assigns this urgent task to capable UAVs.
        double slackRatio = Math.max(0.0,
                deadline - txTime - compTime - queueWait) / deadline;
        if (slackRatio < ProposedParams.URGENCY_SLACK_THRESH) {
            // Bonus scales with urgency: tightest deadlines get the most reduction
            double urgency = 1.0 - slackRatio / ProposedParams.URGENCY_SLACK_THRESH;
            baseCost -= ProposedParams.URGENCY_COST_BONUS * urgency;
        }
        return Math.max(0.0, baseCost);
    }
}
