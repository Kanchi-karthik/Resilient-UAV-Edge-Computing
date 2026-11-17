package org.fog.test.proposed;

public class ProposedParams {
    // - Environment -
    public static final int AREA_X = 2000;
    public static final int AREA_Y = 2000;
    /** Base number of mobile devices at maximum task load (3000 tasks). */
    public static final int NUM_MDS_BASE = 800;
    /**
     * Returns the number of MDs to place for a given task count.
     * Scales linearly from 200 (at 500 tasks) to 800 (at 3000 tasks) so that
     * each task-count experiment has a visually distinct coverage topology.
     * Formula: max(200, round(NUM_MDS_BASE * taskCount / 3000))
     */
    public static int getNumMds(int taskCount) {
        return Math.max(200, (int) Math.round(NUM_MDS_BASE * (double) taskCount / 3000.0));
    }
    public static final double UAV_ALTITUDE_M = 120.0;
    public static final double UAV_SPEED_MPS = 6.94;
    public static final double UAV_UAV_RANGE_M = 900.0;

    // - Computing & Tasks -
    public static double CPU_HZ = 1e9;              // Can be changed per experiment
    public static double LAMBDA_TASKS = 25.0;       // Task arrival rate (Poisson) – generates ~1000 user tasks in ~40 slots
    public static final int TASK_SET_LENGTH = 1000;
    public static final double P_DEP = 0.30;        // Probability of dependent tasks
    public static final int DEP_LEN = 3;            // Chain length

    public static final int TASK_DEADLINE_MIN_SEC   = 5;  // 5s min deadline (literature: 5-8s range)
    public static final int TASK_DEADLINE_RANGE_SEC = 3;  // max deadline = 5+3 = 8s

    public static final double MD_SPEED_MIN = 1.0;
    public static final double MD_SPEED_MAX = 3.0;

    // - Naive Bayes (feasibility estimator) -
    public static final double NB_PRIOR_SUCCESS = 0.65;
    public static final double NB_MIN_STD = 1e-3;

    // - Beta prior for online reliability -
    public static final double REL_INIT_ALPHA = 2.0;
    public static final double REL_INIT_BETA = 2.0;

    // - Blend weights (NB + Reliability) -
    public static final double W_NB_MODEL = 0.65;
    public static final double W_ONLINE_RELIABILITY = 0.35;

    // - Assignment threshold -
    public static final double MIN_ASSIGN_PROBABILITY = 0.12;

    // - MCMF cost weights -
    public static final double W_COST_PROB = 0.15;
    public static final double W_COST_LOAD = 0.15;
    public static final double W_COST_ENERGY = 0.05;
    public static final double W_COST_DELAY = 0.65;

    // - UAV energy budget -
    public static final double UAV_ENERGY_BUDGET_J = 200000.0;

    // - Simulation control -
    public static final int WARMUP_SLOTS = 0;
    public static final int MAX_SLOTS = 200;

    // ── Task sweep for automatic multi-count simulation ─────────────────────────
    /**
     * Each ProposedMain run automatically loops over these task counts.
     * Results are stored in tasks_500/, tasks_1000/, … sub-folders per run.
     * Must match GraphConstants.TASK_SWEEP.
     */
    public static final int[] TASK_SWEEP = { 500, 1000, 1500, 2000, 2500, 3000 };

    // - Modes & result directories -
    public enum CoordinationMode {
        HYBRID,
        GOSSIP_REN_2024,
        SECBBA,
        WU_2025
    }

    public static CoordinationMode MODE = CoordinationMode.HYBRID;
    public static final String TEST_RESULTS_ROOT = "test_results";
    public static String LOG_DIR = TEST_RESULTS_ROOT + java.io.File.separator + "hybrid" + java.io.File.separator + "raw";

    public static void configureResultDirectory(CoordinationMode mode) {
        MODE = mode;
        String folderName = mode.name().toLowerCase();
        if (mode == CoordinationMode.GOSSIP_REN_2024) folderName = "gossip_ren";
        else if (mode == CoordinationMode.SECBBA) folderName = "secbba";
        else if (mode == CoordinationMode.WU_2025) folderName = "wu2025";

        LOG_DIR = TEST_RESULTS_ROOT + java.io.File.separator + folderName
                + java.io.File.separator + "raw";
    }

    public static void configureResultDirectory(CoordinationMode mode, String folderName) {
        MODE = mode;
        LOG_DIR = TEST_RESULTS_ROOT + java.io.File.separator + folderName
                + java.io.File.separator + "raw";
    }

    public static String getSummaryDir() {
        java.io.File raw = new java.io.File(LOG_DIR);
        java.io.File modeDir = raw.getParentFile();
        return (modeDir == null)
                ? TEST_RESULTS_ROOT + java.io.File.separator + "summary"
                : new java.io.File(modeDir, "summary").getPath();
    }

    // - Mobility & offloading -
    public static final double HOTSPOT_STEER_PROB = 0.55;
    public static final int HOTSPOT_SAMPLE_K = 30;
    public static final double HOTSPOT_BLEND = 0.70;

    public static final double OFFLOAD_LOAD_THRESH = 0.80;
    public static final int OFFLOAD_MAX_TASKS = 2;

    // - Urgency boost -
    public static final double URGENCY_SLACK_THRESH = 0.40;
    public static final double URGENCY_COST_BONUS = 0.15;

    // - Deadlock injection (Hybrid) -
    public static final boolean ENABLE_DEADLOCK_INJECTION = true;
    public static final double P_DEADLOCK = 0.25;
    public static final int MAX_WAIT_SLOTS = 3;
    public static final boolean STOP_ON_DEADLOCK = false;
    public static final boolean PREVENT_DEADLOCKS_PROACTIVELY = false;

    // - Random injection -
    public static final boolean ENABLE_RANDOM_INJECTION = true;
    public static final double P_INJECTION_PER_SLOT = 0.30;      // 30% per slot — enough to reach all 7 types
    public static final int MIN_INJECTION_GAP = 1;               // 1 slot minimum between injections
    public static final int MAX_INJECTIONS_PER_RUN = 42;         // 7 types × 6 cycles = covers full taxonomy

    // - Energy logging -
    public static final boolean ENABLE_ENERGY_LOGGING = true;
    public static final double COORDINATION_ENERGY_J = 0.002;

    // - Detection trigger -
    public static final double ENERGY_CRITICAL_THRESHOLD = 0.20;

    // - Gossip baseline constants -
    public static final int GOSSIP_INTERVAL_SLOTS = 5;           // T-periodic interval (T=5 slots)
    public static final double GOSSIP_MESSAGE_BITS = 512.0;
    public static final int GOSSIP_CONSENSUS_MIN_ROUNDS = 3;     // c = network diameter (10-UAV grid → 3 hops)

    // - CBBA scheduler parameters (Hybrid) -
    public static final int CBBA_CONSENSUS_ROUNDS = 3;
    public static final double CBBA_ENERGY_PER_ROUND_J = 0.005;

    // - Token ring grace period -
    public static final double TOKEN_PREEMPTION_DELAY_SLOTS = 2.0;
}
