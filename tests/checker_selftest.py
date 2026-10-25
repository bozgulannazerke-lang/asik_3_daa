"""Self-test of the semantic checkers used in tests/large_check.py.

Run from the repository root:
    python tests/checker_selftest.py

For every task it shows that a checker ACCEPTS alternative valid answers
(other edge IDs, other SCC labels, other topological orders, other tied parents)
and REJECTS invalid ones (cycle in a forest, non-optimal cost, merged or split SCC,
a backward edge, a wrong parent edge, ...).
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import large_check as lc

failed = 0
total = 0


def accepts(name, check, out):
    global failed, total
    total += 1
    try:
        check(out)
        print("PASS accepts:", name)
    except AssertionError as exc:
        failed += 1
        print("FAIL (valid answer was rejected):", name, "->", exc)


def rejects(name, check, out):
    global failed, total
    total += 1
    try:
        check(out)
    except AssertionError as exc:
        print("PASS rejects:", name, "->", exc)
        return
    failed += 1
    print("FAIL (invalid answer was accepted):", name)


# ---- DSU ----
ops = [[0, 1], [1, 2], [0, 2], [3, 3]]
c = lc.dsu_checker(4, ops)
accepts("dsu: published example", c, {"states": [[3, 2], [2, 3], [2, 3], [2, 3]]})
rejects("dsu: component count changes on a repeated union", c, {"states": [[3, 2], [2, 3], [1, 3], [2, 3]]})
rejects("dsu: initial state included", c, {"states": [[4, 1], [3, 2], [2, 3], [2, 3], [2, 3]]})
rejects("dsu: wrong largest size", c, {"states": [[3, 2], [2, 2], [2, 3], [2, 3]]})

# ---- minimum spanning forest ----
edges = [[0, 1, 5], [0, 1, 5], [1, 2, 7], [0, 2, 7]]
c = lc.forest_checker(3, edges)
accepts("forest: one optimal choice", c, {"cost": 12, "edge_ids": [0, 2], "components": 1})
accepts("forest: another optimal choice, other order", c, {"cost": 12, "edge_ids": [3, 1], "components": 1})
rejects("forest: edges 0 and 1 form a cycle", c, {"cost": 10, "edge_ids": [0, 1], "components": 1})
rejects("forest: spanning but not optimal (cost 14)", c, {"cost": 14, "edge_ids": [2, 3], "components": 1})
rejects("forest: claimed cost is not the sum of the edges", c, {"cost": 11, "edge_ids": [0, 2], "components": 1})
rejects("forest: repeated edge ID", c, {"cost": 10, "edge_ids": [0, 0], "components": 1})
rejects("forest: wrong component count", c, {"cost": 12, "edge_ids": [0, 2], "components": 2})
rejects("forest: not spanning (one edge only)", c, {"cost": 5, "edge_ids": [0], "components": 1})
loop = [[0, 0, -5], [0, 1, 3]]
c = lc.forest_checker(2, loop)
accepts("forest: self-loop skipped", c, {"cost": 3, "edge_ids": [1], "components": 1})
rejects("forest: self-loop selected", c, {"cost": -2, "edge_ids": [0, 1], "components": 1})

# ---- strongly connected components ----
edges = [[0, 1], [1, 0], [1, 2], [0, 2], [2, 3]]
c = lc.scc_checker(4, edges)
accepts("scc: other labels", c, {"component": [5, 5, 7, 9], "condensation": [[5, 7], [7, 9]]})
accepts("scc: other order of condensation pairs", c, {"component": [5, 5, 7, 9], "condensation": [[7, 9], [5, 7]]})
rejects("scc: two SCCs got one label", c, {"component": [0, 0, 0, 1], "condensation": [[0, 1]]})
rejects("scc: one SCC got two labels", c, {"component": [0, 1, 2, 3], "condensation": [[0, 2], [1, 2], [2, 3]]})
rejects("scc: duplicate condensation pair", c, {"component": [5, 5, 7, 9], "condensation": [[5, 7], [5, 7], [7, 9]]})
rejects("scc: missing condensation pair", c, {"component": [5, 5, 7, 9], "condensation": [[5, 7]]})
rejects("scc: internal edge in condensation", c, {"component": [5, 5, 7, 9], "condensation": [[5, 5], [5, 7], [7, 9]]})
rejects("scc: reversed condensation pair", c, {"component": [5, 5, 7, 9], "condensation": [[7, 5], [7, 9]]})

# ---- topological order ----
edges = [[0, 2], [1, 2], [2, 3]]
c = lc.topo_checker(4, edges)
accepts("topo: published order", c, {"acyclic": True, "order": [1, 0, 2, 3]})
accepts("topo: another valid order", c, {"acyclic": True, "order": [0, 1, 2, 3]})
rejects("topo: an edge goes backwards", c, {"acyclic": True, "order": [2, 0, 1, 3]})
rejects("topo: a vertex is missing", c, {"acyclic": True, "order": [0, 1, 2]})
rejects("topo: a vertex is repeated", c, {"acyclic": True, "order": [0, 0, 2, 3]})
rejects("topo: acyclic graph reported as cyclic", c, {"acyclic": False, "order": []})
accepts("topo: cycle reported correctly", lc.cyclic_topo_check, {"acyclic": False, "order": []})
rejects("topo: cycle reported but order not empty", lc.cyclic_topo_check, {"acyclic": False, "order": [0]})
rejects("topo: cycle not reported", lc.cyclic_topo_check, {"acyclic": True, "order": [0, 1, 2]})

# ---- DAG shortest paths ----
edges = [[0, 1, 3], [0, 2, 8], [1, 2, -7], [2, 3, 4]]
c = lc.dag_checker(5, edges, 0)
accepts("dag: published example", c, {"acyclic": True, "distance": [0, 3, -4, 0, None], "parent_edge": [-1, 0, 2, 3, -1]})
rejects("dag: longer route chosen (distance 8)", c, {"acyclic": True, "distance": [0, 3, 8, 12, None], "parent_edge": [-1, 0, 1, 3, -1]})
rejects("dag: -1 used instead of null", c, {"acyclic": True, "distance": [0, 3, -4, 0, -1], "parent_edge": [-1, 0, 2, 3, -1]})
rejects("dag: unreachable vertex given a distance", c, {"acyclic": True, "distance": [0, 3, -4, 0, 0], "parent_edge": [-1, 0, 2, 3, -1]})
rejects("dag: parent edge is not on a shortest route", c, {"acyclic": True, "distance": [0, 3, -4, 0, None], "parent_edge": [-1, 0, 1, 3, -1]})
rejects("dag: parent edge ends at another vertex", c, {"acyclic": True, "distance": [0, 3, -4, 0, None], "parent_edge": [-1, 0, 3, 3, -1]})
rejects("dag: source has a parent edge", c, {"acyclic": True, "distance": [0, 3, -4, 0, None], "parent_edge": [0, 0, 2, 3, -1]})
rejects("dag: acyclic graph reported as cyclic", c, {"acyclic": False, "distance": [], "parent_edge": []})
edges = [[0, 1, 2], [0, 1, 2], [1, 2, 1], [0, 2, 3]]
c = lc.dag_checker(3, edges, 0)
for p1 in (0, 1):
    for p2 in (2, 3):
        accepts("dag: tied parents [-1,%d,%d]" % (p1, p2), c,
                {"acyclic": True, "distance": [0, 2, 3], "parent_edge": [-1, p1, p2]})
rejects("dag: parent edge of vertex 1 ends at vertex 2", c, {"acyclic": True, "distance": [0, 2, 3], "parent_edge": [-1, 2, 3]})
accepts("dag: cycle reported correctly", lc.cyclic_dag_check, {"acyclic": False, "distance": [], "parent_edge": []})
rejects("dag: cycle reported but arrays not empty", lc.cyclic_dag_check, {"acyclic": False, "distance": [0], "parent_edge": [-1]})
rejects("dag: cycle not reported", lc.cyclic_dag_check, {"acyclic": True, "distance": [], "parent_edge": []})

print("%d checks, %d failed" % (total, failed))
print("ALL PASSED" if failed == 0 else "%d FAILED" % failed)
sys.exit(1 if failed else 0)