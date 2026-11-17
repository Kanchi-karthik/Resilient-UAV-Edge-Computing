package org.fog.research.baseline;
import org.fog.research.baseline.ProposedUAVState;
public class ProposedTask {
    private static int NEXT_ID = 1;
    private final int id;
    private final double mdX;
    private final double mdY;
    private final double arrivalTime;
    private final double deadlineSec;
    private final double dataBits;
    private final double resultBits;
    private final double totalCpuCycles;
    private double remainingCpuCycles;
    private double failureTime = -1.0;
    private org.fog.research.baseline.ProposedUAVState assignedUAV;
    private double remainingTxTimeSec = 0.0;
    private double txEnergyJ = 0.0;
    private int depGroupId = 0;
    private int depSeq = 0;
    private boolean releasable = true;
    private boolean finished = false;
    private boolean dropped = false;
    private double completionTime = -1.0;
    private double predictedProbability = 0.0;
    private double predictedCost = Double.POSITIVE_INFINITY;
    public ProposedTask(double dataBits,
                        double resultBits,
                        double totalCpuCycles,
                        double deadlineSec,
                        double mdX,
                        double mdY,
                        double arrivalTime) {
        this.id = NEXT_ID++;
        this.dataBits = dataBits;
        this.resultBits = resultBits;
        this.totalCpuCycles = totalCpuCycles;
        this.remainingCpuCycles = totalCpuCycles;
        this.deadlineSec = deadlineSec;
        this.mdX = mdX;
        this.mdY = mdY;
        this.arrivalTime = arrivalTime;
    }
    public static void resetIdCounter() {
        NEXT_ID = 1;
    }
    public int getId() { return id; }
    public double getMdX() { return mdX; }
    public double getMdY() { return mdY; }
    public double getArrivalTime() { return arrivalTime; }
    public double getDeadlineSec() { return deadlineSec; }
    public double getDataBits() { return dataBits; }
    public double getResultBits() { return resultBits; }
    public double getTotalCpuCycles() { return totalCpuCycles; }
    public double getRemainingCpuCycles() { return remainingCpuCycles; }
    public void consumeCpuCycles(double cycles) {
        remainingCpuCycles = Math.max(0.0, remainingCpuCycles - cycles);
    }
    public boolean isComputeDone() {
        return remainingCpuCycles <= 1e-9;
    }
    public org.fog.research.baseline.ProposedUAVState getAssignedUAV() { return assignedUAV; }
    public void setAssignedUAV(org.fog.research.baseline.ProposedUAVState assignedUAV) { this.assignedUAV = assignedUAV; }
    public void startMdToUavTx(double txTimeSec, double txEnergyJ) {
        this.remainingTxTimeSec = txTimeSec;
        this.txEnergyJ = txEnergyJ;
    }
    public void reduceMdToUavTx(double dt) {
        remainingTxTimeSec -= dt;
    }
    public boolean mdToUavArrived() {
        return remainingTxTimeSec <= 0.0;
    }
    public double getTxEnergy() { return txEnergyJ; }
    public void setDependency(int depGroupId, int depSeq) {
        this.depGroupId = depGroupId;
        this.depSeq = depSeq;
    }
    public boolean isDependent() { return depGroupId != 0; }
    public int getDepGroupId() { return depGroupId; }
    public int getDepSeq() { return depSeq; }
    public boolean isReleasable() { return releasable; }
    public void setReleasable(boolean releasable) { this.releasable = releasable; }
    public boolean isFinished() { return finished; }
    public void markFinished(double completionTime) {
        this.finished = true;
        this.completionTime = completionTime;
    }
    public boolean isDropped() { return dropped; }
    public void markDropped(double time) {
        this.dropped = true;
        this.failureTime = time;
    }
    public double getCompletionTime() { return completionTime; }
    public double getLatency() {
        if (completionTime < 0.0) return -1.0;
        return completionTime - arrivalTime;
    }
    public boolean metDeadline() {
        return finished && getLatency() <= deadlineSec;
    }
    public double getPredictedProbability() { return predictedProbability; }
    public void setPredictedProbability(double predictedProbability) { this.predictedProbability = predictedProbability; }
    public double getPredictedCost() { return predictedCost; }
    public void setPredictedCost(double predictedCost) { this.predictedCost = predictedCost; }
    public double getFailureTime() {
        return failureTime;
    }
}
