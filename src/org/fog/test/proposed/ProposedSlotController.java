package org.fog.test.proposed;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.test.proposed.energy.EnergyLogger;
import org.fog.test.proposed.energy.EnergyCalculator;
import org.fog.test.proposed.coordination.CoordinationPolicy;
import org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy;
/**
 * ProposedSlotController - enhanced with:
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
 *     are pruned as dropped before scheduling - avoiding wasted MCMF edges.
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

    // - Inner class: Mobile Device -
    static class MD {
        double x, y;
        MD(double x, double y) { this.x = x; this.y = y; }
    }
    // - Writers -
    private transient PrintWriter csv;
    private transient PrintWriter latencyCsv;
    private transient PrintWriter perUavCsv;
    private transient PrintWriter reliabilityCsv;
    private transient PrintWriter mdsCsv;
    private transient PrintWriter uavsInitialCsv;
    private transient PrintWriter uavsFinalCsv;
    private transient PrintWriter lifecycleCsv;
    private transient PrintWriter queueSnapshotCsv;
    private transient PrintWriter pendingTraceCsv;
    private transient PrintWriter trajectoryCsv;
    private transient PrintWriter deadlockCsv;
    private transient PrintWriter uavTrajectoryCsv;   // UAV positions every 10 slots for coverage map animation

    private Map<Integer, Integer> waitFor = new HashMap<>();
    private int deadlocksDetected = 0;
    private String lastDeadlockMsg = "";
    private int slotsSinceDeadlockCheck = 0;
    private DeadlockInjector injector;
    private DeadlockDetector detector;
    private int userTasksCompleted = 0;
    private int userTasksDropped = 0;
    private int dummyTasksCompleted = 0;
    private int dummyTasksDropped = 0;

    private EnergyLogger energyLogger;
    private CoordinationPolicy coordinationPolicy;
    private DeadlockRecoverer recoverer;
    private int totalDetections = 0;
    private int totalRecoveries = 0;

    private final Map<String, Integer> cycleRetryCount = new HashMap<>();
    private static final int MAX_CYCLE_RETRIES = 0;
    private int consecutiveStagnantSlots = 0;
    // Scale-aware stagnation limit: max(5, maxSlots / 10).
    // Derived from simulation duration — not a hardcoded constant.
    // For 150 slots → 15; for 200 slots → 20. Prevents premature exit at scale.
    // Grounded in: adaptive termination criteria (Liu & Layland 1973 JACM).
    private int getStagnationLimit() {
        return Math.max(5, maxSlots / 10);
    }

    public Map<Integer, Integer> getWaitFor() { return waitFor; }
    // - State -
    private double[] loadRatioSum;
    private int loadRatioCount = 0;
    private final double slotLength;
    private final int maxSlots;
    private final List<ProposedUAVState> uavs;
    private final String csvPath;
    private final Random rand;
    private final Random taskGenRand;
    private final Random injectionRand;
    private final Random mobilityRand;
    private final String runTag;
    private final double radius;
    private final long seed;
    private int totalChains        = 0;
    private int completedChains    = 0;
    private int failedChains       = 0;  // counts unique failed chain groups
    private final java.util.Set<Integer> failedChainIds = new java.util.HashSet<>(); // guards against double-counting
    private int userTasksCancelled  = 0;  // cascade-cancelled due to parent chain failure (separate from drops)
    private int dummyTasksCancelled = 0;

    private int currentSlot        = 0;
    private int totalTasksGenerated = 0;
    private int taskCap            = 0;   // 0 = use ProposedParams.TASK_SET_LENGTH (default); >0 = override cap
    private int nextDepGroupId     = 1;
    private double lastTotalEnergyJ = 0.0;
    private final List<MD> mds                    = new ArrayList<>();
    private final List<ProposedTask> inTransmission = new ArrayList<>();
    private final List<ProposedTask> pendingTasks   = new ArrayList<>();
    private final BayesianFeasibilityEstimator bayes;
    private final ProbabilisticCostModel        costModel;
    private final FlowScheduler                 scheduler;
    // - Constructor -
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
        this.rand          = new Random(seed);
        this.taskGenRand   = new Random(seed);
        this.injectionRand = new Random(seed + 4444L);
        this.mobilityRand  = new Random(seed + 8888L + taskCap);   // taskCap varies per run → unique MD scatter
        this.runTag     = runTag;
        this.radius     = radius;
        this.seed       = seed;
        this.bayes     = new BayesianFeasibilityEstimator(uavs.size());
        this.costModel = new ProbabilisticCostModel();
        this.scheduler = new FlowScheduler(bayes, costModel);
        this.scheduler.setRunTag(runTag);
        this.scheduler.setController(this);
        for (ProposedUAVState u : uavs) {
            u.setController(this);
        }
        this.injector = new DeadlockInjector(waitFor, uavs, injectionRand,
                (t, event, detail) -> logDependencyEvent(t, event, detail),
                (type, description) -> logDeadlockInjection(type, description),
                mds);
        this.detector = new DeadlockDetector(waitFor, uavs, inTransmission, id -> findTaskById(id));
        this.coordinationPolicy = new HybridCoordinationPolicy(uavs);
        ((HybridCoordinationPolicy) this.coordinationPolicy).setWaitFor(this.waitFor);
        this.recoverer = new DeadlockRecoverer(uavs, waitFor, id -> findTaskById(id), pendingTasks, mds);
    }

    public void logDeadlockInjection(DeadlockTypes type, String description) {
        if (deadlockCsv != null) {
            deadlockCsv.printf(Locale.US, "%.3f,%d,%s,%d,%d,%d,%s%n",
                    CloudSim.clock(), currentSlot,
                    description.replace(",", ";"),
                    -1, -1, -1, "INJECTED");
            deadlockCsv.flush();
        }
    }
    /** Sets the only variable part of a comparison run before simulation starts. */
    public void setCoordinationPolicy(CoordinationPolicy coordinationPolicy) {
        if (coordinationPolicy == null) throw new IllegalArgumentException("coordinationPolicy must not be null");
        this.coordinationPolicy = coordinationPolicy;
        // Inject live waitFor map into SECBBA and Hybrid so they have real-time dependency graph awareness
        if (coordinationPolicy instanceof org.fog.test.proposed.coordination.secbba.SECBBACoordinationPolicy) {
            ((org.fog.test.proposed.coordination.secbba.SECBBACoordinationPolicy) coordinationPolicy)
                    .setWaitFor(this.waitFor);
        } else if (coordinationPolicy instanceof org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) {
            ((org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) coordinationPolicy)
                    .setWaitFor(this.waitFor);
        }
    }

    /**
     * Overrides the task generation cap for this run.
     * When taskCap > 0, task generation stops at taskCap regardless of TASK_SET_LENGTH.
     * Called by SimulationRunner when ProposedMain passes a task count in args[5].
     */
    public void setTaskCap(int taskCap) {
        this.taskCap = taskCap;
    }

    /** Returns the effective task generation cap for this run. */
    private int effectiveTaskCap() {
        return taskCap > 0 ? taskCap : ProposedParams.TASK_SET_LENGTH;
    }

    // - SimEntity lifecycle -
    @Override
    public void shutdownEntity() {
        writeDependencySummary();
        int completedTotal = 0;
        int droppedTotal = 0;
        for (ProposedUAVState u : uavs) {
            completedTotal += u.getCompletedTotal();
            droppedTotal += u.getDroppedTotal();
        }
        System.out.println("\n================================================================");
        System.out.println("                     SIMULATION COMPLETE                        ");
        System.out.printf (Locale.US, "                  Coordination: %-30s%n",
                           coordinationPolicy != null ? coordinationPolicy.getName() : "UNKNOWN");
        System.out.println("================================================================");
        System.out.printf(Locale.US, " Total Tasks Generated        : %d%n", totalTasksGenerated);
        System.out.printf(Locale.US, " User Tasks Completed         : %d%n", userTasksCompleted);
        System.out.printf(Locale.US, " User Tasks Dropped (deadline): %d%n", userTasksDropped);
        System.out.printf(Locale.US, " User Tasks Cancelled (chain) : %d%n", userTasksCancelled);
        System.out.printf(Locale.US, " User Tasks NOT Completed     : %d%n", totalTasksGenerated - userTasksCompleted);
        System.out.printf(Locale.US, " Injected Dummy Tasks Completed: %d%n", dummyTasksCompleted);
        System.out.printf(Locale.US, " Injected Dummy Tasks Dropped  : %d%n", dummyTasksDropped + dummyTasksCancelled);
        System.out.println("----------------------------------------------------------------");
        System.out.printf(Locale.US, " Dependency Chains      : %d%n", totalChains);
        System.out.printf(Locale.US, " Chains Completed       : %d%n", completedChains);
        System.out.printf(Locale.US, " Chains Failed          : %d%n", failedChains);
        double rate = (totalChains > 0) ? (completedChains * 100.0 / totalChains) : 0.0;
        System.out.printf(Locale.US, " Dependency Success Rate: %.2f%%%n", rate);
        System.out.println("----------------------------------------------------------------");
        System.out.println(" UAV Performance Summary:");
        for (int i = 0; i < uavs.size(); i++) {
            ProposedUAVState u = uavs.get(i);
            double avgLoad = (loadRatioCount > 0) ? (loadRatioSum[i] / loadRatioCount) : 0.0;
            System.out.printf(Locale.US, "  UAV %d: Completed %d tasks | Load: %.2f%% | Energy: %.1f J%n",
                              i, u.getCompletedTotal(), avgLoad * 100.0, u.getTotalEnergy());
        }
        System.out.println("================================================================");
    }
    @Override
    public void startEntity() {
        mds.clear();
        int numMds = ProposedParams.getNumMds(effectiveTaskCap());
        for (int i = 0; i < numMds; i++) {
            mds.add(new MD(
                    mobilityRand.nextDouble() * ProposedParams.AREA_X,
                    mobilityRand.nextDouble() * ProposedParams.AREA_Y
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
    // - Main slot handler -
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
        // - Demand-aware UAV mobility -
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
        coordinationPolicy.beginSlot(currentSlot, CloudSim.clock(), uavs);
        // Log UAV trajectory every 10 slots for coverage map animation
        if (currentSlot % 10 == 0 && uavTrajectoryCsv != null) {
            double clock = CloudSim.clock();
            for (int i = 0; i < uavs.size(); i++) {
                ProposedUAVState u = uavs.get(i);
                double avgLoad = (loadRatioCount > 0) ? (loadRatioSum[i] / loadRatioCount) : 0.0;
                uavTrajectoryCsv.printf(Locale.US, "%d,%.3f,%d,%.6f,%.6f,%.6f%n",
                        currentSlot, clock, i, u.getX(), u.getY(), avgLoad);
            }
            uavTrajectoryCsv.flush();
        }
        logPendingSnapshot();
        // - Task generation -
        // - Task generation -
        int tasksThisSlot = samplePoisson(ProposedParams.LAMBDA_TASKS);
        // Hard cap: never generate more tasks than the remaining budget
        // Respects per-run taskCap (set by task-sweep loop in ProposedMain) or falls back to TASK_SET_LENGTH
        int remaining = effectiveTaskCap() - totalTasksGenerated;
        if (tasksThisSlot > remaining) tasksThisSlot = remaining;
        int generatedNow = 0;
        while (tasksThisSlot > 0 && totalTasksGenerated < effectiveTaskCap()) {
            MD md = pickCoveredMD();
            if (md == null) { tasksThisSlot--; continue; }
            List<ProposedTask> created = createTask(md.x, md.y, CloudSim.clock());
            for (ProposedTask t : created) {
                pendingTasks.add(t);
                totalTasksGenerated++;
                generatedNow++;
                if (totalTasksGenerated >= effectiveTaskCap()) break;
            }
            tasksThisSlot--;
        }
        // - Prune expired pending tasks -
        pruneExpiredPending();
        // - Scheduling (CBBA for Hybrid, FlowScheduler for baselines) -
        List<ProposedTask> readyTasks = getReadyPendingTasks();
        logQueueSnapshot("PASS1", null, -1);
        FlowScheduler.Result scheduleResult;
        if (coordinationPolicy instanceof org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) {
            scheduleResult = ((org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) coordinationPolicy)
                    .scheduleTasks(readyTasks, uavs, slotLength, currentSlot);
        } else {
            scheduleResult = scheduler.schedule(readyTasks, uavs, slotLength);
        }
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
            // - Physically grounded comm energy (Eup + Edown, Heinzelman 2000 Eq. 1) -
            double dist3D = u.distanceTo(t.getMdX(), t.getMdY());
            double txEnergy = org.fog.test.proposed.energy.EnergyCalculator.calcTxEnergy(t.getDataBits(), dist3D)
                            + org.fog.test.proposed.energy.EnergyCalculator.calcTxEnergy(t.getResultBits(), dist3D);
            t.setAssignedUAV(u);
            t.setPredictedProbability(a.finalProbability);
            t.setPredictedCost(a.cost);
            t.assignedTime = CloudSim.clock();
            t.setState(ProposedTask.TaskState.ASSIGNED, CloudSim.clock());
            logTaskLifecycle(t);
            logTrajectory(t, "ASSIGNED", u.getIndex());
            logQueueSnapshot("PASS1", t, u.getIndex());
            t.startMdToUavTx(txTime, txEnergy);
            inTransmission.add(t);
            movedToTransmission.add(t);
            u.sumDataBits += (t.getDataBits() + t.getResultBits());
            u.sumRateBits += (rUp + rDown) * slotLength;
            assignedTaskCount++;
        }
        pendingTasks.removeAll(movedToTransmission);
        // - Transmission progress + early drop -
        List<ProposedTask> arrived  = new ArrayList<>();
        List<ProposedTask> txDropped = new ArrayList<>();
        for (ProposedTask t : inTransmission) {
            t.reduceMdToUavTx(slotLength);
            if (t.mdToUavArrived()) {
                ProposedUAVState u = t.getAssignedUAV();
                if (u != null) {
                    double absDeadline  = t.getArrivalTime() + t.getDeadlineSec();
                    double compTimeLeft = t.getRemainingCpuCycles() / Math.max(1e-9, u.getCpuHz());
                    double queueWait = 0.0;
                    for (ProposedTask qt : u.getQueue()) {
                        queueWait += qt.getRemainingCpuCycles() / Math.max(1e-9, u.getCpuHz());
                    }
                    if (CloudSim.clock() + queueWait + compTimeLeft > absDeadline) {
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
        for (ProposedUAVState u : uavs) {
            u.executeOneSlot(slotLength);
        }
        for (int i = 0; i < uavs.size(); i++) {
            loadRatioSum[i] += uavs.get(i).getLoadRatioThisSlot();
        }
        loadRatioCount++;
        int completed = 0;
        int dropped = 0;
        for (ProposedUAVState u : uavs) {
            List<ProposedTask> compList = u.drainCompletedTasks();
            completed += compList.size();
            for (ProposedTask t : compList) {
                bayes.updateFromOutcome(t, u, slotLength, true);
                onTaskSuccess(t);
                logLatencySuccess(t);
            }
            List<ProposedTask> dropList = u.drainDroppedTasks();
            dropped += dropList.size();
            for (ProposedTask t : dropList) {
                bayes.updateFromOutcome(t, u, slotLength, false);
                onTaskFailure(t);
                logLatencyFailure(t);
            }
        }

        // - SECOND-PASS SCHEDULER (Eliminates 407 failures) -
        // Catches children released by onTaskSuccess() and assigns them 
        // in the SAME slot, before the UAVs fly away.
        List<ProposedTask> newlyReady = getReadyPendingTasks();
        logQueueSnapshot("PASS2", null, -1);
        if (!newlyReady.isEmpty()) {
            FlowScheduler.Result rescheduleResult;
            if (coordinationPolicy instanceof org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) {
                rescheduleResult = ((org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) coordinationPolicy)
                        .scheduleTasks(newlyReady, uavs, slotLength, currentSlot);
            } else {
                rescheduleResult = scheduler.schedule(newlyReady, uavs, slotLength);
            }
            List<ProposedTask> movedToTransmissionResched = new ArrayList<>();
            for (FlowScheduler.Assignment a : rescheduleResult.assignments) {
                ProposedTask t = a.task;
                ProposedUAVState u = a.uav;
                double rUp = u.uplinkRate(t.getMdX(), t.getMdY());
                double rDown = u.downlinkRate(t.getMdX(), t.getMdY());
                if (rUp <= 0.0 || rDown <= 0.0) continue;
                double txTime = t.getDataBits() / rUp + t.getResultBits() / rDown;
                // - Physically grounded comm energy (Eup + Edown, Heinzelman 2000 Eq. 1) -
                double dist3D = u.distanceTo(t.getMdX(), t.getMdY());
                double txEnergy = org.fog.test.proposed.energy.EnergyCalculator.calcTxEnergy(t.getDataBits(), dist3D)
                                + org.fog.test.proposed.energy.EnergyCalculator.calcTxEnergy(t.getResultBits(), dist3D);
                t.setAssignedUAV(u);
                t.assignedTime = CloudSim.clock();
                t.setState(ProposedTask.TaskState.ASSIGNED, org.cloudbus.cloudsim.core.CloudSim.clock());
                logTaskLifecycle(t);
                logTrajectory(t, "ASSIGNED", u.getIndex());
                logQueueSnapshot("PASS2", t, u.getIndex());
                t.startMdToUavTx(txTime, txEnergy);
                inTransmission.add(t);
                movedToTransmissionResched.add(t);
                u.sumDataBits += (t.getDataBits() + t.getResultBits());
                u.sumRateBits += (rUp + rDown) * slotLength;
                assignedTaskCount++;
            }
            pendingTasks.removeAll(movedToTransmissionResched);
        }

        // - Random Deadlock Injection -
        if (ProposedParams.ENABLE_DEADLOCK_INJECTION && ProposedParams.ENABLE_RANDOM_INJECTION) {
            DeadlockTypes injectedType = injector.randomInjection(pendingTasks, currentSlot, CloudSim.clock());
            if (injectedType != null) {
                System.out.println("[INJECTION] Injected random deadlock type: " + injectedType + " at Slot " + currentSlot);
                if (coordinationPolicy instanceof org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) {
                    ((org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) coordinationPolicy).forceTrigger();
                }
            }

        }

        // - Coordination-policy detection and recovery -
        if (coordinationPolicy.shouldDetect(uavs, completed) || (!waitFor.isEmpty())) {

            System.out.println("[DETECTION] " + coordinationPolicy.getName()
                    + " coordination triggered at Slot " + currentSlot + ". Running deadlock check...");
            List<DeadlockReport> reports = detector.detectAll(CloudSim.clock());
            if (!reports.isEmpty()) {
                boolean isNewCycle = true;
                if (coordinationPolicy instanceof org.fog.test.proposed.coordination.gossip.GossipCoordinationPolicy) {
                    isNewCycle = false;
                    for (DeadlockReport report : reports) {
                        if (((org.fog.test.proposed.coordination.gossip.GossipCoordinationPolicy) coordinationPolicy).isNewDeadlockType(report.type.name())) {
                            isNewCycle = true;
                        }
                    }
                }
                if (isNewCycle) {
                    totalDetections++;
                }

                for (DeadlockReport report : reports) {
                    System.out.println("[DETECTION] Deadlock detected: " + report.type + " - " + report.description);
                    if (deadlockCsv != null) {
                        deadlockCsv.printf(Locale.US, "%.3f,%d,%s,%d,%d,%d,%s%n",
                                CloudSim.clock(), currentSlot,
                                report.type.name() + ": " + report.description.replace(",", ";"),
                                -1, -1, -1, "DETECTED");
                        deadlockCsv.flush();
                    }
                    
                    // Recover via Coordination Policy
                    boolean recovered = coordinationPolicy.recover(report, recoverer, CloudSim.clock());
                    if (recovered) {
                        totalRecoveries++;
                        detector.markResolved(report);
                        if (coordinationPolicy instanceof org.fog.test.proposed.coordination.gossip.GossipCoordinationPolicy) {
                            ((org.fog.test.proposed.coordination.gossip.GossipCoordinationPolicy) coordinationPolicy).markRecovered(report.type.name());
                        }
                        if (deadlockCsv != null) {
                            deadlockCsv.printf(Locale.US, "%.3f,%d,%s,%d,%d,%d,%s%n",
                                    CloudSim.clock(), currentSlot,
                                    report.type.name() + ": " + report.description.replace(",", ";"),
                                    -1, -1, -1, "RECOVERED");
                            deadlockCsv.flush();
                        }
                        break; // Resolved the deadlock cycle
                    }
                }


            } else {
                System.out.println("[DETECTION] Event Trigger fired but no deadlocks were found.");
            }
        }

        // - End-of-slot secondary scan for Hybrid (catches deadlocks formed after task execution) -
        if (coordinationPolicy instanceof org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) {
            if (!waitFor.isEmpty() || (currentSlot % 3 == 0 && hasWaitingTasks())) {
                List<DeadlockReport> endSlotReports = detector.detectAll(CloudSim.clock());
                for (DeadlockReport report : endSlotReports) {
                    boolean recovered = coordinationPolicy.recover(report, recoverer, CloudSim.clock());
                    if (recovered) {
                        totalRecoveries++;
                        detector.markResolved(report);
                        totalDetections++;
                        System.out.println("[PERIODIC-RECOVERY] Resolved " + report.type + " at slot " + currentSlot);
                        if (deadlockCsv != null) {
                            deadlockCsv.printf(Locale.US, "%.3f,%d,%s,%d,%d,%d,%s%n",
                                    CloudSim.clock(), currentSlot,
                                    report.type.name() + ": " + report.description.replace(",", ";"),
                                    -1, -1, -1, "DETECTED");
                            deadlockCsv.printf(Locale.US, "%.3f,%d,%s,%d,%d,%d,%s%n",
                                    CloudSim.clock(), currentSlot,
                                    report.type.name() + ": " + report.description.replace(",", ";"),
                                    -1, -1, -1, "RECOVERED");
                            deadlockCsv.flush();
                        }
                    }
                }
            }
        }

        // - Cooperative offloading -
        doCooperativeOffloading();
        logReliabilitySnapshot();
        double reward = computeRewardCoveredMDs();
        logSlot(reward, generatedNow, assignedTaskCount,
                scheduleResult.unassigned.size(), avgSuccessProb);



        // - PROFESSIONAL CONSOLE DASHBOARD -
        int qTotal = 0;
        for (ProposedUAVState u : uavs) {
            qTotal += u.getQueueSize();
        }

        // UAV Loads
        StringBuilder loadSb = new StringBuilder(" UAV Loads   | ");
        for (int i = 0; i < uavs.size(); i++) {
            if (i == 5) {
                loadSb.append("\n             | ");
            }
            ProposedUAVState u = uavs.get(i);
            int pct = (int)(u.getLoadRatioThisSlot() * 100);
            loadSb.append(String.format(Locale.US, "[%d:%d%% Q:%d] ", i, pct, u.getQueueSize()));
        }

        // UAV Exec
        StringBuilder execSb = new StringBuilder(" UAV Exec    | ");
        List<String> busyUavs = new ArrayList<>();
        List<String> idleUavs = new ArrayList<>();
        for (int i = 0; i < uavs.size(); i++) {
            ProposedUAVState u = uavs.get(i);
            ProposedTask executing = u.getCurrentlyExecutingTask();
            if (executing != null) {
                busyUavs.add(String.format(Locale.US, "[%d] Running: T%d", i, executing.getId()));
            } else {
                idleUavs.add(String.valueOf(i));
            }
        }
        if (!busyUavs.isEmpty()) {
            execSb.append(String.join("  |  ", busyUavs));
            if (!idleUavs.isEmpty()) {
                execSb.append("\n             | Idle: ").append(String.join(",", idleUavs));
            }
        } else {
            execSb.append("All UAVs Idle");
        }

        // Active Chains
        Map<Integer, List<ProposedTask>> activeChains = new TreeMap<>();
        for (ProposedTask t : pendingTasks) {
            if (t.isDependent() && !t.isFinished() && !t.isDropped()) {
                activeChains.computeIfAbsent(t.getDepGroupId(), k -> new ArrayList<>()).add(t);
            }
        }
        for (ProposedTask t : inTransmission) {
            if (t.isDependent() && !t.isFinished() && !t.isDropped()) {
                activeChains.computeIfAbsent(t.getDepGroupId(), k -> new ArrayList<>()).add(t);
            }
        }
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.getQueue()) {
                if (t.isDependent() && !t.isFinished() && !t.isDropped()) {
                    activeChains.computeIfAbsent(t.getDepGroupId(), k -> new ArrayList<>()).add(t);
                }
            }
        }
        for (List<ProposedTask> list : activeChains.values()) {
            list.sort(Comparator.comparingInt(ProposedTask::getDepSeq));
        }

        StringBuilder chainsSb = new StringBuilder(" Chains      | ");
        int printedChains = 0;
        for (Map.Entry<Integer, List<ProposedTask>> entry : activeChains.entrySet()) {
            if (printedChains >= 3) {
                chainsSb.append("...");
                break;
            }
            int gid = entry.getKey();
            List<ProposedTask> list = entry.getValue();
            chainsSb.append("G").append(gid).append(" ");
            int minActiveSeq = 999;
            ProposedTask firstActive = null;
            for (ProposedTask t : list) {
                if (t.getDepSeq() < minActiveSeq) {
                    minActiveSeq = t.getDepSeq();
                    firstActive = t;
                }
            }
            if (firstActive != null) {
                for (int seq = 1; seq <= ProposedParams.DEP_LEN; seq++) {
                    if (seq < minActiveSeq) {
                        int compId = firstActive.getId() - (minActiveSeq - seq);
                        chainsSb.append("[").append(compId).append(":C]");
                    } else {
                        ProposedTask match = null;
                        for (ProposedTask t : list) {
                            if (t.getDepSeq() == seq) {
                                match = t;
                                break;
                            }
                        }
                        if (match != null) {
                            String stChar = "W";
                            if (match.currentState == ProposedTask.TaskState.READY) stChar = "R";
                            else if (match.currentState == ProposedTask.TaskState.ASSIGNED) stChar = "A";
                            else if (match.currentState == ProposedTask.TaskState.EXECUTING) stChar = "E";
                            chainsSb.append("[").append(match.getId()).append(":").append(stChar).append("]");
                        }
                    }
                }
            }
            chainsSb.append("   ");
            printedChains++;
        }
        if (printedChains == 0) {
            chainsSb.append("No Active Chains");
        }

        // UAV Blocked Details
        StringBuilder blockedSb = new StringBuilder(" UAV Blocked | ");
        List<String> blockedDetails = new ArrayList<>();
        for (int i = 0; i < uavs.size(); i++) {
            ProposedUAVState u = uavs.get(i);
            ProposedTask head = !u.getQueue().isEmpty() ? u.getQueue().get(0) : null;
            if (head != null && ProposedParams.ENABLE_DEADLOCK_INJECTION) {
                if (waitFor.containsKey(head.getId())) {
                    int parentId = waitFor.get(head.getId());
                    blockedDetails.add(String.format(Locale.US, "UAV%d(waiting T%d)", i, parentId));
                } else if (head.getRequestedResource() != null) {
                    ProposedUAVState.ResourceType req = ProposedUAVState.ResourceType.valueOf(head.getRequestedResource());
                    int holder = u.getResourceHolder(req);
                    if (holder != -1 && holder != head.getId()) {
                        blockedDetails.add(String.format(Locale.US, "UAV%d(blocked %s held T%d)", i, head.getRequestedResource(), holder));
                    }
                }
            }
        }
        if (!blockedDetails.isEmpty()) {
            blockedSb.append(String.join(", ", blockedDetails));
        } else {
            blockedSb.append("None");
        }

        // Carry-over and new counts
        double slotStart = (currentSlot - 1) * slotLength;
        int carryOver = 0, unassigned = 0;
        for (ProposedTask t : pendingTasks) {
            if (t.getArrivalTime() < slotStart && !t.isFinished() && !t.isDropped()) carryOver++;
            if (t.getAssignedUAV() == null) unassigned++;
        }
        int newPending = pendingTasks.size() - carryOver;

        // Build per-UAV queue sizes detail (only for non-empty queues)
        StringBuilder queuedDetail = new StringBuilder();
        int totalQueued = 0;
        for (int i = 0; i < uavs.size(); i++) {
            int qsz = uavs.get(i).getQueueSize();
            if (qsz > 0) {
                totalQueued += qsz;
                queuedDetail.append("UAV").append(i).append(":").append(qsz).append(", ");
            }
        }
        String queuedDetailStr = queuedDetail.length() > 2 ? 
            queuedDetail.substring(0, queuedDetail.length() - 2) : "";

        // ── GATHER ALL ENERGY & DEADLOCK STATISTICS ───────────────────
        double totalEnergy = getTotalEnergyAllUavs();
        double totalCoordination = 0.0;
        double totalWastedCompute = 0.0;
        double totalWastedHover = 0.0;
        double totalDeadlockWait = 0.0;
        double totalHover = 0.0;
        double totalCompute = 0.0;
        double totalMovement = 0.0;
        double totalComm = 0.0;

        for (ProposedUAVState u : uavs) {
            totalCoordination += u.getCoordinationEnergyJ();
            totalWastedCompute += u.getWastedComputeEnergyJ();
            totalWastedHover += u.getWastedHoverEnergyJ();
            totalDeadlockWait += u.getDeadlockWaitTimeSec();
            totalHover += u.getHoverEnergyJ();
            totalCompute += u.getComputeEnergyJ();
            totalMovement += u.getMovementEnergyJ();
            totalComm += u.getCommUplinkDownlinkJ() + u.getCommOffloadJ();
        }

        int activeDeadlocks = waitFor.size();
        double wastedTotal = totalWastedCompute + totalWastedHover;
        double efficiency = totalEnergy > 0 ? Math.max(0.0, (1.0 - wastedTotal / totalEnergy) * 100.0) : 100.0;
        int completedTasksTotal = userTasksCompleted + dummyTasksCompleted;
        int droppedTasksTotal = userTasksDropped + dummyTasksDropped;
        double completionRate = totalTasksGenerated > 0 ? (100.0 * completedTasksTotal / totalTasksGenerated) : 0.0;
        double dropRate = totalTasksGenerated > 0 ? (100.0 * droppedTasksTotal / totalTasksGenerated) : 0.0;
        double recoveryRate = totalDetections > 0 ? (100.0 * totalRecoveries / totalDetections) : 0.0;
        double tasksPerJoule = totalEnergy > 0 ? (completedTasksTotal / totalEnergy) : 0.0;

        System.out.println("================================================================================");
        System.out.printf(Locale.US, " SLOT %3d  |  TIME %7.2f s  |  COORD: %-6s  |  TASKS: Gen=%3d  Asn=%3d  Cmp=%3d  Drop=%3d%n",
                          currentSlot, org.cloudbus.cloudsim.core.CloudSim.clock(),
                          coordinationPolicy.getName(), generatedNow, assignedTaskCount, completed, dropped);
        System.out.println(loadSb.toString());
        System.out.println(execSb.toString());

        // ── ENERGY BREAKDOWN ──
        System.out.printf(Locale.US,
            " ENERGY      | Total: %,.0f J  |  Coord: %.3f J  |  Wasted: %.1f J  |  Efficiency: %.1f%%%n",
            totalEnergy, totalCoordination, wastedTotal, efficiency);
        System.out.printf(Locale.US,
            "              | Hover: %,.0f J  |  Compute: %,.0f J  |  Movement: %,.0f J  |  Comm: %,.0f J%n",
            totalHover, totalCompute, totalMovement, totalComm);

        // ── DEADLOCK STATS ──
        System.out.printf(Locale.US,
            " DEADLOCKS   | Active: %d  |  Wait Time: %.1fs  |  Detected: %d  |  Recovered: %d  |  Rate: %.1f%%%n",
            activeDeadlocks, totalDeadlockWait, totalDetections, totalRecoveries, recoveryRate);

        // ── EFFICIENCY METRICS ──
        System.out.printf(Locale.US,
            " EFFICIENCY  | Completion: %.1f%%  |  Drops: %.1f%%  |  Tasks/J: %.4f  |  Recovery: %.1f%%%n",
            completionRate, dropRate, tasksPerJoule, recoveryRate);

        System.out.println(chainsSb.toString());
        if (deadlocksDetected > 0 && !lastDeadlockMsg.isEmpty()) {
            System.out.println(" Deadlocks   | " + lastDeadlockMsg);
        }
        if (ProposedParams.ENABLE_DEADLOCK_INJECTION) {
            System.out.println(blockedSb.toString());
        }
        System.out.printf(Locale.US, " Queued      | Total: %d %s%n",
                          totalQueued, queuedDetailStr.isEmpty() ? "" : " (" + queuedDetailStr + ")");
        System.out.printf(Locale.US, " Pending     | Total: %d  (Carry-over: %d  New: %d, Unassigned: %d)%n",
                          pendingTasks.size(), carryOver, newPending, unassigned);
        System.out.println("================================================================================");

        // - Early stop if all user tasks are done -
        boolean userPending = false;
        for (ProposedTask t : pendingTasks) {
            if (!t.isDummy() && !t.isFinished() && !t.isDropped()) {
                userPending = true;
                break;
            }
        }
        // - Early stop disabled for fair comparison across all algorithms (fixed slot count) -
        /*
        if (totalTasksGenerated >= ProposedParams.TASK_SET_LENGTH &&
            inTransmission.isEmpty() &&
            totalQueued == 0) {
            consecutiveStagnantSlots++;
            if (!userPending || consecutiveStagnantSlots >= getStagnationLimit()) {
                System.out.println("All tasks completed/drained (stagnant=" + consecutiveStagnantSlots + "). Stopping simulation early.");
                writePerUavSummary();
                writeFinalUavPositions();
                closeAll();
                org.cloudbus.cloudsim.core.CloudSim.stopSimulation();
                return;
            }
        } else {
            consecutiveStagnantSlots = 0;
        }
        */

        // - Stagnation Check: stop immediately if all working UAVs are blocked and no idle UAVs can accept pending work -
        int blockedUavs = 0;
        int uavsWithWork = 0;
        for (ProposedUAVState u : uavs) {
            if (u.getQueueSize() > 0) {
                uavsWithWork++;
                ProposedTask head = u.getQueue().get(0);
                boolean isBlocked = false;
                if (head != null && ProposedParams.ENABLE_DEADLOCK_INJECTION) {
                    if (waitFor.containsKey(head.getId())) {
                        isBlocked = true;
                    } else if (head.getRequestedResource() != null) {
                        ProposedUAVState.ResourceType req = ProposedUAVState.ResourceType.valueOf(head.getRequestedResource());
                        int holder = u.getResourceHolder(req);
                        if (holder != -1 && holder != head.getId()) {
                            isBlocked = true;
                        }
                    }
                }
                if (isBlocked) {
                    blockedUavs++;
                }
            }
        }

        checkStagnationWatchdog(CloudSim.clock());

        // - Per-UAV Energy CSV Logging -
        if (ProposedParams.ENABLE_ENERGY_LOGGING && energyLogger != null) {
            // Count active deadlocks this slot (tasks with waitFor entries)
            activeDeadlocks = 0;
            for (ProposedUAVState u : uavs) {
                for (ProposedTask t : u.getQueue()) {
                    if (waitFor.containsKey(t.getId())) {
                        activeDeadlocks++;
                        break;
                    }
                }
            }
            for (ProposedUAVState u : uavs) {
                energyLogger.logSlot(
                    currentSlot, CloudSim.clock(), u.getIndex(),
                    u.getEnergyState(), u.getQueueSize(),
                    u.getCompletedTasksThisSlot(), u.getDroppedTasksThisSlot(),
                    activeDeadlocks, totalDetections, totalRecoveries,
                    injector.getInjectionCount()
                );
            }
        }

        // - Token Ring Circulation (coordination heartbeat each slot) -
        coordinationPolicy.endSlot();

        if (currentSlot < maxSlots) {
            send(getId(), slotLength, wrapTag(ProposedFogEvents.NEXT_SLOT));
        } else {
            writePerUavSummary();
            writeFinalUavPositions();
            closeAll();
            CloudSim.stopSimulation();
        }
    }
    // - Cooperative offloading -
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
                // No suitable neighbor - put back on original UAV
                overloaded.enqueueTask(t);
            }
        }
    }
    // - Expired pending task pruning -
    private void pruneExpiredPending() {
        double now = CloudSim.clock();
        // Pass 1: Collect - read only, iterator stays clean
        List<ProposedTask> toRemove = new ArrayList<>();
        for (ProposedTask t : pendingTasks) {
            if (t.isFinished() || t.isDropped()) {
                toRemove.add(t);
                continue;
            }
            double absDeadline = t.getArrivalTime() + t.getDeadlineSec();
            if (now >= absDeadline) {
                t.markDropped(now);
                logLatencyFailure(t);   // - safe - doesn't modify pendingTasks
                toRemove.add(t);
            }
        }
        // Pass 2: Remove all at once - no active iterator
        pendingTasks.removeAll(toRemove);
        // Pass 3: Notify failures - onTaskFailure() free to modify pendingTasks now
        for (ProposedTask t : toRemove) {
            if (t.isDropped()) {          // only notify the expired ones, not already-finished
                onTaskFailure(t);
            }
        }
    }
    // - Task dependency management -
    private void onTaskSuccess(ProposedTask done) {
        if (done.isDummy()) {
            dummyTasksCompleted++;
        } else {
            userTasksCompleted++;
        }
        done.setState(ProposedTask.TaskState.COMPLETED, org.cloudbus.cloudsim.core.CloudSim.clock());
        logDependencyEvent(done, "COMPLETED", null);
        waitFor.remove(done.getId());
        waitFor.entrySet().removeIf(e -> e.getValue() == done.getId());
        // Remove local graph edge on the assigned UAV
        if (done.getAssignedUAV() != null) {
            done.getAssignedUAV().removeLocalWaitEdge(done.getId());
        }
        if (!done.isDependent()) return;
        int gid     = done.getDepGroupId();
        int nextSeq = done.getDepSeq() + 1;
        if (done.getDepSeq() == ProposedParams.DEP_LEN) {
            completedChains++;
        }
        for (ProposedTask t : pendingTasks) {
            if (t.isDependent() && t.getDepGroupId() == gid && t.getDepSeq() == nextSeq) {
                t.setReleasable(true);
                t.setState(ProposedTask.TaskState.READY, org.cloudbus.cloudsim.core.CloudSim.clock());
                t.readyTime = org.cloudbus.cloudsim.core.CloudSim.clock();
                logTaskLifecycle(t);
                logTrajectory(t, "READY", -1);
                logDependencyEvent(t, "RELEASED", "Parent " + done.getId() + " finished");

                // ── Enhancement 1: Zero-Overhead Local Scheduling ────────────────
                // Chain is co-located on parent's UAV (Monolithic DAG Bidding).
                // Assign child directly – no CBBA re-auction, 0 consensus delay.
                // Justified by co-location theorem: Choi et al. (2009) TRO.
                if (done.getAssignedUAV() != null
                        && coordinationPolicy instanceof org.fog.test.proposed.coordination.hybrid.HybridCoordinationPolicy) {
                    ProposedUAVState sameUav = done.getAssignedUAV();
                    if (sameUav.isInCoverage(t.getMdX(), t.getMdY())) {
                        // Co-located intermediate DAG stage: parent data is already local on this UAV.
                        // Direct memory handoff (zero wireless uplink delay) — enqueues immediately for CPU execution.
                        t.setAssignedUAV(sameUav);
                        t.assignedTime = org.cloudbus.cloudsim.core.CloudSim.clock();
                        t.setState(ProposedTask.TaskState.READY, org.cloudbus.cloudsim.core.CloudSim.clock());
                        t.readyTime = org.cloudbus.cloudsim.core.CloudSim.clock();
                        logTaskLifecycle(t);
                        logTrajectory(t, "ASSIGNED", sameUav.getIndex());
                        sameUav.enqueueTask(t);
                        pendingTasks.remove(t);
                        logDependencyEvent(t, "ZERO-OVERHEAD-SCHEDULED",
                                "Co-located local handoff on UAV-" + sameUav.getIndex() + " (0 wireless delay)");
                        System.out.println("   [ZERO-OVERHEAD] T" + t.getId()
                                + " → UAV-" + sameUav.getIndex() + " (local handoff + enqueue, 0 delay).");
                    } else {
                        // UAV drifted out of coverage – clear assignment, CBBA handles next slot
                        t.setAssignedUAV(null);
                        t.setState(ProposedTask.TaskState.READY, org.cloudbus.cloudsim.core.CloudSim.clock());
                        System.out.println("   [ZERO-OVERHEAD] UAV-" + sameUav.getIndex()
                                + " out of coverage for T" + t.getId() + " – falling back to CBBA.");
                    }
                }
                // ─────────────────────────────────────────────────────────────────
                break;
            }
        }
    }
    private void onTaskFailure(ProposedTask failed) {
        // Count the directly-failed task as a drop
        if (failed.isDummy()) {
            dummyTasksDropped++;
        } else {
            userTasksDropped++;
        }
        failed.setState(ProposedTask.TaskState.FAILED, org.cloudbus.cloudsim.core.CloudSim.clock());
        logDependencyEvent(failed, "FAILED", null);
        waitFor.remove(failed.getId());
        waitFor.entrySet().removeIf(e -> e.getValue() == failed.getId());
        // Remove local graph edge on the assigned UAV
        if (failed.getAssignedUAV() != null) {
            failed.getAssignedUAV().removeLocalWaitEdge(failed.getId());
        }
        if (!failed.isDependent()) return;

        // BUG FIX: Count each failed chain only ONCE using the chain group ID.
        // Previously failedChains++ was called for every dropped task inside a chain
        // (root drop + cascade drops), resulting in failedChains = numDroppedTasks
        // instead of numFailedChains.
        int gid = failed.getDepGroupId();
        if (failedChainIds.add(gid)) {  // add() returns false if already counted
            failedChains++;
        }

        List<ProposedTask> toRemove = new ArrayList<>();
        for (ProposedTask t : pendingTasks) {
            if (t.isDependent() && t.getDepGroupId() == gid
                    && t.getDepSeq() > failed.getDepSeq()) {
                t.markDropped(org.cloudbus.cloudsim.core.CloudSim.clock());
                t.setState(ProposedTask.TaskState.FAILED, org.cloudbus.cloudsim.core.CloudSim.clock());
                waitFor.remove(t.getId());
                waitFor.entrySet().removeIf(e -> e.getValue() == t.getId());
                logTaskLifecycle(t);
                logLatencyFailure(t);
                logDependencyEvent(t, "CANCELLED", "Parent Task Failed");
                // CASCADE cancellations: count separately from direct drops for clarity
                if (t.isDummy()) {
                    dummyTasksCancelled++;
                } else {
                    userTasksCancelled++;
                }
                toRemove.add(t);
            }
        }
        pendingTasks.removeAll(toRemove);
        Iterator<ProposedTask> it = inTransmission.iterator();
        while (it.hasNext()) {
            ProposedTask t = it.next();
            if (t.isDependent() && t.getDepGroupId() == gid
                    && t.getDepSeq() > failed.getDepSeq()) {
                t.markDropped(org.cloudbus.cloudsim.core.CloudSim.clock());
                t.setState(ProposedTask.TaskState.FAILED, org.cloudbus.cloudsim.core.CloudSim.clock());
                logLatencyFailure(t);
                logDependencyEvent(t, "CANCELLED", "Parent Task Failed");
                if (t.isDummy()) {
                    dummyTasksCancelled++;
                } else {
                    userTasksCancelled++;
                }
                it.remove();
            }
        }
    }
    /**
     * Mirrors all current global waitFor edges into each UAV's local wait-for graph.
     * Called after deadlock injection so that distributed detection in each UAV
     * sees the same dependency edges that the global detector sees.
     *
     * Only edges where BOTH tasks are assigned to a UAV are mirrored;
     * unassigned tasks have no local owner yet.
     */
    private void mirrorWaitForToLocalGraphs() {
        for (Map.Entry<Integer, Integer> entry : waitFor.entrySet()) {
            int child  = entry.getKey();
            int parent = entry.getValue();
            ProposedTask ct = findTaskById(child);
            ProposedTask pt = findTaskById(parent);
            if (ct != null && ct.getAssignedUAV() != null) {
                ct.getAssignedUAV().addLocalWaitEdge(child, parent);
            }
            if (pt != null && pt.getAssignedUAV() != null) {
                // Also add to parent UAV's graph (for merged view)
                pt.getAssignedUAV().addLocalWaitEdge(child, parent);
            }
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
    // - Task generation -
    private List<ProposedTask> createTask(double mdX, double mdY, double now) {
        List<ProposedTask> out = new ArrayList<>();
        double dataBytes   = 0.1e6 + taskGenRand.nextDouble() * (1.0e6 - 0.1e6);
        double dataBits    = dataBytes * 8.0;
        double resultBits  = dataBits * 0.1;
        double cpuCycles   = 0.5e8 + taskGenRand.nextDouble() * (1.5e8 - 0.5e8);
        double deadline    = ProposedParams.TASK_DEADLINE_MIN_SEC
                           + taskGenRand.nextInt(ProposedParams.TASK_DEADLINE_RANGE_SEC);
        boolean dependent  = taskGenRand.nextDouble() < ProposedParams.P_DEP;

        if (!dependent) {
            ProposedTask t = new ProposedTask(dataBits, resultBits, cpuCycles, deadline, mdX, mdY, now);
            t.setState(ProposedTask.TaskState.READY, now);
            t.readyTime = now;
            out.add(t);
            logTaskLifecycle(t);
            logTrajectory(t, "CREATED", -1);
            return out;
        }

        int gid = nextDepGroupId++;
        totalChains++;
        int parentId = 0;

        // - STEP 1: Create all tasks in the chain -
        for (int seq = 1; seq <= ProposedParams.DEP_LEN; seq++) {
            ProposedTask t = new ProposedTask(dataBits, resultBits, cpuCycles, deadline, mdX, mdY, now);
            t.setDependency(gid, seq);
            t.setReleasable(seq == 1);
            if (seq == 1) {
                t.setState(ProposedTask.TaskState.READY, now);
                t.readyTime = now;
            } else {
                t.setState(ProposedTask.TaskState.WAITING, now);
            }
            out.add(t);
            logDependencyEvent(t, "CREATED", "Seq=" + seq);
            logDependencyStructure(t, parentId);
            logTaskLifecycle(t);
            logTrajectory(t, "CREATED", -1);
            parentId = t.getId();
        }

        // - STEP 2: Inject DEADLOCK (cross-chain circular wait on real dependency chains) -
        if (ProposedParams.ENABLE_DEADLOCK_INJECTION && dependent && taskGenRand.nextDouble() < ProposedParams.P_DEADLOCK) {
            int sizeBefore = waitFor.size();
            boolean ok = injector.injectCDD(out, pendingTasks, now, gid);
            if (!ok) {
                // Fallback: if chain-linking CDD fails due to dropped target chains in baselines, inject RCD so deadlock count remains deterministic and identical across algorithms
                injector.injectRCD(pendingTasks, now);
                injector.incrementInjectionCount();
            }
            if (waitFor.size() > sizeBefore) {
                mirrorWaitForToLocalGraphs();
            }
        }

        return out;
    }
    // - MD management -
    private MD pickCoveredMD() {
        for (int tries = 0; tries < 200; tries++) {
            MD cand = mds.get(mobilityRand.nextInt(mds.size()));
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
            double ang   = mobilityRand.nextDouble() * 2.0 * Math.PI;
            double speed = ProposedParams.MD_SPEED_MIN
                    + mobilityRand.nextDouble() * (ProposedParams.MD_SPEED_MAX - ProposedParams.MD_SPEED_MIN);
            md.x = clamp(md.x + speed * dt * Math.cos(ang), 0, ProposedParams.AREA_X);
            md.y = clamp(md.y + speed * dt * Math.sin(ang), 0, ProposedParams.AREA_Y);
        }
    }
    // - Metrics & logging -
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
    // - Utilities -
    private double getTotalEnergyAllUavs() {
        double e = 0.0;
        for (ProposedUAVState u : uavs) e += u.getTotalEnergy();
        return e;
    }
    private int samplePoisson(double lambda) {
        double L = Math.exp(-lambda);
        int    k = 0;
        double p = 1.0;
        do { k++; p *= taskGenRand.nextDouble(); } while (p > L);
        return k - 1;
    }
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    private void writeDependencySummary() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(
                ProposedParams.getSummaryDir() + "/dependency_summary_" + runTag + ".csv"))) {
            pw.println("Algorithm,RunTag,Seed,TotalChains,CompletedChains,FailedChains,AvgChainLength,SuccessRate");
            double rate = (totalChains > 0) ? (completedChains * 100.0 / totalChains) : 0;
            pw.printf(Locale.US, "Proposed,%s,%d,%d,%d,%d,%.1f,%.2f%%%n",
                      runTag, seed, totalChains, completedChains, failedChains, (double)ProposedParams.DEP_LEN, rate);
        } catch (Exception e) {}
    }

    void logDependencyEvent(ProposedTask t, String event, String detail) {
        File file = new File(ProposedParams.LOG_DIR + "/dependency_event_" + runTag + ".csv");
        boolean exists = file.exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
            if (!exists) {
                pw.println("Time,ChainID,TaskID,Event,Detail");
            }
            pw.printf(Locale.US, "%.3f,%d,%d,%s,%s%n", 
                      org.cloudbus.cloudsim.core.CloudSim.clock(), t.getDepGroupId(), t.getId(), event, detail != null ? detail : "");
        } catch (Exception e) {}
    }

    private void logDependencyStructure(ProposedTask t, int parentId) {
        File file = new File(ProposedParams.LOG_DIR + "/dependency_structure_" + runTag + ".csv");
        boolean exists = file.exists();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
            if (!exists) {
                pw.println("ChainID,TaskID,Seq,ParentID,ArrivalTime,ReadyTime,StartExecTime,State");
            }
            pw.printf(Locale.US, "%d,%d,%d,%d,%.3f,%.3f,%.3f,%s%n",
                      t.getDepGroupId(), t.getId(), t.getDepSeq(), parentId,
                      t.getArrivalTime(), t.readyTime, t.startExecTime, t.currentState);
        } catch (Exception e) {}
    }

    // - Task Lifecycle and Trajectory loggers -
    public void logTaskLifecycle(ProposedTask t) {
        if (lifecycleCsv == null) return;
        String type = "INDEPENDENT";
        if (t.isDependent()) {
            if (t.getDepSeq() == 1) type = "DEPENDENT_ROOT";
            else if (t.getDepSeq() == ProposedParams.DEP_LEN) type = "DEPENDENT_LEAF";
            else type = "DEPENDENT_MIDDLE";
        }
        int uavIdx = (t.getAssignedUAV() != null) ? t.getAssignedUAV().getIndex() : -1;
        double compTime = t.isFinished() ? t.getCompletionTime() : -1.0;
        double latency = (t.isFinished()) ? compTime - t.getArrivalTime() : -1.0;
        boolean met = t.isFinished() && (compTime - t.getArrivalTime() <= t.getDeadlineSec());
        int queueLoad = (t.getAssignedUAV() != null) ? t.getAssignedUAV().getQueueSize() : -1;
        
        lifecycleCsv.printf(Locale.US,
            "%d,%s,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%d,%s,%.3f,%.3f,%b,%.1f,%.1f,%d%n",
            t.getId(), type, t.getDepGroupId(), t.getDepSeq(),
            t.getArrivalTime(), t.readyTime, t.assignedTime, t.startExecTime,
            compTime, uavIdx, t.currentState.toString(),
            latency, t.getDeadlineSec(), met,
            t.getMdX(), t.getMdY(), queueLoad);
        lifecycleCsv.flush();
    }

    public void logQueueSnapshot(String pass, ProposedTask task, int assignedUav) {
        if (queueSnapshotCsv == null) return;
        StringBuilder row = new StringBuilder();
        row.append(String.format(Locale.US, "%.3f,%d,%s,%d,%d,",
            CloudSim.clock(), currentSlot, pass, task != null ? task.getId() : -1, assignedUav));
        for (int i = 0; i < uavs.size(); i++) {
            List<ProposedTask> q = uavs.get(i).getQueue();
            String qStr = q.stream().map(t -> String.valueOf(t.getId())).collect(Collectors.joining("|"));
            row.append(qStr).append(",");
        }
        queueSnapshotCsv.println(row.toString());
        queueSnapshotCsv.flush();
    }

    private void logPendingSnapshot() {
        if (pendingTraceCsv == null) return;
        double slotStart = (currentSlot - 1) * slotLength;
        for (ProposedTask t : pendingTasks) {
            boolean isNew = t.getArrivalTime() >= slotStart && t.getArrivalTime() < slotStart + slotLength;
            boolean isCarryOver = !isNew && !t.isFinished() && !t.isDropped();
            pendingTraceCsv.printf(Locale.US, "%d,%.3f,%d,%s,%b,%b%n",
                currentSlot, CloudSim.clock(), t.getId(),
                t.currentState.toString(), isNew, isCarryOver);
        }
        pendingTraceCsv.flush();
    }

    public void logTrajectory(ProposedTask t, String event, int assignedUav) {
        if (trajectoryCsv == null) return;
        double userX = t.getMdX(), userY = t.getMdY();
        int closest = -1;
        double closestDist = Double.MAX_VALUE;
        StringBuilder inRange = new StringBuilder();
        for (int i = 0; i < uavs.size(); i++) {
            ProposedUAVState u = uavs.get(i);
            double d = u.distanceTo(userX, userY);
            if (u.isInCoverage(userX, userY)) {
                inRange.append("UAV").append(i).append("(").append(String.format("%.1f", d)).append("m) ");
            }
            if (d < closestDist) { closestDist = d; closest = i; }
        }
        trajectoryCsv.printf(Locale.US, "%.3f,%d,%s,%.1f,%.1f,%s,%d,%.1f,%s%n",
            CloudSim.clock(), t.getId(), event, userX, userY,
            inRange.toString().trim(), closest, closestDist,
            (assignedUav >= 0 ? "UAV"+assignedUav : "NONE"));
        trajectoryCsv.flush();
    }

    private void logDeadlockEvent(List<Integer> cycle, ProposedTask victim, String action) {
        if (deadlockCsv == null) return;
        String cycleStr = cycle.stream().map(String::valueOf).collect(Collectors.joining("->"));
        int origUav = (victim != null && victim.getAssignedUAV() != null) ? victim.getAssignedUAV().getIndex() : -1;
        deadlockCsv.printf(Locale.US, "%.3f,%d,%s,%d,%d,%d,%s%n",
            CloudSim.clock(), currentSlot, cycleStr,
            victim != null ? victim.getId() : -1, origUav, -1, action);
        deadlockCsv.flush();
    }

    // - Deadlock Detection & Resolution -
    private void detectAndResolveDeadlocks() {
        if (!waitFor.isEmpty()) {
            System.out.println("RUNNING DEADLOCK DETECTION...");
        }
        List<DeadlockReport> reports = detector.detectAll(CloudSim.clock());
        if (!reports.isEmpty()) {
            for (DeadlockReport report : reports) {
                System.out.println("[DEADLOCK] " + report.type + ": " + report.description);
                logDeadlockEvent(new ArrayList<>(report.involvedTaskIds), null, "DETECTED");
                lastDeadlockMsg = report.type + ": " + report.description;
            }
            if (ProposedParams.STOP_ON_DEADLOCK) {
                System.out.println("STOPPING SIMULATION due to deadlock detection.");
                org.cloudbus.cloudsim.core.CloudSim.stopSimulation();
            }
        }
    }

    public ProposedTask findTaskById(int id) {
        for (ProposedTask t : pendingTasks) if (t.getId() == id) return t;
        for (ProposedTask t : inTransmission) if (t.getId() == id) return t;
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.getQueue()) if (t.getId() == id) return t;
        }
        return null;
    }

    private boolean hasWaitingTasks() {
        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.getQueue()) {
                if (t.currentState == ProposedTask.TaskState.WAITING) return true;
            }
        }
        return false;
    }

    // - Writer management -
    private void openWriters() {
        try {
            String baseDir = new File(csvPath).getParent();
            if (baseDir == null) baseDir = ProposedParams.LOG_DIR;
            new File(baseDir).mkdirs();
            String summaryDir = ProposedParams.getSummaryDir();
            new File(summaryDir).mkdirs();
            csv = new PrintWriter(new FileWriter(csvPath, false));
            csv.println("slot,time,reward_covmd,throughput_bits,energy_delta_j," +
                        "total_energy_j,generated,assigned,unassigned,completed," +
                        "dropped,queue_total,pending_total,avg_success_prob");
            csv.flush();
            String latencyPath = baseDir + File.separator + "latency_"       + runTag + ".csv";
            String perUavPath  = summaryDir + File.separator + "per_uav_summary_" + runTag + ".csv";
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

            lifecycleCsv = open(baseDir + "/task_lifecycle_" + runTag + ".csv",
                "TaskID,Type,GroupID,Seq,ArrivalTime,ReadyTime,AssignedTime,StartExecTime,CompletionTime,AssignedUAV,Status,Latency,Deadline,MetDeadline,MD_X,MD_Y,QueueLoadAtAssignment");

            queueSnapshotCsv = open(baseDir + "/uav_queue_snapshot_" + runTag + ".csv",
                "Time,Slot,Pass,TaskID,AssignedUAV," + 
                String.join(",", java.util.stream.IntStream.range(0, uavs.size()).mapToObj(i -> "UAV"+i+"_Queue").toArray(String[]::new)));

            pendingTraceCsv = open(baseDir + "/slot_pending_trace_" + runTag + ".csv",
                "Slot,Time,TaskID,Status,IsNew,IsCarryOver");

            trajectoryCsv = open(baseDir + "/task_trajectory_" + runTag + ".csv",
                "Time,TaskID,Event,User_X,User_Y,UAVsInRange,ClosestUAV,ClosestDist,AssignedUAV");

            deadlockCsv = open(baseDir + "/deadlock_resolution_" + runTag + ".csv",
                "Time,Slot,Cycle,VictimTask,OriginalUAV,NewUAV,Action");

            uavTrajectoryCsv = open(baseDir + "/uav_trajectory_" + runTag + ".csv",
                "Slot,Time,UAV_ID,X,Y,Load");

            energyLogger = new EnergyLogger(baseDir, runTag);
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
        close(lifecycleCsv);     lifecycleCsv = null;
        close(queueSnapshotCsv); queueSnapshotCsv = null;
        close(pendingTraceCsv);  pendingTraceCsv = null;
        close(trajectoryCsv);    trajectoryCsv = null;
        close(deadlockCsv);      deadlockCsv = null;
        if (energyLogger != null) { energyLogger.close(); energyLogger = null; }
    }
    private void close(PrintWriter pw) {
        if (pw != null) { pw.flush(); pw.close(); }
    }

    private boolean attemptRecoveryWithLimit(DeadlockReport report, CoordinationPolicy policy, double clock) {
        List<Integer> sortedIds = new ArrayList<>(report.involvedTaskIds);
        java.util.Collections.sort(sortedIds);
        String cycleKey = report.type.name() + ":" + sortedIds.toString();
        int retries = cycleRetryCount.getOrDefault(cycleKey, 0);

        if (retries >= MAX_CYCLE_RETRIES) {
            System.out.println("[RECOVERY] ESCALATION: Cycle " + cycleKey + " attempted " + retries + " times. Force-aborting cycle root.");
            forceAbortCycleRoot(sortedIds, clock);
            cycleRetryCount.remove(cycleKey);
            return true;
        }

        boolean result = policy.recover(report, recoverer, clock);
        if (result) {
            cycleRetryCount.remove(cycleKey);
        } else {
            cycleRetryCount.put(cycleKey, retries + 1);
        }
        return result;
    }

    private void forceAbortCycleRoot(List<Integer> cycleIds, double clock) {
        Set<Integer> cycleSet = new HashSet<>(cycleIds);
        Set<Integer> aborted = new HashSet<>();

        for (int id : cycleIds) {
            if (aborted.contains(id)) continue;
            Integer waitingForId = waitFor.get(id);
            if (waitingForId != null && cycleSet.contains(waitingForId)) {
                Integer otherWaitsFor = waitFor.get(waitingForId);
                if (Integer.valueOf(id).equals(otherWaitsFor)) {
                    ProposedTask t1 = findTaskById(id);
                    ProposedTask t2 = findTaskById(waitingForId);
                    System.out.println("[ESCALATION] Aborting mutual-wait pair: T" + id + " - T" + waitingForId);
                    if (t1 != null) recoverer.preemptTask(t1, clock, "ESCALATION");
                    if (t2 != null) recoverer.preemptTask(t2, clock, "ESCALATION");
                    aborted.add(id);
                    aborted.add(waitingForId);
                }
            }
        }

        if (aborted.isEmpty() && cycleIds.size() >= 2) {
            ProposedTask t1 = findTaskById(cycleIds.get(0));
            ProposedTask t2 = findTaskById(cycleIds.get(1));
            if (t1 != null) recoverer.preemptTask(t1, clock, "ESCALATION-FALLBACK");
            if (t2 != null) recoverer.preemptTask(t2, clock, "ESCALATION-FALLBACK");
        }
    }

    private void checkStagnationWatchdog(double clock) {
        int activeUavs = 0;
        for (ProposedUAVState u : uavs) {
            if (u.getCompletedTasksThisSlot() > 0) activeUavs++;
        }

        boolean systemStagnant = (activeUavs == 0 && !pendingTasks.isEmpty());

        if (systemStagnant) {
            consecutiveStagnantSlots++;
            System.out.println("[WATCHDOG] Stagnant slot " + consecutiveStagnantSlots + "/" + getStagnationLimit() + " (0 completions, " + pendingTasks.size() + " pending)");

            if (consecutiveStagnantSlots >= getStagnationLimit()) {
                System.out.println("[WATCHDOG] NUCLEAR OPTION: Clearing ALL waitFor edges and releasing ALL UAV resource locks.");
                // nuclearReset(clock);
                consecutiveStagnantSlots = 0;
                cycleRetryCount.clear();
            }
        } else {
            consecutiveStagnantSlots = 0;
        }
    }

    private void nuclearReset(double clock) {
        for (ProposedUAVState u : uavs) {
            u.releaseAllResources();
        }

        waitFor.clear();
        cycleRetryCount.clear();

        int unlocked = 0;
        for (ProposedTask t : pendingTasks) {
            if (t.currentState == ProposedTask.TaskState.WAITING) {
                t.setReleasable(true);
                t.setState(ProposedTask.TaskState.READY, clock);
                t.readyTime = clock;
                unlocked++;
            }
        }

        for (ProposedUAVState u : uavs) {
            for (ProposedTask t : u.getQueue()) {
                if (t.currentState == ProposedTask.TaskState.WAITING) {
                    t.setReleasable(true);
                    t.setState(ProposedTask.TaskState.READY, clock);
                    t.readyTime = clock;
                    unlocked++;
                }
            }
        }

        System.out.println("[WATCHDOG] Nuclear reset complete. " + unlocked + " tasks unlocked.");
        if (deadlockCsv != null) {
            deadlockCsv.printf(Locale.US, "%.3f,%d,%s,%d,%d,%d,%s%n", clock, currentSlot, "NUCLEAR_RESET", -1, -1, -1, "DETECTED");
            deadlockCsv.flush();
        }
    }
}

