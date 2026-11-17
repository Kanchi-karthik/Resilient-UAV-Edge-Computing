package org.fog.test.proposed.coordination.ren2024;

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

    /**
     * Copy constructor.
     */
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

    /**
     * Set the bit indicating UAV 'fromUav' sent/received packet regarding UAV 'targetUav'.
     */
    public void setPacketStatus(int fromUav, int targetUav, boolean received) {
        if (fromUav >= 0 && fromUav < numUavs && targetUav >= 0 && targetUav < numUavs) {
            int index = fromUav * numUavs + targetUav;
            bits.set(index, received);
        }
    }

    /**
     * Check if UAV 'fromUav' has received packet from 'targetUav'.
     */
    public boolean getPacketStatus(int fromUav, int targetUav) {
        if (fromUav >= 0 && fromUav < numUavs && targetUav >= 0 && targetUav < numUavs) {
            int index = fromUav * numUavs + targetUav;
            return bits.get(index);
        }
        return false;
    }

    /**
     * Update Bitgraph from a local wait-for graph mapping (childTask -> parentTask / sourceUav).
     */
    public void updateFromWaitForGraph(int localUavId, Map<Integer, Integer> waitForMap) {
        // Local UAV knows its own packet state
        setPacketStatus(localUavId, localUavId, true);

        // Update bits based on active dependency / wait-for items
        for (Map.Entry<Integer, Integer> entry : waitForMap.entrySet()) {
            int child = entry.getKey();
            int parent = entry.getValue();
            int srcUav = Math.abs(parent) % numUavs;
            setPacketStatus(localUavId, srcUav, true);
        }
    }

    /**
     * Merge another Bitgraph into this Bitgraph using bitwise OR (Step 1-6 in Ren 2024).
     */
    public void merge(Bitgraph incoming) {
        if (incoming != null && incoming.numUavs == this.numUavs) {
            this.bits.or(incoming.bits);
        }
    }

    /**
     * Returns the filling ratio b_t in [0.0, 1.0].
     * b_t = (number of 1s) / (N^2)
     */
    public double getFillingPercentage() {
        int cardinality = bits.cardinality();
        return (double) cardinality / getTotalBits();
    }

    /**
     * Computes Jaccard Similarity between this Bitgraph and another UAV's Bitgraph.
     * Similarity = |this ∩ other| / |this ∪ other|
     */
    public double similarity(Bitgraph other) {
        if (other == null || other.numUavs != this.numUavs) return 0.0;
        BitSet intersection = (BitSet) bits.clone();
        intersection.and(other.bits);

        BitSet union = (BitSet) bits.clone();
        union.or(other.bits);

        int uni = union.cardinality();
        if (uni == 0) return 1.0;
        return (double) intersection.cardinality() / uni;
    }

    /**
     * Returns true if all bits are set (Bitgraph is full).
     */
    public boolean isFull() {
        return bits.cardinality() == getTotalBits();
    }

    public void clear() {
        bits.clear();
    }
}
