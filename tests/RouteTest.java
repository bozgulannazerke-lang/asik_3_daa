import java.util.*;

public class RouteTest {
    private static int failed = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failed++;
    }

    private static int[] run(String name, int n, int[][] edges, int source, int target) {
        Map<String, Object> r = Algorithms.dag(n, edges, source);
        int[] parent = (int[]) r.get("parent_edge");
        Long[] dist = (Long[]) r.get("distance");
        int[] path = Algorithms.route(source, target, parent, edges);
        System.out.println(name + ": route " + source + " -> " + target + " = " + Arrays.toString(path));
        if (dist[target] == null) {
            check(name + ": unreachable target gives the documented empty route", path.length == 0);
            return path;
        }
        boolean ok = path.length > 0 && path[0] == source && path[path.length - 1] == target;
        long cost = 0;
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i < path.length && ok; i++) {
            int e = parent[path[i]];
            ids.add(e);
            ok = edges[e][0] == path[i - 1] && edges[e][1] == path[i];
            cost += edges[e][2];
        }
        System.out.println("   edge IDs " + ids + ", total weight " + cost + ", distance " + dist[target]);
        check(name + ": direction, endpoints, total weight", ok && cost == dist[target]);
        return path;
    }

    public static void main(String[] args) {
        int[][] ex = {{0, 1, 3}, {0, 2, 8}, {1, 2, -7}, {2, 3, 4}};
        int[] p = run("example", 5, ex, 0, 3);
        check("example: route to 3 is [0,1,2,3]", Arrays.equals(p, new int[]{0, 1, 2, 3}));
        p = run("example/source", 5, ex, 0, 0);
        check("source: route is [0]", Arrays.equals(p, new int[]{0}));
        run("example/unreachable", 5, ex, 0, 4);

        int[][] tied = {{0, 1, 2}, {0, 1, 2}, {1, 2, 1}, {0, 2, 3}};
        run("tied/parallel to 1", 3, tied, 0, 1);
        run("tied/parallel to 2", 3, tied, 0, 2);

        run("single vertex", 1, new int[0][], 0, 0);
        System.exit(failed == 0 ? 0 : 1);
    }
}