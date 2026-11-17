package org.fog.research.utils;

public class Vector3D {
    private final double x;
    private final double y;
    private final double z;

    public Vector3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
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

    public double distanceTo(Vector3D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceTo(double otherX, double otherY, double otherZ) {
        double dx = this.x - otherX;
        double dy = this.y - otherY;
        double dz = this.z - otherZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double distanceTo2D(double otherX, double otherY) {
        double dx = this.x - otherX;
        double dy = this.y - otherY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public Vector3D move(double angleRad, double distance, double deltaZ) {
        double newX = this.x + distance * Math.cos(angleRad);
        double newY = this.y + distance * Math.sin(angleRad);
        double newZ = this.z + deltaZ;
        return new Vector3D(newX, newY, newZ);
    }

    @Override
    public String toString() {
        return String.format("Vector3D(x=%.2f, y=%.2f, z=%.2f)", x, y, z);
    }
}
