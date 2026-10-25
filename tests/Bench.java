import java.util.*;

/**
 * Benchmark of Kruskal vs Prim on the SAME graphs.
 * Timed region: one call Algorithms.kruskal(n, edges) / Algorithms.prim(n, edges) with the
 * edge list already in memory (int[][]). It includes everything the method itself does
 * (sorting or heap, building adjacency lists, DSU, building the result map).
 * Not timed: compilation, JSON parsing, output, graph generation, result verification.
 * Timer: System.nanoTime(). Graph for size n: new Random(SEED + n), weights in [-1e9, 1e9].
 */
public class Bench {
    static final long SEED = 20261025L;
    static final int WARMUP = 5;
    static final int RUNS = 7;

    static int[][] generate(int n, int m, long seed) {
        Random rnd = new Random(seed);
        int[][] edges = new int[m][];
        for (int i = 0; i < m; i++) {
            int u = rnd.nextInt(n), v = rnd.nextInt(n);
            int w = rnd.nextInt(2_000_000_001) - 1_000_000_000;
            edges[i] = new int[]{u, v, w};
        }
        return edges;
    }

    static int find(int[] p, int x) {
        while (p[x] != x) { p[x] = p[p[x]]; x = p[x]; }
        return x;
    }

    static int countComponents(int n, int[][] edges) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        int comps = n;
        for (int[] e : edges) {
            int a = find(p, e[0]), b = find(p, e[1]);
            if (a != b) { p[a] = b; comps--; }
        }
        return comps;
    }

    /** Checks that the answer is a valid spanning forest and returns its cost. */
    static long verify(String name, int n, int[][] edges, Map<String, Object> r, int refComps) {
        int[] ids = (int[]) r.get("edge_ids");
        long cost = ((Number) r.get("cost")).longValue();
        int comps = ((Number) r.get("components")).intValue();
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        boolean[] used = new boolean[edges.length];
        long sum = 0;
        for (int id : ids) {
            if (id < 0 || id >= edges.length || used[id])
                throw new IllegalStateException(name + ": bad or repeated edge ID " + id);
            used[id] = true;
            int a = find(p, edges[id][0]), b = find(p, edges[id][1]);
            if (a == b) throw new IllegalStateException(name + ": an edge closes a cycle or is a self-loop");
            p[a] = b;
            sum += edges[id][2];
        }
        if (sum != cost) throw new IllegalStateException(name + ": cost differs from the sum of edges");
        if (comps != refComps || ids.length != n - refComps)
            throw new IllegalStateException(name + ": not a spanning forest of the whole graph");
        return cost;
    }

    static double median(double[] a) {
        double[] c = a.clone();
        Arrays.sort(c);
        int k = c.length;
        return k % 2 == 1 ? c[k / 2] : (c[k / 2 - 1] + c[k / 2]) / 2;
    }

    static String fmt(double[] a) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(Locale.ROOT, "%.1f", a[i]));
        }
        return sb.append("]").toString();
    }

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + ")");
        System.out.println("OS " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + ", logical CPUs " + Runtime.getRuntime().availableProcessors()
                + ", max heap " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
        System.out.println("Timer System.nanoTime(), warm-up " + WARMUP + " untimed runs per method, "
                + RUNS + " timed runs per method, seed " + SEED + ", times in ms");
        int[][] sizes = {{10_000, 25_000}, {50_000, 125_000}, {100_000, 250_000}};
        List<String> table = new ArrayList<>();
        table.add("| n | m | Kruskal runs (ms) | Kruskal median | Prim runs (ms) | Prim median |");
        table.add("|---|---|---|---|---|---|");
        for (int[] s : sizes) {
            int n = s[0], m = s[1];
            int[][] edges = generate(n, m, SEED + n);
            int refComps = countComponents(n, edges);
            for (int i = 0; i < WARMUP; i++) {
                verify("kruskal warm-up", n, edges, Algorithms.kruskal(n, edges), refComps);
                verify("prim warm-up", n, edges, Algorithms.prim(n, edges), refComps);
            }
            double[] tk = new double[RUNS], tp = new double[RUNS];
            long cost = 0;
            for (int r = 0; r < RUNS; r++) {
                Map<String, Object> rk = null, rp = null;
                int[] order = (r % 2 == 0) ? new int[]{0, 1} : new int[]{1, 0};
                for (int x : order) {
                    long t0 = System.nanoTime();
                    Map<String, Object> res = (x == 0) ? Algorithms.kruskal(n, edges) : Algorithms.prim(n, edges);
                    long t1 = System.nanoTime();
                    if (x == 0) { tk[r] = (t1 - t0) / 1e6; rk = res; }
                    else { tp[r] = (t1 - t0) / 1e6; rp = res; }
                }
                long ck = verify("kruskal", n, edges, rk, refComps);
                long cp = verify("prim", n, edges, rp, refComps);
                if (ck != cp) throw new IllegalStateException("costs differ: kruskal " + ck + ", prim " + cp);
                cost = ck;
            }
            double mk = median(tk), mp = median(tp);
            System.out.println("n=" + n + " m=" + m + " components=" + refComps + " cost=" + cost + " (Kruskal == Prim, valid forests)");
            System.out.println("  kruskal " + fmt(tk) + " median " + String.format(Locale.ROOT, "%.1f", mk));
            System.out.println("  prim    " + fmt(tp) + " median " + String.format(Locale.ROOT, "%.1f", mp));
            table.add("| " + n + " | " + m + " | " + fmt(tk) + " | " + String.format(Locale.ROOT, "%.1f", mk)
                    + " | " + fmt(tp) + " | " + String.format(Locale.ROOT, "%.1f", mp) + " |");
        }
        System.out.println();
        for (String line : table) System.out.println(line);
    }
}