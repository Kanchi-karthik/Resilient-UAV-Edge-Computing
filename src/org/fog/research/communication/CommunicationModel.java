package org.fog.research.communication;

import org.fog.research.environment.MobileDeviceState;
import org.fog.research.environment.TaskDemand;
import org.fog.research.environment.UavState;
import org.fog.research.utils.ResearchMath;

public class CommunicationModel {

    private final double bandwidthHz;
    private final double transmissionPowerWatt;
    private final double thermalNoiseWatt;
    private final double xi;

    private final double losPathLossConstant;
    private final double nlosPathLossConstant;
    private final double losPathLossExponent;
    private final double nlosPathLossExponent;

    private final double sigma;
    private final double lambda;

    public CommunicationModel() {
        this(
                50e6,
                1.0,
                1e-9,
                1.0,
                1.0,
                20.0,
                2.0,
                3.5,
                9.61,
                0.16);
    }

    public CommunicationModel(
            double bandwidthHz,
            double transmissionPowerWatt,
            double thermalNoiseWatt,
            double xi,
            double losPathLossConstant,
            double nlosPathLossConstant,
            double losPathLossExponent,
            double nlosPathLossExponent,
            double sigma,
            double lambda) {
        this.bandwidthHz = bandwidthHz;
        this.transmissionPowerWatt = transmissionPowerWatt;
        this.thermalNoiseWatt = thermalNoiseWatt;
        this.xi = xi;
        this.losPathLossConstant = losPathLossConstant;
        this.nlosPathLossConstant = nlosPathLossConstant;
        this.losPathLossExponent = losPathLossExponent;
        this.nlosPathLossExponent = nlosPathLossExponent;
        this.sigma = sigma;
        this.lambda = lambda;
    }

    public double calculateDistanceMeters(UavState uav, MobileDeviceState md) {
        double dx = uav.getX() - md.getX();
        double dy = uav.getY() - md.getY();
        double dz = uav.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double calculateElevationAngleDegree(UavState uav, MobileDeviceState md) {
        double horizontalDistance = Math.sqrt(
                (uav.getX() - md.getX()) * (uav.getX() - md.getX())
                        + (uav.getY() - md.getY()) * (uav.getY() - md.getY()));
        if (horizontalDistance <= 1e-9) {
            return 90.0;
        }
        return Math.toDegrees(Math.atan2(uav.getZ(), horizontalDistance));
    }

    public double calculateLosProbability(UavState uav, MobileDeviceState md) {
        double theta = calculateElevationAngleDegree(uav, md);
        return 1.0 / (1.0 + sigma * Math.exp(-lambda * (theta - sigma)));
    }

    public double calculatePathLoss(UavState uav, MobileDeviceState md) {
        double d = Math.max(calculateDistanceMeters(uav, md), 1.0);
        double pLos = calculateLosProbability(uav, md);
        double pathLossLos = losPathLossConstant * Math.pow(d, -losPathLossExponent);
        double pathLossNlos = nlosPathLossConstant * Math.pow(d, -nlosPathLossExponent);
        return pLos * pathLossLos + (1.0 - pLos) * pathLossNlos;
    }

    public double calculateUploadRateBps(UavState uav, MobileDeviceState md) {
        double d = Math.max(calculateDistanceMeters(uav, md), 1.0);
        double snr = (xi * transmissionPowerWatt) / (d * d);
        return bandwidthHz * ResearchMath.log2(1.0 + snr);
    }

    public double calculateDownloadRateBps(UavState uav, MobileDeviceState md) {
        double h = Math.max(calculatePathLoss(uav, md), 1e-12);
        double snr = (transmissionPowerWatt * h) / (bandwidthHz * thermalNoiseWatt);
        return bandwidthHz * ResearchMath.log2(1.0 + snr);
    }

    public double calculateThroughputBps(TaskDemand task, double uploadRateBps, double downloadRateBps) {
        double inputPerSecond = Math.min(task.getInputBits(), uploadRateBps);
        double outputPerSecond = Math.min(task.getOutputBits(), downloadRateBps);
        return Math.min(inputPerSecond, outputPerSecond);
    }

    public double calculateTransmissionLatencySec(TaskDemand task, double uploadRateBps, double downloadRateBps) {
        double safeUp = Math.max(uploadRateBps, 1e-9);
        double safeDown = Math.max(downloadRateBps, 1e-9);
        return task.getInputBits() / safeUp + task.getOutputBits() / safeDown;
    }

    public CommunicationSnapshot evaluate(UavState uav, MobileDeviceState md, TaskDemand task) {
        double distance = calculateDistanceMeters(uav, md);
        double pLos = calculateLosProbability(uav, md);
        double up = calculateUploadRateBps(uav, md);
        double down = calculateDownloadRateBps(uav, md);
        double throughput = calculateThroughputBps(task, up, down);
        double latency = calculateTransmissionLatencySec(task, up, down);

        return new CommunicationSnapshot(
                uav.getUavId(),
                md.getMdId(),
                distance,
                pLos,
                up,
                down,
                throughput,
                latency);
    }

    public double getBandwidthHz() {
        return bandwidthHz;
    }

    public double getTransmissionPowerWatt() {
        return transmissionPowerWatt;
    }

    public double getThermalNoiseWatt() {
        return thermalNoiseWatt;
    }
}
