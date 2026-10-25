# Assignment 3: analysis

Language: Java (`src/Java/Algorithms.java`). Environment of all runs: GitHub Codespaces, Java 25.0.4.1, Python 3.14.2 (the checker only).

## 1. Commands

```
python tools/run.py --setup
python tools/run.py --check
python tools/run.py --input tests/my_cases.jsonl > tests/actual_output.txt
mkdir -p /tmp/rt && javac -d /tmp/rt src/Java/*.java tests/RouteTest.java && java -cp /tmp/rt RouteTest | tee tests/route_output.txt
python tests/large_check.py | tee tests/large_check_output.txt
mkdir -p /tmp/bench && javac -d /tmp/bench src/Java/*.java tests/Bench.java && java -cp /tmp/bench Bench | tee tests/bench_output.txt
```

## 2. Common design

**Graph storage.** Directed and undirected graphs are stored in compressed adjacency arrays (`start`, `to`, `eid`). An undirected edge appears twice (a self-loop too). `eid` keeps the original zero-based position of each edge in `edges`, so IDs survive sorting and adjacency building.

**State reset.** Every method allocates its own arrays inside the call. The class has no mutable static fields, so nothing leaks from one request to the next. This was checked by the public test "two complete batches in one process", by `tests/my_cases.jsonl` (mixed tasks in one process) and by `tests/large_check.py` (15 large mixed requests in one process).

**Deep graphs.** No method uses recursion. DFS uses an explicit `int[]` stack and a per-vertex pointer `ptr[v]` to the next unscanned neighbour, so a suspended vertex resumes where it stopped and no edge is scanned twice. Each vertex is pushed once (when discovered), so the stack never exceeds `n`. Tested with chains of 2,000 vertices (public tests) and 100,000 vertices (`large_check.py`).

**Overflow.** Costs and distances are `long`. The largest possible magnitude is about 10^5 * 10^9 = 10^14. Distances use `Long[]` with `null` for unreachable vertices.

## 3. DSU

**Steps.** `p[]` (parent) and `size[]`. `find` walks to the root and then compresses the path (two passes, iterative). `union` finds both roots and hangs the smaller tree under the larger. After each operation the pair `[components, largest]` is stored.

**Invariant.** The trees of `p` are exactly the current components; `size[root]` is the number of vertices of that tree; `components` is the number of roots; `largest` is the maximum size. `components` decreases and `largest` is updated only when `union` really merges.

**Correctness.** Union by size and path compression do not change the partition, only the tree shapes, so the states are exactly the partition after each prefix of operations. Rank is not used, so size is stored directly.

**Boundary cases.** Repeated unions and self-unions return `false` and change nothing. For `n = 0`: `states = []`. For `n > 0`, `largest` starts at 1.

**Complexity.** `O(n + q α(n))` time, `O(n + q)` memory (the returned `states`).

## 4. Kruskal

**Steps.** Each edge becomes one `long` key `((w + 10^9) << 18) | id`. The key array is sorted (primitive sort). Edges are scanned in key order and accepted when `find(u) != find(v)`; the DSU merges them. The cost is accumulated in `long`.

**Why the key is safe.** `w + 10^9` is between 0 and 2*10^9 (fits 31 bits) and `id < 2^18 = 262,144 > 250,000`, so the key fits into 49 bits. Sorting keys sorts by weight, then by original ID, and the ID is recovered by masking.

**Invariant and correctness.** After processing a prefix of the sorted edges, the accepted edges form a minimum spanning forest of that prefix. An accepted edge is the lightest edge leaving its component among the unprocessed edges (cut property); a rejected edge would close a cycle in which it is the heaviest processed edge (cycle property).

**Boundary cases.** Self-loops never pass `find(u) != find(v)`. Of parallel or equal-weight edges the smallest key wins and the others close a cycle. Negative weights are handled by the offset. Components = `n - accepted`. For `n = 0`: `cost = 0`, `edge_ids = []`, `components = 0`.

**Complexity.** `O(n + m log m)` time (the sort dominates), `O(n + m)` memory.

## 5. Prim

**Steps.** Build the undirected adjacency arrays. For every unvisited vertex `s` (a new component): mark it, push all incident edge keys (same key format as Kruskal) into a hand-written binary min-heap of `long`. Repeatedly pop the minimum key; if both endpoints are already visited the entry is stale and skipped; otherwise the unvisited endpoint is added, the edge is accepted and the new vertex's edges to unvisited neighbours are pushed. When the heap is empty the component is complete and the loop restarts from the next unvisited vertex.

**Invariant and correctness.** The accepted edges of the current component form a minimum spanning tree of the visited vertices, and the heap contains every edge leaving the visited set (plus stale entries). The minimum edge leaving the visited set is safe by the cut property. Lazy deletion is valid because a stale entry is detected when popped. Negative weights are fine: Prim only compares weights.

**Boundary cases.** Isolated vertices are components of their own (the restart counts them). Self-loops are skipped as stale; parallel edges and ties are resolved by the smaller key. `n = 0` returns the empty forest with 0 components.

**Complexity.** Each edge is pushed at most twice, so the heap holds at most `2m` entries: `O(n + m log m)` time, `O(n + m)` memory. Kruskal and Prim agree on cost and component count; the chosen IDs may differ.

## 6. Kosaraju SCC

**Steps.** (1) Iterative DFS over all vertices of the graph, recording the finish order. (2) Build the transposed graph. (3) Take vertices in decreasing finish order; each unlabelled vertex gets a new label, and a traversal of the transposed graph labels everything reachable and unlabelled. (4) Condensation: for every edge whose endpoints have different labels, insert `a * n + b` (a `long`) into a `HashSet`; the first insertion of a pair adds `[a, b]` to the answer.

**Correctness.** The vertex finishing last in pass 1 lies in a source component of the condensation. In the transposed graph the traversal from it cannot leave its own component (edges between components are reversed, and earlier components are already labelled), so each traversal labels exactly one SCC.

**Boundary cases.** Isolated vertices form their own components. Self-loops and edges inside a component are not in the condensation. Duplicate edges give one pair. For `n = 0` both arrays are empty. Labels are `0 .. k-1`.

**Complexity.** `O(n + m)` expected time (hash set), `O(n + m)` memory.

## 7. DFS topological order

**Steps.** Colours: 0 = unvisited, 1 = active (on the DFS stack), 2 = finished. Iterative DFS from every unvisited vertex. An edge to an active vertex is a back edge, which means a directed cycle: return `acyclic = false`, `order = []`. An edge to a finished vertex is ignored. When a vertex has no unscanned neighbours it becomes finished and is appended to the finish list; at the end the list is reversed.

**Correctness.** In an acyclic graph, for every edge `u -> v` the vertex `v` finishes before `u`, so the reversed finish order puts `u` before `v`. In a cyclic graph, DFS reaches the cycle through some vertex and finds an edge back to an active vertex.

**Boundary cases.** A self-loop is an edge to an active vertex, so it is a cycle. Duplicate edges are harmless. The outer loop covers all components. `n = 0` gives `acyclic = true`, `order = []`.

**Complexity.** `O(n + m)` time and memory.

## 8. DAG shortest paths

**Steps.** Run the topological DFS on the whole graph; if there is a cycle anywhere (even unreachable from the source) return `acyclic = false` with empty arrays. Otherwise set `reach[source] = true`, `d[source] = 0`, `parent_edge[] = -1`. Process vertices in topological order, skipping unreachable ones; for every outgoing edge `u -> v` of weight `w`, if `v` is unreached or `d[u] + w < d[v]`, set `d[v]`, `parent_edge[v] = ` the edge ID. Unreachable vertices get `distance = null`.

**Invariant and correctness.** When `u` is processed, all its predecessors come earlier in the order and have already relaxed their edges, so `d[u]` is final. This also works with negative weights because there are no cycles. Relaxing only from reachable vertices prevents garbage values from unreachable ones. A parent edge always ends at its vertex and comes from a reachable vertex, so following parents reaches the source.

**Boundary cases.** Self-loop: cyclic. Parallel and tied edges: the strictly smaller distance wins, ties keep the first edge. `n = 1`, source alone: `distance = [0]`, `parent_edge = [-1]`. Distances beyond 32 bits are tested.

**Complexity.** `O(n + m)` time and memory.

### Route helper

`route(source, target, parentEdge, edges)` returns `[source]` when `target == source`. **No-route result: an empty array** when `parentEdge[target] == -1` (unreachable). Otherwise it counts the length by walking parent edges back (the predecessor of `v` is `edges[parentEdge[v]][0]`) and fills the array from the end. Time and space `O(L)`.

Actual calls (`tests/RouteTest.java`, output in `tests/route_output.txt`; direction, endpoints and total weight are verified in the test):

| Graph (edges as `[u,v,w]`, source 0) | Target | Route | Selected edge IDs | Total weight | `distance` |
|---|---|---|---|---|---|
| `[[0,1,3],[0,2,8],[1,2,-7],[2,3,4]]`, n = 5 | 3 (reachable) | `[0,1,2,3]` | `[0,2,3]` | 3 - 7 + 4 = 0 | 0 |
| same | 0 (source) | `[0]` | `[]` | 0 | 0 |
| same | 4 (unreachable) | `[]` | - | - | `null` |
| `[[0,1,2],[0,1,2],[1,2,1],[0,2,3]]`, n = 3 (parallel and tied) | 1 | `[0,1]` | `[0]` (edge 1 is an equal parallel edge) | 2 | 2 |
| same | 2 | `[0,2]` | `[3]` (the route through 1 also costs 2 + 1 = 3) | 3 | 3 |

## 9. Tests

All results below were actually obtained.

1. **Public checks:** `python tools/run.py --check` passes all 36 public cases and "state reset: two complete batches in one process". GitHub Actions "Public checks" is green.
2. **Own small tests:** `tests/my_cases.jsonl` (24 requests), expected answers with justification in `tests/EXPECTED.md`, actual outputs in `tests/actual_output.txt`. All 24 outputs agree with the expectations. Several outputs differ textually from the example in `EXPECTED.md` but are valid alternatives, for example: Kruskal on the tied graph returns IDs `[0,2]` (any one of {0,1} plus any one of {2,3} is optimal, cost 12); Prim on the specification example returns `[2,1]` (same set in another order, cost 5); the diamond topological order is `[0,2,1,3]`; in the DAG tie test `parent_edge[2] = 3` (the direct edge, equal to the route via vertex 1). Covered: `n = 0`, empty and disconnected graphs, isolated vertices, repeated and self unions, loops, parallel and tied edges, negative weights, totals beyond 32 bits (±3,000,000,000), unreachable vertices, a cycle outside the source's reach, a finished-vertex edge that is not a cycle.
3. **Route tests:** section 8.
4. **Large and deep tests:** `python tests/large_check.py` (fixed seed 20261025, nothing large is stored). 15 cases with `n = 100,000` and `m` up to 250,000: random DSU operations and a deep chain; Kruskal and Prim on a random graph and on a path; SCC on a deep chain, a single big cycle and a random digraph; topological order on a chain, a chain with a back edge and a random DAG; DAG shortest paths on a chain of weight -10^9 (total below -10^14), a random DAG and a graph with a cycle outside the source's reach. All in one process: ALL PASSED, 5.1 s wall time for the whole batch (including build and launch).
5. **How non-unique answers are checked** (`large_check.py`): forests are checked for no repeated IDs, no cycles, no self-loops, cost equal to the sum of the selected edges, equal to the cost of an independent Python Kruskal, `n - c` edges and the right `c`. SCC labels are compared with an independent iterative Tarjan as a partition (a bijection between labels), and the condensation must equal the set of cross pairs exactly once each. Topological orders are checked for edge precedence. DAG distances are compared with an independent dynamic programme, and every parent edge must end at its vertex and satisfy `dist[u] + w == dist[v]`.
6. **Checker self-test:** `python tests/checker_selftest.py` (output in `tests/checker_selftest_output.txt`). 47 checks on small graphs show that the semantic checkers of `tests/large_check.py` accept alternative valid answers (other edge IDs and tied parents, other SCC labels and pair order, other topological orders) and reject invalid ones: a forest with a cycle, a selected self-loop, a non-optimal or wrongly summed cost, repeated IDs, a wrong component count; merged or split SCCs, duplicate, missing, internal or reversed condensation pairs; a backward edge, a missing or repeated vertex in the order; a distance of -1 instead of null, an unreachable vertex with a distance, a parent edge that is not on a shortest route, ends at another vertex, or belongs to the source; a wrongly reported or unreported cycle. All 47 behaved as expected. The self-test checks the checkers, not the Java code.

## 10. Benchmark: Kruskal vs Prim

**Setup.** `tests/Bench.java`, run with the command in section 1. The same three graphs are given to both methods: random graphs with `m = 2.5 n`, `n` = 10,000 / 50,000 / 100,000, endpoints and weights from `java.util.Random(20261025 + n)`, weights in [-10^9, 10^9] (self-loops and parallel edges possible). Timer: `System.nanoTime()`. Warm-up: 5 untimed runs per method and size. Timed: 7 runs per method and size, the order of the two methods alternates between runs. Compilation is excluded.

**Timing boundary (identical for both methods).** Timed: one call `Algorithms.kruskal(n, edges)` or `Algorithms.prim(n, edges)` on an edge list that is already in memory, including everything the method does itself (sorting or heap, adjacency arrays, DSU, building the result). Not timed: JSON parsing, input/output, graph generation, verification of the result. Before the timing is accepted, every result is verified (valid spanning forest, correct component count, equal cost for Kruskal and Prim).

**Hardware and runtime.** GitHub Codespaces VM: AMD EPYC 7763 64-Core Processor, 2 logical CPUs, 7.8 GiB RAM, 1986 MB max Java heap, Java 25.0.4.1 (OpenJDK 64-Bit Server VM), Linux amd64.

**Results (ms).** Full output is in `tests/bench_output.txt`. Both methods returned the same cost on each graph and valid spanning forests (verified before the timings were accepted).

| n | m | components | cost | Kruskal runs (ms) | Kruskal median | Prim runs (ms) | Prim median |
|---|---|---|---|---|---|---|---|
| 10000 | 25000 | 67 | -5334526775020 | [4.5, 4.5, 4.2, 4.2, 4.2, 7.1, 4.3] | 4.3 | [7.3, 6.4, 6.3, 6.1, 9.5, 7.8, 6.6] | 6.6 |
| 50000 | 125000 | 329 | -26463030823142 | [25.3, 12.7, 12.1, 12.9, 17.3, 11.8, 12.7] | 12.7 | [30.7, 33.9, 36.7, 30.2, 25.1, 25.2, 24.1] | 30.2 |
| 100000 | 250000 | 687 | -52737000949726 | [25.1, 28.4, 25.0, 27.0, 24.7, 43.9, 28.1] | 27.0 | [67.1, 59.4, 69.7, 57.1, 57.9, 94.8, 58.1] | 59.4 |


**Observations.**

- Kruskal was faster than Prim at all three sizes: by a factor of 1.5 (n = 10,000), 2.4 (n = 50,000) and 2.2 (n = 100,000), comparing medians.
- Going from 50,000 to 100,000 vertices (m doubles) the medians grew by 2.1x for Kruskal and 2.0x for Prim. The `m log m` bound predicts about 2.12x for a doubling of `m` at these sizes, so the measurements agree with it.
- Going from 10,000 to 50,000 vertices (m grows 5x) the medians grew by only 2.9x (Kruskal) and 4.6x (Prim), less than the roughly 5.8x predicted by `m log m`. I did not investigate the reason. A plausible explanation is that at about 4 ms a larger share of the time is fixed overhead (allocation, incomplete JIT optimisation), but I have not verified this.
- Both methods have the same asymptotic bound, so the difference between them is a constant factor. One plausible reason for the constant is that Prim pushes up to `2m` entries into a heap and reads adjacency arrays in a scattered order, while Kruskal sorts one primitive `long` array. This was not profiled.
- Single runs vary (for example Prim at n = 100,000 ranged from 57.1 to 94.8 ms), which is why medians are reported. The first timed run of Kruskal at n = 50,000 (25.3 ms) is about twice the others, probably a JIT or garbage-collection effect.
**Limitations.** The machine is a shared cloud VM, so single timings are noisy, hence the medians. Only one graph family (random, average degree 5) and one density were measured; dense graphs, very sparse graphs or special structures could change the ranking. The JIT compiler may still optimise during the first timed runs even after warm-up. Measurements say nothing about other languages. The asymptotic bounds (section 4 and 5) are the argument; the measurements only illustrate them for these inputs.

## 11. Sources, AI tools and verification

**AI tools.** I used the AI assistant Claude (Anthropic) throughout this assignment, in two separate chats:
- In an earlier chat, Claude helped me write the Java implementation of all six methods (`dsu`, `kruskal`, `prim`, `scc`, `topo`, `dag`) and the route helper in `src/Java/Algorithms.java`.
- In this chat, Claude reviewed the finished `Algorithms.java` (it did not change it), helped me plan the remaining work, designed the test cases in `tests/my_cases.jsonl` with the expected answers and justifications in `tests/EXPECTED.md`, wrote the programs `tests/RouteTest.java`, `tests/large_check.py`, `tests/Bench.java` and `tests/checker_selftest.py`, and drafted the text of this `ANALYSIS.md`.

**Other sources.** The assignment text and the supplied template (`README.md`, `tools/`). No other external sources or code were used.

**My verification.** I ran every command listed in section 1 myself in GitHub Codespaces. All outputs, benchmark numbers and hardware data in this report come from my own runs. The public checks (36 cases) pass and GitHub Actions is green.