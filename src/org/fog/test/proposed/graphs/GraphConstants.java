package org.fog.test.proposed.graphs;

import java.io.File;

/**
 * GraphConstants
 * ==============
 * Central configuration for the graph generation subsystem.
 *
 * Defines:
 *   - Task sweep counts for scalability study
 *   - Per-run and cross-task figure file names
 *   - Colour palette (IEEE/ACM accessible, print-safe)
 *   - Path helpers used by GraphGenerator
 *
 * @author Proposed Hybrid Framework — UAV-MEC Research
 */
public final class GraphConstants {

    private GraphConstants() { /* utility class */ }

    // ── Task sweep ──────────────────────────────────────────────────────────────
    /**
     * Each simulation run automatically loops over these task counts.
     * Results are stored in tasks_500/, tasks_1000/, … sub-folders.
     */
    public static final int[] TASK_SWEEP = { 500, 1000, 1500, 2000, 2500, 3000 };

    // ── Python interpreter ──────────────────────────────────────────────────────
    public static String getPythonCommand() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
    }

    // ── Script names (written to disk at runtime) ───────────────────────────────
    public static final String PER_TASK_SCRIPT_NAME  = "generate_figures.py";
    public static final String CROSS_TASK_SCRIPT_NAME = "generate_cross_task.py";

    // ── Directory helpers ───────────────────────────────────────────────────────

    /** e.g. test_results/run_1/tasks_1000 */
    public static String getTaskFolder(String runFolder, int taskCount) {
        return runFolder + File.separator + "tasks_" + taskCount;
    }

    /** e.g. test_results/run_1/tasks_1000/images */
    public static String getTaskImagesDir(String runFolder, int taskCount) {
        return getTaskFolder(runFolder, taskCount) + File.separator + "images";
    }

    /** e.g. test_results/run_1/images  (cross-task comparison images) */
    public static String getCrossTaskImagesDir(String runFolder) {
        return runFolder + File.separator + "images";
    }

    /** Path to per-task Python script inside the task folder */
    public static String getPerTaskScriptPath(String runFolder, int taskCount) {
        return getTaskFolder(runFolder, taskCount) + File.separator + PER_TASK_SCRIPT_NAME;
    }

    /** Path to cross-task Python script inside the run folder */
    public static String getCrossTaskScriptPath(String runFolder) {
        return runFolder + File.separator + CROSS_TASK_SCRIPT_NAME;
    }

    // ── Per-task figure file names (11 publication-grade figures) ──────────────
    public static final String[] PER_TASK_FIGURES = {
        "03_tasks_completed_dropped.png",
        "04_effective_latency.png",
        "05_coordination_energy.png",
        "06_taxonomy_heatmap.png",
        "07_deadlock_recovery.png",
        "08_scalability_analysis.png",
        "09_chain_success_rate.png",
        "10_radar_comparison.png",
        "11_coverage_map.png",
        "13_seed_boxplot.png",
        "14_energy_distribution_pie.png"
    };

    // ── Cross-task comparison figure file names (4 sweep figures) ───────────────
    public static final String[] CROSS_TASK_FIGURES = {
        "C1_task_completion_sweep.png",
        "C2_latency_sweep.png",
        "C3_coordination_energy_sweep.png",
        "C4_chain_success_sweep.png"
    };

    // ── Colour palette (IEEE/ACM accessible hex values) ──────────────────────
    public static final String COLOR_HYBRID    = "#0072B2";
    public static final String COLOR_GOSSIP    = "#D55E00";
    public static final String COLOR_SECBBA    = "#009E73";
    public static final String COLOR_WU        = "#CC79A7";
    public static final String COLOR_COMPLETED = "#0072B2";
    public static final String COLOR_DROPPED   = "#D55E00";
    public static final String COLOR_DETECTED  = "#3182BD";
    public static final String COLOR_RECOVERED = "#238B45";
    public static final String COLOR_MOVEMENT  = "#6ACC65";
    public static final String COLOR_HOVER     = "#4878CF";
    public static final String COLOR_COMPUTE   = "#77BEDB";
    public static final String COLOR_COMM      = "#D65F5F";
    public static final String COLOR_COORD     = "#C4AD66";
}
