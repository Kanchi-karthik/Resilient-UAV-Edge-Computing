package org.fog.research.proposed;
public class ProposedParams {
    public static final int AREA_X = 2000;
    public static final int AREA_Y = 2000;
    public static final int NUM_MDS = 800;
    public static final double UAV_ALTITUDE_M = 120.0;
    public static final double UAV_SPEED_MPS = 6.94;
    public static final double UAV_UAV_RANGE_M = 900.0;
    public static double CPU_HZ = 1e9;
    public static double LAMBDA_TASKS = 10.0;
    public static final int TASK_SET_LENGTH = 1000;
    public static final double P_DEP = 0.30;
    public static final int DEP_LEN = 3;
    public static final double MD_SPEED_MIN = 1.0;
    public static final double MD_SPEED_MAX = 3.0;
    // ── Naive Bayes ──────────────────────────────────────────────────────────
    public static final double NB_PRIOR_SUCCESS = 0.65;
    public static final double NB_MIN_STD = 1e-3;
    // ── Online reliability: Beta prior ───────────────────────────────────────
    // Beta(2,2) = neutral 0.5 mean — avoids over-optimistic cold-start
    public static final double REL_INIT_ALPHA = 2.0;
    public static final double REL_INIT_BETA  = 2.0;
    // ── Blend final probability = w1*NB + w2*Reliability ────────────────────
    public static final double W_NB_MODEL          = 0.65;
    public static final double W_ONLINE_RELIABILITY = 0.35;
    // ── Assignment threshold (lowered slightly to reduce unassigned rate) ────
    public static final double MIN_ASSIGN_PROBABILITY = 0.12;
    // ── MCMF cost weights (delay-dominant to hit deadlines) ──────────────────
    // Increased W_COST_DELAY so MCMF prefers UAVs with short queue wait
    public static final double W_COST_PROB   = 0.20;
    public static final double W_COST_LOAD   = 0.20;
    public static final double W_COST_ENERGY = 0.10;
    public static final double W_COST_DELAY  = 0.50;   // ↑ was 0.35
    public static final double UAV_ENERGY_BUDGET_J = 200000.0;
    public static final int WARMUP_SLOTS = 20;
    public static final String LOG_DIR = "results_proposed";
    // ── Demand-aware mobility ────────────────────────────────────────────────
    // Probability a UAV steers toward a demand hotspot each slot
    public static final double HOTSPOT_STEER_PROB = 0.55;
    // Number of MDs sampled to estimate local demand density
    public static final int    HOTSPOT_SAMPLE_K   = 30;
    // Blend factor: how strongly to steer (0=random, 1=straight to hotspot)
    public static final double HOTSPOT_BLEND      = 0.70;
    // ── EDF scheduling ───────────────────────────────────────────────────────
    // EDF re-sort period (every N slots)
    public static final int    EDF_RESORT_PERIOD  = 1;   // every slot
    // ── Cooperative offloading ────────────────────────────────────────────────
    // If a UAV queue load ratio exceeds this, offer tasks to neighbors
    public static final double OFFLOAD_LOAD_THRESH = 0.80;
    // Max tasks offloaded per slot per UAV
    public static final int    OFFLOAD_MAX_TASKS   = 2;
    // ── Urgency priority boost in MCMF cost ──────────────────────────────────
    // Tasks with slack < this fraction of deadline get a cost reduction bonus
    public static final double URGENCY_SLACK_THRESH = 0.40;
    public static final double URGENCY_COST_BONUS   = 0.15;
}
