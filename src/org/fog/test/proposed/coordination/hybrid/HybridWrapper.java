package org.fog.test.proposed.coordination.hybrid;

import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.SimulationRunner;

public final class HybridWrapper {
    public static void main(String[] args) {
        SimulationRunner.run(ProposedParams.CoordinationMode.HYBRID, args);
    }
}