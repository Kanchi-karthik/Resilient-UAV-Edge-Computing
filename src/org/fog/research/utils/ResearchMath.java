package org.fog.research.utils;

public final class ResearchMath {

    private ResearchMath() {
    }

    public static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
