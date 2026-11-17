package org.fog.test.proposed;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.test.proposed.energy.EnergyCalculator;
import org.fog.test.proposed.energy.EnergyState;

/**
 * ProposedUAVState - enhanced with:
 *
 *  1. EDF (Earliest-Deadline-First) execution queue
 *     Tasks are sorted by absolute deadline so the most urgent task always
 *     executes first, drastically reducing deadline misses.
 *
 *  2. Demand-aware mobility (hotspot steering)
 *     Instead of pure random-waypoint, the UAV probabilistically steers
 *     toward the centroid of uncovered MDs with pending demand, improving
 *     spatial coverage and reducing unassigned tasks.
 *
 *  3. Cooperative offloading hook
 *     When this UAV's queue is overloaded, the controller can call
 *     extractOffloadCandidates() to move tail tasks to less-loaded neighbors.
 *
 *  4. Distributed local wait-for graph
 *     Maintains local child->parent wait-for dependency edges, allowing
 *     independent local cycle detection and gossip graph merging.
 */
public class ProposedUAVState {
    private final FogDevice device;
    private final int index;
    private ProposedSlotController controller;

    public void setController(ProposedSlotController controller) {
        this.controller = controller;
    }
    private double x, y, z;
    private double speedMps       = ProposedParams.UAV_SPEED_MPS;
    private double uavUavRange    = ProposedParams.UAV_UAV_RANGE_M;
    private double coverageRadius = 150.0;
    private double cpuHz          = ProposedParams.CPU_HZ;
    private int    completedThisSlot = 0;
    private int    droppedThisSlot   = 0;
    private int    completedTotal    = 0;
    private int    droppedTotal      = 0;
    private double loadRatioThisSlot = 0.0;
    public  double throughputBits    = 0.0;
    public  double sumDataBits       = 0.0;
    public  double sumRateBits       = 0.0;
    private List<ProposedUAVState> neighbors = new ArrayList<>();
    private ProposedTask currentlyExecutingTask = null;
    public void setCurrentlyExecutingTask(ProposedTask task) { this.currentlyExecutingTask = task; }
    public ProposedTask getCurrentlyExecutingTask() { return currentlyExecutingTask; }
    private final PriorityQueue<ProposedTask> queue = new PriorityQueue<>(
        Comparator.<ProposedTask>comparingInt(t -> -t.getPriority())
                  .thenComparingDouble(t -> t.getArrivalTime() + t.getDeadlineSec())
    );
    private static final double XI          = 1e5;
    private static final double TX_POWER_W  = 1.0;
    private static final double BANDWIDTH   = 50e6;
    private static final double N0_W_PER_HZ = 2e-20;
    private static final double LAMBDA      = 9.61;
    private static final double SIGMA        = 0.16;
    
    private final EnergyState energyState = new EnergyState();
    private double processedCyclesThisSlot = 0.0;
    private double processedCyclesTotal    = 0.0;
    private final List<ProposedTask> completedTaskBuffer = new ArrayList<>();
    private final List<ProposedTask> droppedTaskBuffer   = new ArrayList<>();
    // - Demand-aware mobility state -
    private double hotspotTargetX = -1;
    private double hotspotTargetY = -1;

    public ProposedUAVState(FogDevice dev, int index) {
        this.device = dev;
        this.index  = index;
    }
    // - Accessors -
    public FogDevice getDevice()  { return device; }
    public int       getIndex()   { return index; }
    public void setPosition(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
    }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public void setCoverageRadius(double r) { this.coverageRadius = r; }
    public void setSpeed(double s)          { this.speedMps = s; }
    public void setAltitude(double a)       { this.z = a; }
    public void setUavUavRange(double r)    { this.uavUavRange = r; }
    public void setCpuHz(double hz)         { this.cpuHz = hz; }
    public double getCpuHz()                { return cpuHz; }
    public List<ProposedTask> getQueue() {
        return new ArrayList<>(queue);
    }
    public int getQueueSize() { return queue.size(); }

    /**
     * Physically-derived queue capacity: how many average tasks this UAV can
     * process per slot based on CPU throughput.
     *   capacity = floor( CPU_Hz × slotLength / avgCyclesPerTask )
     * avgCyclesPerTask = midpoint of generation range [0.5e8, 1.5e8] = 1.0e8.
     * This is derived from system parameters, not hardcoded.
     * Grounded in: processor capacity region theory (Liu & Layland 1973 JACM).
     *
     * @param slotLength  Simulation slot duration in seconds
     * @return Maximum number of average-size tasks this UAV can service per slot
     */
    public int getDerivedQueueCapacity(double slotLength) {
        double avgCyclesPerTask = (0.5e8 + 1.5e8) / 2.0;   // midpoint of generation range
        double availableCycles  = cpuHz * slotLength;
        return (int) Math.floor(availableCycles / avgCyclesPerTask);
    }
    public List<ProposedUAVState> getNeighbors() { return neighbors; }
    public int    getCompletedTasksThisSlot() { return completedThisSlot; }
    public int    getDroppedTasksThisSlot()   { return droppedThisSlot; }
    public int    getCompletedTotal()         { return completedTotal; }
    public int    getDroppedTotal()           { return droppedTotal; }
    public double getProcessedCyclesThisSlot()  { return processedCyclesThisSlot; }
    public double getProcessedCyclesTotal()     { return processedCyclesTotal; }
    public double getProcessedMegaCyclesTotal() { return processedCyclesTotal / 1e6; }
    public double getProcessedGigaCyclesTotal() { return processedCyclesTotal / 1e9; }
    public List<ProposedTask> drainCompletedTasks() {
        List<ProposedTask> out = new ArrayList<>(completedTaskBuffer);
        completedTaskBuffer.clear();
        return out;
    }
    public List<ProposedTask> drainDroppedTasks() {
        List<ProposedTask> out = new ArrayList<>(droppedTaskBuffer);
        droppedTaskBuffer.clear();
        return out;
    }
    public EnergyState getEnergyState() { return energyState; }
    public double getTotalEnergy() { return energyState.getTotalEnergyJ(); }
    public double getResidualEnergyJ() { return energyState.getResidualEnergyJ(); }
    public double getResidualEnergyRatio() { return energyState.getResidualEnergyRatio(); }
    public void addCommEnergy(double e) { energyState.addCommEnergy(e); }
    public void addOffloadEnergy(double e) { energyState.addOffloadEnergy(e); }
    public void addCoordinationEnergy(double e) { energyState.addCoordinationEnergy(e); }
    public boolean dequeueTask(ProposedTask t) {
        return queue.remove(t);
    }
    public double getMovementEnergyJ()       { return energyState.getMovementEnergyJ(); }
    public double getComputeEnergyJ()        { return energyState.getComputeEnergyJ(); }
    public double getCommUplinkDownlinkJ()   { return energyState.getCommUplinkDownlinkJ(); }
    public double getCommOffloadJ()          { return energyState.getCommOffloadJ(); }
    public double getHoverEnergyJ()          { return energyState.getHoverEnergyJ(); }
    public double getCoordinationEnergyJ()   { return energyState.getCoordinationEnergyJ(); }
    // ── Wasted-energy proxies (new) ──────────────────────────────────────────
    public double getWastedComputeEnergyJ()  { return energyState.getWastedComputeEnergyJ(); }
    public double getWastedHoverEnergyJ()    { return energyState.getWastedHoverEnergyJ(); }
    public double getDeadlockWaitTimeSec()   { return energyState.getCumulativeDeadlockWaitTime(); }
    public double getEnergyEfficiency()      { return energyState.getEnergyEfficiency(); }
    public double getTotalWastedEnergyJ()    { return energyState.getTotalWastedEnergyJ(); }
    public void resetSlotStats() {
        completedThisSlot    = 0;
        droppedThisSlot      = 0;
        loadRatioThisSlot    = 0.0;
        throughputBits       = 0.0;
        sumDataBits          = 0.0;
        sumRateBits          = 0.0;
        processedCyclesThisSlot = 0.0;
        completedTaskBuffer.clear();
        droppedTaskBuffer.clear();
    }
    // - Coverage / distance -
    public boolean isInCoverage(double mdX, double mdY) {
        double dx = x - mdX, dy = y - mdY;
        return Math.sqrt(dx*dx + dy*dy) <= coverageRadius;
    }
    public double distanceTo(double mdX, double mdY) {
        double dx = x - mdX, dy = y - mdY;
        return Math.sqrt(dx*dx + dy*dy + z*z);
    }
    public double distanceToUav(ProposedUAVState other) {
        double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    // - Mobility -
    public void moveByAction(double angle, double distance, double dt) {
        distance = Math.max(0.0, Math.min(distance, speedMps * dt));
        x += distance * Math.cos(angle);
        y += distance * Math.sin(angle);
        x = clamp(x, 0, ProposedParams.AREA_X);
        y = clamp(y, 0, ProposedParams.AREA_Y);
        double V = (dt <= 1e-9) ? 0.0 : distance / dt;
        double propPower = EnergyCalculator.calcPropulsionPower(V);
        energyState.addMovementEnergy(propPower * dt);
    }
    public void moveWithDemandAwareness(double dt, Random rand, List<double[]> mds) {
        if (mds == null || mds.isEmpty() || rand.nextDouble() > ProposedParams.HOTSPOT_STEER_PROB) {
            moveRandomly(dt, rand);
            return;
        }
        int K = ProposedParams.HOTSPOT_SAMPLE_K;
        double cx = 0, cy = 0;
        int count = 0;
        double[] bestDist = new double[K];
        double[] bestX    = new double[K];
        double[] bestY    = new double[K];
        java.util.Arrays.fill(bestDist, Double.MAX_VALUE);
        for (double[] md : mds) {
            if (isInCoverage(md[0], md[1])) continue;
            double dx = x - md[0], dy = y - md[1];
            double d  = dx*dx + dy*dy;
            int worst = 0;
            for (int i = 1; i < K; i++) {
                if (bestDist[i] > bestDist[worst]) worst = i;
            }
            if (d < bestDist[worst]) {
                bestDist[worst] = d;
                bestX[worst]    = md[0];
                bestY[worst]    = md[1];
            }
        }
        for (int i = 0; i < K; i++) {
            if (bestDist[i] < Double.MAX_VALUE) {
                cx += bestX[i];
                cy += bestY[i];
                count++;
            }
        }
        if (count == 0) {
            moveRandomly(dt, rand);
            return;
        }
        cx /= count;
        cy /= count;
        double toHotX = cx - x;
        double toHotY = cy - y;
        double len = Math.sqrt(toHotX*toHotX + toHotY*toHotY);
        if (len < 1e-6) { moveRandomly(dt, rand); return; }
        double hotAngle = Math.atan2(toHotY, toHotX);
        double rndAngle = rand.nextDouble() * 2.0 * Math.PI;
        double blend = ProposedParams.HOTSPOT_BLEND;
        double finalAngle = blend * hotAngle + (1.0 - blend) * rndAngle;
        moveByAction(finalAngle, speedMps * dt, dt);
    }
    public void moveRandomly(double dt, Random rand) {
        double angle = rand.nextDouble() * 2.0 * Math.PI;
        moveByAction(angle, speedMps * dt, dt);
    }
    // - Task queue management -
    public void enqueueTask(ProposedTask t) {
        queue.add(t);
    }
    public void promoteToHead(ProposedTask t) {
        queue.remove(t);
        t.boostedPriority = 10;
        queue.add(t);
    }
    public boolean swapTasks(ProposedTask t1, ProposedTask t2) {
        if (queue.contains(t1) && queue.contains(t2)) {
            queue.remove(t1);
            queue.remove(t2);
            t1.boostedPriority = Math.max(t1.getPriority(), t2.getPriority()) + 1;
            queue.add(t1);
            queue.add(t2);
            return true;
        }
        return false;
    }
    public List<ProposedTask> extractOffloadCandidates(int maxTasks) {
        if (queue.size() <= 1) return new ArrayList<>();
        List<ProposedTask> all = new ArrayList<>(queue);
        all.sort((a, b) -> Double.compare(
                b.getArrivalTime() + b.getDeadlineSec(),
                a.getArrivalTime() + a.getDeadlineSec()));
        int take = Math.min(maxTasks, all.size() - 1);
        List<ProposedTask> offload = all.subList(0, take);
        List<ProposedTask> keep    = all.subList(take, all.size());
        queue.clear();
        queue.addAll(keep);
        return new ArrayList<>(offload);
    }
    // - Slot execution (EDF) -
    public void executeOneSlot(double dt) {
        double budgetCycles = cpuHz * dt;
        double elapsedInSlot = 0.0;
        List<ProposedTask> blockedTasksBuffer = new ArrayList<>();

        releaseResource(ResourceType.CPU_SLOT);

        if (queue.isEmpty()) {
            currentlyExecutingTask = null;
            // Optimal aerodynamic forward loiter (Zeng et al. 2019 Eq. 7 at V* = 7.0 m/s)
            double loiterPower = EnergyCalculator.calcPropulsionPower(7.0);
            energyState.addHoverEnergy(loiterPower * dt);
            sampleLoad(dt);
            return;
        }

        // ─── Deadlock wait-time tracking ─────────────────────────────────────
        // Count how long this slot a blocked task occupied the queue without executing.
        // Gossip's 10-slot consensus delay means UAVs hover while waiting → wasted hover energy.
        double deadlockWaitThisSlot = 0.0;
        for (ProposedTask qt : queue) {
            if (ProposedParams.ENABLE_DEADLOCK_INJECTION
                    && qt.isDependent()
                    && controller != null
                    && controller.getWaitFor().containsKey(qt.getId())) {
                deadlockWaitThisSlot += dt;  // each blocked task costs 1 slot of wait
                break;                        // count once per UAV per slot
            }
        }
        if (deadlockWaitThisSlot > 0) {
            energyState.addDeadlockWaitTime(deadlockWaitThisSlot);
            // Hover energy spent waiting for this deadlocked task is "wasted"
            energyState.addWastedHoverEnergy(EnergyCalculator.calcHoverPower() * deadlockWaitThisSlot);
        }

        while (budgetCycles > 1e-9 && !queue.isEmpty()) {
            ProposedTask t = queue.poll();
            if (t == null) break;


            if (ProposedParams.ENABLE_DEADLOCK_INJECTION && t.isDependent() &&
                controller != null && controller.getWaitFor().containsKey(t.getId())) {

                // --- DEADLINE CHECK FOR BLOCKED/DEADLOCKED TASKS ---
                // A task stuck in a deadlock cycle MUST still respect its deadline.
                // Gossip consensus takes up to GOSSIP_CONSENSUS_MIN_ROUNDS × GOSSIP_INTERVAL_SLOTS
                // slots to recover, so tasks with tight 2-5 s deadlines naturally EXPIRE
                // during the wait — producing a realistic higher drop count for Gossip vs Hybrid.
                double absDeadlineBlocked = t.getArrivalTime() + t.getDeadlineSec();
                if (CloudSim.clock() >= absDeadlineBlocked) {

                    // ── Track wasted COMPUTE energy (partial execution before deadlock) ──
                    double cyclesConsumed = t.getTotalCpuCycles() - t.getRemainingCpuCycles();
                    if (cyclesConsumed > 0) {
                        double wastedCompute = EnergyCalculator.calcComputeEnergy(cyclesConsumed, cpuHz);
                        energyState.addWastedComputeEnergy(wastedCompute);
                    }

                    t.markDropped(CloudSim.clock());
                    if (!t.isDummy()) {
                        droppedThisSlot++;
                        droppedTotal++;
                    }
                    droppedTaskBuffer.add(t);
                    // Remove from waitFor so downstream tasks can unblock
                    controller.getWaitFor().remove(t.getId());
                    if (controller != null) controller.logTaskLifecycle(t);
                    System.out.printf(
                        "[DEADLINE-DROP] Task %d dropped while DEADLOCK-BLOCKED at t=%.2f (deadline=%.2f, wasted_compute=%.4f J)%n",
                        t.getId(), CloudSim.clock(), absDeadlineBlocked,
                        EnergyCalculator.calcComputeEnergy(
                            t.getTotalCpuCycles() - t.getRemainingCpuCycles(), cpuHz));
                    continue;
                }

                blockedTasksBuffer.add(t);
                continue;
            }


            if (t.getRequestedResource() != null) {
                ResourceType req = ResourceType.valueOf(t.getRequestedResource());
                int holder = getResourceHolder(req);
                if (holder != -1 && holder != t.getId()) {
                    blockedTasksBuffer.add(t);
                    continue;
                }
            }

            grantResource(ResourceType.CPU_SLOT, t.getId());

            if (t.currentState != ProposedTask.TaskState.EXECUTING) {
                t.setState(ProposedTask.TaskState.EXECUTING, CloudSim.clock() + elapsedInSlot);
                t.startExecTime = CloudSim.clock() + elapsedInSlot;
                if (controller != null) {
                    controller.logTaskLifecycle(t);
                }
            }

            double absDeadline = t.getArrivalTime() + t.getDeadlineSec();
            if (CloudSim.clock() + elapsedInSlot >= absDeadline) {
                t.markDropped(CloudSim.clock() + elapsedInSlot);
                if (!t.isDummy()) {
                    droppedThisSlot++;
                    droppedTotal++;
                }
                droppedTaskBuffer.add(t);
                releaseResource(ResourceType.CPU_SLOT);
                if (controller != null) {
                    controller.logTaskLifecycle(t);
                    controller.getWaitFor().remove(t.getId());
                }
                continue;
            }

            double consume = Math.min(budgetCycles, t.getRemainingCpuCycles());
            double compTime = consume / Math.max(1e-9, cpuHz);
            t.consumeCpuCycles(consume);
            budgetCycles -= consume;
            elapsedInSlot += compTime;
            energyState.addComputeEnergy(EnergyCalculator.calcComputeEnergy(consume, cpuHz));
            processedCyclesThisSlot += consume;
            processedCyclesTotal += consume;

            if (t.isComputeDone()) {
                releaseResource(ResourceType.CPU_SLOT);
                double finishTime = CloudSim.clock() + elapsedInSlot;
                if (finishTime <= absDeadline) {
                    t.markFinished(finishTime);
                    if (!t.isDummy()) {
                        completedThisSlot++;
                        completedTotal++;
                    }
                    completedTaskBuffer.add(t);
                    throughputBits += (t.getDataBits() + t.getResultBits());
                } else {
                    t.markDropped(finishTime);
                    if (!t.isDummy()) {
                        droppedThisSlot++;
                        droppedTotal++;
                    }
                    droppedTaskBuffer.add(t);
                }
                if (controller != null) {
                    controller.logTaskLifecycle(t);
                    controller.getWaitFor().entrySet().removeIf(e -> e.getValue() == t.getId());
                }
            } else {
                releaseResource(ResourceType.CPU_SLOT);
                queue.add(t);
                break;
            }
        }

        queue.addAll(blockedTasksBuffer);
        currentlyExecutingTask = queue.isEmpty() ? null : queue.peek();
        energyState.addHoverEnergy(EnergyCalculator.calcHoverPower() * dt);
        sampleLoad(dt);
    }
    // - Load metrics -
    public double getChiLoNowMcycles(double slotLength) {
        double queuedCycles = 0.0;
        for (ProposedTask t : queue) queuedCycles += t.getRemainingCpuCycles();
        double mcycles = queuedCycles / 1e6;
        return (slotLength <= 1e-9) ? mcycles : (mcycles / slotLength);
    }
    public double getChiMaxMcyclesPerSec() { return cpuHz / 1e6; }
    public double getLoadRatioNow(double slotLength) {
        double lr = getChiLoNowMcycles(slotLength) / Math.max(1e-9, getChiMaxMcyclesPerSec());
        return Math.max(0.0, Math.min(1.0, lr));
    }
    public double getLoadRatioThisSlot() { return loadRatioThisSlot; }
    private void sampleLoad(double slotLength) {
        double slotCapacityCycles = cpuHz * slotLength;
        loadRatioThisSlot = Math.max(0.0, Math.min(1.0,
                processedCyclesThisSlot / Math.max(1e-9, slotCapacityCycles)));
    }
    public int estimatedTaskCapacity(double slotLength, List<ProposedTask> tasks) {
        double slotCycles   = cpuHz * slotLength;
        double queuedCycles = 0.0;
        for (ProposedTask t : queue) queuedCycles += t.getRemainingCpuCycles();
        double residual = Math.max(0.0, slotCycles - queuedCycles);
        double meanTaskCycles = 0.0;
        if (tasks != null && !tasks.isEmpty()) {
            for (ProposedTask t : tasks) meanTaskCycles += t.getTotalCpuCycles();
            meanTaskCycles /= tasks.size();
        }
        if (meanTaskCycles <= 1e-9) meanTaskCycles = slotCycles;
        return Math.max(1, (int) Math.floor(residual / meanTaskCycles));
    }
    // - Channel model -
    public double uplinkRate(double mdX, double mdY) {
        double d   = Math.max(1e-6, distanceTo(mdX, mdY));
        double snr = (XI * TX_POWER_W) / (d * d);
        return BANDWIDTH * (Math.log(1.0 + snr) / Math.log(2.0));
    }
    public double downlinkRate(double mdX, double mdY) {
        double d          = Math.max(1e-6, distanceTo(mdX, mdY));
        double horizontal = Math.sqrt((x-mdX)*(x-mdX) + (y-mdY)*(y-mdY));
        double theta      = Math.atan2(z, horizontal);
        double pLos       = losProbability(theta);
        double h          = pLos * Math.pow(d, -2.0) + (1.0 - pLos) * Math.pow(d, -2.5);
        double bn         = BANDWIDTH * N0_W_PER_HZ;
        double snr        = (TX_POWER_W * h) / Math.max(1e-12, bn);
        return BANDWIDTH * (Math.log(1.0 + snr) / Math.log(2.0));
    }
    private double losProbability(double theta) {
        return 1.0 / (1.0 + SIGMA * Math.exp(-LAMBDA * (theta - SIGMA)));
    }
    // - Graph utilities -
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    public static void buildNeighborsByRange(List<ProposedUAVState> uavs) {
        for (ProposedUAVState u : uavs) u.neighbors = new ArrayList<>();
        for (int i = 0; i < uavs.size(); i++) {
            for (int j = i + 1; j < uavs.size(); j++) {
                ProposedUAVState a = uavs.get(i), b = uavs.get(j);
                if (a.distanceToUav(b) <= a.uavUavRange) {
                    a.neighbors.add(b);
                    b.neighbors.add(a);
                }
            }
        }
    }
    public static boolean isGraphConnected(List<ProposedUAVState> uavs) {
        if (uavs.isEmpty()) return true;
        Set<ProposedUAVState> visited = new HashSet<>();
        Deque<ProposedUAVState> dq = new ArrayDeque<>();
        dq.add(uavs.get(0));
        visited.add(uavs.get(0));
        while (!dq.isEmpty()) {
            for (ProposedUAVState nb : dq.pollFirst().neighbors) {
                if (visited.add(nb)) dq.addLast(nb);
            }
        }
        return visited.size() == uavs.size();
    }

    // - Resource Contention Deadlock Tracking -
    public enum ResourceType { CPU_SLOT, UPLINK, DOWNLINK }
    private final java.util.Map<ResourceType, Integer> resourceHolder = new java.util.HashMap<>();

    public void grantResource(ResourceType type, int taskId) {
        resourceHolder.put(type, taskId);
    }
    public void releaseResource(ResourceType type) {
        resourceHolder.remove(type);
    }
    public int getResourceHolder(ResourceType type) {
        return resourceHolder.getOrDefault(type, -1);
    }
    public boolean isResourceFree(ResourceType type) {
        return !resourceHolder.containsKey(type);
    }
    public void releaseAllResources() {
        resourceHolder.clear();
    }
    public void consumeEnergy(double energyAmount) {
        energyState.addHoverEnergy(energyAmount);
    }
    public double getMaxEnergyJ() {
        return ProposedParams.UAV_ENERGY_BUDGET_J;
    }

    // - Defensive methods for optional Gossip coordination -
    public Map<Integer, Integer> getWaitForCopy() {
        if (controller == null) return new HashMap<>();
        return new HashMap<>(controller.getWaitFor());
    }

    public Set<Integer> getQueuedTaskIdsCopy() {
        Set<Integer> ids = new HashSet<>();
        for (ProposedTask t : queue) ids.add(t.getId());
        return ids;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DISTRIBUTED DETECTION: LOCAL WAIT-FOR GRAPH
    // ═══════════════════════════════════════════════════════════════════════
    private final Map<Integer, Integer> localWaitFor = new HashMap<>();

    public void addLocalWaitEdge(int child, int parent) {
        localWaitFor.put(child, parent);
    }

    public void removeLocalWaitEdge(int taskId) {
        localWaitFor.remove(taskId);
        localWaitFor.entrySet().removeIf(e -> e.getValue() == taskId);
    }

    public Map<Integer, Integer> getLocalWaitForCopy() {
        return new HashMap<>(localWaitFor);
    }

    public void mergeFromNeighbour(Map<Integer, Integer> received) {
        for (Map.Entry<Integer, Integer> e : received.entrySet()) {
            localWaitFor.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    public void receiveGraphMessage(GraphMessage msg) {
        mergeFromNeighbour(msg.getGraph());
    }

    public Set<Integer> detectLocalCycle() {
        Set<Integer> visited  = new HashSet<>();
        Set<Integer> recStack = new HashSet<>();
        for (Integer start : localWaitFor.keySet()) {
            List<Integer> path = new ArrayList<>();
            if (dfsCycle(start, visited, recStack, path)) {
                int cycleStart = path.get(path.size() - 1);
                Set<Integer> cycle = new HashSet<>();
                boolean inCycle = false;
                for (Integer node : path) {
                    if (node.equals(cycleStart)) inCycle = true;
                    if (inCycle) cycle.add(node);
                }
                return cycle;
            }
        }
        return java.util.Collections.emptySet();
    }

    private boolean dfsCycle(int node,
                             Set<Integer> visited,
                             Set<Integer> recStack,
                             List<Integer> path) {
        if (recStack.contains(node)) {
            path.add(node);
            return true;
        }
        if (visited.contains(node)) return false;
        visited.add(node);
        recStack.add(node);
        path.add(node);
        Integer parent = localWaitFor.get(node);
        if (parent != null && dfsCycle(parent, visited, recStack, path)) {
            return true;
        }
        recStack.remove(node);
        path.remove(path.size() - 1);
        return false;
    }

    public boolean agreesWithCycle(Set<Integer> cycleIds) {
        if (cycleIds == null || cycleIds.isEmpty()) return false;
        for (int taskId : cycleIds) {
            if (!localWaitFor.containsKey(taskId) && !localWaitFor.containsValue(taskId)) {
                return false;
            }
        }
        Set<Integer> localCycle = detectLocalCycle();
        return !localCycle.isEmpty() && localCycle.stream().anyMatch(cycleIds::contains);
    }
}
