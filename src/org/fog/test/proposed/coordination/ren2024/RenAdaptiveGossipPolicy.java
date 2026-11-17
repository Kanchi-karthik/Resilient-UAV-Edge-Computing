package org.fog.test.proposed.coordination.ren2024;

import java.util.*;
import org.fog.test.proposed.DeadlockRecoverer;
import org.fog.test.proposed.DeadlockReport;
import org.fog.test.proposed.GraphMessage;
import org.fog.test.proposed.ProposedParams;
import org.fog.test.proposed.ProposedUAVState;
import org.fog.test.proposed.coordination.CoordinationPolicy;
import org.fog.test.proposed.energy.EnergyCalculator;

/**
 * Ren et al. (2024) - "Intelligent Adaptive Gossip-Based Broadcast Protocol for UAV-MEC Using Multi-Agent DRL"
 * (IEEE Transactions on Mobile Computing, Vol. 23, No. 6, June 2024).
 *
 * Core Features:
 *  1. Bitgraph virtual view tracking to eliminate message redundancy.
 *  2. Adaptive forwarding probability (p_g) based on residual energy, queue load, and Bitgraph filling.
 *  3. Intelligent neighbor selection (N_nb) prioritizing neighbors with highest information gain (lowest Bitgraph overlap).
 */
public class RenAdaptiveGossipPolicy implements CoordinationPolicy {
    private final Random rand = new Random();
    private final Map<Integer, Bitgraph> uavBitgraphs = new HashMap<>();
    private final Map<String, Integer> consensusRounds = new HashMap<>();
    private int currentSlot = 0;

    // Weight parameters based on Ren et al. (2024) formulation
    private static final double ALPHA_ENERGY = 0.30;   // Residual energy weight
    private static final double BETA_LOAD    = 0.30;   // Queue load availability weight (1 - load)
    private static final double GAMMA_FILL   = 0.15;   // Bitgraph filling percentage weight
    private static final double MIN_PROB     = 0.15;   // Minimum gossip probability
    private static final double MAX_PROB     = 0.95;   // Maximum gossip probability

    @Override
    public String getName() {
        return "GOSSIP_REN_2024";
    }

    @Override
    public void beginSlot(int slot, double clock, List<ProposedUAVState> uavs) {
        this.currentSlot = slot;
        int n = uavs.size();

        // 1. Initialize or refresh Bitgraphs
        for (ProposedUAVState uav : uavs) {
            uavBitgraphs.putIfAbsent(uav.getIndex(), new Bitgraph(n));
            Bitgraph bg = uavBitgraphs.get(uav.getIndex());
            bg.updateFromWaitForGraph(uav.getIndex(), uav.getLocalWaitForCopy());
        }

        // 2. Perform Adaptive Gossip every GOSSIP_INTERVAL_SLOTS slots
        if (slot % ProposedParams.GOSSIP_INTERVAL_SLOTS == 0) {
            for (ProposedUAVState sender : uavs) {
                Bitgraph senderBg = uavBitgraphs.get(sender.getIndex());
                if (senderBg == null) continue;

                // A. Compute Adaptive Forwarding Probability (p_g)
                double energyRatio = sender.getResidualEnergyRatio();
                double loadRatio = sender.getLoadRatioNow(1.0);
                double fillRatio = senderBg.getFillingPercentage();

                double p_g = ALPHA_ENERGY * energyRatio
                           + BETA_LOAD * (1.0 - loadRatio)
                           + GAMMA_FILL * fillRatio;
                p_g = Math.max(MIN_PROB, Math.min(MAX_PROB, p_g));

                // Decide whether to forward based on adaptive probability
                if (rand.nextDouble() > p_g) {
                    continue; // Skip gossip broadcast this round
                }

                // B. Select Best Forwarding Neighbor (N_nb) using Bitgraph Similarity & Load
                ProposedUAVState bestNeighbor = selectBestNeighbor(sender, senderBg);
                if (bestNeighbor == null) continue;

                // C. Transmit GraphMessage + Bitgraph update
                GraphMessage msg = new GraphMessage(
                        sender.getIndex(),
                        sender.getLocalWaitForCopy(),
                        sender.getQueueSize(),
                        energyRatio,
                        clock
                );

                // Receive and merge Bitgraphs
                Bitgraph receiverBg = uavBitgraphs.get(bestNeighbor.getIndex());
                if (receiverBg != null) {
                    receiverBg.merge(senderBg);
                    senderBg.merge(receiverBg);
                }

                bestNeighbor.receiveGraphMessage(msg);

                // D. Energy Accounting for Adaptive Gossip Control Broadcast
                int msgBits = msg.getMessageSizeInBits() + (n * n); // Payload + Bitgraph string bits
                double distance = sender.distanceToUav(bestNeighbor);
                double txCost = EnergyCalculator.calcTxEnergy(msgBits, distance);
                double rxCost = EnergyCalculator.calcRxEnergy(msgBits);

                sender.addCoordinationEnergy(txCost);
                bestNeighbor.addCoordinationEnergy(rxCost);
            }
        }
    }

    /**
     * Intelligent neighbor selection (Ren et al. 2024):
     * Chooses neighbor with lowest Bitgraph overlap (highest new info gain) and lowest queue load.
     */
    private ProposedUAVState selectBestNeighbor(ProposedUAVState sender, Bitgraph senderBg) {
        List<ProposedUAVState> neighbors = sender.getNeighbors();
        if (neighbors.isEmpty()) return null;

        ProposedUAVState best = null;
        double bestScore = -Double.MAX_VALUE;

        for (ProposedUAVState nb : neighbors) {
            Bitgraph nbBg = uavBitgraphs.get(nb.getIndex());
            double similarity = (nbBg != null) ? senderBg.similarity(nbBg) : 0.0;
            double loadRatio = nb.getLoadRatioNow(1.0);
            double energyRatio = nb.getResidualEnergyRatio();

            // Score formulation: high energy, low queue load, low similarity (high info gain)
            double infoGain = 1.0 - similarity;
            double score = 0.40 * infoGain + 0.35 * (1.0 - loadRatio) + 0.25 * energyRatio;

            if (score > bestScore) {
                bestScore = score;
                best = nb;
            }
        }
        return (best != null) ? best : neighbors.get(rand.nextInt(neighbors.size()));
    }

    @Override
    public boolean shouldDetect(List<ProposedUAVState> uavs, int completedThisSlot) {
        // Trigger deadlock check at gossip periodic interval
        return currentSlot % ProposedParams.GOSSIP_INTERVAL_SLOTS == 0;
    }

    @Override
    public boolean recover(DeadlockReport report, DeadlockRecoverer recoverer, double clock) {
        // Ren et al. adaptive consensus delay tracking (c = GOSSIP_CONSENSUS_MIN_ROUNDS)
        String key = report.type + "_" + canonicalIds(report.involvedTaskIds);
        int rounds = consensusRounds.getOrDefault(key, 0) + 1;

        if (rounds < ProposedParams.GOSSIP_CONSENSUS_MIN_ROUNDS) {
            consensusRounds.put(key, rounds);
            return false;
        }

        consensusRounds.remove(key);
        return recoverer.recover(report, clock);
    }

    @Override
    public void endSlot() {
        // Slot cleanup if necessary
    }

    private static String canonicalIds(Set<Integer> ids) {
        List<Integer> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        return sorted.toString();
    }
}
