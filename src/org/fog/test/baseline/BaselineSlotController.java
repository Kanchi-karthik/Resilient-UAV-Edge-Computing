package org.fog.test.baseline;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.fog.test.baseline.ProposedTask;
import org.fog.test.baseline.ProposedUAVState;
import org.fog.test.baseline.ProposedParams;
import org.fog.test.baseline.ProposedFogEvents;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
public class BaselineSlotController extends SimEntity {
    private static class MD {
        double x, y;
        MD(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    private final double slotLength;
    private final int maxSlots;
    private final List<ProposedUAVState> uavs;
    private final String csvPath;
    private final Random rand;
    private final String runTag;
    private final SchedulingAlgorithm algorithm;
    private int currentSlot = 0;
    private int totalTasksGenerated = 0;
    private int nextDepGroupId = 1;
    private double lastTotalEnergyJ = 0.0;
    private double[] loadRatioSum;
    private int loadRatioCount = 0;
    private transient PrintWriter csv;
    private transient PrintWriter latencyCsv;
    private transient PrintWriter perUavCsv;
    private transient PrintWriter mdsCsv;
    private transient PrintWriter uavsInitialCsv;
    private transient PrintWriter uavsFinalCsv;
    private final List<MD> mds = new ArrayList<>();
    private final List<ProposedTask> inTransmission = new ArrayList<>();
    private final List<ProposedTask> pendingTasks = new ArrayList<>();
    private final BaselineScheduler scheduler;
    public BaselineSlotController(String name,
                                  double slotLength,
                                  int maxSlots,
                                  List<ProposedUAVState> uavs,
                                  String csvPath,
                                  long seed,
                                  String runTag,
                                  SchedulingAlgorithm algorithm) {
        super(name);
        this.slotLength = slotLength;
        this.maxSlots = maxSlots;
        this.uavs = uavs;
        this.csvPath = csvPath;
        this.rand = new Random(seed);
        this.runTag = runTag;
        this.algorithm = algorithm;
        switch (algorithm) {
        case GALE_SHAPLEY:
            this.scheduler = new GaleShapleyBaselineScheduler();
            break;
        case MGA:
            this.scheduler = new MGABaselineScheduler(seed);
            break;
        case PSO:
            this.scheduler = new PSOBaselineScheduler(seed);
            break; 
        case GREEDY:
        default:
            this.scheduler = new GreedyBaselineScheduler();
            break;
    }
    }

    private org.cloudbus.cloudsim.core.CloudSimTags wrapTag(final int value) {
        return new org.cloudbus.cloudsim.core.CloudSimTags() {
            @Override
            public String toString() {
                return String.valueOf(value);
            }
            @Override
            public int hashCode() {
                return value;
            }
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof org.cloudbus.cloudsim.core.CloudSimTags) {
                    return String.valueOf(value).equals(obj.toString());
                }
                return false;
            }
        };
    }

    @Override
    public void startEntity() {
        mds.clear();
        for (int i = 0; i < ProposedParams.NUM_MDS; i++) {
            mds.add(new MD(
                    rand.nextDouble() * ProposedParams.AREA_X,
                    rand.nextDouble() * ProposedParams.AREA_Y
            ));
        }
        loadRatioSum = new double[uavs.size()];
        loadRatioCount = 0;
        try {
            csv = new PrintWriter(new FileWriter(csvPath, false));
            csv.println("slot,time,reward_covmd,throughput_bits,energy_delta_j,total_energy_j,generated,assigned,unassigned,completed,dropped,queue_total,pending_total,avg_assign_score");
            csv.flush();
            String baseDir = new File(csvPath).getParent();
            latencyCsv = new PrintWriter(new FileWriter(baseDir + File.separator + "latency_" + runTag + ".csv", false));
            latencyCsv.println("task_id,arrival_time,completion_time,latency,deadline,score_or_probability,actual_success");
            latencyCsv.flush();
            perUavCsv = new PrintWriter(new FileWriter(baseDir + File.separator + "per_uav_summary_" + runTag + ".csv", false));
            
            perUavCsv.println("uav_id,completed_total,dropped_total,avg_load_ratio,processed_mcycles_total,posterior_mean");
            perUavCsv.flush();
            String mdsPath = baseDir + File.separator + "mds_positions_" + runTag + ".csv";
            mdsCsv = new PrintWriter(new FileWriter(mdsPath, false));
            mdsCsv.println("md_id,md_x,md_y");
            mdsCsv.flush();
            String uavsInitPath = baseDir + File.separator + "uavs_initial_" + runTag + ".csv";
            uavsInitialCsv = new PrintWriter(new FileWriter(uavsInitPath, false));
            uavsInitialCsv.println("uav_id,uav_x,uav_y,load");
            uavsInitialCsv.flush();
            String uavsFinalPath = baseDir + File.separator + "uavs_final_" + runTag + ".csv";
            uavsFinalCsv = new PrintWriter(new FileWriter(uavsFinalPath, false));
            uavsFinalCsv.println("uav_id,uav_x,uav_y,load");
            uavsFinalCsv.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }

        lastTotalEnergyJ = getTotalEnergyAllUavs();
        writeMdPositions();
        writeInitialUavPositions();
        send(getId(), 0, wrapTag(ProposedFogEvents.NEXT_SLOT));
    }
    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() != null && ev.getTag().hashCode() == ProposedFogEvents.NEXT_SLOT) {
            handleNextSlot();
        }
    }
    private void handleNextSlot() {
        moveMDs(slotLength);
        double[] oldX = new double[uavs.size()];
        double[] oldY = new double[uavs.size()];
        for (int i = 0; i < uavs.size(); i++) {
            oldX[i] = uavs.get(i).getX();
            oldY[i] = uavs.get(i).getY();
        }
        for (ProposedUAVState u : uavs) {
        u.moveRandomly(slotLength, rand);
        }
        ProposedUAVState.buildNeighborsByRange(uavs);
        if (!ProposedUAVState.isGraphConnected(uavs)) {
            for (int i = 0; i < uavs.size(); i++) {
                uavs.get(i).setPosition(oldX[i], oldY[i], uavs.get(i).getZ());
            }
            ProposedUAVState.buildNeighborsByRange(uavs);
        }
        for (ProposedUAVState u : uavs) {
            u.resetSlotStats();
        }
        currentSlot++;
        System.out.println("ALG=" + algorithm + " TIME=" + CloudSim.clock() + " SLOT=" + currentSlot);
        int tasksThisSlot = samplePoisson(ProposedParams.LAMBDA_TASKS);
        int generatedNow = 0;
        while (tasksThisSlot > 0 && totalTasksGenerated < ProposedParams.TASK_SET_LENGTH) {
            MD md = pickCoveredMD();
            if (md == null) {
                tasksThisSlot--;
                continue;
            }
            List<ProposedTask> created = createTask(md.x, md.y, CloudSim.clock());
            for (ProposedTask t : created) {
                pendingTasks.add(t);
                totalTasksGenerated++;
                generatedNow++;
                if (totalTasksGenerated >= ProposedParams.TASK_SET_LENGTH) break;
            }
            tasksThisSlot--;
        }
        List<ProposedTask> ready = getReadyPendingTasks();
        BaselineScheduler.Result r = scheduler.schedule(ready, uavs, slotLength);
        double avgAssignScore = 0.0;
        if (!r.assignments.isEmpty()) {
            for (BaselineScheduler.Assignment a : r.assignments) avgAssignScore += a.estimatedServiceTime;
            avgAssignScore /= r.assignments.size();
        }
        int assigned = 0;
        List<ProposedTask> movedToTransmission = new ArrayList<>();
        for (BaselineScheduler.Assignment a : r.assignments) {
            ProposedTask t = a.task;
            ProposedUAVState u = a.uav;
            double up = u.uplinkRate(t.getMdX(), t.getMdY());
            double down = u.downlinkRate(t.getMdX(), t.getMdY());
            if (up <= 0.0 || down <= 0.0) continue;
            double txTime = t.getDataBits() / up + t.getResultBits() / down;
            double txEnergy = txTime * 1.0;
            t.setAssignedUAV(u);
            t.startMdToUavTx(txTime, txEnergy);
            inTransmission.add(t);
            movedToTransmission.add(t);
            u.sumDataBits += (t.getDataBits() + t.getResultBits());
            u.sumRateBits += (up + down) * slotLength;
            assigned++;
        }
        pendingTasks.removeAll(movedToTransmission);
        List<ProposedTask> arrived = new ArrayList<>();
        for (ProposedTask t : inTransmission) {
            t.reduceMdToUavTx(slotLength);
            if (t.mdToUavArrived()) {
                ProposedUAVState u = t.getAssignedUAV();
                if (u != null) {
                    u.addCommEnergy(t.getTxEnergy());
                    u.enqueueTask(t);
                }
                arrived.add(t);
            }
        }
        inTransmission.removeAll(arrived);
        for (ProposedUAVState u : uavs) {
            u.executeOneSlot(slotLength);
        }
        for (int i = 0; i < uavs.size(); i++) {
            loadRatioSum[i] += uavs.get(i).getLoadRatioThisSlot();
        }
        loadRatioCount++;
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.drainCompletedTasks()) {
                onTaskSuccess(t);
                logLatencySuccess(t);
            }
            for (ProposedTask t : u.drainDroppedTasks()) {
                onTaskFailure(t);
                logLatencyFailure(t);
            }
        }
        double reward = computeRewardCoveredMDs();
        logSlot(reward, generatedNow, assigned, r.unassigned.size(), avgAssignScore);
        if (currentSlot < maxSlots) {
            send(getId(), slotLength, wrapTag(ProposedFogEvents.NEXT_SLOT));
        } else {
            writePerUavSummary();
            writeFinalUavPositions();
            closeAll();
            CloudSim.stopSimulation();
        }
    }
    private List<ProposedTask> getReadyPendingTasks() {
        List<ProposedTask> ready = new ArrayList<>();
        for (ProposedTask t : pendingTasks) {
            if (!t.isFinished() && !t.isDropped() && t.isReleasable()) {
                ready.add(t);
            }
        }
        return ready;
    }
    private void onTaskSuccess(ProposedTask done) {
        if (!done.isDependent()) return;
        int gid = done.getDepGroupId();
        int nextSeq = done.getDepSeq() + 1;
        for (ProposedTask t : pendingTasks) {
            if (t.isDependent() && t.getDepGroupId() == gid && t.getDepSeq() == nextSeq) {
                t.setReleasable(true);
                break;
            }
        }
    }
    private void onTaskFailure(ProposedTask failed) {
        if (!failed.isDependent()) return;
        int gid = failed.getDepGroupId();
        List<ProposedTask> toRemove = new ArrayList<>();
        for (ProposedTask t : pendingTasks) {
            if (t.isDependent() && t.getDepGroupId() == gid && t.getDepSeq() > failed.getDepSeq()) {
                t.markDropped(CloudSim.clock());
                logLatencyFailure(t);
                toRemove.add(t);
            }
        }
        pendingTasks.removeAll(toRemove);
        Iterator<ProposedTask> it = inTransmission.iterator();
        while (it.hasNext()) {
            ProposedTask t = it.next();
            if (t.isDependent() && t.getDepGroupId() == gid && t.getDepSeq() > failed.getDepSeq()) {
                t.markDropped(CloudSim.clock());
                logLatencyFailure(t);
                it.remove();
            }
        }
    }
    private void logLatencySuccess(ProposedTask t) {
        if (latencyCsv == null) return;
        latencyCsv.printf(Locale.US,
                "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n",
                t.getId(),
                t.getArrivalTime(),
                t.getCompletionTime(),
                t.getLatency(),
                t.getDeadlineSec(),
                t.getPredictedProbability() > 0 ? t.getPredictedProbability() : t.getPredictedCost(),
                1);
        latencyCsv.flush();
    }
    private void logLatencyFailure(ProposedTask t) {
        if (latencyCsv == null) return;
        double failTime = (t.getFailureTime() > 0.0) ? t.getFailureTime() : CloudSim.clock();
        double latency = Math.max(0.0, failTime - t.getArrivalTime());
        latencyCsv.printf(Locale.US,
                "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n",
                t.getId(),
                t.getArrivalTime(),
                failTime,
                latency,
                t.getDeadlineSec(),
                t.getPredictedProbability() > 0 ? t.getPredictedProbability() : t.getPredictedCost(),
                0);
        latencyCsv.flush();
    }
    private double computeRewardCoveredMDs() {
        double total = 0.0;
        for (ProposedUAVState u : uavs) {
            int covered = 0;
            for (MD md : mds) {
                if (u.isInCoverage(md.x, md.y)) covered++;
            }
            total += covered;
        }
        return total;
    }
    private List<ProposedTask> createTask(double mdX, double mdY, double now) {
        List<ProposedTask> out = new ArrayList<>();
        double dataBytes = 0.1e6 + rand.nextDouble() * (1.0e6 - 0.1e6);
        double dataBits = dataBytes * 8.0;
        double resultBits = dataBits * 0.1;
        double cpuCycles = 0.5e8 + rand.nextDouble() * (1.5e8 - 0.5e8);
        double deadline = 5 + rand.nextInt(6);
        boolean dependent = rand.nextDouble() < ProposedParams.P_DEP;
        if (!dependent) {
            out.add(new ProposedTask(dataBits, resultBits, cpuCycles, deadline, mdX, mdY, now));
            return out;
        }
        int gid = nextDepGroupId++;
        for (int seq = 1; seq <= ProposedParams.DEP_LEN; seq++) {
            ProposedTask t = new ProposedTask(dataBits, resultBits, cpuCycles, deadline, mdX, mdY, now);
            t.setDependency(gid, seq);
            t.setReleasable(seq == 1);
            out.add(t);
        }
        return out;
    }
    private MD pickCoveredMD() {
        for (int tries = 0; tries < 200; tries++) {
            MD cand = mds.get(rand.nextInt(mds.size()));
            if (isCoveredByAnyUav(cand.x, cand.y)) return cand;
        }
        return null;
    }
    private boolean isCoveredByAnyUav(double x, double y) {
        for (ProposedUAVState u : uavs) {
            if (u.isInCoverage(x, y)) return true;
        }
        return false;
    }
    private void moveMDs(double dt) {
        for (MD md : mds) {
            double ang = rand.nextDouble() * 2.0 * Math.PI;
            double speed = ProposedParams.MD_SPEED_MIN
                    + rand.nextDouble() * (ProposedParams.MD_SPEED_MAX - ProposedParams.MD_SPEED_MIN);
            md.x = clamp(md.x + speed * dt * Math.cos(ang), 0, ProposedParams.AREA_X);
            md.y = clamp(md.y + speed * dt * Math.sin(ang), 0, ProposedParams.AREA_Y);
        }
    }
    private void writeMdPositions() {
        if (mdsCsv == null) return;
        for (int i = 0; i < mds.size(); i++) {
            MD md = mds.get(i);
            mdsCsv.printf(Locale.US, "%d,%.6f,%.6f%n", i, md.x, md.y);
        }
        mdsCsv.flush();
    }
    private void writeInitialUavPositions() {
        if (uavsInitialCsv == null) return;
        for (ProposedUAVState u : uavs) {
            uavsInitialCsv.printf(Locale.US, "%d,%.6f,%.6f,%.6f%n",
                    u.getIndex(), u.getX(), u.getY(), 0.0);
        }
        uavsInitialCsv.flush();
    }
    private void writeFinalUavPositions() {
        if (uavsFinalCsv == null) return;
        for (int i = 0; i < uavs.size(); i++) {
            ProposedUAVState u = uavs.get(i);
            double avgLoad = (loadRatioCount > 0) ? (loadRatioSum[i] / loadRatioCount) : 0.0;
            uavsFinalCsv.printf(Locale.US, "%d,%.6f,%.6f,%.6f%n",
                    u.getIndex(), u.getX(), u.getY(), avgLoad);
        }
        uavsFinalCsv.flush();
    }
    private void logSlot(double reward,
                         int generated,
                         int assigned,
                         int unassigned,
                         double avgAssignScore) {
        if (csv == null) return;
        double thr = 0.0;
        int completed = 0;
        int dropped = 0;
        int qTotal = 0;
        for (ProposedUAVState u : uavs) {
            thr += u.throughputBits;
            completed += u.getCompletedTasksThisSlot();
            dropped += u.getDroppedTasksThisSlot();
            qTotal += u.getQueueSize();
        }
        double totalEnergyNow = getTotalEnergyAllUavs();
        double energyDelta = totalEnergyNow - lastTotalEnergyJ;
        if (energyDelta < 0) energyDelta = 0.0;
        lastTotalEnergyJ = totalEnergyNow;
        if (currentSlot > ProposedParams.WARMUP_SLOTS) {
            csv.printf(Locale.US,
                    "%d,%.3f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d,%d,%d,%d,%.6f%n",
                    currentSlot,
                    CloudSim.clock(),
                    reward,
                    thr,
                    energyDelta,
                    totalEnergyNow,
                    generated,
                    assigned,
                    unassigned,
                    completed,
                    dropped,
                    qTotal,
                    pendingTasks.size(),
                    avgAssignScore
            );
            csv.flush();
        }
    }
    private void writePerUavSummary() {
        if (perUavCsv == null) return;
        // Change the header when opening (in startEntity) to match proposed:
        // "uav_id,completed_total,dropped_total,avg_load_ratio,processed_mcycles_total,posterior_mean"
        for (int i = 0; i < uavs.size(); i++) {
            ProposedUAVState u = uavs.get(i);
            double avgLoad = (loadRatioCount > 0) ? (loadRatioSum[i] / loadRatioCount) : 0.0;
            perUavCsv.printf(Locale.US, "%d,%d,%d,%.6f,%.6f,%.6f%n",
                    u.getIndex(),
                    u.getCompletedTotal(),
                    u.getDroppedTotal(),
                    avgLoad,
                    u.getProcessedMegaCyclesTotal(),
                    0.0);   // baselines have no Bayesian posterior; 0.0 placeholder
        }
        perUavCsv.flush();
    }
    private double getTotalEnergyAllUavs() {
        double e = 0.0;
        for (ProposedUAVState u : uavs) e += u.getTotalEnergy();
        return e;
    }
    private int samplePoisson(double lambda) {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rand.nextDouble();
        } while (p > L);
        return k - 1;
    }
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private void closeAll() {
        if (csv != null) { csv.flush(); csv.close(); csv = null; }
        if (latencyCsv != null) { latencyCsv.flush(); latencyCsv.close(); latencyCsv = null; }
        if (perUavCsv != null) { perUavCsv.flush(); perUavCsv.close(); perUavCsv = null; }
        if (mdsCsv != null)        { mdsCsv.flush();        mdsCsv.close();        mdsCsv = null; }
        if (uavsInitialCsv != null){ uavsInitialCsv.flush();uavsInitialCsv.close();uavsInitialCsv = null; }
        if (uavsFinalCsv != null)  { uavsFinalCsv.flush();  uavsFinalCsv.close();  uavsFinalCsv = null; }
    }
    @Override
    public void shutdownEntity() {
    }
}
