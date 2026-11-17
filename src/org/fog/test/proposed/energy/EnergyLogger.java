package org.fog.test.proposed.energy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * EnergyLogger handles structured CSV writing for per-UAV, per-slot energy metrics.
 *
 * Header schema (0-based):
 *   0:slot  1:time  2:uav_id
 *   3:movement_J  4:compute_J  5:comm_uplinkdownlink_J  6:comm_offload_J  7:hover_J  8:coordination_J
 *   9:total_J  10:residual_J  11:residual_pct
 *   12:wasted_compute_J  13:wasted_hover_J  14:deadlock_wait_seconds
 *   15:queue_size  16:tasks_completed_slot  17:tasks_dropped_slot  18:deadlocks_active  19:detections  20:recoveries
 *   21:deadlocks_injected
 */
public class EnergyLogger {
    private PrintWriter csvWriter;

    public EnergyLogger(String baseDir, String runTag) {
        try {
            new File(baseDir).mkdirs();
            String path = baseDir + File.separator + "energy_per_uav_" + runTag + ".csv";
            csvWriter = new PrintWriter(new FileWriter(path, false));

            // ── CSV Header ──────────────────────────────────────────────────
            csvWriter.println(
                "slot,time,uav_id," +
                "movement_J,compute_J,comm_uplinkdownlink_J,comm_offload_J,hover_J,coordination_J," +
                "total_J,residual_J,residual_pct," +
                "wasted_compute_J,wasted_hover_J,deadlock_wait_seconds," +
                "queue_size,tasks_completed_slot,tasks_dropped_slot," +
                "deadlocks_active,detections,recoveries,deadlocks_injected"
            );
            csvWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logSlot(int slot, double time, int uavId, EnergyState state,
                        int queueSize, int completedSlot, int droppedSlot,
                        int activeDeadlocks, int totalDetections, int totalRecoveries,
                        int totalInjected) {
        if (csvWriter == null) return;

        double residualPct = state.getResidualEnergyRatio() * 100.0;

        csvWriter.printf(Locale.US,
            // slot, time, uav_id
            "%d,%.3f,%d," +
            // movement_J, compute_J, comm_uplinkdownlink_J, comm_offload_J, hover_J, coordination_J
            "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f," +
            // total_J, residual_J, residual_pct
            "%.6f,%.6f,%.2f," +
            // wasted_compute_J, wasted_hover_J, deadlock_wait_seconds
            "%.6f,%.6f,%.6f," +
            // queue_size, tasks_completed_slot, tasks_dropped_slot
            "%d,%d,%d," +
            // deadlocks_active, detections, recoveries, deadlocks_injected
            "%d,%d,%d,%d%n",
            slot, time, uavId,
            state.getMovementEnergyJ(), state.getComputeEnergyJ(),
            state.getCommUplinkDownlinkJ(), state.getCommOffloadJ(),
            state.getHoverEnergyJ(), state.getCoordinationEnergyJ(),
            state.getTotalEnergyJ(), state.getResidualEnergyJ(), residualPct,
            state.getWastedComputeEnergyJ(), state.getWastedHoverEnergyJ(),
            state.getCumulativeDeadlockWaitTime(),
            queueSize, completedSlot, droppedSlot,
            activeDeadlocks, totalDetections, totalRecoveries, totalInjected
        );
        csvWriter.flush();
    }

    public void close() {
        if (csvWriter != null) {
            csvWriter.flush();
            csvWriter.close();
            csvWriter = null;
        }
    }
}
