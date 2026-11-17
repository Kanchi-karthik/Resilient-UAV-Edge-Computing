package org.fog.test.proposed.coordination.secbba;

import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.SimulationRunner;

public class SECBBAMain {
    public static void main(String[] args) {
        SimulationRunner.run(ProposedParams.CoordinationMode.SECBBA, args);
    }
}
