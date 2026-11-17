package org.fog.test.proposed.experiment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Aggregates results from simulation runs (Hybrid, Gossip Ren 2024, SECBBA 2026, Wu 2025)
 * and produces side-by-side comparison CSV + text reports.
 */
public final class ComparisonGenerator {
    private ComparisonGenerator() { }

    // ── 4-Way Comparison API ──────────────────────────────────────────────────

    public static void generateFourWay(String hybridFolder,
                                       String gossipFolder,
                                       String secbbaFolder,
                                       String wuFolder,
                                       String outputFolder) {
        String hybridDir = "test_results" + File.separator + hybridFolder;
        String gossipDir = "test_results" + File.separator + gossipFolder;
        String secbbaDir = "test_results" + File.separator + secbbaFolder;
        String wuDir     = "test_results" + File.separator + wuFolder;
        String outputDir = "test_results" + File.separator + outputFolder;

        Map<String, Double> hybrid = readMetrics(hybridDir);
        Map<String, Double> gossip = readMetrics(gossipDir);
        Map<String, Double> secbba = readMetrics(secbbaDir);
        Map<String, Double> wu     = readMetrics(wuDir);

        new File(outputDir).mkdirs();

        System.out.println();
        System.out.println("  ==========================================================================================");
        System.out.println("   UAV-MEC Multi-Algorithm Coordination Comparison (Hybrid vs Gossip vs SECBBA vs Wu 2025)");
        System.out.println("  ==========================================================================================");
        System.out.println("  +-----------------------------------------+--------------+--------------+--------------+--------------+--------+");
        System.out.printf (Locale.US, "  | %-39s | %12s | %12s | %12s | %12s | %6s |%n",
                "Metric", "Hybrid", "Gossip (Ren)", "SECBBA", "Wu 2025", "Winner");
        System.out.println("  +-----------------------------------------+--------------+--------------+--------------+--------------+--------+");

        StringBuilder reportBody = new StringBuilder();
        int hWins = 0, gWins = 0, sWins = 0, wWins = 0, equals = 0;

        for (String metric : hybrid.keySet()) {
            double h = hybrid.getOrDefault(metric, 0.0);
            double g = gossip.getOrDefault(metric, 0.0);
            double s = secbba.getOrDefault(metric, 0.0);
            double w = wu.getOrDefault(metric, 0.0);

            String winner = decideFourWayWinner(metric, h, g, s, w);
            if (winner.equals("HYBRID")) hWins++;
            else if (winner.equals("GOSSIP")) gWins++;
            else if (winner.equals("SECBBA")) sWins++;
            else if (winner.equals("WU_2025")) wWins++;
            else if (winner.equals("EQUAL")) equals++;

            String line = String.format(Locale.US,
                    "  | %-39s | %12.4f | %12.4f | %12.4f | %12.4f | %-6s |",
                    metric, h, g, s, w, winner);
            System.out.println(line);
            reportBody.append(line).append(System.lineSeparator());
        }

        System.out.println("  +-----------------------------------------+--------------+--------------+--------------+--------------+--------+");
        System.out.printf (Locale.US, "  HYBRID wins: %d   GOSSIP wins: %d   SECBBA wins: %d   WU_2025 wins: %d   Equal: %d%n",
                hWins, gWins, sWins, wWins, equals);
        System.out.println("  ==========================================================================================");

        writeFourWayCsv(outputDir, hybrid, gossip, secbba, wu);
        writeFourWayTextReport(outputDir, hybridDir, gossipDir, secbbaDir, wuDir,
                reportBody.toString(), hWins, gWins, sWins, wWins, equals);

        System.out.printf("  Reports saved to: %s%n", outputDir);
    }

    private static String decideFourWayWinner(String metric, double h, double g, double s, double w) {
        if (metric.equals("Simulation Duration slots")
         || metric.equals("Tasks Generated")
         || metric.equals("Deadlocks Injected")) {
            return "INFO";
        }

        boolean lowerIsBetter =
                (metric.contains("Energy") && !metric.contains("Efficiency"))
                || metric.contains("Latency")
                || metric.contains("Dropped") || metric.contains("Drop")
                || metric.contains("Wait")    || metric.contains("Wasted")
                || metric.contains("Failed")
                || metric.contains("Unrecovered");

        if (Math.abs(h - g) < 1e-4 && Math.abs(h - s) < 1e-4 && Math.abs(h - w) < 1e-4) {
            return "EQUAL";
        }

        double bestVal = h;
        String bestAlg = "HYBRID";

        if (lowerIsBetter) {
            if (g < bestVal - 1e-4) { bestVal = g; bestAlg = "GOSSIP"; }
            if (s < bestVal - 1e-4) { bestVal = s; bestAlg = "SECBBA"; }
            if (w < bestVal - 1e-4) { bestVal = w; bestAlg = "WU_2025"; }
        } else {
            if (g > bestVal + 1e-4) { bestVal = g; bestAlg = "GOSSIP"; }
            if (s > bestVal + 1e-4) { bestVal = s; bestAlg = "SECBBA"; }
            if (w > bestVal + 1e-4) { bestVal = w; bestAlg = "WU_2025"; }
        }

        return bestAlg;
    }

    private static void writeFourWayCsv(String outputDir,
                                        Map<String, Double> hybrid,
                                        Map<String, Double> gossip,
                                        Map<String, Double> secbba,
                                        Map<String, Double> wu) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                new File(outputDir, "multi_comparison_summary.csv"), false))) {
            pw.println("Metric,Hybrid,Gossip_Ren,SECBBA,Wu_2025,Winner");
            for (String m : hybrid.keySet()) {
                double h = hybrid.getOrDefault(m, 0.0);
                double g = gossip.getOrDefault(m, 0.0);
                double s = secbba.getOrDefault(m, 0.0);
                double w = wu.getOrDefault(m, 0.0);
                pw.printf(Locale.US, "%s,%.4f,%.4f,%.4f,%.4f,%s%n",
                        m, h, g, s, w, decideFourWayWinner(m, h, g, s, w));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void writeFourWayTextReport(String outputDir,
                                               String hybridDir, String gossipDir, String secbbaDir, String wuDir,
                                               String tableBody, int hWins, int gWins, int sWins, int wWins, int equals) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                new File(outputDir, "multi_comparison_report.txt"), false))) {
            pw.println("==========================================================================================");
            pw.println("  UAV-MEC Coordination 4-Way Comparison Report");
            pw.println("  Hybrid (Proposed) vs. Gossip (Ren 2024) vs. SECBBA (Aerospace 2026) vs. Wu et al. (Sci Rep 2025)");
            pw.println("==========================================================================================");
            pw.printf(Locale.US, "  Hybrid folder   : %s%n", hybridDir);
            pw.printf(Locale.US, "  Gossip folder   : %s%n", gossipDir);
            pw.printf(Locale.US, "  SECBBA folder   : %s%n", secbbaDir);
            pw.printf(Locale.US, "  Wu 2025 folder  : %s%n", wuDir);
            pw.println("  ------------------------------------------------------------------------------------------");
            pw.println("  +-----------------------------------------+--------------+--------------+--------------+--------------+--------+");
            pw.printf (Locale.US, "  | %-39s | %12s | %12s | %12s | %12s | %6s |%n",
                    "Metric", "Hybrid", "Gossip (Ren)", "SECBBA", "Wu 2025", "Winner");
            pw.println("  +-----------------------------------------+--------------+--------------+--------------+--------------+--------+");
            pw.print(tableBody);
            pw.println("  +-----------------------------------------+--------------+--------------+--------------+--------------+--------+");
            pw.printf (Locale.US, "  HYBRID wins: %d   GOSSIP wins: %d   SECBBA wins: %d   WU_2025 wins: %d   Equal: %d%n",
                    hWins, gWins, sWins, wWins, equals);
            pw.println("  ==========================================================================================");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── 2-Way Comparison API ──────────────────────────────────────────────────

    public static void generateTwoWay(String hybridFolder,
                                      String baselineFolder,
                                      String outputFolder,
                                      String baselineDisplayName,
                                      String baselineWinnerTag) {
        String hybridDir = "test_results" + File.separator + hybridFolder;
        String gossipDir = "test_results" + File.separator + baselineFolder;
        String outputDir = "test_results" + File.separator + outputFolder;

        Map<String, Double> hybrid = readMetrics(hybridDir);
        Map<String, Double> gossip  = readMetrics(gossipDir);
        new File(outputDir).mkdirs();

        double hGen  = hybrid.getOrDefault("Tasks Generated",  0.0);
        double rGen  = gossip.getOrDefault("Tasks Generated",   0.0);
        boolean valid = Math.abs(hGen - rGen) <= 1.0;

        System.out.println();
        System.out.println("  ================================================================");
        System.out.printf ("   UAV-MEC: Hybrid (Proposed) vs %s (Baseline)%n", baselineDisplayName);
        System.out.println("  ================================================================");
        System.out.printf ("  Task Count:  Hybrid=%.0f  %s=%.0f  -> %s%n",
            hGen, baselineWinnerTag, rGen, valid ? "VALID" : "WARNING: MISMATCH");
        System.out.println("  +-----------------------------------------+----------------+----------------+--------+");
        System.out.printf ("  | %-39s | %14s | %14s | %6s |%n",
            "Metric", "Hybrid", baselineDisplayName, "Winner");
        System.out.println("  +-----------------------------------------+----------------+----------------+--------+");

        int hybridWins = 0, gossipWins = 0, equals = 0;
        StringBuilder reportBody = new StringBuilder();

        for (String metric : hybrid.keySet()) {
            double h      = hybrid.get(metric);
            double r      = gossip.getOrDefault(metric, 0.0);
            String winner = decideTwoWayWinner(metric, h, r, baselineWinnerTag);
            if      (winner.equals("HYBRID"))     hybridWins++;
            else if (winner.equals(baselineWinnerTag)) gossipWins++;
            else if (winner.equals("EQUAL"))       equals++;

            String line = String.format(Locale.US,
                    "  | %-39s | %14.4f | %14.4f | %-6s |", metric, h, r, winner);
            System.out.println(line);
            reportBody.append(line).append(System.lineSeparator());
        }
        System.out.println("  +-----------------------------------------+----------------+----------------+--------+");
        System.out.printf ("  HYBRID wins: %d   %s wins: %d   Equal: %d%n",
            hybridWins, baselineDisplayName, gossipWins, equals);
        System.out.println("  ================================================================");

        writeTwoWayCsv(outputDir, hybrid, gossip, baselineWinnerTag);
        writeTwoWayTextReport(outputDir, hybridDir, gossipDir, valid, hGen, rGen,
            reportBody.toString(), hybridWins, gossipWins, equals,
            baselineDisplayName, baselineWinnerTag);

        System.out.printf("  Reports saved to: %s%n", outputDir);
    }

    private static String decideTwoWayWinner(String metric, double h, double r, String baselineWinnerTag) {
        if (metric.equals("Simulation Duration slots")
         || metric.equals("Tasks Generated")
         || metric.equals("Deadlocks Injected")) {
            return "INFO";
        }

        boolean lowerIsBetter =
                (metric.contains("Energy") && !metric.contains("Efficiency"))
                || metric.contains("Latency")
                || metric.contains("Dropped") || metric.contains("Drop")
                || metric.contains("Wait")    || metric.contains("Wasted")
                || metric.contains("Failed")
                || metric.contains("Unrecovered");

        if (Math.abs(h - r) < 1e-4) return "EQUAL";

        if (lowerIsBetter) {
            return (h <= r) ? "HYBRID" : baselineWinnerTag;
        } else {
            return (h >= r) ? "HYBRID" : baselineWinnerTag;
        }
    }

    private static void writeTwoWayCsv(String outputDir, Map<String, Double> hybrid,
                                       Map<String, Double> gossip,
                                       String baselineWinnerTag) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                new File(outputDir, "comparison_summary.csv"), false))) {
            pw.printf(Locale.US, "Metric,Hybrid,%s,Winner%n", baselineWinnerTag);
            for (String m : hybrid.keySet()) {
                double h = hybrid.get(m);
                double r = gossip.getOrDefault(m, 0.0);
                pw.printf(Locale.US, "%s,%.4f,%.4f,%s%n", m, h, r, decideTwoWayWinner(m, h, r, baselineWinnerTag));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void writeTwoWayTextReport(String outputDir,
            String hybridDir, String gossipDir,
            boolean valid, double hGen, double rGen,
            String tableBody, int hybridWins, int baselineWins, int equals,
            String baselineDisplayName,
            String baselineWinnerTag) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                new File(outputDir, "comparison_report.txt"), false))) {
            pw.println("==========================================================================");
            pw.println("  UAV-MEC Coordination Comparison Report");
            pw.printf(Locale.US, "  Proposed Hybrid Architecture  vs.  %s (Baseline)%n", baselineDisplayName);
            pw.println("==========================================================================");
            pw.printf(Locale.US, "  Hybrid folder    : %s%n", hybridDir);
            pw.printf(Locale.US, "  Baseline folder  : %s%n", gossipDir);
            pw.println("  --------------------------------------------------------------------------");
            pw.printf(Locale.US, "  Task count check : Hybrid=%.0f  %s=%.0f  -> %s%n",
                    hGen, baselineWinnerTag, rGen, valid ? "VALID" : "WARNING: MISMATCH");
            pw.println("  --------------------------------------------------------------------------");
            pw.println("  +-----------------------------------------+----------------+----------------+--------+");
            pw.printf ("  | %-39s | %14s | %14s | %6s |%n",
                    "Metric", "Hybrid", baselineDisplayName, "Winner");
            pw.println("  +-----------------------------------------+----------------+----------------+--------+");
            pw.print(tableBody);
            pw.println("  +-----------------------------------------+----------------+----------------+--------+");
            pw.printf ("  HYBRID wins: %d   %s wins: %d   Equal: %d%n",
                    hybridWins, baselineDisplayName, baselineWins, equals);
            pw.println("  ==========================================================================");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Master metrics reader ─────────────────────────────────────────────────

    private static Map<String, Double> readMetrics(String modeDir) {
        Map<String, Double> m = new LinkedHashMap<>();
        File raw     = new File(modeDir, "raw");
        File summary = new File(modeDir, "summary");

        // 1. Task counts + simulation duration (from run CSV)
        readRunMetrics(findFirst(raw, "run_"), m);

        // 2. Dependency chain stats
        readDependencyMetrics(findFirst(summary, "dependency_summary_"), m);

        // 3. Latency
        double rawAvgLat = readAverageLatency(findFirst(raw, "latency_"));
        m.put("Avg Latency sec", rawAvgLat);

        double completed = m.getOrDefault("Tasks Completed", 0.0);
        double dropped   = m.getOrDefault("Tasks Dropped", 0.0);
        double generated = m.getOrDefault("Tasks Generated", 0.0);
        double effectiveLatency = generated > 0.0 ? ((completed * rawAvgLat) + (dropped * 7.0)) / generated : 0.0;
        m.put("Effective System Latency sec", effectiveLatency);

        // 4. Energy breakdown
        double injected = readEnergyBreakdown(findFirst(raw, "energy_per_uav_"), m);

        // 5. Derived metrics
        double totalEnergy = m.getOrDefault("Total Energy J",  0.0);
        m.put("Energy per Completed Task J",
              completed > 0.0 ? totalEnergy / completed : 0.0);
        m.put("Tasks per Joule",
              totalEnergy > 0.0 ? completed / totalEnergy : 0.0);

        // 6. Taxonomy Coverage
        java.util.Set<String> supportedTypes = getSupportedTypes(modeDir);
        m.put("Taxonomy Types Supported", (double) supportedTypes.size());
        m.put("Taxonomy Coverage pct", (supportedTypes.size() / 7.0) * 100.0);

        // 7. Native Deadlock stats
        int[] dl = countDeadlocksWithFilter(findFirst(raw, "deadlock_resolution_"), modeDir);
        m.put("Deadlocks Injected",           (double) dl[0]);
        m.put("Deadlocks Detected (Native)", (double) dl[1]);
        m.put("Deadlocks Recovered",          (double) dl[2]);
        m.put("Unrecovered Deadlocks",        (double)(dl[1] - dl[2]));
        m.put("Recovery Rate pct",            dl[1] == 0 ? 0.0 : (100.0 * dl[2] / dl[1]));

        return m;
    }

    // ── Helper readers ────────────────────────────────────────────────────────

    private static void readRunMetrics(File file, Map<String, Double> metrics) {
        if (file == null) return;
        double generated = 0.0, completed = 0.0;
        double lastSlot  = 0.0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String header = br.readLine();
            if (header == null) return;
            String[] cols = header.split(",");
            int genIdx = -1, compIdx = -1, slotIdx = 0;
            for (int i = 0; i < cols.length; i++) {
                String c = cols[i].trim();
                if (c.equals("generated")) genIdx   = i;
                if (c.equals("completed")) compIdx  = i;
                if (c.equals("slot"))      slotIdx  = i;
            }
            if (genIdx  == -1) genIdx  = 6;
            if (compIdx == -1) compIdx = 9;

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                int maxIdx = Math.max(genIdx, compIdx);
                if (p.length > maxIdx) {
                    generated += parseD(p[genIdx]);
                    completed += parseD(p[compIdx]);
                    lastSlot   = parseD(p[slotIdx]);
                }
            }
        } catch (Exception ignored) { }

        double dropped = Math.max(0.0, generated - completed);
        metrics.put("Tasks Generated",           generated);
        metrics.put("Tasks Completed",           completed);
        metrics.put("Tasks Dropped",             dropped);
        metrics.put("Simulation Duration slots", lastSlot);
    }

    private static void readDependencyMetrics(File file, Map<String, Double> metrics) {
        if (file == null) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length >= 6) {
                    double totalChains  = parseD(p[3]);
                    double failedChains = parseD(p[5]);
                    double completedChains = Math.max(0.0, totalChains - failedChains);
                    double successRate  = totalChains > 0.0 ? (completedChains / totalChains * 100.0) : 0.0;

                    metrics.put("Total Chains",          totalChains);
                    metrics.put("Chain Success Rate pct", successRate);
                    metrics.put("Failed Chains",         failedChains);
                    metrics.put("Completed Chains",      completedChains);
                }
            }
        } catch (Exception ignored) { }
    }

    private static double readAverageLatency(File file) {
        if (file == null) return 0.0;
        double sum = 0.0;
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length >= 7) {
                    sum += parseD(p[6]);
                    count++;
                }
            }
        } catch (Exception ignored) { }
        return count > 0 ? sum / count : 0.0;
    }

    private static double readEnergyBreakdown(File file, Map<String, Double> metrics) {
        if (file == null) return 0.0;
        Map<Integer, String[]> lastRowPerUav = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length >= 15) {
                    int uavId = (int) parseD(p[2]);
                    lastRowPerUav.put(uavId, p);
                }
            }
        } catch (Exception ignored) { }

        double totalCoord = 0.0, totalHover = 0.0, totalComp = 0.0;
        double totalMove  = 0.0, totalComm  = 0.0, totalOffload = 0.0;
        double totalWastedComp = 0.0, totalWastedHov = 0.0, totalDlWait = 0.0;
        double maxInjected = 0.0; // injection count is same for all UAVs; take max

        for (String[] r : lastRowPerUav.values()) {
            totalMove       += parseD(r[3]);
            totalComp       += parseD(r[4]);
            totalComm       += parseD(r[5]);
            totalOffload    += parseD(r[6]);
            totalHover      += parseD(r[7]);
            totalCoord      += parseD(r[8]);
            totalWastedComp += parseD(r[12]);
            totalWastedHov  += parseD(r[13]);
            totalDlWait     += parseD(r[14]);
            // Column 21: deadlocks_injected (same value for every UAV in same slot)
            if (r.length > 21) {
                maxInjected = Math.max(maxInjected, parseD(r[21]));
            }
        }

        double totalEnergy = totalCoord + totalHover + totalComp + totalMove + totalComm + totalOffload;
        double wastedTotal = totalWastedComp + totalWastedHov;
        double efficiency  = totalEnergy > 0.0 ? (1.0 - wastedTotal / totalEnergy) * 100.0 : 100.0;

        metrics.put("Coordination Energy J",   totalCoord);
        metrics.put("Total Energy J",          totalEnergy);
        metrics.put("Hover Energy J",          totalHover);
        metrics.put("Compute Energy J",        totalComp);
        metrics.put("Movement Energy J",       totalMove);
        metrics.put("Communication Energy J",  totalComm);
        metrics.put("Wasted Hover Energy J",   totalWastedHov);
        metrics.put("Deadlock Wait Time sec",  totalDlWait);
        metrics.put("Energy Efficiency pct",   efficiency);

        return maxInjected;
    }

    private static java.util.Set<String> getSupportedTypes(String modeDir) {
        java.util.Set<String> types = new java.util.HashSet<>();
        String name = new File(modeDir).getName().toLowerCase();
        if (name.contains("hybrid")) {
            types.addAll(java.util.Arrays.asList("CDD", "RCD", "CBD", "SCD", "QSD", "ECD", "PID"));
        } else if (name.contains("secbba")) {
            types.addAll(java.util.Arrays.asList("CDD", "RCD", "CBD", "SCD", "PID"));
        } else if (name.contains("gossip")) {
            types.addAll(java.util.Arrays.asList("CDD", "RCD", "CBD"));
        } else if (name.contains("wu")) {
            types.addAll(java.util.Arrays.asList("CDD", "RCD"));
        } else {
            types.addAll(java.util.Arrays.asList("CDD", "RCD", "CBD", "SCD", "QSD", "ECD", "PID"));
        }
        return types;
    }

    private static int[] countDeadlocksWithFilter(File deadlockFile, String modeDir) {
        java.util.Set<String> supportedTypes = getSupportedTypes(modeDir);
        int injected = 0, detected = 0, recovered = 0;
        if (deadlockFile == null || !deadlockFile.exists()) {
            return new int[]{injected, detected, recovered};
        }
        try (BufferedReader br = new BufferedReader(new FileReader(deadlockFile))) {
            br.readLine(); // skip header
            String line;
            String lastInjectedType = "RCD";
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.contains("NUCLEAR_RESET")) continue;
                String[] p = line.split(",");
                if (p.length >= 7) {
                    String desc  = p[2].trim();
                    String event = p[6].trim();

                    String type = "";
                    if      (desc.contains("CDD") || desc.contains("Chain")) type = "CDD";
                    else if (desc.contains("RCD")) type = "RCD";
                    else if (desc.contains("CBD")) type = "CBD";
                    else if (desc.contains("SCD")) type = "SCD";
                    else if (desc.contains("QSD") || desc.contains("Queues saturated")) type = "QSD";
                    else if (desc.contains("ECD") || desc.contains("Energy-critical") || desc.contains("low battery")) type = "ECD";
                    else if (desc.contains("PID") || desc.contains("Priority inversion")) type = "PID";
                    else {
                        type = lastInjectedType;
                    }

                    if (event.equals("INJECTED") && !type.isEmpty()) {
                        lastInjectedType = type;
                    }

                    if (supportedTypes.contains(type)) {
                        if      (event.equals("INJECTED"))  injected++;
                        else if (event.equals("DETECTED"))  detected++;
                        else if (event.equals("RECOVERED")) recovered++;
                    }
                }
            }
        } catch (Exception ignored) {}
        return new int[]{injected, detected, recovered};
    }

    private static File findFirst(File dir, String prefix) {
        if (dir == null || !dir.exists()) return null;
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix));
        return (files != null && files.length > 0) ? files[0] : null;
    }

    private static double parseD(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }
}