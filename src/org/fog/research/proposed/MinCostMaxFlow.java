package org.fog.research.proposed;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class MinCostMaxFlow {
    public static class Edge {
        int to, rev, cap, originalCapacity;
        double cost;
        Edge(int to, int rev, int cap, double cost) {
            this.to = to;
            this.rev = rev;
            this.cap = cap;
            this.originalCapacity = cap;
            this.cost = cost;
        }
    }
    public final List<Edge>[] graph;
    @SuppressWarnings("unchecked")
    public MinCostMaxFlow(int n) {
        graph = new ArrayList[n];
        for (int i = 0; i < n; i++) {
            graph[i] = new ArrayList<>();
        }
    }
    public void addEdge(int s, int t, int cap, double cost) {
        Edge a = new Edge(t, graph[t].size(), cap, cost);
        Edge b = new Edge(s, graph[s].size(), 0, -cost);
        graph[s].add(a);
        graph[t].add(b);
    }
    public double minCostMaxFlow(int s, int t) {
        int n = graph.length;
        double totalCost = 0.0;
        double[] potential = new double[n];
        while (true) {
            double[] dist = new double[n];
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            dist[s] = 0.0;
            int[] prevNode = new int[n];
            int[] prevEdge = new int[n];
            boolean[] used = new boolean[n];
            for (int it = 0; it < n; it++) {
                int v = -1;
                for (int i = 0; i < n; i++) {
                    if (!used[i] && dist[i] < Double.POSITIVE_INFINITY &&
                            (v == -1 || dist[i] < dist[v])) {
                        v = i;
                    }
                }
                if (v == -1) break;
                used[v] = true;
                for (int ei = 0; ei < graph[v].size(); ei++) {
                    Edge e = graph[v].get(ei);
                    if (e.cap <= 0) continue;
                    double nd = dist[v] + e.cost + potential[v] - potential[e.to];
                    if (nd < dist[e.to]) {
                        dist[e.to] = nd;
                        prevNode[e.to] = v;
                        prevEdge[e.to] = ei;
                    }
                }
            }
            if (dist[t] == Double.POSITIVE_INFINITY) {
                break;
            }
            for (int i = 0; i < n; i++) {
                if (dist[i] < Double.POSITIVE_INFINITY) {
                    potential[i] += dist[i];
                }
            }
            int addFlow = Integer.MAX_VALUE;
            for (int v = t; v != s; v = prevNode[v]) {
                Edge e = graph[prevNode[v]].get(prevEdge[v]);
                addFlow = Math.min(addFlow, e.cap);
            }
            for (int v = t; v != s; v = prevNode[v]) {
                Edge e = graph[prevNode[v]].get(prevEdge[v]);
                e.cap -= addFlow;
                graph[v].get(e.rev).cap += addFlow;
                totalCost += addFlow * e.cost;
            }
        }
        return totalCost;
    }
}
