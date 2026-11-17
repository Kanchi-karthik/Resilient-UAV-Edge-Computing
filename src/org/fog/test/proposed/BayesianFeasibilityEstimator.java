package org.fog.test.proposed;
import java.util.Arrays;
/**
 * BayesianFeasibilityEstimator - enhanced with:
 *
 *  1. CALIBRATED FEATURE PRIORS
 *     Prior data points are injected that match realistic UAV-MEC operating
 *     conditions (txTime - deadline, queue load < 0.5, energy > 50%) so the
 *     classifier starts with a sensible decision boundary rather than needing
 *     many warm-up samples to find it.
 *
 *  2. NEUTRAL BETA PRIOR (Beta(2,2))
 *     Symmetric prior avoids the optimistic cold-start of Beta(3,2), which
 *     previously rated UAVs too highly before seeing any task outcomes.
 *
 *  3. ADAPTIVE BLEND WEIGHT
 *     After more than 20 outcomes per class, the NB component weight increases
 *     to 0.75 (from 0.65) since the classifier is better calibrated.
 *     Below that threshold the Beta-Bernoulli reliability term dominates.
 *
 *  4. URGENCY-ADJUSTED FEATURE EXTRACTION
 *     Feature [4] now uses remaining wall-clock slack (deadline - elapsed) /
 *     deadline instead of original deadline, making the classifier more
 *     sensitive to tasks that are near their deadline.
 */
public class BayesianFeasibilityEstimator {
    // - Gaussian NB inner class -
    private static class RunningGaussian {
        private int    n    = 0;
        private double mean = 0.0;
        private double m2   = 0.0;
        void update(double x) {
            n++;
            double delta  = x - mean;
            mean         += delta / n;
            m2           += delta * (x - mean);
        }
        double mean() { return mean; }
        double variance() {
            if (n < 2) return ProposedParams.NB_MIN_STD * ProposedParams.NB_MIN_STD;
            return Math.max(ProposedParams.NB_MIN_STD * ProposedParams.NB_MIN_STD,
                            m2 / (n - 1));
        }
    }
    // - Beta-Bernoulli inner class -
    private static class BetaReliability {
        double alpha;
        double beta;
        BetaReliability() {
            this.alpha = ProposedParams.REL_INIT_ALPHA;
            this.beta  = ProposedParams.REL_INIT_BETA;
        }
        double mean() { return alpha / Math.max(1e-12, alpha + beta); }
        void update(boolean success) {
            if (success) alpha += 1.0;
            else          beta  += 1.0;
        }
    }
    // - Fields -
    private static final int NUM_FEATURES = 5;
    private final RunningGaussian[] successStats = new RunningGaussian[NUM_FEATURES];
    private final RunningGaussian[] failureStats = new RunningGaussian[NUM_FEATURES];
    private int successCount = 1;
    private int failureCount = 1;
    private final BetaReliability[] uavReliability;
    // - Constructor -
    public BayesianFeasibilityEstimator(int numUavs) {
        for (int i = 0; i < NUM_FEATURES; i++) {
            successStats[i] = new RunningGaussian();
            failureStats[i] = new RunningGaussian();
        }
        // - Calibrated priors -
        // SUCCESS feature distributions: low ratio values = task fits in deadline
        // [0] txTime/deadline   [1] compTime/deadline
        // [2] queue load ratio  [3] energy risk  [4] deadline tightness
        //
        // Inject multiple representative success examples
        double[][] successSamples = {
            {0.10, 0.15, 0.15, 0.10, 0.20},   // very easy task
            {0.20, 0.25, 0.25, 0.20, 0.30},   // typical success
            {0.30, 0.35, 0.30, 0.25, 0.40},   // moderate load, still succeeds
            {0.15, 0.20, 0.20, 0.15, 0.25},   // another easy case
        };
        // FAILURE feature distributions: high ratio values = overloaded / tight
        double[][] failureSamples = {
            {0.80, 0.85, 0.75, 0.65, 0.90},   // classic failure
            {0.70, 0.75, 0.80, 0.70, 0.85},   // queue overload
            {0.60, 0.65, 0.70, 0.80, 0.80},   // energy depletion
            {0.90, 0.80, 0.65, 0.60, 0.95},   // tx time dominant
        };
        for (double[] s : successSamples) {
            successCount++;
            for (int i = 0; i < NUM_FEATURES; i++) successStats[i].update(s[i]);
        }
        for (double[] f : failureSamples) {
            failureCount++;
            for (int i = 0; i < NUM_FEATURES; i++) failureStats[i].update(f[i]);
        }
        uavReliability = new BetaReliability[numUavs];
        for (int i = 0; i < numUavs; i++) uavReliability[i] = new BetaReliability();
    }
    // - Inference -
    public double predictNBProbability(ProposedTask task,
                                       ProposedUAVState uav,
                                       double slotLength) {
        double[] x = extractFeatures(task, uav, slotLength);
        double psPrior = priorSuccess();
        double pfPrior = 1.0 - psPrior;
        double logPs = Math.log(Math.max(1e-300, psPrior));
        double logPf = Math.log(Math.max(1e-300, pfPrior));
        for (int i = 0; i < NUM_FEATURES; i++) {
            logPs += logGaussianPdf(x[i], successStats[i].mean(), successStats[i].variance());
            logPf += logGaussianPdf(x[i], failureStats[i].mean(), failureStats[i].variance());
        }
        double maxLog = Math.max(logPs, logPf);
        double ps = Math.exp(logPs - maxLog);
        double pf = Math.exp(logPf - maxLog);
        return ps / Math.max(1e-300, ps + pf);
    }
    public double predictReliabilityProbability(ProposedUAVState uav) {
        int idx = uav.getIndex();
        if (idx < 0 || idx >= uavReliability.length) return 0.5;
        return uavReliability[idx].mean();
    }
    /**
     * Adaptive blend: once enough data is collected (>20 outcomes per class),
     * increase the NB weight since it is better calibrated.
     */
    public double predictFinalProbability(ProposedTask task,
                                          ProposedUAVState uav,
                                          double slotLength) {
        double pNB  = predictNBProbability(task, uav, slotLength);
        double pRel = predictReliabilityProbability(uav);
        // Adaptive weights: trust NB more as it collects data
        double wNB, wRel;
        int totalOutcomes = successCount + failureCount;
        if (totalOutcomes > 60) {
            wNB  = 0.75;
            wRel = 0.25;
        } else if (totalOutcomes > 30) {
            wNB  = 0.70;
            wRel = 0.30;
        } else {
            // Early phase: rely more on per-UAV reliability
            wNB  = ProposedParams.W_NB_MODEL;
            wRel = ProposedParams.W_ONLINE_RELIABILITY;
        }
        return clamp(wNB * pNB + wRel * pRel, 0.0, 1.0);
    }
    // - Online update -
    public void updateFromOutcome(ProposedTask task,
                                  ProposedUAVState uav,
                                  double slotLength,
                                  boolean success) {
        double[] x = extractFeatures(task, uav, slotLength);
        if (success) {
            successCount++;
            for (int i = 0; i < NUM_FEATURES; i++) successStats[i].update(x[i]);
        } else {
            failureCount++;
            for (int i = 0; i < NUM_FEATURES; i++) failureStats[i].update(x[i]);
        }
        int idx = uav.getIndex();
        if (idx >= 0 && idx < uavReliability.length) {
            uavReliability[idx].update(success);
        }
    }
    // - Accessors -
    public double[] getReliabilityMeans() {
        double[] out = new double[uavReliability.length];
        for (int i = 0; i < out.length; i++) out[i] = uavReliability[i].mean();
        return out;
    }
    public double[] getReliabilityAlpha() {
        double[] out = new double[uavReliability.length];
        for (int i = 0; i < out.length; i++) out[i] = uavReliability[i].alpha;
        return out;
    }
    public double[] getReliabilityBeta() {
        double[] out = new double[uavReliability.length];
        for (int i = 0; i < out.length; i++) out[i] = uavReliability[i].beta;
        return out;
    }
    // - Private helpers -
    /**
     * Extract 5 normalised features:
     *   [0] txTime / deadline
     *   [1] compTime / deadline
     *   [2] queue load ratio
     *   [3] energy risk = 1 - residualEnergyRatio
     *   [4] remaining slack tightness  (uses wall-clock remaining deadline)
     */
    private double[] extractFeatures(ProposedTask t, ProposedUAVState u, double slotLength) {
        double upRate   = u.uplinkRate(t.getMdX(), t.getMdY());
        double downRate = u.downlinkRate(t.getMdX(), t.getMdY());
        double txTime   = t.getDataBits()   / Math.max(1e-9, upRate)
                        + t.getResultBits() / Math.max(1e-9, downRate);
        double compTime = t.getTotalCpuCycles() / Math.max(1e-9, u.getCpuHz());
        double deadline   = Math.max(1e-9, t.getDeadlineSec());
        // Remaining wall-clock deadline (accounts for how long the task has waited)
        double now              = org.cloudbus.cloudsim.core.CloudSim.clock();
        double remainingDeadline = Math.max(1e-9,
                (t.getArrivalTime() + t.getDeadlineSec()) - now);
        // Slack relative to remaining time budget
        double queueWait   = 0.0;
        for (ProposedTask qt : u.getQueue()) {
            queueWait += qt.getRemainingCpuCycles() / Math.max(1e-9, u.getCpuHz());
        }
        double slackRatio  = Math.max(0.0,
                remainingDeadline - txTime - compTime - queueWait) / remainingDeadline;
        return new double[] {
            clamp(txTime   / deadline,                     0.0, 3.0),
            clamp(compTime / deadline,                     0.0, 3.0),
            clamp(u.getLoadRatioNow(slotLength),           0.0, 1.0),
            clamp(1.0 - u.getResidualEnergyRatio(),        0.0, 1.0),
            clamp(1.0 - slackRatio,                        0.0, 1.0)
        };
    }
    private double logGaussianPdf(double x, double mean, double variance) {
        return -0.5 * Math.log(2.0 * Math.PI * variance)
               - (x - mean) * (x - mean) / (2.0 * variance);
    }
    private double priorSuccess() {
        double empirical = (double) successCount / (successCount + failureCount);
        return 0.5 * ProposedParams.NB_PRIOR_SUCCESS + 0.5 * empirical;
    }
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
    @Override
    public String toString() {
        return "BayesianFeasibilityEstimator{"
             + "successCount=" + successCount
             + ", failureCount=" + failureCount
             + ", successMeans=" + Arrays.toString(
                   Arrays.stream(successStats).mapToDouble(RunningGaussian::mean).toArray())
             + ", failureMeans=" + Arrays.toString(
                   Arrays.stream(failureStats).mapToDouble(RunningGaussian::mean).toArray())
             + ", reliability=" + Arrays.toString(getReliabilityMeans())
             + '}';
    }
}

