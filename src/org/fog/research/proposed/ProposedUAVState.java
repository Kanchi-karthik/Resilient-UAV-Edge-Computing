package org.fog.research.proposed;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
/**
 * ProposedUAVState — enhanced with:
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
 */
public class ProposedUAVState {
    private final FogDevice device;
    private final int index;
    private double x, y, z;
    private double speedMps    = ProposedParams.UAV_SPEED_MPS;
    private double altitudeM   = ProposedParams.UAV_ALTITUDE_M;
    private double coverageRadius = 130.0;
    private double uavUavRange = ProposedParams.UAV_UAV_RANGE_M;
    private double cpuHz       = ProposedParams.CPU_HZ;
    private double waypointX = -1;
    private double waypointY = -1;
    private List<ProposedUAVState> neighbors = new ArrayList<>();
    // ── EDF priority queue ───────────────────────────────────────────────────
    // Tasks are sorted by (arrivalTime + deadlineSec) ascending so the soonest
    // deadline always runs first.  We use a PriorityQueue backed list so
    // insertion is O(log n) and peek is O(1).
    private final PriorityQueue<ProposedTask> queue = new PriorityQueue<>(
            Comparator.comparingDouble(t -> t.getArrivalTime() + t.getDeadlineSec())
    );
    private int completedThisSlot = 0;
    private int droppedThisSlot   = 0;
    private double loadRatioThisSlot = 0.0;
    private int completedTotal = 0;
    private int droppedTotal   = 0;
    public double throughputBits = 0.0;
    public double sumDataBits    = 0.0;
    public double sumRateBits    = 0.0;
    private static final double BANDWIDTH    = 50e6;
    private static final double TX_POWER_W   = 1.0;
    private static final double N0_W_PER_HZ  = 2e-20;
    private static final double XI           = 1e5;
    private static final double LAMBDA       = 9.61;
    private static final double SIGMA        = 0.16;
    private static final double HOVER_POWER_W = 120.0;
    private static final double COMP_POWER_W  = 5.0;
    private static final double MOVE_POWER_W  = 7.0;
    private double movementEnergyJ = 0.0;
    private double computeEnergyJ  = 0.0;
    private double commEnergyJ     = 0.0;
    private double hoverEnergyJ    = 0.0;
    private double processedCyclesThisSlot = 0.0;
    private double processedCyclesTotal    = 0.0;
    private final List<ProposedTask> completedTaskBuffer = new ArrayList<>();
    private final List<ProposedTask> droppedTaskBuffer   = new ArrayList<>();
    // ── Demand-aware mobility state ──────────────────────────────────────────
    // Target hotspot coordinate; updated each slot
    private double hotspotTargetX = -1;
    private double hotspotTargetY = -1;
    public ProposedUAVState(FogDevice dev, int index) {
        this.device = dev;
        this.index  = index;
    }
    // ── Accessors ────────────────────────────────────────────────────────────
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
    public void setAltitude(double a)       { this.altitudeM = a; }
    public void setUavUavRange(double r)    { this.uavUavRange = r; }
    public void setCpuHz(double hz)         { this.cpuHz = hz; }
    public double getCpuHz()                { return cpuHz; }
    /** Returns a snapshot list of tasks in the EDF queue (for scheduling cost estimation). */
    public List<ProposedTask> getQueue() {
        return new ArrayList<>(queue);
    }
    public int getQueueSize() { return queue.size(); }
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
    public double getTotalEnergy() {
        return movementEnergyJ + computeEnergyJ + commEnergyJ + hoverEnergyJ;
    }
    public double getResidualEnergyJ() {
        return Math.max(1e-9, ProposedParams.UAV_ENERGY_BUDGET_J - getTotalEnergy());
    }
    public double getResidualEnergyRatio() {
        return Math.max(0.0, Math.min(1.0,
                getResidualEnergyJ() / ProposedParams.UAV_ENERGY_BUDGET_J));
    }
    public void addCommEnergy(double e) { commEnergyJ += e; }
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
    // ── Coverage / distance ──────────────────────────────────────────────────
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
    // ── Mobility ─────────────────────────────────────────────────────────────
    public void moveByAction(double angle, double distance, double dt) {
        distance = Math.max(0.0, Math.min(distance, speedMps * dt));
        x += distance * Math.cos(angle);
        y += distance * Math.sin(angle);
        x = clamp(x, 0, ProposedParams.AREA_X);
        y = clamp(y, 0, ProposedParams.AREA_Y);
        double flyTime = (speedMps <= 1e-9) ? 0.0 : distance / speedMps;
        movementEnergyJ += flyTime * MOVE_POWER_W;
    }
    /**
     * Demand-aware mobility: with probability HOTSPOT_STEER_PROB the UAV
     * steers toward the centroid of the K nearest uncovered MDs.
     * Otherwise it takes a random step.
     *
     * @param dt   slot length (s)
     * @param rand seeded RNG for reproducibility
     * @param mds  list of all MD positions as double[]{x,y}
     */
    public void moveWithDemandAwareness(double dt, Random rand,
                                        List<double[]> mds) {
        if (mds == null || mds.isEmpty()
                || rand.nextDouble() > ProposedParams.HOTSPOT_STEER_PROB) {
            moveRandomly(dt, rand);
            return;
        }
        // Find centroid of K nearest uncovered MDs
        int K = ProposedParams.HOTSPOT_SAMPLE_K;
        double cx = 0, cy = 0;
        int count = 0;
        // Sort-free: iterate all, keep track of K nearest by distance
        // For efficiency use a simple linear scan (800 MDs is cheap)
        double[] bestDist = new double[K];
        double[] bestX    = new double[K];
        double[] bestY    = new double[K];
        java.util.Arrays.fill(bestDist, Double.MAX_VALUE);
        for (double[] md : mds) {
            if (isInCoverage(md[0], md[1])) continue;   // already covered
            double dx = x - md[0], dy = y - md[1];
            double d  = dx*dx + dy*dy;
            // Simple insertion into sorted K-best list
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
        // Blend: hotspot direction (1-blend) + random direction (blend)
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
    /** Pure random step (kept for fallback / connectivity restore). */
    public void moveRandomly(double dt, Random rand) {
        double angle = rand.nextDouble() * 2.0 * Math.PI;
        moveByAction(angle, speedMps * dt, dt);
    }
    // ── Task queue management ────────────────────────────────────────────────
    public void enqueueTask(ProposedTask t) {
        queue.add(t);   // PriorityQueue maintains EDF order automatically
    }
    /**
     * Cooperative offloading: extract up to maxTasks tasks from the TAIL
     * of the deadline-priority queue (longest remaining deadline = least urgent)
     * so they can be re-assigned to less-loaded neighbors.
     */
    public List<ProposedTask> extractOffloadCandidates(int maxTasks) {
        if (queue.size() <= 1) return new ArrayList<>();
        // Drain the queue to a list, take last N (furthest deadlines), reinsert rest
        List<ProposedTask> all = new ArrayList<>(queue);
        // Sort by absolute deadline descending to pick least urgent
        all.sort((a, b) -> Double.compare(
                b.getArrivalTime() + b.getDeadlineSec(),
                a.getArrivalTime() + a.getDeadlineSec()));
        int take = Math.min(maxTasks, all.size() - 1); // keep at least 1
        List<ProposedTask> offload = all.subList(0, take);
        List<ProposedTask> keep    = all.subList(take, all.size());
        queue.clear();
        queue.addAll(keep);
        return new ArrayList<>(offload);
    }
    // ── Slot execution (EDF) ─────────────────────────────────────────────────
    /**
     * Execute computation for one slot using EDF order.
     * The PriorityQueue already gives us EDF peek, so we simply process
     * tasks in priority order, dropping any whose absolute deadline has passed.
     */
    public void executeOneSlot(double dt) {
        double budgetCycles  = cpuHz * dt;
        double elapsedInSlot = 0.0;
        if (queue.isEmpty()) {
            hoverEnergyJ += HOVER_POWER_W * dt;
            sampleLoad(dt);
            return;
        }
        while (budgetCycles > 1e-9 && !queue.isEmpty()) {
            ProposedTask t = queue.peek();
            if (t == null) break;
            double absDeadline = t.getArrivalTime() + t.getDeadlineSec();
            // Drop if already expired
            if (CloudSim.clock() + elapsedInSlot >= absDeadline) {
                queue.poll();
                t.markDropped(CloudSim.clock() + elapsedInSlot);
                droppedThisSlot++;
                droppedTotal++;
                droppedTaskBuffer.add(t);
                continue;
            }
            double consume  = Math.min(budgetCycles, t.getRemainingCpuCycles());
            double compTime = consume / Math.max(1e-9, cpuHz);
            t.consumeCpuCycles(consume);
            budgetCycles  -= consume;
            elapsedInSlot += compTime;
            computeEnergyJ          += COMP_POWER_W * compTime;
            processedCyclesThisSlot += consume;
            processedCyclesTotal    += consume;
            if (t.isComputeDone()) {
                queue.poll();
                double finishTime = CloudSim.clock() + elapsedInSlot;
                if (finishTime <= absDeadline) {
                    t.markFinished(finishTime);
                    completedThisSlot++;
                    completedTotal++;
                    completedTaskBuffer.add(t);
                    throughputBits += (t.getDataBits() + t.getResultBits());
                } else {
                    t.markDropped(finishTime);
                    droppedThisSlot++;
                    droppedTotal++;
                    droppedTaskBuffer.add(t);
                }
            } else {
                // Task partially processed; put it back (PQ will re-sort, same key)
                // We don't need to re-add since peek/poll already handle this.
                // But since we didn't poll yet, just break.
                break;
            }
        }
        hoverEnergyJ += HOVER_POWER_W * dt;
        sampleLoad(dt);
    }
    // ── Load metrics ─────────────────────────────────────────────────────────
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
    // ── Channel model ─────────────────────────────────────────────────────────
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
    // ── Graph utilities ───────────────────────────────────────────────────────
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

    // ── Resource Contention Deadlock Tracking ────────────────────────────────
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
        hoverEnergyJ += energyAmount;
    }
    public double getMaxEnergyJ() {
        return ProposedParams.UAV_ENERGY_BUDGET_J;
    }
}
