package org.fog.test.proposed.coordination.gossip;

import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.SimulationRunner;

public final class GossipMain {
    public static void main(String[] args) {
        SimulationRunner.run(ProposedParams.CoordinationMode.GOSSIP_REN_2024, args);
    }
}