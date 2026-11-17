package org.fog.research.environment;

public class UavState {

    private final int uavId;
    private final double x;
    private final double y;
    private final double z;
    private final double availableCpuCyclesPerSec;

    public UavState(int uavId, double x, double y, double z, double availableCpuCyclesPerSec) {
        this.uavId = uavId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.availableCpuCyclesPerSec = availableCpuCyclesPerSec;
    }

    public int getUavId() {
        return uavId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getAvailableCpuCyclesPerSec() {
        return availableCpuCyclesPerSec;
    }
}
