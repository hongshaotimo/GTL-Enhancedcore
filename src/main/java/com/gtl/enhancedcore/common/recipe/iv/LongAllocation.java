package com.gtl.enhancedcore.common.recipe.iv;

import java.util.*;
import java.util.function.Predicate;

/** Integral allocation against a snapshot. Overlapping ingredients cannot double-spend stock. */
public final class LongAllocation {
    private LongAllocation() {}
    public record Supply<K>(K key, long amount, boolean consumable) {}
    public record Need<K>(Predicate<K> matches, long amount, boolean consumed) {}

    public static <K> Map<K, Long> plan(List<Supply<K>> stock, List<Need<K>> needs) {
        int source = stock.size() + needs.size(), sink = source + 1;
        List<List<Edge>> graph = new ArrayList<>();
        for (int i = 0; i <= sink; i++) graph.add(new ArrayList<>());
        long required = 0;
        for (int j = 0; j < needs.size(); j++) {
            Need<K> need = needs.get(j);
            if (need.amount < 0) throw new IllegalArgumentException("negative demand");
            required = Math.addExact(required, need.amount);
            add(graph, stock.size() + j, sink, need.amount);
        }
        Edge[][] transfers = new Edge[stock.size()][needs.size()];
        for (int i = 0; i < stock.size(); i++) {
            Supply<K> supply = stock.get(i);
            if (supply.amount < 0) throw new IllegalArgumentException("negative supply");
            add(graph, source, i, supply.amount);
            for (int j = 0; j < needs.size(); j++) {
                Need<K> need = needs.get(j);
                if ((!need.consumed || supply.consumable) && need.matches.test(supply.key))
                    transfers[i][j] = add(graph, i, stock.size() + j, Math.min(supply.amount, need.amount));
            }
        }
        long flow = 0;
        while (flow < required) {
            Edge[] path = new Edge[graph.size()];
            int[] parent = new int[graph.size()];
            Arrays.fill(parent, -1);
            parent[source] = source;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int node = queue.remove();
                for (Edge edge : graph.get(node)) if (edge.left > 0 && parent[edge.to] < 0) {
                    path[edge.to] = edge;
                    parent[edge.to] = node;
                    queue.add(edge.to);
                }
            }
            if (parent[sink] < 0) return null;
            long amount = required - flow;
            for (int node = sink; node != source; node = parent[node]) amount = Math.min(amount, path[node].left);
            for (int node = sink; node != source; node = parent[node]) {
                Edge edge = path[node];
                edge.left -= amount;
                edge.reverse.left += amount;
            }
            flow += amount;
        }
        Map<K, Long> consumed = new LinkedHashMap<>();
        for (int i = 0; i < stock.size(); i++) for (int j = 0; j < needs.size(); j++) {
            Edge edge = transfers[i][j];
            if (edge != null && needs.get(j).consumed && edge.reverse.left > 0)
                consumed.merge(stock.get(i).key, edge.reverse.left, Math::addExact);
        }
        return consumed;
    }

    private static Edge add(List<List<Edge>> graph, int from, int to, long capacity) {
        Edge forward = new Edge(to, capacity), reverse = new Edge(from, 0);
        forward.reverse = reverse;
        reverse.reverse = forward;
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
        return forward;
    }
    private static final class Edge {
        final int to;
        long left;
        Edge reverse;
        Edge(int to, long left) { this.to = to; this.left = left; }
    }
}
