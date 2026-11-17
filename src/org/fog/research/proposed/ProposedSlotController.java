package org.fog.research.proposed;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
/**
 * ProposedSlotController — enhanced with:
 *
 *  1. DEMAND-AWARE UAV MOBILITY
 *     Each slot, UAVs use moveWithDemandAwareness() to steer toward uncovered
 *     MDs, maximising coverage and reducing unassigned tasks.
 *
 *  2. COOPERATIVE OFFLOADING
 *     After execution, overloaded UAVs (queue load > OFFLOAD_LOAD_THRESH)
 *     extract their least-urgent queued tasks and re-enqueue them on the
 *     least-loaded neighbor, balancing compute load across the fleet.
 *
 *  3. PENDING-TASK EXPIRY PRUNING
 *     Each slot, pending tasks whose absolute deadline has already passed
 *     are pruned as dropped before scheduling — avoiding wasted MCMF edges.
 *
 *  4. DEADLINE-MISS EARLY DROP
 *     Tasks in the in-transmission list whose deadline would be missed even
 *     with zero queue wait are dropped early, freeing UAV capacity.
 */
public class ProposedSlotController extends SimEntity {

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

    // ── Inner class: Mobile Device ────────────────────────────────────────────
    private static class MD {
        double x, y;
        MD(double x, double y) { this.x = x; this.y = y; }
    }
    // ── Writers ───────────────────────────────────────────────────────────────
    private transient PrintWriter csv;
    private transient PrintWriter latencyCsv;
    private transient PrintWriter perUavCsv;
    private transient PrintWriter reliabilityCsv;
    private transient PrintWriter mdsCsv;
    private transient PrintWriter uavsInitialCsv;
    private transient PrintWriter uavsFinalCsv;
    // ── State ─────────────────────────────────────────────────────────────────
    private double[] loadRatioSum;
    private int loadRatioCount = 0;
    private final double slotLength;
    private final int maxSlots;
    private final List<ProposedUAVState> uavs;
    private final String csvPath;
    private final Random rand;
    private final String runTag;
    private final double radius;
    private int currentSlot        = 0;
    private int totalTasksGenerated = 0;
    private int nextDepGroupId     = 1;
    private double lastTotalEnergyJ = 0.0;
    private final List<MD> mds                    = new ArrayList<>();
    private final List<ProposedTask> inTransmission = new ArrayList<>();
    private final List<ProposedTask> pendingTasks   = new ArrayList<>();
    private final BayesianFeasibilityEstimator bayes;
    private final ProbabilisticCostModel        costModel;
    private final FlowScheduler                 scheduler;
    // ── Constructor ───────────────────────────────────────────────────────────
    public ProposedSlotController(String name,
                                  double slotLength,
                                  int maxSlots,
                                  List<ProposedUAVState> uavs,
                                  String csvPath,
                                  long seed,
                                  String runTag,
                                  double radius) {
        super(name);
        this.slotLength = slotLength;
        this.maxSlots   = maxSlots;
        this.uavs       = uavs;
        this.csvPath    = csvPath;
        this.rand       = new Random(seed);
        this.runTag     = runTag;
        this.radius     = radius;
        this.bayes     = new BayesianFeasibilityEstimator(uavs.size());
        this.costModel = new ProbabilisticCostModel();
        this.scheduler = new FlowScheduler(bayes, costModel);
    }
    // ── SimEntity lifecycle ───────────────────────────────────────────────────
    @Override
    public void startEntity() {
        mds.clear();
        for (int i = 0; i < ProposedParams.NUM_MDS; i++) {
            mds.add(new MD(
                    rand.nextDouble() * ProposedParams.AREA_X,
                    rand.nextDouble() * ProposedParams.AREA_Y
            ));
        }
        loadRatioSum   = new double[uavs.size()];
        loadRatioCount = 0;
        openWriters();
        writeMdPositions();
        writeInitialUavPositions();
        lastTotalEnergyJ = getTotalEnergyAllUavs();
        send(getId(), 0, wrapTag(ProposedFogEvents.NEXT_SLOT));
    }
    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() != null && ev.getTag().hashCode() == ProposedFogEvents.NEXT_SLOT) {
            handleNextSlot();
        }
    }
    // ── Main slot handler ─────────────────────────────────────────────────────
    private void handleNextSlot() {
        moveMDs(slotLength);
        // Build MD position list for demand-aware mobility
        List<double[]> mdPositions = new ArrayList<>(mds.size());
        for (MD md : mds) mdPositions.add(new double[]{md.x, md.y});
        // Save positions for connectivity fallback
        double[] oldX = new double[uavs.size()];
        double[] oldY = new double[uavs.size()];
        for (int i = 0; i < uavs.size(); i++) {
            oldX[i] = uavs.get(i).getX();
            oldY[i] = uavs.get(i).getY();
        }
        // ── Demand-aware UAV mobility ─────────────────────────────────────────
        for (ProposedUAVState u : uavs) {
            u.moveWithDemandAwareness(slotLength, rand, mdPositions);
        }
        ProposedUAVState.buildNeighborsByRange(uavs);
        // Restore positions if connectivity is broken
        if (!ProposedUAVState.isGraphConnected(uavs)) {
            for (int i = 0; i < uavs.size(); i++) {
                uavs.get(i).setPosition(oldX[i], oldY[i], uavs.get(i).getZ());
            }
            ProposedUAVState.buildNeighborsByRange(uavs);
        }
        for (ProposedUAVState u : uavs) u.resetSlotStats();
        currentSlot++;
        System.out.println("TIME=" + CloudSim.clock() + " SLOT=" + currentSlot);
        // ── Task generation ───────────────────────────────────────────────────
        int tasksThisSlot = samplePoisson(ProposedParams.LAMBDA_TASKS);
        int generatedNow  = 0;
        while (tasksThisSlot > 0 && totalTasksGenerated < ProposedParams.TASK_SET_LENGTH) {
            MD md = pickCoveredMD();
            if (md == null) { tasksThisSlot--; continue; }
            List<ProposedTask> created = createTask(md.x, md.y, CloudSim.clock());
            for (ProposedTask t : created) {
                pendingTasks.add(t);
                totalTasksGenerated++;
                generatedNow++;
                if (totalTasksGenerated >= ProposedParams.TASK_SET_LENGTH) break;
            }
            tasksThisSlot--;
        }
        // ── Prune expired pending tasks ───────────────────────────────────────
        pruneExpiredPending();
        // ── MCMF scheduling ───────────────────────────────────────────────────
        List<ProposedTask> readyTasks = getReadyPendingTasks();
        FlowScheduler.Result scheduleResult = scheduler.schedule(readyTasks, uavs, slotLength);
        double avgSuccessProb = 0.0;
        for (FlowScheduler.Assignment a : scheduleResult.assignments) {
            avgSuccessProb += a.finalProbability;
        }
        if (!scheduleResult.assignments.isEmpty()) {
            avgSuccessProb /= scheduleResult.assignments.size();
        }
        int assignedTaskCount = 0;
        List<ProposedTask> movedToTransmission = new ArrayList<>();
        for (FlowScheduler.Assignment a : scheduleResult.assignments) {
            ProposedTask     t = a.task;
            ProposedUAVState u = a.uav;
            double rUp   = u.uplinkRate(t.getMdX(), t.getMdY());
            double rDown = u.downlinkRate(t.getMdX(), t.getMdY());
            if (rUp <= 0.0 || rDown <= 0.0) continue;
            double txTime   = t.getDataBits() / rUp + t.getResultBits() / rDown;
            double txEnergy = txTime * 1.0;
            t.setAssignedUAV(u);
            t.setPredictedProbability(a.finalProbability);
            t.setPredictedCost(a.cost);
            t.startMdToUavTx(txTime, txEnergy);
            inTransmission.add(t);
            movedToTransmission.add(t);
            u.sumDataBits += (t.getDataBits() + t.getResultBits());
            u.sumRateBits += (rUp + rDown) * slotLength;
            assignedTaskCount++;
        }
        pendingTasks.removeAll(movedToTransmission);
        // ── Transmission progress + early drop ────────────────────────────────
        List<ProposedTask> arrived  = new ArrayList<>();
        List<ProposedTask> txDropped = new ArrayList<>();
        for (ProposedTask t : inTransmission) {
            t.reduceMdToUavTx(slotLength);
            if (t.mdToUavArrived()) {
                ProposedUAVState u = t.getAssignedUAV();
                if (u != null) {
                    // Early-drop: if even with zero queue wait we'd miss the deadline
                    double absDeadline  = t.getArrivalTime() + t.getDeadlineSec();
                    double compTimeLeft = t.getRemainingCpuCycles() / Math.max(1e-9, u.getCpuHz());
                    double queueWait = 0.0;
                    for (ProposedTask qt : u.getQueue()) {
                        queueWait += qt.getRemainingCpuCycles() / Math.max(1e-9, u.getCpuHz());
                    }
                    if (CloudSim.clock() + queueWait + compTimeLeft > absDeadline) {
                        // drop early
                        t.markDropped(CloudSim.clock());
                        bayes.updateFromOutcome(t, u, slotLength, false);
                        onTaskFailure(t);
                        logLatencyFailure(t);
                        txDropped.add(t);
                    } else {
                        u.addCommEnergy(t.getTxEnergy());
                        u.enqueueTask(t);
                        arrived.add(t);
                    }
                } else {
                    arrived.add(t);
                }
            }
        }
        inTransmission.removeAll(arrived);
        inTransmission.removeAll(txDropped);
        // ── Compute execution (EDF) ───────────────────────────────────────────
        for (ProposedUAVState u : uavs) {
            u.executeOneSlot(slotLength);
        }
        // ── Load tracking ─────────────────────────────────────────────────────
        for (int i = 0; i < uavs.size(); i++) {
            loadRatioSum[i] += uavs.get(i).getLoadRatioThisSlot();
        }
        loadRatioCount++;
        // ── Outcome feedback to Bayesian estimator ────────────────────────────
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.drainCompletedTasks()) {
                bayes.updateFromOutcome(t, u, slotLength, true);
                onTaskSuccess(t);
                logLatencySuccess(t);
            }
            for (ProposedTask t : u.drainDroppedTasks()) {
                bayes.updateFromOutcome(t, u, slotLength, false);
                onTaskFailure(t);
                logLatencyFailure(t);
            }
        }
        // ── Cooperative offloading ────────────────────────────────────────────
        doCooperativeOffloading();
        logReliabilitySnapshot();
        double reward = computeRewardCoveredMDs();
        logSlot(reward, generatedNow, assignedTaskCount,
                scheduleResult.unassigned.size(), avgSuccessProb);
        if (currentSlot < maxSlots) {
            send(getId(), slotLength, wrapTag(ProposedFogEvents.NEXT_SLOT));
        } else {
            writePerUavSummary();
            writeFinalUavPositions();
            closeAll();
            CloudSim.stopSimulation();
        }
    }
    // ── Cooperative offloading ────────────────────────────────────────────────
    /**
     * For each overloaded UAV, extract tail tasks (least urgent) and enqueue
     * them on the least-loaded neighbor that covers the task's MD.
     * This directly reduces queue overload and prevents deadline misses.
     */
    private void doCooperativeOffloading() {
        for (ProposedUAVState overloaded : uavs) {
            if (overloaded.getLoadRatioNow(slotLength) < ProposedParams.OFFLOAD_LOAD_THRESH) {
                continue;
            }
            List<ProposedTask> candidates =
                    overloaded.extractOffloadCandidates(ProposedParams.OFFLOAD_MAX_TASKS);
            for (ProposedTask t : candidates) {
                ProposedUAVState best     = null;
                double           bestLoad = Double.MAX_VALUE;
                for (ProposedUAVState nb : overloaded.getNeighbors()) {
                    // Neighbor must cover the MD and have lower load
                    if (!nb.isInCoverage(t.getMdX(), t.getMdY())) continue;
                    double load = nb.getLoadRatioNow(slotLength);
                    if (load < bestLoad) {
                        bestLoad = load;
                        best     = nb;
                    }
                }
                if (best != null && bestLoad < ProposedParams.OFFLOAD_LOAD_THRESH) {
                    // Verify deadline still feasible after offload
                    double compTime = t.getRemainingCpuCycles() / Math.max(1e-9, best.getCpuHz());
                    double absDeadline = t.getArrivalTime() + t.getDeadlineSec();
                    if (CloudSim.clock() + compTime <= absDeadline) {
                        t.setAssignedUAV(best);
                        best.enqueueTask(t);
                        continue;
                    }
                }
                // No suitable neighbor — put back on original UAV
                overloaded.enqueueTask(t);
            }
        }
    }
    // ── Expired pending task pruning ──────────────────────────────────────────
    private void pruneExpiredPending() {
        double now = CloudSim.clock();
        // Pass 1: Collect — read only, iterator stays clean
        List<ProposedTask> toRemove = new ArrayList<>();
        for (ProposedTask t : pendingTasks) {
            if (t.isFinished() || t.isDropped()) {
                toRemove.add(t);
                continue;
            }
            double absDeadline = t.getArrivalTime() + t.getDeadlineSec();
            if (now >= absDeadline) {
                t.markDropped(now);
                logLatencyFailure(t);   // ✅ safe — doesn't modify pendingTasks
                toRemove.add(t);
            }
        }
        // Pass 2: Remove all at once — no active iterator
        pendingTasks.removeAll(toRemove);
        // Pass 3: Notify failures — onTaskFailure() free to modify pendingTasks now
        for (ProposedTask t : toRemove) {
            if (t.isDropped()) {          // only notify the expired ones, not already-finished
                onTaskFailure(t);
            }
        }
    }
    // ── Task dependency management ────────────────────────────────────────────
    private void onTaskSuccess(ProposedTask done) {
        if (!done.isDependent()) return;
        int gid     = done.getDepGroupId();
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
            if (t.isDependent() && t.getDepGroupId() == gid
                    && t.getDepSeq() > failed.getDepSeq()) {
                t.markDropped(CloudSim.clock());
                logLatencyFailure(t);
                toRemove.add(t);
            }
        }
        pendingTasks.removeAll(toRemove);
        Iterator<ProposedTask> it = inTransmission.iterator();
        while (it.hasNext()) {
            ProposedTask t = it.next();
            if (t.isDependent() && t.getDepGroupId() == gid
                    && t.getDepSeq() > failed.getDepSeq()) {
                t.markDropped(CloudSim.clock());
                logLatencyFailure(t);
                it.remove();
            }
        }
    }
    // ── Ready task selection ──────────────────────────────────────────────────
    private List<ProposedTask> getReadyPendingTasks() {
        List<ProposedTask> ready = new ArrayList<>();
        for (ProposedTask t : pendingTasks) {
            if (!t.isFinished() && !t.isDropped() && t.isReleasable()) {
                ready.add(t);
            }
        }
        return ready;
    }
    // ── Task generation ───────────────────────────────────────────────────────
    private List<ProposedTask> createTask(double mdX, double mdY, double now) {
        List<ProposedTask> out = new ArrayList<>();
        double dataBytes   = 0.1e6 + rand.nextDouble() * (1.0e6 - 0.1e6);
        double dataBits    = dataBytes * 8.0;
        double resultBits  = dataBits * 0.1;
        double cpuCycles   = 0.5e8 + rand.nextDouble() * (1.5e8 - 0.5e8);
        double deadline    = 5 + rand.nextInt(6);
        boolean dependent  = rand.nextDouble() < ProposedParams.P_DEP;
        if (!dependent) {
            out.add(new ProposedTask(dataBits, resultBits, cpuCycles, deadline, mdX, mdY, now));
            return out;
        }
        int gid = nextDepGroupId++;
        for (int seq = 1; seq <= ProposedParams.DEP_LEN; seq++) {
            ProposedTask t = new ProposedTask(dataBits, resultBits, cpuCycles,
                    deadline, mdX, mdY, now);
            t.setDependency(gid, seq);
            t.setReleasable(seq == 1);
            out.add(t);
        }
        return out;
    }
    // ── MD management ─────────────────────────────────────────────────────────
    private MD pickCoveredMD() {
        for (int tries = 0; tries < 200; tries++) {
            MD cand = mds.get(rand.nextInt(mds.size()));
            if (isCoveredByAnyUAV(cand.x, cand.y)) return cand;
        }
        return null;
    }
    private boolean isCoveredByAnyUAV(double x, double y) {
        for (ProposedUAVState u : uavs) {
            if (u.isInCoverage(x, y)) return true;
        }
        return false;
    }
    private void moveMDs(double dt) {
        for (MD md : mds) {
            double ang   = rand.nextDouble() * 2.0 * Math.PI;
            double speed = ProposedParams.MD_SPEED_MIN
                    + rand.nextDouble() * (ProposedParams.MD_SPEED_MAX - ProposedParams.MD_SPEED_MIN);
            md.x = clamp(md.x + speed * dt * Math.cos(ang), 0, ProposedParams.AREA_X);
            md.y = clamp(md.y + speed * dt * Math.sin(ang), 0, ProposedParams.AREA_Y);
        }
    }
    // ── Metrics & logging ─────────────────────────────────────────────────────
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
    private void logSlot(double reward, int generated, int assigned,
                         int unassigned, double avgSuccessProb) {
        if (csv == null) return;
        double thr       = 0.0;
        int    completed = 0;
        int    dropped   = 0;
        int    qTotal    = 0;
        for (ProposedUAVState u : uavs) {
            thr      += u.throughputBits;
            completed += u.getCompletedTasksThisSlot();
            dropped   += u.getDroppedTasksThisSlot();
            qTotal    += u.getQueueSize();
        }
        double totalEnergyNow = getTotalEnergyAllUavs();
        double energyDelta    = Math.max(0.0, totalEnergyNow - lastTotalEnergyJ);
        lastTotalEnergyJ      = totalEnergyNow;
        if (currentSlot > ProposedParams.WARMUP_SLOTS) {
            csv.printf(Locale.US,
                    "%d,%.3f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d,%d,%d,%d,%.6f%n",
                    currentSlot, CloudSim.clock(), reward, thr, energyDelta,
                    totalEnergyNow, generated, assigned, unassigned,
                    completed, dropped, qTotal, pendingTasks.size(), avgSuccessProb);
            csv.flush();
        }
    }
    private void logLatencySuccess(ProposedTask t) {
        if (latencyCsv == null) return;
        latencyCsv.printf(Locale.US,
                "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n",
                t.getId(), t.getArrivalTime(), t.getCompletionTime(),
                t.getLatency(), t.getDeadlineSec(), t.getPredictedProbability(), 1);
        latencyCsv.flush();
    }
    private void logLatencyFailure(ProposedTask t) {
        if (latencyCsv == null) return;
        double failTime = (t.getFailureTime() > 0) ? t.getFailureTime() : CloudSim.clock();
        double latency  = Math.max(0.0, failTime - t.getArrivalTime());
        latencyCsv.printf(Locale.US,
                "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d%n",
                t.getId(), t.getArrivalTime(), failTime, latency,
                t.getDeadlineSec(), t.getPredictedProbability(), 0);
        latencyCsv.flush();
    }
    private void logReliabilitySnapshot() {
        if (reliabilityCsv == null) return;
        double[] means  = bayes.getReliabilityMeans();
        double[] alphas = bayes.getReliabilityAlpha();
        double[] betas  = bayes.getReliabilityBeta();
        for (int i = 0; i < uavs.size(); i++) {
            reliabilityCsv.printf(Locale.US, "%d,%.6f,%d,%.6f,%.6f,%.6f%n",
                    currentSlot, CloudSim.clock(), i, means[i], alphas[i], betas[i]);
        }
        reliabilityCsv.flush();
    }
    private void writePerUavSummary() {
        if (perUavCsv == null) return;
        double[] rel = bayes.getReliabilityMeans();
        for (int i = 0; i < uavs.size(); i++) {
            ProposedUAVState u = uavs.get(i);
            double avgLoad = (loadRatioCount > 0) ? loadRatioSum[i] / loadRatioCount : 0.0;
            perUavCsv.printf(Locale.US, "%d,%d,%d,%.6f,%.6f,%.6f%n",
                    u.getIndex(), u.getCompletedTotal(), u.getDroppedTotal(),
                    avgLoad, u.getProcessedMegaCyclesTotal(), rel[i]);
        }
        perUavCsv.flush();
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
            double avgLoad = (loadRatioCount > 0) ? loadRatioSum[i] / loadRatioCount : 0.0;
            uavsFinalCsv.printf(Locale.US, "%d,%.6f,%.6f,%.6f%n",
                    u.getIndex(), u.getX(), u.getY(), avgLoad);
        }
        uavsFinalCsv.flush();
    }
    // ── Utilities ─────────────────────────────────────────────────────────────
    private double getTotalEnergyAllUavs() {
        double e = 0.0;
        for (ProposedUAVState u : uavs) e += u.getTotalEnergy();
        return e;
    }
    private int samplePoisson(double lambda) {
        double L = Math.exp(-lambda);
        int    k = 0;
        double p = 1.0;
        do { k++; p *= rand.nextDouble(); } while (p > L);
        return k - 1;
    }
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    // ── Writer management ─────────────────────────────────────────────────────
    private void openWriters() {
        try {
            String baseDir = new File(csvPath).getParent();
            if (baseDir == null) baseDir = ProposedParams.LOG_DIR;
            new File(baseDir).mkdirs();
            csv = new PrintWriter(new FileWriter(csvPath, false));
            csv.println("slot,time,reward_covmd,throughput_bits,energy_delta_j," +
                        "total_energy_j,generated,assigned,unassigned,completed," +
                        "dropped,queue_total,pending_total,avg_success_prob");
            csv.flush();
            String latencyPath = baseDir + File.separator + "latency_"       + runTag + ".csv";
            String perUavPath  = baseDir + File.separator + "per_uav_summary_" + runTag + ".csv";
            String relPath     = baseDir + File.separator + "reliability_history_" + runTag + ".csv";
            String mdsPath     = baseDir + File.separator + "mds_positions_"  + runTag + ".csv";
            String uavInitPath = baseDir + File.separator + "uavs_initial_"   + runTag + ".csv";
            String uavFinPath  = baseDir + File.separator + "uavs_final_"     + runTag + ".csv";
            latencyCsv = open(latencyPath,
                    "task_id,arrival_time,completion_time,latency,deadline,final_probability,actual_success");
            perUavCsv  = open(perUavPath,
                    "uav_id,completed_total,dropped_total,avg_load_ratio,processed_mcycles_total,posterior_mean");
            reliabilityCsv = open(relPath,
                    "slot,time,uav_id,reliability,alpha,beta");
            mdsCsv         = open(mdsPath,  "md_id,md_x,md_y");
            uavsInitialCsv = open(uavInitPath, "uav_id,uav_x,uav_y,load");
            uavsFinalCsv   = open(uavFinPath,  "uav_id,uav_x,uav_y,load");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private PrintWriter open(String path, String header) throws Exception {
        PrintWriter pw = new PrintWriter(new FileWriter(path, false));
        pw.println(header);
        pw.flush();
        return pw;
    }
    private void closeAll() {
        close(csv);           csv           = null;
        close(latencyCsv);    latencyCsv    = null;
        close(perUavCsv);     perUavCsv     = null;
        close(reliabilityCsv);reliabilityCsv= null;
        close(mdsCsv);        mdsCsv        = null;
        close(uavsInitialCsv);uavsInitialCsv= null;
        close(uavsFinalCsv);  uavsFinalCsv  = null;
    }
    private void close(PrintWriter pw) {
        if (pw != null) { pw.flush(); pw.close(); }
    }
    @Override
    public void shutdownEntity() {}
}
