package org.fog.research.algorithms.factorization;

public class TaskFactorizationNetwork {
    private final int numUavs;

    public TaskFactorizationNetwork(int numUavs) {
        this.numUavs = numUavs;
    }

    // Eq. (23): Q_total = \sum Q_i
    public double calculateTotalQ(double[] individualQs) {
        double totalQ = 0.0;
        for (double q : individualQs) {
            totalQ += q;
        }
        return totalQ;
    }

    // Eq. (24): Loss function L(theta) = (y_i - Q_total)^2
    public double calculateLoss(double targetY, double totalQ) {
        return Math.pow(targetY - totalQ, 2);
    }

    // Eq. (25): Verify Individual-Global-Max (IGM) principle
    public boolean verifyIGM(double[] individualMaxQs, double[] actualIndividualQs, double totalMaxQ, double actualTotalQ) {
        double sumMax = 0.0;
        for (double q : individualMaxQs) {
            sumMax += q;
        }
        return Math.abs(sumMax - totalMaxQ) < 1e-5;
    }

    // Eq. (26): Verify constraint [argmax Q(a)]_U >= [Q(a)]_U
    public boolean verifyConstraint(double[] maxQs, double[] chosenQs) {
        if (maxQs.length != chosenQs.length) {
            return false;
        }
        for (int i = 0; i < maxQs.length; i++) {
            if (maxQs[i] < chosenQs[i] - 1e-5) {
                return false;
            }
        }
        return true;
    }
}
