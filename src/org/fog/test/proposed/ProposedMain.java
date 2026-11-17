package org.fog.test.proposed;

import org.fog.test.proposed.experiment.ComparisonGenerator;
import org.fog.test.proposed.graphs.GraphGenerator;
import java.io.File;

/**
 * ProposedMain — Unified Simulation Entry Point
 * ==============================================
 *
 * DEFAULT BEHAVIOUR (no arguments / "compare" mode):
 *   Runs a FULL TASK SWEEP automatically.  For every task count in
 *   {@link ProposedParams#TASK_SWEEP} (500 → 1000 → 1500 → 2000 → 2500 → 3000):
 *     1. Run all 4 algorithms  (Hybrid, Gossip Ren 2024, SECBBA, Wu 2025)
 *     2. Generate 4-way comparison CSV
 *     3. Auto-generate 13 publication-ready figures  →  tasks_N/images/
 *   After the sweep:
 *     4. Generate 4 cross-task comparison figures   →  run_N/images/
 *
 * OUTPUT STRUCTURE:
 *   test_results/
 *   └── run_N/                          ← next available run folder
 *       ├── tasks_500/
 *       │   ├── hybrid/raw/             ← Hybrid CSVs
 *       │   ├── gossip/raw/             ← Gossip CSVs
 *       │   ├── secbba/raw/             ← SECBBA CSVs
 *       │   ├── wu2025/raw/             ← Wu CSVs
 *       │   ├── comparison/             ← 4-way comparison summary CSV
 *       │   └── images/                 ← 13 per-task PNGs (Fig 02–14)
 *       ├── tasks_1000/  →  images/  (13 PNGs)
 *       ├── tasks_1500/  →  images/  (13 PNGs)
 *       ├── tasks_2000/  →  images/  (13 PNGs)
 *       ├── tasks_2500/  →  images/  (13 PNGs)
 *       ├── tasks_3000/  →  images/  (13 PNGs)
 *       └── images/                     ← 4 cross-task comparison PNGs
 *
 * SINGLE-MODE (legacy):
 *   java ProposedMain hybrid  10 130 compare 7 200
 *   java ProposedMain gossip  10 130 compare 7 200
 *   java ProposedMain secbba  10 130 compare 7 200
 *   java ProposedMain wu      10 130 compare 7 200
 *
 * @author Proposed Hybrid Framework — UAV-MEC Research
 */
public class ProposedMain {

    public static void main(String[] args) {

        // ── Parse CLI arguments ───────────────────────────────────────────────
        String mode = (args.length >= 1 && !isNumeric(args[0])) ? args[0].toLowerCase() : "compare";
        int argOffset = "compare".equals(mode) || "hybrid".equals(mode) || "secbba".equals(mode)
                     || "wu".equals(mode) || "wu2025".equals(mode) || "gossip".equals(mode)
                     || "ren".equals(mode) ? 1 : 0;

        int    uavCount = (args.length >= argOffset + 1) ? Integer.parseInt(args[argOffset])      : 10;
        double radius   = (args.length >= argOffset + 2) ? Double.parseDouble(args[argOffset + 1]) : 130.0;
        String baseTag  = (args.length >= argOffset + 3) ? args[argOffset + 2]                     : mode;
        long   seed     = (args.length >= argOffset + 4) ? Long.parseLong(args[argOffset + 3])     : 7L;
        int    maxSlots = (args.length >= argOffset + 5) ? Integer.parseInt(args[argOffset + 4])   : ProposedParams.MAX_SLOTS;

        // ── Legacy single-algorithm modes (backward compatibility) ────────────
        if ("secbba".equals(mode) || "wu".equals(mode) || "wu2025".equals(mode)
                || "gossip".equals(mode) || "ren".equals(mode) || "hybrid".equals(mode)) {
            String runFolder = nextAvailableRunDir("test_results");
            runLegacySingleMode(mode, uavCount, radius, baseTag, seed, maxSlots, runFolder);
            return;
        }

        // ── Full Task-Sweep Run ───────────────────────────────────────────────
        String runFolder = nextAvailableRunDir("test_results");
        new File("test_results" + File.separator + runFolder).mkdirs();

        int[] taskSweep = ProposedParams.TASK_SWEEP;
        long  wallStart = System.currentTimeMillis();

        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  UAV-MEC RESEARCH — Full Task-Count Sweep");
        System.out.println("  4 Algorithms × " + taskSweep.length + " Task Counts → " + taskSweep.length + " × (4 simulations + 13 figures) + 4 cross-task figures");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf ("  Run folder  : test_results/%s/%n", runFolder);
        System.out.printf ("  Task counts : %s%n", arrayToString(taskSweep));
        System.out.printf ("  UAVs        : %d%n",  uavCount);
        System.out.printf ("  Radius      : %.0f m%n", radius);
        System.out.printf ("  Slots       : %d%n",  maxSlots);
        System.out.printf ("  Seed        : %d%n",  seed);
        System.out.println("──────────────────────────────────────────────────────────────────────────────");

        // ── Task count loop ───────────────────────────────────────────────────
        for (int idx = 0; idx < taskSweep.length; idx++) {
            int taskCount = taskSweep[idx];
            long taskStart = System.currentTimeMillis();

            System.out.println();
            System.out.println("══════════════════════════════════════════════════════════════════════════════");
            System.out.printf ("  [%d/%d] TASK COUNT: %d tasks%n", idx + 1, taskSweep.length, taskCount);
            System.out.println("══════════════════════════════════════════════════════════════════════════════");

            // Task sub-folder: test_results/run_N/tasks_T/
            String taskSubDir   = runFolder + File.separator + "tasks_" + taskCount;
            String fullTaskPath = "test_results" + File.separator + taskSubDir;
            new File(fullTaskPath).mkdirs();

            // Algorithm sub-folders inside tasks_T/
            String hybridDir     = taskSubDir + File.separator + "hybrid";
            String gossipRenDir  = taskSubDir + File.separator + "gossip";
            String secbbaDir     = taskSubDir + File.separator + "secbba";
            String wuDir         = taskSubDir + File.separator + "wu2025";
            String comparisonDir = taskSubDir + File.separator + "comparison";

            System.out.printf ("  Output: test_results/%s/%n", taskSubDir);
            System.out.println("──────────────────────────────────────────────────────────────────────────────");

            // -- 1. Hybrid --
            System.out.println();
            System.out.printf("[1/4] HYBRID  (tasks=%d)%n", taskCount);
            runSingleMode(ProposedParams.CoordinationMode.HYBRID,
                    uavCount, radius, baseTag, seed, maxSlots, taskCount, hybridDir);

            // -- 2. Gossip Ren 2024 --
            System.out.println();
            System.out.printf("[2/4] GOSSIP  (tasks=%d)%n", taskCount);
            runSingleMode(ProposedParams.CoordinationMode.GOSSIP_REN_2024,
                    uavCount, radius, baseTag, seed, maxSlots, taskCount, gossipRenDir);

            // -- 3. SECBBA --
            System.out.println();
            System.out.printf("[3/4] SECBBA  (tasks=%d)%n", taskCount);
            runSingleMode(ProposedParams.CoordinationMode.SECBBA,
                    uavCount, radius, baseTag, seed, maxSlots, taskCount, secbbaDir);

            // -- 4. Wu 2025 --
            System.out.println();
            System.out.printf("[4/4] WU 2025 (tasks=%d)%n", taskCount);
            runSingleMode(ProposedParams.CoordinationMode.WU_2025,
                    uavCount, radius, baseTag, seed, maxSlots, taskCount, wuDir);

            // -- 4-way comparison CSV --
            System.out.println();
            System.out.printf("[Comparison] tasks=%d — generating 4-way comparison summary...%n", taskCount);
            ComparisonGenerator.generateFourWay(hybridDir, gossipRenDir, secbbaDir, wuDir, comparisonDir);

            // -- 13 per-task figures --
            String fullRunRoot = "test_results" + File.separator + runFolder;
            GraphGenerator.generatePerTaskFigures(fullRunRoot, taskCount);

            long taskElapsed = (System.currentTimeMillis() - taskStart) / 1000;
            System.out.println();
            System.out.printf("  ✅ tasks_%d complete  (%ds)  →  test_results/%s/tasks_%d/%n",
                    taskCount, taskElapsed, runFolder, taskCount);
        }

        // ── Cross-task comparison figures (4 PNGs, run folder level) ─────────
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  CROSS-TASK COMPARISON — Generating 4 figures across all task counts");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        String fullRunRoot = "test_results" + File.separator + runFolder;
        GraphGenerator.generateCrossTaskFigures(fullRunRoot, taskSweep);

        // ── Final summary ─────────────────────────────────────────────────────
        long totalElapsed = (System.currentTimeMillis() - wallStart) / 1000;
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  ALL DONE — FULL TASK SWEEP COMPLETE");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.printf ("  Total time  : %d min %d sec%n", totalElapsed / 60, totalElapsed % 60);
        System.out.printf ("  Results in  : test_results/%s/%n", runFolder);
        System.out.println("  Contents:");
        for (int tc : taskSweep) {
            System.out.printf("    tasks_%d/images/  → 13 per-task figures%n", tc);
        }
        System.out.printf ("    images/          → 4 cross-task comparison figures%n");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SIMULATION RUNNER HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Configures the task cap (TASK_SET_LENGTH equivalent) and runs the simulation.
     * Each call resets the task ID counter so IDs start fresh for every (algo, taskCount) pair.
     */
    private static void runSingleMode(ProposedParams.CoordinationMode mode,
                                      int uavCount,
                                      double radius,
                                      String baseTag,
                                      long seed,
                                      int maxSlots,
                                      int taskCount,
                                      String outputDirName) {
        // Encode taskCount into runTag so CSV filenames are unique per task count
        String runTag  = baseTag + "_t" + taskCount + "_" + mode.name().toLowerCase();
        String[] runArgs = {
            String.valueOf(uavCount),
            String.valueOf(radius),
            runTag,
            String.valueOf(seed),
            String.valueOf(maxSlots),
            String.valueOf(taskCount)        // SimulationRunner reads this as task cap
        };

        ProposedTask.resetIdCounter();
        ProposedParams.configureResultDirectory(mode, outputDirName);
        SimulationRunner.run(mode, runArgs);
    }

    /**
     * Legacy single-algorithm runner (backward compatibility — no task sweep).
     */
    private static void runLegacySingleMode(String mode,
                                            int uavCount, double radius,
                                            String baseTag, long seed,
                                            int maxSlots, String runFolder) {
        ProposedParams.CoordinationMode cm;
        String subDir;
        switch (mode) {
            case "hybrid":              cm = ProposedParams.CoordinationMode.HYBRID;          subDir = "hybrid";   break;
            case "gossip": case "ren":  cm = ProposedParams.CoordinationMode.GOSSIP_REN_2024; subDir = "gossip";   break;
            case "secbba":              cm = ProposedParams.CoordinationMode.SECBBA;           subDir = "secbba";   break;
            default:                    cm = ProposedParams.CoordinationMode.WU_2025;          subDir = "wu2025";   break;
        }
        String outDir = runFolder + File.separator + subDir;
        String runTag = baseTag + "_" + cm.name().toLowerCase();
        String[] runArgs = {
            String.valueOf(uavCount), String.valueOf(radius),
            runTag, String.valueOf(seed), String.valueOf(maxSlots)
        };
        ProposedTask.resetIdCounter();
        ProposedParams.configureResultDirectory(cm, outDir);
        SimulationRunner.run(cm, runArgs);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private static boolean isNumeric(String value) {
        try { Double.parseDouble(value); return true; }
        catch (Exception e) { return false; }
    }

    private static String arrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.append("]").toString();
    }

    /**
     * Returns the next available run folder name: run_1, run_2, run_3 ...
     * A run folder is considered taken only if test_results/run_N/ already exists.
     */
    public static String nextAvailableRunDir(String root) {
        for (int n = 1; n <= 9999; n++) {
            File candidate = new File(root + File.separator + "run_" + n);
            if (!candidate.exists()) return "run_" + n;
        }
        return "run_ts_" + System.currentTimeMillis();
    }

    /** Legacy helper kept for backward compatibility with ExperimentRunner, ScalabilitySweep etc. */
    public static String nextAvailableDir(String root, String base) {
        File first = new File(root + File.separator + base);
        if (!first.exists()) return base;
        for (int n = 2; n <= 999; n++) {
            File candidate = new File(root + File.separator + base + "_" + n);
            if (!candidate.exists()) return base + "_" + n;
        }
        return base + "_run_" + System.currentTimeMillis();
    }
}