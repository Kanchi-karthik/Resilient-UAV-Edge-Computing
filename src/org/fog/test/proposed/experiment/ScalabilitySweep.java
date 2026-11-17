package org.fog.test.proposed.experiment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Locale;
import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.SimulationRunner;

/**
 * ScalabilitySweep runs the comparison across varying UAV fleet sizes (2 to 16 UAVs)
 * to demonstrate the theoretical O(1) vs O(n^2) coordination energy scaling.
 */
public class ScalabilitySweep {
    public static void main(String[] args) {
        int[] fleetSizes = {2, 4, 6, 8, 10, 12, 14, 16};
        int slots = (args.length >= 1) ? Integer.parseInt(args[0]) : 100;
        long seed = 7L;
        double radius = 130.0;

        File outDir = new File("test_results/scalability");
        outDir.mkdirs();
        File csvFile = new File(outDir, "scalability_coordination_energy.csv");

        System.out.println("==================================================================");
        System.out.println("   UAV Fleet Scalability Sweep (O(1) Token Ring vs O(n^2) Gossip) ");
        System.out.println("==================================================================");

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, false))) {
            writer.println("UAV_Count,Hybrid_Coordination_Energy_J,Gossip_Coordination_Energy_J,Energy_Ratio_Gossip_to_Hybrid");

            for (int uavCount : fleetSizes) {
                System.out.println("\n--- Testing Fleet Size: " + uavCount + " UAVs ---");
                String tag = "scale_u" + uavCount;

                // 1. Run HYBRID
                ProposedParams.configureResultDirectory(ProposedParams.CoordinationMode.HYBRID);
                SimulationRunner.run(ProposedParams.CoordinationMode.HYBRID,
                        new String[]{String.valueOf(uavCount), String.valueOf(radius), tag, String.valueOf(seed), String.valueOf(slots)});
                double hybridEnergy = getCoordinationEnergy("test_results/hybrid/raw", tag + "_hybrid");

                // 2. Run GOSSIP
                ProposedParams.configureResultDirectory(ProposedParams.CoordinationMode.GOSSIP_REN_2024);
                SimulationRunner.run(ProposedParams.CoordinationMode.GOSSIP_REN_2024,
                        new String[]{String.valueOf(uavCount), String.valueOf(radius), tag, String.valueOf(seed), String.valueOf(slots)});
                double gossipEnergy = getCoordinationEnergy("test_results/gossip_ren/raw", tag + "_gossip_ren");

                double ratio = hybridEnergy > 0 ? gossipEnergy / hybridEnergy : 0.0;
                System.out.printf(Locale.US, "[RESULT] UAVs: %d | Hybrid: %.4f J | Gossip: %.4f J | Ratio: %.2fx%n",
                        uavCount, hybridEnergy, gossipEnergy, ratio);

                writer.printf(Locale.US, "%d,%.6f,%.6f,%.2f%n", uavCount, hybridEnergy, gossipEnergy, ratio);
                writer.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("\n==================================================================");
        System.out.println("  Scalability Sweep Complete! Output: test_results/scalability/");
        System.out.println("==================================================================");
    }

    private static double getCoordinationEnergy(String rawDir, String tagPrefix) {
        File dir = new File(rawDir);
        File[] files = dir.listFiles((d, name) -> name.startsWith("energy_per_uav_") && name.contains(tagPrefix) && name.endsWith(".csv"));
        if (files == null || files.length == 0) return 0.0;
        return readLastSlotCoordination(files[0]);
    }

    private static double readLastSlotCoordination(File file) {
        if (file == null) return 0.0;
        java.util.Map<String, Double> slotTotals = new java.util.LinkedHashMap<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length > 8) {
                    String slot = p[0].trim();
                    double val = Double.parseDouble(p[8].trim());
                    slotTotals.put(slot, slotTotals.getOrDefault(slot, 0.0) + val);
                }
            }
        } catch (Exception ignored) {}
        if (slotTotals.isEmpty()) return 0.0;
        String lastSlot = null;
        for (String k : slotTotals.keySet()) lastSlot = k;
        return slotTotals.getOrDefault(lastSlot, 0.0);
    }
}
