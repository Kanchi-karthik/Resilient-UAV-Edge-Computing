package org.fog.test.proposed.coordination.gossip;

import java.util.BitSet;
import java.util.Map;

/**
 * Implements the Bitgraph data structure from Ren et al. (2024):
 * "Intelligent Adaptive Gossip-Based Broadcast Protocol for UAV-MEC Using Multi-Agent DRL" (IEEE TMC 2024).
 *
 * Bitgraph is a customized binary string of length N^2 (where N = number of UAVs in cluster).
 * It represents the virtual view of packet creation, transmission, and historical reception
 * across all UAVs to eliminate redundant message propagation.
 */
public class Bitgraph {
    private final int numUavs;
    private final BitSet bits;

    public Bitgraph(int numUavs) {
        this.numUavs = numUavs;
        this.bits = new BitSet(numUavs * numUavs);
    }

    public Bitgraph(Bitgraph other) {
        this.numUavs = other.numUavs;
        this.bits = (BitSet) other.bits.clone();
    }

    public int getNumUavs() {
        return numUavs;
    }

    public int getTotalBits() {
        return numUavs * numUavs;
    }

    public void setPacketStatus(int fromUav, int targetUav, boolean received) {
        if (fromUav >= 0 && fromUav < numUavs && targetUav >= 0 && targetUav < numUavs) {
            int index = fromUav * numUavs + targetUav;
            bits.set(index, received);
        }
    }

    public boolean getPacketStatus(int fromUav, int targetUav) {
        if (fromUav >= 0 && fromUav < numUavs && targetUav >= 0 && targetUav < numUavs) {
            int index = fromUav * numUavs + targetUav;
            return bits.get(index);
        }
        return false;
    }

    public void updateFromWaitForGraph(int localUavId, Map<Integer, Integer> waitForMap) {
        setPacketStatus(localUavId, localUavId, true);
        for (Map.Entry<Integer, Integer> entry : waitForMap.entrySet()) {
            int child = entry.getKey();
            int parent = entry.getValue();
            int srcUav = Math.abs(parent) % numUavs;
            setPacketStatus(localUavId, srcUav, true);
        }
    }

    public void merge(Bitgraph incoming) {
        if (incoming != null && incoming.numUavs == this.numUavs) {
            this.bits.or(incoming.bits);
        }
    }

    public double getFillingPercentage() {
        int cardinality = bits.cardinality();
        return (double) cardinality / getTotalBits();
    }

    public double similarity(Bitgraph other) {
        if (other == null || other.numUavs != this.numUavs) return 0.0;
        BitSet intersection = (BitSet) bits.clone();
        intersection.and(other.bits);
        BitSet union = (BitSet) bits.clone();
        union.or(other.bits);
        int unionSize = union.cardinality();
        if (unionSize == 0) return 1.0;
        return (double) intersection.cardinality() / unionSize;
    }
}
