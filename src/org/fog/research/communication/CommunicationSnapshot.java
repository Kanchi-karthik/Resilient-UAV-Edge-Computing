package org.fog.research.communication;

public class CommunicationSnapshot {

    private final int uavId;
    private final int mdId;
    private final double distanceMeters;
    private final double losProbability;
    private final double uplinkRateBps;
    private final double downlinkRateBps;
    private final double throughputBps;
    private final double transmissionLatencySec;

    public CommunicationSnapshot(
            int uavId,
            int mdId,
            double distanceMeters,
            double losProbability,
            double uplinkRateBps,
            double downlinkRateBps,
            double throughputBps,
            double transmissionLatencySec) {
        this.uavId = uavId;
        this.mdId = mdId;
        this.distanceMeters = distanceMeters;
        this.losProbability = losProbability;
        this.uplinkRateBps = uplinkRateBps;
        this.downlinkRateBps = downlinkRateBps;
        this.throughputBps = throughputBps;
        this.transmissionLatencySec = transmissionLatencySec;
    }

    public int getUavId() {
        return uavId;
    }

    public int getMdId() {
        return mdId;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public double getLosProbability() {
        return losProbability;
    }

    public double getUplinkRateBps() {
        return uplinkRateBps;
    }

    public double getDownlinkRateBps() {
        return downlinkRateBps;
    }

    public double getThroughputBps() {
        return throughputBps;
    }

    public double getTransmissionLatencySec() {
        return transmissionLatencySec;
    }
}
