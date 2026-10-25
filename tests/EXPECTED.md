# Expected results for `tests/my_cases.jsonl`

Line numbers refer to lines of `my_cases.jsonl`. "Any of" means several outputs are valid; the checker must accept all of them semantically.

## Commands

```
python tools/run.py --setup
python tools/run.py --check
python tools/run.py --input tests/my_cases.jsonl > tests/actual_output.txt
```

## DSU

| Line | Why this case | Expected answer and justification |
|---|---|---|
| 1 | Specification example: repeated union and self-union | `{"states":[[3,2],[2,3],[2,3],[2,3]]}`. 0-1 merges (3 components, largest 2); 1-2 merges (2, 3); 0-2 is already connected; 3-3 is a self-union. |
| 2 | `n=0` | `{"states":[]}`. There are no operations, so no states. |
| 3 | `n=1`, only self-unions | `{"states":[[1,1],[1,1]]}`. One vertex, nothing ever changes, `largest=1`. |
| 4 | Repeats and growth of the largest component | `{"states":[[4,2],[3,2],[3,2],[2,3],[1,5]]}`. {0,1}; {2,3}; repeat 1-0; {2,3}+4 gives size 3; finally {0,1}+{2,3,4} gives size 5. |

## Kruskal and Prim (the same graphs, lines 5-8 and 9-12)

| Lines | Graph | Expected answer and justification |
|---|---|---|
| 5, 9 | Specification example, vertex 3 isolated | `cost=5`, `edge_ids=[1,2]` (weights 2 and 3; edge 0 would close a cycle), `components=2`. The optimum is unique. |
| 6, 10 | Parallel edges and ties | `cost=12`, `components=1`. Any of: one of edge IDs {0,1} (weight 5) plus one of {2,3} (weight 7). Exactly `n-c=2` edges. |
| 7, 11 | Weights beyond 32 bits, negative weights, self-loop | `cost=-3000000000`, `components=1`, three edge IDs chosen from {0,1,2,4}. The cycle 0-1-2-3-0 has four edges of weight -1e9; any three form a tree. Edge 3 is a self-loop and must not be selected. -3e9 does not fit into `int`. |
| 8 | Kruskal `n=0` | `cost=0`, `edge_ids=[]`, `components=0`. |
| 12 | Prim, three isolated vertices | `cost=0`, `edge_ids=[]`, `components=3`. |

## SCC

| Line | Why this case | Expected answer and justification |
|---|---|---|
| 13 | Specification example, duplicate cross edges | SCCs: {0,1}, {2}, {3}. `component` has equal labels at indices 0 and 1 and distinct labels for 2 and 3. `condensation` has exactly two pairs: (label of {0,1}, label of {2}) and (label of {2}, label of {3}). Edges 1->2 and 0->2 give the same pair, which must appear once. |
| 14 | Self-loop and isolated vertices | Three different labels. `condensation` contains only the pair (label of 1, label of 2). The self-loop 0->0 is internal and excluded. |
| 15 | `n=0` | `{"component":[],"condensation":[]}`. |

## Topological order

| Line | Why this case | Expected answer and justification |
|---|---|---|
| 16 | Specification example | `acyclic=true`, any order where 0 and 1 precede 2 and 2 precedes 3, for example `[1,0,2,3]` or `[0,1,2,3]`. |
| 17 | Directed cycle of length 3 | `acyclic=false`, `order=[]`. |
| 18 | Self-loop | `acyclic=false`, `order=[]`. A self-loop is a cycle. |
| 19 | Diamond plus duplicate edges to an already finished vertex | `acyclic=true`. Edge 0->3 reaches a finished vertex, which alone is not a cycle. Valid orders: 0 first, 3 last, 1 and 2 in the middle in any order. |

## DAG shortest paths

| Line | Why this case | Expected answer and justification |
|---|---|---|
| 20 | Specification example, negative edge, unreachable vertex | `acyclic=true`, `distance=[0,3,-4,0,null]`, `parent_edge=[-1,0,2,3,-1]`. Route to 2 is 0->1->2 with cost 3-7=-4, shorter than 8. Vertex 4 is unreachable. |
| 21 | Cycle 2<->3 not reachable from the source | `acyclic=false`, `distance=[]`, `parent_edge=[]`. The whole graph is checked. |
| 22 | Parallel edges and tied routes | `acyclic=true`, `distance=[0,2,3]`. `parent_edge[1]` is 0 or 1 (equal weights). `parent_edge[2]` is 2 (via 1: 2+1) or 3 (direct: 3), both cost 3. |
| 23 | `n=1` | `distance=[0]`, `parent_edge=[-1]`. |
| 24 | Total -3e9, beyond 32 bits | `distance=[0,-1000000000,-2000000000,-3000000000]`, `parent_edge=[-1,0,1,2]`. |

## Route helper (to be tested separately)

On line 20: route to 3 must be `[0,1,2,3]` (cost 0), route to the source 0 must be `[0]`, route to the unreachable vertex 4 returns the documented no-route value (empty array).
