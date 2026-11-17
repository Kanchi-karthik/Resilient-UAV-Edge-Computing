package org.fog.research.utils;

public class Vector2D {
    private final double x;
    private final double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double distanceTo(Vector2D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceTo(double otherX, double otherY) {
        double dx = this.x - otherX;
        double dy = this.y - otherY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public Vector2D move(double angleRad, double distance) {
        double newX = this.x + distance * Math.cos(angleRad);
        double newY = this.y + distance * Math.sin(angleRad);
        return new Vector2D(newX, newY);
    }

    @Override
    public String toString() {
        return String.format("Vector2D(x=%.2f, y=%.2f)", x, y);
    }
}
