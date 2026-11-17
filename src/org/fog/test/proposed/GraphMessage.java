package org.fog.test.proposed;

import java.util.HashMap;
import java.util.Map;

/**
 * GraphMessage - Message exchanged between UAVs during Gossip coordination.
 * Contains the sender's local wait-for graph, queue state, and energy ratio.
 * Used for distributed deadlock detection via graph merging across neighbours.
 *
 * Energy accounting: message size is computed dynamically from edge count
 * to give accurate TX/RX energy values (Heinzelman 2000).
 */
public final class GraphMessage {
    /** Header overhead: senderIndex(32) + timestamp(64) + queueSize(32) + energyRatio(64) = 192 bits */
    private static final int HEADER_BITS = 192;
    /** Per-edge cost: child-taskId(32) + parent-taskId(32) = 64 bits per edge */
    private static final int BITS_PER_EDGE = 64;

    private final int senderIndex;
    private final Map<Integer, Integer> graph;   // child -> parent (local waitFor snapshot)
    private final int queueSize;
    private final double energyRatio;
    private final double timestamp;

    public GraphMessage(int senderIndex,
                        Map<Integer, Integer> graph,
                        int queueSize,
                        double energyRatio,
                        double timestamp) {
        this.senderIndex = senderIndex;
        this.graph       = new HashMap<>(graph);   // defensive copy
        this.queueSize   = queueSize;
        this.energyRatio = energyRatio;
        this.timestamp   = timestamp;
    }

    public int getSenderIndex()              { return senderIndex; }
    public Map<Integer, Integer> getGraph()  { return graph; }
    public int getQueueSize()               { return queueSize; }
    public double getEnergyRatio()          { return energyRatio; }
    public double getTimestamp()            { return timestamp; }

    /**
     * Dynamic message size in bits.
     * Larger wait-for graphs produce larger messages → higher energy cost.
     * Minimum is the header-only message (empty graph).
     */
    public int getMessageSizeInBits() {
        return HEADER_BITS + graph.size() * BITS_PER_EDGE;
    }

    @Override
    public String toString() {
        return "GraphMessage{sender=" + senderIndex
                + ", edges=" + graph.size()
                + ", queue=" + queueSize
                + ", energy=" + String.format("%.2f", energyRatio)
                + ", bits=" + getMessageSizeInBits() + "}";
    }
}
