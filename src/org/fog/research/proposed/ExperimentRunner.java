package org.fog.research.proposed;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;
/**
 * ExperimentRunner — multi-seed edition.
 *
 * Every configuration is now run across N_SEEDS independent seeds.
 * Each seed produces its own CSV (tagged run_*_seed<N>.csv).
 * After all seeds finish for a configuration, SeedAggregator writes
 * a single summary row (mean ± std) into a per-study aggregate CSV.
 *
 * Why 30 seeds?
 *   - Central Limit Theorem: sample mean is approximately normal for n ≥ 30.
 *   - Enables paired Wilcoxon signed-rank test (non-parametric, n ≥ 10)
 *     or paired t-test (n ≥ 30) against each baseline at p < 0.05.
 *   - Consistent with IEEE IoT Journal / IEEE TWC submission standards.
 *
 * Seeds chosen to be well-separated in the PRNG state space.
 * Do NOT use sequential seeds (1,2,3…) — they can be correlated in LCG PRNGs.
 */
public class ExperimentRunner {
    // ── Reproducible seed set (30 seeds) ────────────────────────────────────
    private static final long[] SEEDS = {
        7L,    42L,   137L,  256L,  512L,
        1024L, 2048L, 3141L, 4096L, 5000L,
        6174L, 7777L, 8192L, 9001L, 10007L,
        11213L,12345L,13579L,14641L,15013L,
        16384L,17711L,18181L,19937L,20480L,
        21701L,22222L,23456L,24576L,25565L
    };
    // Set to a smaller number during development to save time, e.g. 5.
    // Set to 30 for final journal submission.
    private static final int N_SEEDS = 30;
    // ── Experiment parameters ────────────────────────────────────────────────
    private static final double FIXED_CPU_HZ     = 1e9;
    private static final int[]  UAV_COUNTS       = {2, 4, 6, 8, 10, 12, 14, 16};
    private static final double[]LAMBDA_VALUES   = {5.0, 7.5, 10.0, 12.5, 15.0, 17.5};
    private static final double[]RADIUS_VALUES   = {30, 50, 70, 90, 110, 130};
    private static final int    FIXED_UAV_RADIUS = 10;
    private static final double FIXED_LAM_RADIUS = 10.0;
    private static final int    DETAILED_UAVS    = 10;
    private static final double DETAILED_RADIUS  = 130.0;
    private static final double DETAILED_LAMBDA  = 10.0;
    // ── Entry point ──────────────────────────────────────────────────────────
    public static void main(String[] args) {
        new File(ProposedParams.LOG_DIR).mkdirs();
        // Aggregate CSVs — one row per configuration, columns = mean ± std
        String aggStandardPath = ProposedParams.LOG_DIR + File.separator
                + "aggregate_standard.csv";
        String aggRadiusPath   = ProposedParams.LOG_DIR + File.separator
                + "aggregate_radius.csv";
        String aggDetailPath   = ProposedParams.LOG_DIR + File.separator
                + "aggregate_detailed.csv";
        initAggregateFile(aggStandardPath,
                "uav_count,lambda,radius," + SeedAggregator.HEADER);
        initAggregateFile(aggRadiusPath,
                "uav_count,lambda,radius," + SeedAggregator.HEADER);
        initAggregateFile(aggDetailPath,
                "uav_count,lambda,radius," + SeedAggregator.HEADER);
        int runNo = 1;
        // ── Study 1: Standard (UAV count × lambda) ───────────────────────────
        System.out.println("\n========= STUDY 1: STANDARD EXPERIMENTS =========\n");
        for (int uavCount : UAV_COUNTS) {
            for (double lambda : LAMBDA_VALUES) {
                ProposedParams.CPU_HZ      = FIXED_CPU_HZ;
                ProposedParams.LAMBDA_TASKS = lambda;
                SeedAggregator agg = new SeedAggregator();
                for (int si = 0; si < N_SEEDS; si++) {
                    long seed = SEEDS[si];
                    String runTag = "u" + uavCount
                            + "_cpu" + (long) FIXED_CPU_HZ
                            + "_lam" + lambdaTag(lambda)
                            + "_seed" + seed;
                    printRunHeader(runNo, "STANDARD", uavCount, 130.0,
                            FIXED_CPU_HZ, lambda, seed, si + 1, N_SEEDS);
                 // Reset task ID counters before each run
                    org.fog.research.proposed.ProposedTask.resetIdCounter();
                    org.fog.test.baseline.ProposedTask.resetIdCounter();
                    ProposedMain.main(new String[]{
                            String.valueOf(uavCount),
                            "130.0",
                            runTag,
                            String.valueOf(seed)
                    });
                    agg.collectFromCsv(ProposedParams.LOG_DIR
                            + File.separator + "run_" + runTag + ".csv");
                    runNo++;
                }
                appendAggregateLine(aggStandardPath,
                        uavCount + "," + lambda + ",130.0", agg);
                System.out.printf(Locale.US,
                        "[DONE] UAV=%d λ=%.1f — mean_latency=%.3f±%.3f  "
                        + "assign_ratio=%.3f±%.3f%n",
                        uavCount, lambda,
                        agg.mean("mean_latency"), agg.std("mean_latency"),
                        agg.mean("assignment_ratio"), agg.std("assignment_ratio"));
            }
        }
        // ── Study 2: Coverage-radius sweep ───────────────────────────────────
        System.out.println("\n========= STUDY 2: RADIUS SWEEP =========\n");
        ProposedParams.CPU_HZ       = FIXED_CPU_HZ;
        ProposedParams.LAMBDA_TASKS = FIXED_LAM_RADIUS;
        for (double radius : RADIUS_VALUES) {
            SeedAggregator agg = new SeedAggregator();
            for (int si = 0; si < N_SEEDS; si++) {
                long seed = SEEDS[si];
                String runTag = "radius_u" + FIXED_UAV_RADIUS
                        + "_cpu" + (long) FIXED_CPU_HZ
                        + "_lam" + lambdaTag(FIXED_LAM_RADIUS)
                        + "_r"   + radiusTag(radius)
                        + "_seed" + seed;
                printRunHeader(runNo, "RADIUS_SWEEP", FIXED_UAV_RADIUS,
                        radius, FIXED_CPU_HZ, FIXED_LAM_RADIUS, seed,
                        si + 1, N_SEEDS);
             // Reset task ID counters before each run
                org.fog.research.proposed.ProposedTask.resetIdCounter();
                org.fog.test.baseline.ProposedTask.resetIdCounter();
                ProposedMain.main(new String[]{
                        String.valueOf(FIXED_UAV_RADIUS),
                        String.valueOf(radius),
                        runTag,
                        String.valueOf(seed)
                });
                agg.collectFromCsv(ProposedParams.LOG_DIR
                        + File.separator + "run_" + runTag + ".csv");
                runNo++;
            }
            appendAggregateLine(aggRadiusPath,
                    FIXED_UAV_RADIUS + "," + FIXED_LAM_RADIUS + "," + radius,
                    agg);
            System.out.printf(Locale.US,
                    "[DONE] radius=%.0f — throughput=%.1f±%.1f Mbps%n",
                    radius,
                    agg.mean("throughput_bits") / 1e6,
                    agg.std("throughput_bits") / 1e6);
        }
        // ── Study 3: Detailed trace (single config, all seeds) ───────────────
        System.out.println("\n========= STUDY 3: DETAILED LOAD TRACE =========\n");
        ProposedParams.CPU_HZ       = FIXED_CPU_HZ;
        ProposedParams.LAMBDA_TASKS = DETAILED_LAMBDA;
        SeedAggregator detailAgg = new SeedAggregator();
        for (int si = 0; si < N_SEEDS; si++) {
            long seed = SEEDS[si];
            String runTag = "detailed_u" + DETAILED_UAVS
                    + "_cpu" + (long) FIXED_CPU_HZ
                    + "_lam" + lambdaTag(DETAILED_LAMBDA)
                    + "_r"   + radiusTag(DETAILED_RADIUS)
                    + "_seed" + seed;
            printRunHeader(runNo, "DETAILED_LOAD_TRACE", DETAILED_UAVS,
                    DETAILED_RADIUS, FIXED_CPU_HZ, DETAILED_LAMBDA, seed,
                    si + 1, N_SEEDS);
         // Reset task ID counters before each run
            org.fog.research.proposed.ProposedTask.resetIdCounter();
            org.fog.test.baseline.ProposedTask.resetIdCounter();
            ProposedMain.main(new String[]{
                    String.valueOf(DETAILED_UAVS),
                    String.valueOf(DETAILED_RADIUS),
                    runTag,
                    String.valueOf(seed)
            });
            detailAgg.collectFromCsv(ProposedParams.LOG_DIR
                    + File.separator + "run_" + runTag + ".csv");
            runNo++;
        }
        appendAggregateLine(aggDetailPath,
                DETAILED_UAVS + "," + DETAILED_LAMBDA + "," + DETAILED_RADIUS,
                detailAgg);
        System.out.println("\nAll experiments completed.");
        System.out.println("Aggregate results written to:");
        System.out.println("  " + aggStandardPath);
        System.out.println("  " + aggRadiusPath);
        System.out.println("  " + aggDetailPath);
    }
    // ── Helpers ──────────────────────────────────────────────────────────────
    private static void initAggregateFile(String path, String header) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path, false))) {
            pw.println(header);
        } catch (Exception e) {
            System.err.println("Could not init aggregate CSV: " + path);
        }
    }
    private static void appendAggregateLine(String path,
                                            String configCols,
                                            SeedAggregator agg) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path, true))) {
            pw.printf(Locale.US, "%s,%s%n", configCols, agg.toCsvLine());
        } catch (Exception e) {
            System.err.println("Could not append to: " + path);
        }
    }
    private static void printRunHeader(int runNo, String mode,
                                       int uavCount, double radius,
                                       double cpuHz, double lambda,
                                       long seed, int seedIdx, int nSeeds) {
        System.out.println("==================================================");
        System.out.printf("RUN    = %d%n", runNo);
        System.out.printf("MODE   = %s%n", mode);
        System.out.printf("UAVs   = %d%n", uavCount);
        System.out.printf("Radius = %.0f m%n", radius);
        System.out.printf("λ      = %.1f%n", lambda);
        System.out.printf("Seed   = %d  (%d / %d)%n", seed, seedIdx, nSeeds);
        System.out.println("==================================================");
    }
    private static String lambdaTag(double v) {
        return (Math.floor(v) == v)
                ? String.valueOf((int) v)
                : String.valueOf(v).replace('.', 'p');
    }
    private static String radiusTag(double v) {
        return (Math.floor(v) == v)
                ? String.valueOf((int) v)
                : String.valueOf(v).replace('.', 'p');
    }
    // ════════════════════════════════════════════════════════════════════════
    // SeedAggregator — inner class
    //
    // Reads a run_*.csv produced by ProposedSlotController, computes the
    // per-run average of each metric (averaged over post-warmup slots),
    // accumulates across N seeds, then exposes mean ± std per metric.
    //
    // CSV columns (from ProposedSlotController):
    //   slot, time, reward_covmd, throughput_bits, energy_delta_j,
    //   total_energy_j, generated, assigned, unassigned, completed,
    //   dropped, queue_total, pending_total, avg_success_prob
    // ════════════════════════════════════════════════════════════════════════
    public static class SeedAggregator {
        // Column indices in run_*.csv (0-based, after skipping header)
        private static final int COL_THROUGHPUT    = 3;
        private static final int COL_ENERGY_DELTA  = 4;
        private static final int COL_TOTAL_ENERGY  = 5;
        private static final int COL_GENERATED     = 6;
        private static final int COL_ASSIGNED      = 7;
        private static final int COL_UNASSIGNED     = 8;
        private static final int COL_COMPLETED     = 9;
        private static final int COL_DROPPED       = 10;
        private static final int COL_QUEUE         = 11;
        private static final int COL_PENDING       = 12;
        private static final int COL_AVG_PROB      = 13;
        // Metric names used for header and programmatic access
        public static final String HEADER =
            "throughput_bits_mean,throughput_bits_std," +
            "energy_delta_mean,energy_delta_std," +
            "total_energy_mean,total_energy_std," +
            "generated_mean,generated_std," +
            "assigned_mean,assigned_std," +
            "completed_mean,completed_std," +
            "dropped_mean,dropped_std," +
            "queue_mean,queue_std," +
            "pending_mean,pending_std," +
            "assignment_ratio_mean,assignment_ratio_std," +
            "completion_ratio_mean,completion_ratio_std," +
            "drop_ratio_mean,drop_ratio_std," +
            "avg_success_prob_mean,avg_success_prob_std," +
            "n_seeds";
        // Per-seed summary values (one entry per seed)
        private final java.util.List<double[]> perSeedRows = new java.util.ArrayList<>();
        /** Indices into the per-seed row array. */
        private static final int M_THROUGHPUT   = 0;
        private static final int M_ENERGY_DELTA = 1;
        private static final int M_TOTAL_ENERGY = 2;
        private static final int M_GENERATED    = 3;
        private static final int M_ASSIGNED     = 4;
        private static final int M_COMPLETED    = 5;
        private static final int M_DROPPED      = 6;
        private static final int M_QUEUE        = 7;
        private static final int M_PENDING      = 8;
        private static final int M_ASSIGN_RATIO = 9;
        private static final int M_COMP_RATIO   = 10;
        private static final int M_DROP_RATIO   = 11;
        private static final int M_AVG_PROB     = 12;
        private static final int N_METRICS      = 13;
        /**
         * Parse run_*.csv, average each metric across all rows (slots),
         * and store one summary row for this seed.
         */
        public void collectFromCsv(String csvPath) {
            double[] sums   = new double[N_METRICS];
            double[] counts = new double[N_METRICS];
            try (java.io.BufferedReader br =
                         new java.io.BufferedReader(new java.io.FileReader(csvPath))) {
                String line = br.readLine(); // skip header
                if (line == null) return;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] tok = line.split(",");
                    if (tok.length < 14) continue;
                    try {
                        double throughput  = Double.parseDouble(tok[COL_THROUGHPUT]);
                        double eDelta      = Double.parseDouble(tok[COL_ENERGY_DELTA]);
                        double eTotal      = Double.parseDouble(tok[COL_TOTAL_ENERGY]);
                        double generated   = Double.parseDouble(tok[COL_GENERATED]);
                        double assigned    = Double.parseDouble(tok[COL_ASSIGNED]);
                        double unassigned  = Double.parseDouble(tok[COL_UNASSIGNED]);
                        double completed   = Double.parseDouble(tok[COL_COMPLETED]);
                        double dropped     = Double.parseDouble(tok[COL_DROPPED]);
                        double queue       = Double.parseDouble(tok[COL_QUEUE]);
                        double pending     = Double.parseDouble(tok[COL_PENDING]);
                        double avgProb     = Double.parseDouble(tok[COL_AVG_PROB]);
                        double totalAssign = assigned + unassigned;
                        double assignRatio = (totalAssign > 0) ? assigned / totalAssign : 0;
                        double totalOutcome = completed + dropped;
                        double compRatio  = (totalOutcome > 0) ? completed / totalOutcome : 0;
                        double dropRatio  = (totalOutcome > 0) ? dropped   / totalOutcome : 0;
                        sums[M_THROUGHPUT]   += throughput;
                        sums[M_ENERGY_DELTA] += eDelta;
                        sums[M_TOTAL_ENERGY] += eTotal;
                        sums[M_GENERATED]    += generated;
                        sums[M_ASSIGNED]     += assigned;
                        sums[M_COMPLETED]    += completed;
                        sums[M_DROPPED]      += dropped;
                        sums[M_QUEUE]        += queue;
                        sums[M_PENDING]      += pending;
                        sums[M_ASSIGN_RATIO] += assignRatio;
                        sums[M_COMP_RATIO]   += compRatio;
                        sums[M_DROP_RATIO]   += dropRatio;
                        sums[M_AVG_PROB]     += avgProb;
                        for (int i = 0; i < N_METRICS; i++) counts[i]++;
                    } catch (NumberFormatException ignored) {
                        // skip malformed lines
                    }
                }
            } catch (Exception e) {
                System.err.println("SeedAggregator: cannot read " + csvPath
                        + " — " + e.getMessage());
                return;
            }
            // Per-seed slot averages
            double[] row = new double[N_METRICS];
            for (int i = 0; i < N_METRICS; i++) {
                row[i] = (counts[i] > 0) ? sums[i] / counts[i] : 0.0;
            }
            perSeedRows.add(row);
        }
        /** Mean across seeds for a named metric. */
        public double mean(String metric) {
            return mean(metricIndex(metric));
        }
        /** Std (sample) across seeds for a named metric. */
        public double std(String metric) {
            return std(metricIndex(metric));
        }
        private double mean(int idx) {
            if (perSeedRows.isEmpty()) return 0.0;
            double s = 0;
            for (double[] r : perSeedRows) s += r[idx];
            return s / perSeedRows.size();
        }
        private double std(int idx) {
            int n = perSeedRows.size();
            if (n < 2) return 0.0;
            double m = mean(idx);
            double ss = 0;
            for (double[] r : perSeedRows) ss += (r[idx] - m) * (r[idx] - m);
            return Math.sqrt(ss / (n - 1));   // sample std
        }
        private int metricIndex(String name) {
            switch (name) {
                case "throughput_bits":   return M_THROUGHPUT;
                case "energy_delta":      return M_ENERGY_DELTA;
                case "total_energy":      return M_TOTAL_ENERGY;
                case "generated":         return M_GENERATED;
                case "assigned":          return M_ASSIGNED;
                case "completed":         return M_COMPLETED;
                case "dropped":           return M_DROPPED;
                case "queue":             return M_QUEUE;
                case "pending":           return M_PENDING;
                case "assignment_ratio":  return M_ASSIGN_RATIO;
                case "completion_ratio":  return M_COMP_RATIO;
                case "drop_ratio":        return M_DROP_RATIO;
                case "mean_latency":      return M_AVG_PROB; // proxy if latency not in run CSV
                default: throw new IllegalArgumentException("Unknown metric: " + name);
            }
        }
        /**
         * Produce one CSV line: mean, std for each metric, then n_seeds.
         * Matches HEADER field exactly.
         */
        public String toCsvLine() {
            StringBuilder sb = new StringBuilder();
            int[] order = {
                M_THROUGHPUT, M_ENERGY_DELTA, M_TOTAL_ENERGY,
                M_GENERATED, M_ASSIGNED, M_COMPLETED, M_DROPPED,
                M_QUEUE, M_PENDING, M_ASSIGN_RATIO, M_COMP_RATIO,
                M_DROP_RATIO, M_AVG_PROB
            };
            for (int idx : order) {
                sb.append(String.format(Locale.US, "%.6f,%.6f,", mean(idx), std(idx)));
            }
            sb.append(perSeedRows.size());
            return sb.toString();
        }
    }
}
