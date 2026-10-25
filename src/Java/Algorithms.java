import java.util.*;

public final class Algorithms {

    static Map<String, Object> dsu(Map<String, Object> input) {
        return dsu(Input.vertexCount(input), Input.operations(input));
    }

    static Map<String, Object> kruskal(Map<String, Object> input) {
        return kruskal(Input.vertexCount(input), Input.edges(input));
    }

    static Map<String, Object> prim(Map<String, Object> input) {
        return prim(Input.vertexCount(input), Input.edges(input));
    }

    static Map<String, Object> scc(Map<String, Object> input) {
        return scc(Input.vertexCount(input), Input.edges(input));
    }

    static Map<String, Object> topo(Map<String, Object> input) {
        return topo(Input.vertexCount(input), Input.edges(input));
    }

    static Map<String, Object> dag(Map<String, Object> input) {
        return dag(Input.vertexCount(input), Input.edges(input), Input.source(input));
    }

    private static final class Csr {
        final int[] start, to, eid;
        Csr(int n, int[][] edges, boolean directed, boolean reverse) {
            int[] deg = new int[n + 1];
            for (int[] e : edges) {
                int u = reverse ? e[1] : e[0], v = reverse ? e[0] : e[1];
                deg[u]++;
                if (!directed) deg[v]++;
            }
            start = new int[n + 1];
            for (int i = 0; i < n; i++) start[i + 1] = start[i] + deg[i];
            int[] pos = Arrays.copyOf(start, n + 1);
            to = new int[start[n]];
            eid = new int[start[n]];
            for (int i = 0; i < edges.length; i++) {
                int u = reverse ? edges[i][1] : edges[i][0], v = reverse ? edges[i][0] : edges[i][1];
                to[pos[u]] = v; eid[pos[u]++] = i;
                if (!directed) { to[pos[v]] = u; eid[pos[v]++] = i; }
            }
        }
    }

    private static final class Dsu {
        final int[] p, size;
        Dsu(int n) {
            p = new int[n]; size = new int[n];
            for (int i = 0; i < n; i++) { p[i] = i; size[i] = 1; }
        }
        int find(int x) {
            int r = x;
            while (p[r] != r) r = p[r];
            while (p[x] != r) { int nx = p[x]; p[x] = r; x = nx; }
            return r;
        }
        boolean union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra == rb) return false;
            if (size[ra] < size[rb]) { int t = ra; ra = rb; rb = t; }
            p[rb] = ra; size[ra] += size[rb];
            return true;
        }
    }

    public static Map<String, Object> dsu(int n, int[][] operations) {
        Dsu d = new Dsu(n);
        int comps = n, largest = n > 0 ? 1 : 0;
        int[][] states = new int[operations.length][];
        for (int i = 0; i < operations.length; i++) {
            int u = operations[i][0], v = operations[i][1];
            if (d.union(u, v)) {
                comps--;
                largest = Math.max(largest, d.size[d.find(u)]);
            }
            states[i] = new int[]{comps, largest};
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("states", states);
        return result;
    }

    private static final long OFF = 1_000_000_000L;
    private static long key(long w, int id) { return ((w + OFF) << 18) | id; }
    private static int idOf(long k) { return (int) (k & ((1 << 18) - 1)); }

    public static Map<String, Object> kruskal(int n, int[][] edges) {
        int m = edges.length;
        long[] keys = new long[m];
        for (int i = 0; i < m; i++) keys[i] = key(edges[i][2], i);
        Arrays.sort(keys);
        Dsu d = new Dsu(n);
        int[] chosen = new int[Math.max(0, n - 1)];
        int cnt = 0; long cost = 0;
        for (long k : keys) {
            int i = idOf(k);
            if (d.union(edges[i][0], edges[i][1])) { chosen[cnt++] = i; cost += edges[i][2]; }
        }
        return forest(cost, Arrays.copyOf(chosen, cnt), n - cnt);
    }

    private static Map<String, Object> forest(long cost, int[] ids, int comps) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("cost", cost); r.put("edge_ids", ids); r.put("components", comps);
        return r;
    }

    private static final class LongHeap {
        long[] a = new long[16]; int sz = 0;
        void push(long x) {
            if (sz == a.length) a = Arrays.copyOf(a, sz * 2);
            int i = sz++;
            while (i > 0) { int p = (i - 1) >> 1; if (a[p] <= x) break; a[i] = a[p]; i = p; }
            a[i] = x;
        }
        long pop() {
            long top = a[0], x = a[--sz];
            int i = 0;
            while (true) {
                int c = 2 * i + 1;
                if (c >= sz) break;
                if (c + 1 < sz && a[c + 1] < a[c]) c++;
                if (a[c] >= x) break;
                a[i] = a[c]; i = c;
            }
            if (sz > 0) a[i] = x;
            return top;
        }
    }

    public static Map<String, Object> prim(int n, int[][] edges) {
        Csr g = new Csr(n, edges, false, false);
        boolean[] seen = new boolean[n];
        int[] chosen = new int[Math.max(0, n - 1)];
        int cnt = 0, comps = 0; long cost = 0;
        LongHeap h = new LongHeap();
        for (int s = 0; s < n; s++) {
            if (seen[s]) continue;
            comps++;
            seen[s] = true;
            for (int k = g.start[s]; k < g.start[s + 1]; k++) h.push(key(edges[g.eid[k]][2], g.eid[k]));
            while (h.sz > 0) {
                int i = idOf(h.pop());
                int a = edges[i][0], b = edges[i][1];
                int v;
                if (!seen[a]) v = a; else if (!seen[b]) v = b; else continue;
                seen[v] = true;
                chosen[cnt++] = i; cost += edges[i][2];
                for (int k = g.start[v]; k < g.start[v + 1]; k++)
                    if (!seen[g.to[k]]) h.push(key(edges[g.eid[k]][2], g.eid[k]));
            }
        }
        return forest(cost, Arrays.copyOf(chosen, cnt), comps);
    }

    public static Map<String, Object> scc(int n, int[][] edges) {
        Csr g = new Csr(n, edges, true, false);

        boolean[] seen = new boolean[n];
        int[] ptr = Arrays.copyOf(g.start, n + 1);
        int[] fin = new int[n]; int fc = 0;
        int[] stack = new int[n];
        for (int s = 0; s < n; s++) {
            if (seen[s]) continue;
            seen[s] = true; int sp = 0; stack[sp++] = s;
            while (sp > 0) {
                int v = stack[sp - 1];
                if (ptr[v] < g.start[v + 1]) {
                    int w = g.to[ptr[v]++];
                    if (!seen[w]) { seen[w] = true; stack[sp++] = w; }
                } else { sp--; fin[fc++] = v; }
            }
        }

        Csr t = new Csr(n, edges, true, true);
        int[] comp = new int[n]; Arrays.fill(comp, -1);
        int label = 0;
        for (int idx = n - 1; idx >= 0; idx--) {
            int s = fin[idx];
            if (comp[s] != -1) continue;
            comp[s] = label; int sp = 0; stack[sp++] = s;
            while (sp > 0) {
                int v = stack[--sp];
                for (int k = t.start[v]; k < t.start[v + 1]; k++) {
                    int w = t.to[k];
                    if (comp[w] == -1) { comp[w] = label; stack[sp++] = w; }
                }
            }
            label++;
        }

        HashSet<Long> set = new HashSet<>();
        List<int[]> cond = new ArrayList<>();
        for (int[] e : edges) {
            int a = comp[e[0]], b = comp[e[1]];
            if (a != b && set.add((long) a * n + b)) cond.add(new int[]{a, b});
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("component", comp);
        r.put("condensation", cond.toArray(new int[0][]));
        return r;
    }

    private static int[] topoOrder(int n, Csr g) {
        byte[] color = new byte[n];
        int[] ptr = Arrays.copyOf(g.start, n + 1);
        int[] out = new int[n]; int oc = 0;
        int[] stack = new int[n];
        for (int s = 0; s < n; s++) {
            if (color[s] != 0) continue;
            color[s] = 1; int sp = 0; stack[sp++] = s;
            while (sp > 0) {
                int v = stack[sp - 1];
                if (ptr[v] < g.start[v + 1]) {
                    int w = g.to[ptr[v]++];
                    if (color[w] == 1) return null;
                    if (color[w] == 0) { color[w] = 1; stack[sp++] = w; }
                } else { color[v] = 2; sp--; out[oc++] = v; }
            }
        }
        for (int i = 0, j = n - 1; i < j; i++, j--) { int x = out[i]; out[i] = out[j]; out[j] = x; }
        return out;
    }

    public static Map<String, Object> topo(int n, int[][] edges) {
        int[] order = topoOrder(n, new Csr(n, edges, true, false));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("acyclic", order != null);
        r.put("order", order == null ? new int[0] : order);
        return r;
    }

    public static Map<String, Object> dag(int n, int[][] edges, int source) {
        Csr g = new Csr(n, edges, true, false);
        int[] order = topoOrder(n, g);
        Map<String, Object> r = new LinkedHashMap<>();
        if (order == null) {
            r.put("acyclic", false); r.put("distance", new Long[0]); r.put("parent_edge", new int[0]);
            return r;
        }
        Long[] dist = new Long[n];
        long[] d = new long[n]; boolean[] reach = new boolean[n];
        int[] parent = new int[n]; Arrays.fill(parent, -1);
        reach[source] = true;
        for (int u : order) {
            if (!reach[u]) continue;
            for (int k = g.start[u]; k < g.start[u + 1]; k++) {
                int i = g.eid[k], v = g.to[k];
                long nd = d[u] + edges[i][2];
                if (!reach[v] || nd < d[v]) { reach[v] = true; d[v] = nd; parent[v] = i; }
            }
        }
        for (int v = 0; v < n; v++) dist[v] = reach[v] ? Long.valueOf(d[v]) : null;
        r.put("acyclic", true); r.put("distance", dist); r.put("parent_edge", parent);
        return r;
    }

    public static int[] route(int source, int target, int[] parentEdge, int[][] edges) {
        if (target == source) return new int[]{source};
        if (parentEdge[target] == -1) return new int[0];
        int len = 1;
        for (int v = target; v != source; v = edges[parentEdge[v]][0]) len++;
        int[] path = new int[len];
        int v = target;
        for (int i = len - 1; i >= 0; i--) { path[i] = v; if (i > 0) v = edges[parentEdge[v]][0]; }
        return path;
    }
}
