package org.fog.test.proposed.coordination.hybrid;

import java.util.Arrays;

/**
 * TokenState - State carried by the Enhanced Token Ring token.
 */
public final class TokenState {
    private int    tokenHolderIndex   = 0;
    private int    circulationCount   = 0;
    private double cumulativeEnergyJ  = 0.0;
    private int    totalDetected      = 0;
    private int    totalResolved      = 0;
    private int    detectionAttempts  = 0;

    private int dynamicInterval = 5;
    private double timeSinceLastDetection = 0.0;
    private double averageDetectionLatency = 0.0;

    private final double[] uavResidualEnergy;
    private final int[]    uavQueueSize;

    public TokenState(int numUavs) {
        this.uavResidualEnergy = new double[numUavs];
        this.uavQueueSize      = new int[numUavs];
        Arrays.fill(uavResidualEnergy, 1.0);
    }

    public int getTokenHolderIndex()   { return tokenHolderIndex; }
    public void setTokenHolderIndex(int idx) { this.tokenHolderIndex = idx; }

    public int getCirculationCount()   { return circulationCount; }
    public void incrementCirculation() { circulationCount++; }

    public double getCumulativeEnergyJ() { return cumulativeEnergyJ; }
    public void addEnergy(double e)      { cumulativeEnergyJ += e; }

    public int getTotalDetected()      { return totalDetected; }
    public void incrementDetected()    { totalDetected++; }

    public int getTotalResolved()      { return totalResolved; }
    public void incrementResolved()    { totalResolved++; }

    public int getDetectionAttempts()  { return detectionAttempts; }
    public void incrementDetectionAttempts() { detectionAttempts++; }

    public int getDynamicInterval() { return dynamicInterval; }
    public void setDynamicInterval(int interval) { this.dynamicInterval = interval; }

    public double getTimeSinceLastDetection() { return timeSinceLastDetection; }
    public void setTimeSinceLastDetection(double time) { this.timeSinceLastDetection = time; }

    public double getAverageDetectionLatency() { return averageDetectionLatency; }
    public void updateAverageLatency(double newLatency) {
        this.averageDetectionLatency = (this.averageDetectionLatency + newLatency) / 2.0;
    }

    public void updateUavState(int uavIndex, double residualEnergyRatio, int queueSize) {
        if (uavIndex >= 0 && uavIndex < uavResidualEnergy.length) {
            uavResidualEnergy[uavIndex] = residualEnergyRatio;
            uavQueueSize[uavIndex]      = queueSize;
        }
    }

    public int findNextHolderByEnergy() {
        double best      = -1.0;
        int    bestIdx   = (tokenHolderIndex + 1) % uavResidualEnergy.length;
        for (int i = 0; i < uavResidualEnergy.length; i++) {
            if (i == tokenHolderIndex) continue;
            if (uavResidualEnergy[i] > best) {
                best    = uavResidualEnergy[i];
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    @Override
    public String toString() {
        return String.format("TokenState{holder=%d, circ=%d, energy=%.4fJ, detected=%d, resolved=%d, interval=%d}",
                tokenHolderIndex, circulationCount, cumulativeEnergyJ, totalDetected, totalResolved, dynamicInterval);
    }
}
