package org.fog.research.utils;

public final class Constants {

    private Constants() {
    }

    // Environment Configuration
    public static final double ENVIRONMENT_SIZE_X = 2000.0; // 2 km
    public static final double ENVIRONMENT_SIZE_Y = 2000.0; // 2 km
    public static final double UAV_ALTITUDE = 100.0;        // Fixed flight altitude in meters

    // UAV Physical Limits & Power Models (Table III)
    public static final double UAV_MAX_SPEED = 25.0 * 1000.0 / 3600.0; // 25 km/h in m/s = ~6.94 m/s
    public static final double UAV_PROPEL_POWER_WATT = 7.0;             // P_move = 7 W
    public static final double UAV_COMPUTATION_POWER_WATT = 5.0;        // P_comp = 5 W
    public static final double UAV_TRANSMISSION_POWER_WATT = 1.0;       // P_trans = 1 W
    public static final double UAV_BATTERY_CAPACITY_JOULES = 50000.0;   // UAV energy budget
    
    // Communication & Propagation Parameters
    public static final double BANDWIDTH_HZ = 50e6;                     // 50 MHz
    public static final double THERMAL_NOISE_WATT = 1e-9;               // Thermal noise power
    public static final double XI = 1.0;                                // Transmission rate coefficient ξ = 1.0
    public static final double LOS_PATH_LOSS_CONSTANT = 1.0;
    public static final double NLOS_PATH_LOSS_CONSTANT = 20.0;
    public static final double LOS_PATH_LOSS_EXPONENT = 2.0;
    public static final double NLOS_PATH_LOSS_EXPONENT = 3.5;
    public static final double SIGMA = 9.61;                            // LoS probability parameter σ = 9.61
    public static final double LAMBDA = 0.16;                           // LoS probability parameter λ = 0.16

    // Task & Computation defaults
    public static final double DEFAULT_CPU_CAPACITY_HZ = 100e6;         // 100 MHz
    public static final double DELTA_L = 0.8;                           // Load balancing threshold Δl = 0.8
    
    // Time slots
    public static final double SIMULATION_TIME_STEP_SEC = 1.0;          // Time slot interval
}
