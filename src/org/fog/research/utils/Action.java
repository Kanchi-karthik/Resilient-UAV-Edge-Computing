package org.fog.research.utils;

public class Action {
    private final double directionRad;
    private final double distance;

    public Action(double directionRad, double distance) {
        this.directionRad = directionRad;
        this.distance = distance;
    }

    public double getDirectionRad() {
        return directionRad;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return String.format("Action(directionRad=%.4f, distance=%.2f)", directionRad, distance);
    }
}
