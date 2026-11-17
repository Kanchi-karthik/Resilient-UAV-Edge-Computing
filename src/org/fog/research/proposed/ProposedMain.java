package org.fog.research.proposed;
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
public class ProposedMain {
    public static String MY_LOG_DIR = ProposedParams.LOG_DIR; // Resolves to the default folder name "results_proposed"
    public static void main(String[] args) {
        try {
            Log.disable();
            int NUM_UAVS = (args.length >= 1) ? Integer.parseInt(args[0]) : 10;
            double radius = (args.length >= 2) ? Double.parseDouble(args[1]) : 130.0;
            String runTag = (args.length >= 3) ? args[2] : ("proposed_u" + NUM_UAVS + "_r" + (int) radius);
            long seed = (args.length >= 4) ? Long.parseLong(args[3]) : 7L;
            new File(MY_LOG_DIR).mkdirs();
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
            String csvPath = MY_LOG_DIR + File.separator + "run_" + runTag + ".csv";
            ProposedSlotController controller = new ProposedSlotController(
                    "proposed-slot-controller",
                    1.0,
                    100,
                    uavs,
                    csvPath,
                    seed,
                    runTag,
                    radius
            );
            CloudSim.addEntity(controller);
            CloudSim.startSimulation();
            CloudSim.stopSimulation();
            System.out.println("\n=== Proposed Non-Cluster Scheduling Finished ===");
            System.out.println("CSV: " + csvPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
