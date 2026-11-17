package org.fog.research.baseline;
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
    // Naive Bayes
    public static final double NB_PRIOR_SUCCESS = 0.60;
    public static final double NB_MIN_STD = 1e-3;
    // Online reliability: Beta prior
    public static final double REL_INIT_ALPHA = 3.0;
    public static final double REL_INIT_BETA = 2.0;
    // Blend final probability = w1*NB + w2*Reliability
    public static final double W_NB_MODEL = 0.70;
    public static final double W_ONLINE_RELIABILITY = 0.30;
    // Assignment threshold
    public static final double MIN_ASSIGN_PROBABILITY = 0.15;
    // Cost weights
    public static final double W_COST_PROB   = 0.25;
    public static final double W_COST_LOAD   = 0.25;
    public static final double W_COST_ENERGY = 0.15;
    public static final double W_COST_DELAY  = 0.35;
    public static final double UAV_ENERGY_BUDGET_J = 200000.0;
    public static final int WARMUP_SLOTS = 20;
    public static final String LOG_DIR = "results_proposed";
}
