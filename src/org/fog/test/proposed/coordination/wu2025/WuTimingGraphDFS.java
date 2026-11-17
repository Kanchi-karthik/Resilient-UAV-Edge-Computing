package org.fog.test.proposed.coordination.wu2025;

import java.util.*;

/**
 * WuTimingGraphDFS - Timing Precedence Graph Deadlock Filter (Wu et al., Scientific Reports 2025).
 * 
 * Constructs graph T = (V, E) where:
 *  - Execution edges: Order of tasks on the same UAV.
 *  - Precedence edges: C -> R -> V chronological constraints for the same target.
 * 
 * Returns true if the chromosome contains a deadlock cycle.
 */
public class WuTimingGraphDFS {

    public static boolean checkChromosomeDeadlock(WuChromosome chrom) {
        int n = chrom.targetId.length;
        Map<Integer, List<Integer>> adj = new HashMap<>();

        // 1. Execution edges: consecutive tasks on the same UAV
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (chrom.uavId[i] == chrom.uavId[j]) {
                    adj.computeIfAbsent(i, k -> new ArrayList<>()).add(j);
                    break;
                }
            }
        }

        // 2. Precedence constraint edges: C -> R -> V for the same target
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && chrom.targetId[i] == chrom.targetId[j]) {
                    if (chrom.taskType[j] == chrom.taskType[i] + 1) {
                        adj.computeIfAbsent(i, k -> new ArrayList<>()).add(j);
                    }
                }
            }
        }

        // 3. DFS cycle detection
        Set<Integer> visited = new HashSet<>();
        Set<Integer> recStack = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (hasCycle(i, adj, visited, recStack)) return true;
        }
        return false;
    }

    private static boolean hasCycle(int node, Map<Integer, List<Integer>> adj,
                                    Set<Integer> visited, Set<Integer> recStack) {
        if (recStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        recStack.add(node);

        for (int neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            if (hasCycle(neighbor, adj, visited, recStack)) return true;
        }

        recStack.remove(node);
        return false;
    }
}
