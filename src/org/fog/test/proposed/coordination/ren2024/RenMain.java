package org.fog.test.proposed.coordination.ren2024;

import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.SimulationRunner;

public final class RenMain {
    public static void main(String[] args) {
        SimulationRunner.run(ProposedParams.CoordinationMode.GOSSIP_REN_2024, args);
    }
}
