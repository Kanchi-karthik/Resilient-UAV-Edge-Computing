package org.fog.test.proposed.energy;

/**
 * EnergyCalculator provides analytical physics models for UAV computation,
 * communication, and propulsion energy consumption based on authoritative literature:
 * 
 * 1. Propulsion & Hovering: Zeng et al. (2019) - "Energy-Minimization for UAV-Enabled Mobile Edge Computing"
 * 2. Computation: Burd & Brodersen (1996) - CMOS Dynamic Power Model (P = \kappa * f^3)
 * 3. Communication: Heinzelman et al. (2000) - First-Order Radio Model with Free Space & Multipath fading
 */
public class EnergyCalculator {

    // - UAV Propulsion & Aerodynamics Constants (Zeng 2019) -
    private static final double P0 = 79.86;      // Blade profile power in hover (Watts)
    private static final double P1 = 88.63;      // Induced power in hover (Watts)
    private static final double U_TIP = 120.0;   // Tip speed of rotor blade (m/s)
    private static final double V0 = 4.03;       // Mean rotor induced velocity in hover (m/s)
    private static final double D0 = 0.6;        // Fuselage drag ratio
    private static final double RHO = 1.225;     // Air density (kg/m^3)
    private static final double S = 0.05;        // Rotor solidity
    private static final double A = 0.503;       // Rotor disc area (m^2)

    // - Radio Communication Constants (Heinzelman 2000) -
    private static final double E_ELEC = 50e-9;         // Transmitter/Receiver electronics energy (50 nJ/bit)
    private static final double EPS_FS = 10e-12;        // Free-space amplifier energy (10 pJ/bit/m^2)
    private static final double EPS_MP = 0.0013e-12;    // Multipath amplifier energy (0.0013 pJ/bit/m^4)
    private static final double CROSSOVER_DIST = 87.7;  // Distance threshold d_0 (m)

    // - CMOS Computation Constants (Burd 1996) -
    private static final double KAPPA = 1e-28;          // Effective switched capacitance coefficient

    /**
     * Calculates UAV instantaneous propulsion power requirements based on forward speed V (Zeng 2019 Eq. 7).
     * @param V Forward flight speed in m/s
     * @return Power in Watts
     */
    public static double calcPropulsionPower(double V) {
        V = Math.max(0.0, V);
        double term1 = P0 * (1.0 + (3.0 * V * V) / (U_TIP * U_TIP));
        double term2 = P1 * Math.sqrt(Math.sqrt(1.0 + Math.pow(V, 4) / (4.0 * Math.pow(V0, 4))) - (V * V) / (2.0 * V0 * V0));
        double term3 = 0.5 * D0 * RHO * S * A * Math.pow(V, 3);
        return term1 + term2 + term3;
    }

    /**
     * Calculates UAV hover power requirements (Zeng 2019 Eq. 8, special case V=0).
     * @return Power in Watts
     */
    public static double calcHoverPower() {
        return P0 + P1;
    }

    /**
     * Calculates energy consumed by CPU for processing task workload (Burd 1996).
     * E_comp = \kappa * f^2 * \omega_\tau
     * @param cpuCycles CPU cycles needed for task
     * @param cpuHz Operational CPU frequency in Hz
     * @return Energy in Joules
     */
    public static double calcComputeEnergy(double cpuCycles, double cpuHz) {
        // Burd 1996 Eq. (3.1): E_comp = - - f- - -_-
        // - = effective switched capacitance, f = CPU frequency (Hz), -_- = workload (cycles)
        return KAPPA * cpuHz * cpuHz * cpuCycles;
    }

    /**
     * Calculates transmission energy for sending data over 3D distance d (Heinzelman 2000).
     * @param bits Data size in bits
     * @param distance3D 3D Euclidean distance in meters
     * @return Energy in Joules
     */
    public static double calcTxEnergy(double bits, double distance3D) {
        if (distance3D < CROSSOVER_DIST) {
            return bits * E_ELEC + bits * EPS_FS * Math.pow(distance3D, 2);
        } else {
            return bits * E_ELEC + bits * EPS_MP * Math.pow(distance3D, 4);
        }
    }

    /**
     * Calculates reception energy for receiving data (Heinzelman 2000).
     * @param bits Data size in bits
     * @return Energy in Joules
     */
    public static double calcRxEnergy(double bits) {
        return bits * E_ELEC;
    }

    /**
     * 3D Euclidean distance helper.
     */
    public static double calc3DDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

