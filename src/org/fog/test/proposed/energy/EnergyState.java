package org.fog.test.proposed.energy;

import org.fog.test.proposed.ProposedParams;

/**
 * EnergyState encapsulates fine-grained energy accumulation buckets for a UAV instance.
 *
 * Extended with:
 *  - wastedComputeEnergyJ  : CPU cycles consumed by tasks that were eventually dropped
 *  - wastedHoverEnergyJ    : hover energy burned while UAV waited during deadlock
 *  - cumulativeDeadlockWaitTime : total seconds this UAV has had a task stuck in a deadlock
 *  - detectionCount / recoveryCount : local counters for per-UAV tracking
 */
public class EnergyState {

    // ─── Core energy buckets ──────────────────────────────────────────────────
    private double movementEnergyJ     = 0.0;
    private double computeEnergyJ      = 0.0;
    private double commUplinkDownlinkJ = 0.0;
    private double commOffloadJ        = 0.0;
    private double hoverEnergyJ        = 0.0;
    private double coordinationEnergyJ = 0.0;

    // ─── Wasted / deadlock energy buckets ────────────────────────────────────
    /** Compute energy spent on tasks that were dropped before completion. */
    private double wastedComputeEnergyJ       = 0.0;
    /** Hover energy burned while a UAV waited for a deadlocked task to unblock. */
    private double wastedHoverEnergyJ         = 0.0;
    /** Cumulative seconds this UAV had at least one task blocked in a deadlock. */
    private double cumulativeDeadlockWaitTime = 0.0;
    /** How many deadlock detection events were attributed to this UAV. */
    private double detectionCount  = 0.0;
    /** How many deadlock recovery events were attributed to this UAV. */
    private double recoveryCount   = 0.0;

    // ─── Adders ──────────────────────────────────────────────────────────────
    public void addMovementEnergy(double e)     { movementEnergyJ     += e; }
    public void addComputeEnergy(double e)      { computeEnergyJ      += e; }
    public void addCommEnergy(double e)         { commUplinkDownlinkJ += e; }
    public void addOffloadEnergy(double e)      { commOffloadJ        += e; }
    public void addHoverEnergy(double e)        { hoverEnergyJ        += e; }
    public void addCoordinationEnergy(double e) { coordinationEnergyJ += e; }

    public void addWastedComputeEnergy(double e)   { wastedComputeEnergyJ       += e; }
    public void addWastedHoverEnergy(double e)     { wastedHoverEnergyJ         += e; }
    public void addDeadlockWaitTime(double t)      { cumulativeDeadlockWaitTime += t; }
    public void addDetection()  { detectionCount++; }
    public void addRecovery()   { recoveryCount++;  }

    // ─── Getters: core ───────────────────────────────────────────────────────
    public double getMovementEnergyJ()     { return movementEnergyJ;     }
    public double getComputeEnergyJ()      { return computeEnergyJ;      }
    public double getCommUplinkDownlinkJ() { return commUplinkDownlinkJ; }
    public double getCommOffloadJ()        { return commOffloadJ;        }
    public double getHoverEnergyJ()        { return hoverEnergyJ;        }
    public double getCoordinationEnergyJ() { return coordinationEnergyJ; }

    // ─── Getters: wasted / deadlock ──────────────────────────────────────────
    public double getWastedComputeEnergyJ()      { return wastedComputeEnergyJ;       }
    public double getWastedHoverEnergyJ()        { return wastedHoverEnergyJ;         }
    public double getCumulativeDeadlockWaitTime(){ return cumulativeDeadlockWaitTime; }
    public double getDetectionCount()            { return detectionCount;             }
    public double getRecoveryCount()             { return recoveryCount;              }

    // ─── Aggregates ──────────────────────────────────────────────────────────

    /**
     * Total energy consumed by this UAV.
     * Includes wasted compute and hover so that the Gossip/Hybrid difference
     * in total energy reflects the real cost of delayed deadlock recovery.
     */
    public double getTotalEnergyJ() {
        return movementEnergyJ
             + computeEnergyJ
             + commUplinkDownlinkJ
             + commOffloadJ
             + hoverEnergyJ
             + coordinationEnergyJ
             + wastedComputeEnergyJ
             + wastedHoverEnergyJ;
    }

    /** Wasted compute + wasted hover = energy that produced no useful result. */
    public double getTotalWastedEnergyJ() {
        return wastedComputeEnergyJ + wastedHoverEnergyJ;
    }

    /**
     * Energy efficiency: fraction of total energy that produced useful work.
     * Returns value in [0.0, 1.0].
     */
    public double getEnergyEfficiency() {
        double total = getTotalEnergyJ();
        if (total <= 1e-9) return 1.0;
        return Math.max(0.0, Math.min(1.0, 1.0 - getTotalWastedEnergyJ() / total));
    }

    public double getResidualEnergyJ() {
        return Math.max(1e-9, ProposedParams.UAV_ENERGY_BUDGET_J - getTotalEnergyJ());
    }

    public double getResidualEnergyRatio() {
        return Math.max(0.0, Math.min(1.0,
                getResidualEnergyJ() / ProposedParams.UAV_ENERGY_BUDGET_J));
    }
}
