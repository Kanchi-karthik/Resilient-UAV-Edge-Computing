package org.fog.research.baseline;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
public class ProposedUAVState {
    private final FogDevice device;
    private final int index;
    private double x, y, z;
    private double speedMps = ProposedParams.UAV_SPEED_MPS;
    private double altitudeM = ProposedParams.UAV_ALTITUDE_M;
    private double coverageRadius = 130.0;
    private double uavUavRange = ProposedParams.UAV_UAV_RANGE_M;
    private double cpuHz = ProposedParams.CPU_HZ;
    private List<ProposedUAVState> neighbors = new ArrayList<>();
    private final Deque<ProposedTask> queue = new ArrayDeque<>();
    private int completedThisSlot = 0;
    private int droppedThisSlot = 0;
    private double loadRatioThisSlot = 0.0;
    private double waypointX = -1;
    private double waypointY = -1;
    private double waypointPauseRemaining = 0.0;
    private static final double RWP_PAUSE_MAX_S = 5.0;   // max pause at waypoint
    private static final double RWP_MIN_SPEED   = 2.0;   // m/s
    private static final double RWP_MAX_SPEED   = 15.0;  // m/s
    private int completedTotal = 0;
    private int droppedTotal = 0;
    public double throughputBits = 0.0;
    public double sumDataBits = 0.0;
    public double sumRateBits = 0.0;
    private static final double BANDWIDTH = 50e6;
    private static final double TX_POWER_W = 1.0;
    // Thermal noise PSD: N0 = k*T*NF where k=1.38e-23 J/K, T=290 K (ITU standard),
    // NF = 5.0 (noise figure, ~7 dB) — standard for UAV receiver hardware.
    // Previous value of 1e-9 W/Hz was ~70 dB above thermal floor, artificially
    // suppressing downlink rates to ~0.06 Mbps. Corrected to 2e-20 W/Hz.
    private static final double N0_W_PER_HZ = 2e-20;
    private static final double XI = 1e5;
    private static final double LAMBDA = 9.61;
    private static final double SIGMA = 0.16;
    private static final double HOVER_POWER_W = 120.0;
    private static final double COMP_POWER_W = 5.0;
    private static final double MOVE_POWER_W = 7.0;
    private double movementEnergyJ = 0.0;
    private double computeEnergyJ = 0.0;
    private double commEnergyJ = 0.0;
    private double hoverEnergyJ = 0.0;
    // Added for meaningful per-UAV computational load tracking
    private double processedCyclesThisSlot = 0.0;
    private double processedCyclesTotal = 0.0;
    private final List<ProposedTask> completedTaskBuffer = new ArrayList<>();
    private final List<ProposedTask> droppedTaskBuffer = new ArrayList<>();
    public ProposedUAVState(FogDevice dev, int index) {
        this.device = dev;
        this.index = index;
    }
    public FogDevice getDevice() { return device; }
    public int getIndex() { return index; }
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public void setCoverageRadius(double coverageRadius) { this.coverageRadius = coverageRadius; }
    public void setSpeed(double speedMps) { this.speedMps = speedMps; }
    public void setAltitude(double altitudeM) { this.altitudeM = altitudeM; }
    public void setUavUavRange(double uavUavRange) { this.uavUavRange = uavUavRange; }
    public void setCpuHz(double cpuHz) { this.cpuHz = cpuHz; }
    public double getCpuHz() { return cpuHz; }
    public Deque<ProposedTask> getQueue() { return queue; }
    public int getQueueSize() { return queue.size(); }
    public List<ProposedUAVState> getNeighbors() { return neighbors; }
    public int getCompletedTasksThisSlot() { return completedThisSlot; }
    public int getDroppedTasksThisSlot() { return droppedThisSlot; }
    public int getCompletedTotal() { return completedTotal; }
    public int getDroppedTotal() { return droppedTotal; }
    public double getProcessedCyclesThisSlot() { return processedCyclesThisSlot; }
    public double getProcessedCyclesTotal() { return processedCyclesTotal; }
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
        double remain = ProposedParams.UAV_ENERGY_BUDGET_J - getTotalEnergy();
        return Math.max(1e-9, remain);
    }
    public double getResidualEnergyRatio() {
        return Math.max(0.0, Math.min(1.0, getResidualEnergyJ() / ProposedParams.UAV_ENERGY_BUDGET_J));
    }
    public void addCommEnergy(double e) {
        commEnergyJ += e;
    }
    public void resetSlotStats() {
        completedThisSlot = 0;
        droppedThisSlot = 0;
        loadRatioThisSlot = 0.0;
        throughputBits = 0.0;
        sumDataBits = 0.0;
        sumRateBits = 0.0;
        processedCyclesThisSlot = 0.0;
        completedTaskBuffer.clear();
        droppedTaskBuffer.clear();
    }
    public boolean isInCoverage(double mdX, double mdY) {
        double dx = x - mdX;
        double dy = y - mdY;
        return Math.sqrt(dx * dx + dy * dy) <= coverageRadius;
    }
    public double distanceTo(double mdX, double mdY) {
        double dx = x - mdX;
        double dy = y - mdY;
        return Math.sqrt(dx * dx + dy * dy + z * z);
    }
    public double distanceToUav(ProposedUAVState other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
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
     * Move UAV in a random direction using the caller-supplied seeded Random.
     * Previously used Math.random() (system-clock seed), which made UAV
     * mobility non-reproducible even when a seed was provided to the controller.
     * Passing rand here ensures full per-seed reproducibility.
     */
 // --- Replace moveRandomly() entirely ---
    /**
     * Random Waypoint mobility model (IEEE standard for MEC/UAV papers).
     * UAV picks a random destination, flies toward it at a random speed in
     * [RWP_MIN_SPEED, RWP_MAX_SPEED], pauses for a random dwell time when it
     * arrives, then picks the next waypoint.  Fully reproducible via rand.
     *
     * This replaces the simple random-walk that was previously used, which
     * is non-standard and was flagged by reviewers.
     */
    public void moveRandomly(double dt, Random rand) {
        // Initialise on first call
        if (waypointX < 0) {
            waypointX = rand.nextDouble() * ProposedParams.AREA_X;
            waypointY = rand.nextDouble() * ProposedParams.AREA_Y;
        }
        // Consume pause time first
        if (waypointPauseRemaining > 0) {
            double pause = Math.min(waypointPauseRemaining, dt);
            waypointPauseRemaining -= pause;
            dt -= pause;
            hoverEnergyJ += HOVER_POWER_W * pause;   // hovering while paused
            if (dt <= 1e-9) return;
        }
        // Fly toward current waypoint
        double dx = waypointX - x;
        double dy = waypointY - y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= 1e-3) {
            // Arrived — pause then pick next waypoint
            waypointPauseRemaining = rand.nextDouble() * RWP_PAUSE_MAX_S;
            waypointX = rand.nextDouble() * ProposedParams.AREA_X;
            waypointY = rand.nextDouble() * ProposedParams.AREA_Y;
            return;
        }
        double angle   = Math.atan2(dy, dx);
        double vRwp    = RWP_MIN_SPEED + rand.nextDouble() * (RWP_MAX_SPEED - RWP_MIN_SPEED);
        double maxDist = vRwp * dt;
        double step    = Math.min(dist, maxDist);
        moveByAction(angle, step, dt);   // reuses existing energy + clamp logic
        // If we overshot / arrived this step, immediately set new waypoint
        double dxAfter = waypointX - x;
        double dyAfter = waypointY - y;
        if (Math.sqrt(dxAfter * dxAfter + dyAfter * dyAfter) <= 1e-3) {
            waypointPauseRemaining = rand.nextDouble() * RWP_PAUSE_MAX_S;
            waypointX = rand.nextDouble() * ProposedParams.AREA_X;
            waypointY = rand.nextDouble() * ProposedParams.AREA_Y;
        }
    }
    public void enqueueTask(ProposedTask t) {
        queue.addLast(t);
    }
    public void executeOneSlot(double dt) {
        double budgetCycles = cpuHz * dt;
        double elapsedInSlot = 0.0;
        if (queue.isEmpty()) {
            hoverEnergyJ += HOVER_POWER_W * dt;
            sampleLoad(dt);
            return;
        }
        while (budgetCycles > 1e-9 && !queue.isEmpty()) {
            ProposedTask t = queue.peekFirst();
            if (t == null) break;
            double absDeadline = t.getArrivalTime() + t.getDeadlineSec();
            if (CloudSim.clock() + elapsedInSlot >= absDeadline) {
                queue.pollFirst();
                t.markDropped(CloudSim.clock() + elapsedInSlot);
                droppedThisSlot++;
                droppedTotal++;
                droppedTaskBuffer.add(t);
                continue;
            }
            double consume = Math.min(budgetCycles, t.getRemainingCpuCycles());
            double compTime = consume / Math.max(1e-9, cpuHz);
            t.consumeCpuCycles(consume);
            budgetCycles -= consume;
            elapsedInSlot += compTime;
            computeEnergyJ += COMP_POWER_W * compTime;
            processedCyclesThisSlot += consume;
            processedCyclesTotal += consume;
            if (t.isComputeDone()) {
                queue.pollFirst();
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
                break;
            }
        }
        hoverEnergyJ += HOVER_POWER_W * dt;
        sampleLoad(dt);
    }
    public double getChiLoNowMcycles(double slotLength) {
        double queuedCycles = 0.0;
        for (ProposedTask t : queue) {
            queuedCycles += t.getRemainingCpuCycles();
        }
        double mcycles = queuedCycles / 1e6;
        return (slotLength <= 1e-9) ? mcycles : (mcycles / slotLength);
    }
    public double getChiMaxMcyclesPerSec() {
        return cpuHz / 1e6;
    }
    // Queue-based load, still useful for scheduling decisions
    public double getLoadRatioNow(double slotLength) {
        double chiLo = getChiLoNowMcycles(slotLength);
        double chiMax = getChiMaxMcyclesPerSec();
        double lr = chiLo / Math.max(1e-9, chiMax);
        return Math.max(0.0, Math.min(1.0, lr));
    }
    // Actual utilization in the current slot
    public double getLoadRatioThisSlot() {
        return loadRatioThisSlot;
    }
    private void sampleLoad(double slotLength) {
        double slotCapacityCycles = cpuHz * slotLength;
        double usedRatio = processedCyclesThisSlot / Math.max(1e-9, slotCapacityCycles);
        loadRatioThisSlot = Math.max(0.0, Math.min(1.0, usedRatio));
    }
    public int estimatedTaskCapacity(double slotLength, List<ProposedTask> tasks) {
        double slotCycles = cpuHz * slotLength;
        double queuedCycles = 0.0;
        for (ProposedTask t : queue) {
            queuedCycles += t.getRemainingCpuCycles();
        }
        double residual = Math.max(0.0, slotCycles - queuedCycles);
        double meanTaskCycles = 0.0;
        if (tasks != null && !tasks.isEmpty()) {
            for (ProposedTask t : tasks) {
                meanTaskCycles += t.getTotalCpuCycles();
            }
            meanTaskCycles /= tasks.size();
        }
        if (meanTaskCycles <= 1e-9) {
            meanTaskCycles = slotCycles;
        }
        int cap = (int) Math.floor(residual / meanTaskCycles);
        return Math.max(1, cap);
    }
    public double uplinkRate(double mdX, double mdY) {
        double d = distanceTo(mdX, mdY);
        if (d <= 1e-6) d = 1e-6;
        double snr = (XI * TX_POWER_W) / (d * d);
        return BANDWIDTH * (Math.log(1.0 + snr) / Math.log(2.0));
    }
    public double downlinkRate(double mdX, double mdY) {
        double d = distanceTo(mdX, mdY);
        if (d <= 1e-6) d = 1e-6;
        double horizontal = Math.sqrt((x - mdX) * (x - mdX) + (y - mdY) * (y - mdY));
        double theta = Math.atan2(z, horizontal);
        double pLos = losProbability(theta);
        double h = pLos * Math.pow(d, -2.0) + (1.0 - pLos) * Math.pow(d, -2.5);
        double bn = BANDWIDTH * N0_W_PER_HZ;
        double snr = (TX_POWER_W * h) / Math.max(1e-12, bn);
        return BANDWIDTH * (Math.log(1.0 + snr) / Math.log(2.0));
    }
    private double losProbability(double theta) {
        return 1.0 / (1.0 + SIGMA * Math.exp(-LAMBDA * (theta - SIGMA)));
    }
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    public static void buildNeighborsByRange(List<ProposedUAVState> uavs) {
        for (ProposedUAVState u : uavs) {
            u.neighbors = new ArrayList<>();
        }
        for (int i = 0; i < uavs.size(); i++) {
            for (int j = i + 1; j < uavs.size(); j++) {
                ProposedUAVState a = uavs.get(i);
                ProposedUAVState b = uavs.get(j);
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
            ProposedUAVState cur = dq.pollFirst();
            for (ProposedUAVState nb : cur.neighbors) {
                if (!visited.contains(nb)) {
                    visited.add(nb);
                    dq.addLast(nb);
                }
            }
        }
        return visited.size() == uavs.size();
    }
}
