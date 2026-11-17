package org.fog.test.proposed.coordination.wu2025;

import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.SimulationRunner;

public class WuMain {
    public static void main(String[] args) {
        SimulationRunner.run(ProposedParams.CoordinationMode.WU_2025, args);
    }
}
