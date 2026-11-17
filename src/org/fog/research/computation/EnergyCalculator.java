package org.fog.research.computation;

import org.fog.research.utils.Constants;

public class EnergyCalculator {

    // Eq. (10): E_trans = Gamma * P_trans
    public static double calculateTransmissionEnergy(double transmissionLatencySec) {
        return transmissionLatencySec * Constants.UAV_TRANSMISSION_POWER_WATT;
    }

    // Eq. (12): E_comp = E_tau * P_comp
    public static double calculateComputationEnergy(double computationLatencySec) {
        return computationLatencySec * Constants.UAV_COMPUTATION_POWER_WATT;
    }

    // Eq. (14): E_move = F_uk * P_move
    public static double calculateMovementEnergy(double flightTimeSec) {
        return flightTimeSec * Constants.UAV_PROPEL_POWER_WATT;
    }
}
