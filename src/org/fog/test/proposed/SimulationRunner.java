package org.fog.test.proposed;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
public final class SimulationRunner {
    /**
     * Primary run method. args[] layout:
     *   args[0] = uavCount   (default 10)
     *   args[1] = radius (m) (default 130.0)
     *   args[2] = runTag     (used AS-IS for filenames - ProposedMain already embeds mode suffix)
     *   args[3] = seed       (default 7)
     *   args[4] = maxSlots   (default ProposedParams.MAX_SLOTS)
     *
     * NOTE: runTag is taken directly from args[2].  We do NOT append the mode name
     * here because ProposedMain already adds "_hybrid" / "_gossip" to the tag.
     * This ensures Hybrid and Gossip CSV files have distinct names while the seed
     * and all other parameters remain identical for a fair comparison.
     */
    public static void run(ProposedParams.CoordinationMode mode, String[] args) {
        try {
            // LOG_DIR is already configured by caller (e.g. ProposedMain)
            ProposedParams.MODE = mode;
            Log.disable();
            int    NUM_UAVS  = (args.length >= 1) ? Integer.parseInt(args[0])   : 10;
            double radius    = (args.length >= 2) ? Double.parseDouble(args[1]) : 130.0;
            // Use runTag verbatim - do NOT append mode name again
            String runTag    = (args.length >= 3) ? args[2]
                                                  : (mode.name().toLowerCase() + "_u" + NUM_UAVS + "_r" + (int) radius);
            long   seed      = (args.length >= 4) ? Long.parseLong(args[3])     : 7L;
            int    maxSlots  = (args.length >= 5) ? Integer.parseInt(args[4])   : ProposedParams.MAX_SLOTS;
            // args[5] = taskCap (how many tasks to generate); 0 = use ProposedParams.TASK_SET_LENGTH
            int    taskCap   = (args.length >= 6) ? Integer.parseInt(args[5])   : 0;

            new File(ProposedParams.LOG_DIR).mkdirs();
            new File(ProposedParams.getSummaryDir()).mkdirs();
            CloudSim.init(1, Calendar.getInstance(), false);
            List<FogDevice> fogDevices = new ArrayList<>();
            List<ProposedUAVState> uavs = new ArrayList<>();
            for (int i = 0; i < NUM_UAVS; i++) {
                FogDevice dev = createFogDevice("PUAV-" + i, 100, 2048, 10000, 10000);
                fogDevices.add(dev);
                ProposedUAVState st = new ProposedUAVState(dev, i);
                st.setCoverageRadius(radius);
                st.setAltitude(ProposedParams.UAV_ALTITUDE_M);
                st.setSpeed(ProposedParams.UAV_SPEED_MPS);
                st.setUavUavRange(ProposedParams.UAV_UAV_RANGE_M);
                st.setCpuHz(ProposedParams.CPU_HZ);
                uavs.add(st);
            }
            placeUavsGridConnected(uavs, ProposedParams.UAV_ALTITUDE_M, seed);
            ProposedUAVState.buildNeighborsByRange(uavs);
            String csvPath = ProposedParams.LOG_DIR + File.separator + "run_" + runTag + ".csv";
            ProposedSlotController controller = new ProposedSlotController(
                    "proposed-slot-controller",
                    1.0,
                    maxSlots,
                    uavs,
                    csvPath,
                    seed,
                    runTag,
                    radius
            );
            // Apply task cap if specified (used by task-sweep loop in ProposedMain)
            if (taskCap > 0) {
                controller.setTaskCap(taskCap);
            }
            if (mode == ProposedParams.CoordinationMode.HYBRID) {
                controller.setCoordinationPolicy(new org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy(uavs, seed));
            } else if (mode == ProposedParams.CoordinationMode.GOSSIP_REN_2024) {
                controller.setCoordinationPolicy(new org.fog.test.proposed.coordination.gossip.GossipCoordinationPolicy());
            } else if (mode == ProposedParams.CoordinationMode.SECBBA) {
                controller.setCoordinationPolicy(new org.fog.test.proposed.coordination.secbba.SECBBACoordinationPolicy(NUM_UAVS, seed));
            } else if (mode == ProposedParams.CoordinationMode.WU_2025) {
                controller.setCoordinationPolicy(new org.fog.test.proposed.coordination.wu2025.WuCoordinationPolicy(seed));
            }
            CloudSim.addEntity(controller);
            CloudSim.startSimulation();
            CloudSim.stopSimulation();
            System.out.println("\n=== " + mode + " Coordination Scheduling Finished ===");
            System.out.println("CSV: " + csvPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convenience overload for programmatic callers (e.g., ExperimentRunner).
     * Constructs the args array and delegates to run(mode, String[]).
     */
    public static void run(ProposedParams.CoordinationMode mode,
                           int uavCount, double radius, String runTag, long seed) {
        run(mode, new String[]{
            String.valueOf(uavCount),
            String.valueOf(radius),
            runTag,
            String.valueOf(seed),
            String.valueOf(ProposedParams.MAX_SLOTS)
        });
    }
    private static void placeUavsGridConnected(List<ProposedUAVState> uavs, double altitude, long seed) {
        int n = uavs.size();
        int rows = (int) Math.max(1, Math.round(Math.sqrt(n)));
        int cols = (int) Math.ceil((double) n / rows);
        double xStep = (double) ProposedParams.AREA_X / (cols + 1);
        double yStep = (double) ProposedParams.AREA_Y / (rows + 1);
        Random rand = new Random(seed);
        for (int attempt = 0; attempt < 50; attempt++) {
            int idx = 0;
            for (int r = 1; r <= rows; r++) {
                for (int c = 1; c <= cols; c++) {
                    if (idx >= n) break;
                    double x = c * xStep;
                    double y = r * yStep;
                    if (attempt > 0) {
                        x = clamp(x + rand.nextGaussian() * 50.0, 0, ProposedParams.AREA_X);
                        y = clamp(y + rand.nextGaussian() * 50.0, 0, ProposedParams.AREA_Y);
                    }
                    uavs.get(idx).setPosition(x, y, altitude);
                    idx++;
                }
            }
            ProposedUAVState.buildNeighborsByRange(uavs);
            if (ProposedUAVState.isGraphConnected(uavs)) {
                return;
            }
        }
    }
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;
        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(100, 50)
        );
        List<Host> hostList = new ArrayList<>();
        hostList.add(host);
        FogDeviceCharacteristics characteristics =
                new FogDeviceCharacteristics("x86", "Linux", "Xen",
                        host, 10.0, 3.0, 0.05, 0.001, 0.0);
        try {
            return new FogDevice(
                    nodeName,
                    characteristics,
                    new AppModuleAllocationPolicy(hostList),
                    new LinkedList<>(),
                    1.0,
                    upBw,
                    downBw,
                    0.0,
                    0.0
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

