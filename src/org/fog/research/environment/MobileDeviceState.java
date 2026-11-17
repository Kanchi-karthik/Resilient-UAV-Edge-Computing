package org.fog.research.environment;

public class MobileDeviceState {

    private final int mdId;
    private final double x;
    private final double y;

    public MobileDeviceState(int mdId, double x, double y) {
        this.mdId = mdId;
        this.x = x;
        this.y = y;
    }

    public int getMdId() {
        return mdId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
