"""Large and deep-input checks. Run from the repository root:

    python tests/large_check.py

Inputs are generated with a fixed seed, so nothing large is stored in the repository.
All cases go to tools/run.py in ONE process; every answer is verified semantically
against an independent reference (answers are not unique, so text is not compared).
"""
import json
import os
import random
import subprocess
import sys
import tempfile
import time

SEED = 20261025
N = 100_000
M = 250_000
W = 1_000_000_000


class Dsu:
    def __init__(self, n):
        self.p = list(range(n))
        self.sz = [1] * n

    def find(self, x):
        p = self.p
        while p[x] != x:
            p[x] = p[p[x]]
            x = p[x]
        return x

    def union(self, a, b):
        a, b = self.find(a), self.find(b)
        if a == b:
            return False
        if self.sz[a] < self.sz[b]:
            a, b = b, a
        self.p[b] = a
        self.sz[a] += self.sz[b]
        return True


def ref_dsu(n, ops):
    d = Dsu(n)
    comps, largest, states = n, (1 if n else 0), []
    for u, v in ops:
        if d.union(u, v):
            comps -= 1
            largest = max(largest, d.sz[d.find(u)])
        states.append([comps, largest])
    return states


def ref_forest(n, edges):
    d = Dsu(n)
    cost = used = 0
    for i in sorted(range(len(edges)), key=lambda i: edges[i][2]):
        u, v, w = edges[i]
        if d.union(u, v):
            cost += w
            used += 1
    return cost, n - used


def ref_scc(n, edges):
    """Iterative Tarjan: independent of the Kosaraju code under test."""
    adj = [[] for _ in range(n)]
    for u, v in edges:
        adj[u].append(v)
    index = [0] * n
    low = [0] * n
    on_stack = [False] * n
    seen = [False] * n
    comp = [-1] * n
    stack = []
    counter, label = 1, 0
    for s in range(n):
        if seen[s]:
            continue
        seen[s] = True
        index[s] = low[s] = counter
        counter += 1
        stack.append(s)
        on_stack[s] = True
        work = [(s, 0)]
        while work:
            v, i = work.pop()
            if i < len(adj[v]):
                work.append((v, i + 1))
                w = adj[v][i]
                if not seen[w]:
                    seen[w] = True
                    index[w] = low[w] = counter
                    counter += 1
                    stack.append(w)
                    on_stack[w] = True
                    work.append((w, 0))
                elif on_stack[w]:
                    low[v] = min(low[v], index[w])
            else:
                if low[v] == index[v]:
                    while True:
                        w = stack.pop()
                        on_stack[w] = False
                        comp[w] = label
                        if w == v:
                            break
                    label += 1
                if work:
                    parent = work[-1][0]
                    low[parent] = min(low[parent], low[v])
    return comp


def dsu_checker(n, ops):
    def check(out):
        assert out["states"] == ref_dsu(n, ops), "states differ from the reference"
    return check


def forest_checker(n, edges):
    ref_cost, ref_comps = ref_forest(n, edges)

    def check(out):
        ids = out["edge_ids"]
        assert len(set(ids)) == len(ids), "repeated edge ID"
        d = Dsu(n)
        total = 0
        for i in ids:
            u, v, w = edges[i]
            assert d.union(u, v), "selected edge closes a cycle or is a self-loop"
            total += w
        assert out["cost"] == total, "cost differs from the sum of selected edges"
        assert total == ref_cost, "cost %d is not optimal (%d)" % (total, ref_cost)
        assert out["components"] == ref_comps, "wrong component count"
        assert len(ids) == n - ref_comps, "not a spanning forest"
    return check


def scc_checker(n, edges):
    ref = ref_scc(n, edges)

    def check(out):
        comp = out["component"]
        assert len(comp) == n, "wrong length of component"
        fwd, back = {}, {}
        for x in range(n):
            assert fwd.setdefault(comp[x], ref[x]) == ref[x], "a label mixes several SCCs"
            assert back.setdefault(ref[x], comp[x]) == comp[x], "an SCC has several labels"
        want = {(comp[u], comp[v]) for u, v in edges if comp[u] != comp[v]}
        got = [tuple(p) for p in out["condensation"]]
        assert len(got) == len(set(got)), "duplicate condensation pair"
        assert set(got) == want, "condensation differs from the reference"
    return check


def topo_checker(n, edges):
    def check(out):
        assert out["acyclic"] is True, "should be acyclic"
        order = out["order"]
        assert sorted(order) == list(range(n)), "order is not a permutation"
        pos = [0] * n
        for i, v in enumerate(order):
            pos[v] = i
        for u, v in edges:
            assert pos[u] < pos[v], "an edge goes backwards"
    return check


def cyclic_topo_check(out):
    assert out["acyclic"] is False and out["order"] == [], "should report a cycle"


def dag_checker(n, edges, source):
    """Reference DP; valid because every edge goes from a smaller to a larger index."""
    adj = [[] for _ in range(n)]
    for u, v, w in edges:
        adj[u].append((v, w))
    dist = [None] * n
    dist[source] = 0
    for u in range(n):
        if dist[u] is None:
            continue
        for v, w in adj[u]:
            nd = dist[u] + w
            if dist[v] is None or nd < dist[v]:
                dist[v] = nd

    def check(out):
        assert out["acyclic"] is True, "should be acyclic"
        assert out["distance"] == dist, "distances differ from the reference"
        par = out["parent_edge"]
        assert len(par) == n, "wrong length of parent_edge"
        for v in range(n):
            if v == source or dist[v] is None:
                assert par[v] == -1, "unexpected parent edge"
            else:
                u, t, w = edges[par[v]]
                assert t == v and dist[u] is not None and dist[u] + w == dist[v], "bad parent edge"
    return check


def cyclic_dag_check(out):
    assert out["acyclic"] is False and out["distance"] == [] and out["parent_edge"] == [], \
        "should report a cycle with empty arrays"


def build_cases():
    rnd = random.Random(SEED)
    cases = []

    def add(name, req, check):
        cases.append((name, req, check))

    ops = [[rnd.randrange(N), rnd.randrange(N)] for _ in range(M)]
    add("dsu random 250000 operations", {"task": "dsu", "n": N, "operations": ops}, dsu_checker(N, ops))
    chain_ops = [[i, i + 1] for i in range(N - 1)]
    add("dsu deep chain", {"task": "dsu", "n": N, "operations": chain_ops}, dsu_checker(N, chain_ops))

    edges = [[rnd.randrange(N), rnd.randrange(N), rnd.randint(-W, W)] for _ in range(M)]
    chk = forest_checker(N, edges)
    add("kruskal random graph", {"task": "kruskal", "n": N, "edges": edges}, chk)
    add("prim random graph", {"task": "prim", "n": N, "edges": edges}, chk)
    path = [[i, i + 1, rnd.randint(-W, W)] for i in range(N - 1)]
    chk = forest_checker(N, path)
    add("kruskal path graph", {"task": "kruskal", "n": N, "edges": path}, chk)
    add("prim path graph", {"task": "prim", "n": N, "edges": path}, chk)

    chain = [[i, i + 1] for i in range(N - 1)]
    cycle = chain + [[N - 1, 0]]
    add("scc deep chain", {"task": "scc", "n": N, "edges": chain}, scc_checker(N, chain))
    add("scc one big cycle", {"task": "scc", "n": N, "edges": cycle}, scc_checker(N, cycle))
    digraph = [[rnd.randrange(N), rnd.randrange(N)] for _ in range(M)]
    add("scc random digraph", {"task": "scc", "n": N, "edges": digraph}, scc_checker(N, digraph))

    add("topo deep chain", {"task": "topo", "n": N, "edges": chain}, topo_checker(N, chain))
    add("topo chain with back edge", {"task": "topo", "n": N, "edges": cycle}, cyclic_topo_check)
    dag_plain = []
    for _ in range(M):
        u = rnd.randrange(N - 1)
        dag_plain.append([u, rnd.randrange(u + 1, N)])
    add("topo random DAG", {"task": "topo", "n": N, "edges": dag_plain}, topo_checker(N, dag_plain))

    wchain = [[i, i + 1, -W] for i in range(N - 1)]
    base = dag_checker(N, wchain, 0)

    def chain_check(out):
        base(out)
        assert out["distance"][-1] == -(N - 1) * W, "wrong total on the deep chain"
    add("dag deep chain, total below -1e14", {"task": "dag", "n": N, "source": 0, "edges": wchain}, chain_check)
    wdag = []
    for _ in range(M - 5):
        u = rnd.randrange(N - 1)
        wdag.append([u, rnd.randrange(u + 1, N), rnd.randint(-W, W)])
    for _ in range(5):
        wdag.append([0, rnd.randrange(1, N), rnd.randint(-W, W)])
    add("dag random DAG", {"task": "dag", "n": N, "source": 0, "edges": wdag}, dag_checker(N, wdag, 0))
    hidden = [[i, i + 1, 1] for i in range(1000)] + [[N - 2, N - 1, 1], [N - 1, N - 2, 1]]
    add("dag cycle outside source reach", {"task": "dag", "n": N, "source": 0, "edges": hidden}, cyclic_dag_check)
    return cases


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cases = build_cases()
    with tempfile.NamedTemporaryFile("w", suffix=".jsonl", delete=False, encoding="utf-8") as f:
        for _, req, _ in cases:
            f.write(json.dumps(req, separators=(",", ":")) + "\n")
        path = f.name
    print("%d cases generated (seed %d), one process, one batch" % (len(cases), SEED))
    start = time.perf_counter()
    proc = subprocess.run([sys.executable, os.path.join(root, "tools", "run.py"), "--input", path],
                          cwd=root, capture_output=True, text=True, encoding="utf-8")
    elapsed = time.perf_counter() - start
    os.unlink(path)
    if proc.returncode != 0:
        print("tools/run.py failed:\n" + proc.stderr)
        sys.exit(1)
    lines = [ln for ln in proc.stdout.splitlines() if ln.strip()]
    if len(lines) != len(cases):
        print("expected %d result lines, got %d" % (len(cases), len(lines)))
        sys.exit(1)
    failed = 0
    for (name, _, check), line in zip(cases, lines):
        try:
            check(json.loads(line))
            print("PASS", name)
        except Exception as exc:
            failed += 1
            print("FAIL", name, "->", repr(exc))
    print("total wall time of the batch (build + launch + all cases): %.1f s" % elapsed)
    print("ALL PASSED" if failed == 0 else "%d FAILED" % failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()