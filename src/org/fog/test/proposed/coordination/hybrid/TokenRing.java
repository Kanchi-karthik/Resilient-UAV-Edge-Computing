package org.fog.test.proposed.coordination.hybrid;

import java.util.List;
import org.fog.test.proposed.DeadlockRecoverer;
import org.fog.test.proposed.DeadlockReport;
import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.ProposedUAVState;

/**
 * Enhanced Token Ring with Energy-Weighted Passing and Adaptive Circulation.
 */
public class TokenRing {
    private final List<ProposedUAVState> uavs;
    private final TokenState tokenState;

    public TokenRing(List<ProposedUAVState> uavs) {
        this.uavs = uavs;
        this.tokenState = new TokenState(uavs.size());
    }

    public int getTokenHolderIndex() {
        return tokenState.getTokenHolderIndex();
    }

    public TokenState getTokenState() {
        return tokenState;
    }

    public void circulateToken(boolean triggerFired) {
        if (!triggerFired) return;

        int currentHolderIdx = tokenState.getTokenHolderIndex();
        ProposedUAVState sender = uavs.get(currentHolderIdx);

        // 1. Update current holder state in token
        tokenState.updateUavState(currentHolderIdx, sender.getResidualEnergyRatio(), sender.getQueueSize());

        // 2. Consume coordination energy for passing token (1 hop = O(1))
        sender.addCoordinationEnergy(ProposedParams.COORDINATION_ENERGY_J);
        tokenState.addEnergy(ProposedParams.COORDINATION_ENERGY_J);

        // 3. Energy-aware weighted token passing: 70% residual energy, 30% load availability
        int nextIndex = findOptimalNextHolder();

        // 4. Update token holder index and circulation count
        tokenState.setTokenHolderIndex(nextIndex);
        tokenState.incrementCirculation();

        // 5. Adaptive circulation interval
        double avgLoad = computeAverageLoad();
        int dynamicInterval = (int) (3 + (1.0 - avgLoad) * 4);
        tokenState.setDynamicInterval(dynamicInterval);
    }

    private int findOptimalNextHolder() {
        int currentHolder = tokenState.getTokenHolderIndex();
        double bestScore = -1.0;
        int bestIdx = (currentHolder + 1) % uavs.size();

        for (int i = 0; i < uavs.size(); i++) {
            if (i == currentHolder) continue;

            ProposedUAVState u = uavs.get(i);
            double energyScore = u.getResidualEnergyRatio();
            double loadScore = 1.0 - u.getLoadRatioNow(1.0);

            // Weighted combination: 70% energy preservation, 30% load balancing
            double score = 0.70 * energyScore + 0.30 * loadScore;

            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private double computeAverageLoad() {
        if (uavs.isEmpty()) return 0.0;
        double sum = 0.0;
        for (ProposedUAVState u : uavs) {
            sum += u.getLoadRatioNow(1.0);
        }
        return sum / uavs.size();
    }

    public boolean executeRecovery(DeadlockReport report, DeadlockRecoverer recoverer, double clock) {
        tokenState.incrementDetected();
        int holderIdx = tokenState.getTokenHolderIndex();
        System.out.println("[TOKEN RING] Token Holder UAV-" + holderIdx + " executing recovery for " + report.type + " | " + tokenState);

        boolean success = recoverer.recover(report, clock);
        if (success) {
            tokenState.incrementResolved();
        }
        return success;
    }
}
