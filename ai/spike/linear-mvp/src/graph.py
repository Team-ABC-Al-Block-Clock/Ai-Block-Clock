"""그래프 실행 — edges · 위상 정렬 · fan-in · 조건 분기 · 규모 상한. 계약 §5.3 ~ §5.7.

실행 규칙은 하나다.

> 노드는 **실제로 실행된** 상위 노드들의 결과를 받는다. 하나면 그 payload 그대로,
> 여럿이면 edge `port` 이름으로 묶은 객체로. 분기 노드는 받은 것을 그대로
> 선택된 경로에 넘긴다.

이 규칙 하나로 직선 · 분할 · 병합 · 합류가 전부 같은 코드를 탄다.
**분기 조건 평가는 코드가 한다. LLM 을 쓰지 않는다**(설계 원칙 1) — 같은 입력이면
항상 같은 경로가 나와야 정산과 견적의 근거가 된다.
"""
from __future__ import annotations

import itertools
import operator
from dataclasses import dataclass, field
from typing import Any

from . import catalog
from . import expand as expand_mod

# 계약 §5.6 — 넷 다 정의만 보면 정해지는 정적인 값이다
LIMIT_CANVAS_NODES = 8
LIMIT_EXPANDED_NODES = 20
LIMIT_LONGEST_PATH = 8
LIMIT_AGENT_DEPTH = 1
WARN_LONGEST_PATH = 7

OPS = {
    "==": operator.eq, "!=": operator.ne, ">": operator.gt,
    ">=": operator.ge, "<": operator.lt, "<=": operator.le,
}


class GraphError(Exception):
    """워크플로우 정의 자체가 틀렸다. 실행 전에 걸러진다."""


class ConditionInvalid(Exception):
    """분기 조건이 가리키는 경로가 실행 결과에 없다 — 수요자 책임."""


@dataclass
class Graph:
    nodes: dict[str, dict]
    order: list[str]                                  # 위상 정렬 순서
    incoming: dict[str, list[dict]] = field(default_factory=dict)
    outgoing: dict[str, list[dict]] = field(default_factory=dict)

    def node(self, node_id: str) -> dict:
        return self.nodes[node_id]

    def is_branch(self, node_id: str) -> bool:
        return self.nodes[node_id].get("type") == "branch"

    @property
    def entries(self) -> list[str]:
        """edges 의 to 에 없는 노드. 사용자 입력을 받는다(계약 §5.4)."""
        return [n for n in self.order if not self.incoming.get(n)]


def build(wf: dict) -> Graph:
    """워크플로우 정의를 그래프로. `edges` 가 없으면 배열 순서대로 직선으로 잇는다."""
    nodes_list = wf.get("nodes") or []
    nodes = {}
    for n in nodes_list:
        node_id = n.get("id")
        if not node_id:
            raise GraphError("id 가 없는 노드가 있습니다")
        if node_id in nodes:
            raise GraphError(f"노드 id 가 중복입니다: {node_id}")
        nodes[node_id] = n

    edges = wf.get("edges")
    if not edges:
        ids = list(nodes)
        edges = [{"from": a, "to": b} for a, b in zip(ids, ids[1:])]

    incoming: dict[str, list[dict]] = {k: [] for k in nodes}
    outgoing: dict[str, list[dict]] = {k: [] for k in nodes}
    for e in edges:
        src, dst = e.get("from"), e.get("to")
        if src not in nodes or dst not in nodes:
            raise GraphError(f"edges 가 없는 노드를 가리킵니다: {src} -> {dst}")
        outgoing[src].append(e)
        incoming[dst].append(e)

    return Graph(nodes=nodes, order=_topo_order(nodes, incoming, outgoing),
                 incoming=incoming, outgoing=outgoing)


def _topo_order(nodes: dict, incoming: dict, outgoing: dict) -> list[str]:
    """위상 정렬. 정의 배열 순서를 우선해 같은 정의면 항상 같은 실행 순서가 나온다."""
    remaining = {k: len(v) for k, v in incoming.items()}
    ready = [k for k in nodes if remaining[k] == 0]
    order: list[str] = []
    while ready:
        ready.sort(key=lambda k: list(nodes).index(k))
        current = ready.pop(0)
        order.append(current)
        for e in outgoing[current]:
            remaining[e["to"]] -= 1
            if remaining[e["to"]] == 0:
                ready.append(e["to"])
    if len(order) != len(nodes):
        stuck = sorted(set(nodes) - set(order))
        raise GraphError(f"순환이 있습니다: {', '.join(stuck)}")
    return order


def longest_path(graph: Graph) -> int:
    """가능한 가장 긴 경로의 노드 수. 실제로 도는 경로가 아니다(계약 §5.6)."""
    depth = {n: 1 for n in graph.order}
    for node_id in graph.order:
        for e in graph.outgoing[node_id]:
            depth[e["to"]] = max(depth[e["to"]], depth[node_id] + 1)
    return max(depth.values()) if depth else 0


def branch_constraints(graph: Graph) -> dict[str, set[tuple[str, bool]]]:
    """노드마다 "거기까지 가려면 참이어야 하는 분기 조건" 집합.

    상위가 여럿이면 **교집합**이다 — 어느 경로로든 도착하면 실행되기 때문이다.
    그래서 분기 양쪽이 만나는 합류 지점에서는 제약이 사라진다.
    """
    deps: dict[str, set[tuple[str, bool]]] = {}
    for node_id in graph.order:
        edges = graph.incoming.get(node_id) or []
        if not edges:
            deps[node_id] = set()
            continue
        per_edge = [_edge_constraints(graph, deps, e) for e in edges]
        deps[node_id] = set.intersection(*per_edge) if per_edge else set()
    return deps


def _edge_constraints(graph: Graph, deps: dict, edge: dict) -> set[tuple[str, bool]]:
    src = edge["from"]
    base = set(deps.get(src, set()))
    if graph.is_branch(src):
        base.add((src, bool(edge.get("when"))))
    return base


def is_join(graph: Graph, node_id: str, deps: dict) -> bool:
    """들어오는 간선들이 서로 배타적인가 — 분기 뒤 합류인가, 그냥 병합인가."""
    edges = graph.incoming.get(node_id) or []
    if len(edges) < 2:
        return False
    sets = [_edge_constraints(graph, deps, e) for e in edges]
    for i, a in enumerate(sets):
        for b in sets[i + 1:]:
            if any((bid, not w) in b for bid, w in a):
                return True
    return False


def live_edges(graph: Graph, node_id: str, payloads: dict[str, Any],
               branch_taken: dict[str, bool] | None = None) -> list[dict]:
    """지금 "살아 있는" 들어오는 간선.

    상위가 실행됐고, 그 상위가 분기라면 **선택된 쪽** 간선만 살아 있다. 이 판정 하나로
    분기 뒤 합류가 저절로 처리된다 — 도착한 쪽 간선만 남기 때문이다.
    """
    taken = branch_taken or {}
    alive = []
    for e in graph.incoming.get(node_id) or []:
        src = e["from"]
        if src not in payloads:
            continue
        if graph.is_branch(src) and bool(e.get("when")) != taken.get(src):
            continue
        alive.append(e)
    return alive


def fan_in(graph: Graph, node_id: str, payloads: dict[str, Any],
           branch_taken: dict[str, bool] | None = None) -> Any:
    """실행된 상위들의 결과를 규칙대로 모아 준다(계약 §5.4)."""
    edges = live_edges(graph, node_id, payloads, branch_taken)
    if not edges:
        return None
    if len(edges) == 1:
        return payloads[edges[0]["from"]]
    return {(e.get("port") or e["from"]): payloads[e["from"]] for e in edges}


def skip_reason(graph: Graph, node_id: str, payloads: dict[str, Any],
                branch_taken: dict[str, bool], skipped: dict[str, str]) -> str:
    """실행되지 않는 이유(계약 §5 `not_executed[].reason`)."""
    for e in graph.incoming.get(node_id) or []:
        src = e["from"]
        if graph.is_branch(src) and src in payloads and bool(e.get("when")) != branch_taken.get(src):
            return "branch_not_taken"
    for e in graph.incoming.get(node_id) or []:
        if e["from"] in skipped:
            return skipped[e["from"]]
    return "upstream_failed"


def evaluate(condition: dict, payloads: dict[str, Any]) -> bool:
    """분기 조건 평가. `left` 는 "<노드ID>.<payload 경로>" 이며 임의 상위를 가리킬 수 있다."""
    left_ref = str(condition.get("left") or "")
    # 전개된 노드 id 에는 점이 들어간다(`a1.n1`). 가장 긴 노드 id 부터 맞춘다
    node_id = next((k for k in sorted(payloads, key=len, reverse=True)
                    if left_ref == k or left_ref.startswith(k + ".")), None)
    if node_id is None:
        raise ConditionInvalid(f"조건이 가리키는 노드가 실행되지 않았습니다: {left_ref}")
    path = left_ref[len(node_id):].lstrip(".")
    value = payloads[node_id]
    for key in [p for p in path.split(".") if p]:
        if isinstance(value, dict) and key in value:
            value = value[key]
        elif isinstance(value, list) and key.isdigit() and int(key) < len(value):
            value = value[int(key)]
        else:
            raise ConditionInvalid(f"조건이 가리키는 경로가 결과에 없습니다: {left_ref}")

    op = condition.get("op")
    right = condition.get("right")
    if op == "in":
        if not isinstance(right, (list, tuple, str)):
            raise ConditionInvalid("in 조건의 right 는 배열이어야 합니다")
        return value in right
    if op not in OPS:
        raise ConditionInvalid(f"지원하지 않는 연산자입니다: {op}")
    try:
        return bool(OPS[op](value, right))
    except TypeError as e:
        raise ConditionInvalid(f"비교할 수 없는 값입니다: {left_ref}") from e


def executed_nodes(graph: Graph, taken: dict[str, bool]) -> list[str]:
    """분기 결과를 정해 두면 어느 노드가 도는지 정해진다. 실행 없이 세는 계산이다."""
    ran: list[str] = []
    ran_set: set[str] = set()
    for node_id in graph.order:
        edges = graph.incoming.get(node_id) or []
        if edges:
            alive = False
            for e in edges:
                src = e["from"]
                if src not in ran_set:
                    continue
                if graph.is_branch(src) and bool(e.get("when")) != taken.get(src, True):
                    continue
                alive = True
                break
            if not alive:
                continue
        ran.append(node_id)
        ran_set.add(node_id)
    return ran


def estimate(wf: dict) -> dict:
    """실행 전 견적 — **가능한 분기 조합 중 가장 비싼 경우**를 잡는다.

    경로 길이가 아니라 "그 경우에 실제로 도는 노드들의 합"이다. 분할 · 병합이 있으면
    여러 노드가 함께 돌기 때문에 최장 경로로는 답이 안 나온다.

    단가(`unit_price`)는 백엔드가 요청에 실어 보내는 값이다. 없는 노드는 0으로 두고
    `unpriced_nodes` 에 남겨 "견적을 믿을 수 없다"는 것을 드러낸다(계약 §8.2).
    분기 노드 자체는 과금하지 않는다(계약 §8.4).
    """
    wf, _ = expand_mod.expand(wf)
    graph = build(wf)
    branches = [n for n in graph.order if graph.is_branch(n)]
    best_total, best_nodes = 0.0, []
    for combo in itertools.product([True, False], repeat=len(branches)):
        taken = dict(zip(branches, combo))
        ran = executed_nodes(graph, taken)
        total = sum(float(graph.node(n).get("unit_price") or 0)
                    for n in ran if not graph.is_branch(n))
        if total >= best_total and (total > best_total or not best_nodes):
            best_total, best_nodes = total, [n for n in ran if not graph.is_branch(n)]
    unpriced = [n for n in best_nodes if graph.node(n).get("unit_price") is None]
    return {"max_charge_abc": round(best_total, 4),
            "worst_case_nodes": best_nodes,
            "unpriced_nodes": unpriced}


def validate(wf: dict) -> dict:
    """실행하지 않고 정의만 검사한다(계약 §5.7 · §7.5).

    백엔드 · 프론트가 상한 · 순환 · 스키마 호환 규칙을 복제하지 않아도 되게 하려는
    엔드포인트의 본체다. 저장 시점과 실행 시점의 판정이 갈리지 않는다.
    """
    errors: list[dict] = []
    warnings: list[dict] = []
    nodes_list = wf.get("nodes")
    # 스키마 · edges 가 아예 없는 정의는 09-19 이전 자유 텍스트 워크플로우다.
    # 스모크 직선을 지키기 위해 출력 형식 필수 규칙만 경고로 낮춘다(09-22 이후 제거)
    legacy_text = (isinstance(nodes_list, list)
                   and not wf.get("edges")
                   and not any((n or {}).get("output_schema_id") for n in nodes_list))
    if not isinstance(nodes_list, list) or not nodes_list:
        return {"valid": False,
                "errors": [{"code": "NO_NODES", "node_id": None,
                            "message": "nodes 는 1개 이상의 배열이어야 합니다"}],
                "warnings": [], "stats": {}}

    try:
        wf, expand_stats = expand_mod.expand(wf)
    except expand_mod.ExpandError as e:
        return {"valid": False,
                "errors": [{"code": e.code, "node_id": e.node_id, "message": e.message}],
                "warnings": [], "stats": {"node_count": len(nodes_list)}}

    try:
        graph = build(wf)
    except GraphError as e:
        return {"valid": False,
                "errors": [{"code": "INVALID_GRAPH", "node_id": None, "message": str(e)}],
                "warnings": [], "stats": {"node_count": len(nodes_list)}}

    def err(code: str, node_id: str | None, message: str) -> None:
        errors.append({"code": code, "node_id": node_id, "message": message})

    # 노드 자체
    for node_id in graph.order:
        node = graph.node(node_id)
        kind = node.get("type", "llm")
        if kind == "branch":
            if not isinstance(node.get("condition"), dict):
                err("BRANCH_CONDITION_MISSING", node_id, "분기 노드에 condition 이 없습니다")
            whens = [bool(e.get("when")) for e in graph.outgoing.get(node_id) or []]
            if sorted(whens) != [False, True]:
                err("BRANCH_EDGES_INVALID", node_id,
                    "분기 노드는 when: true · when: false 간선이 각각 하나여야 합니다")
            continue
        if not str(node.get("prompt", "")).strip():
            err("MISSING_PROMPT", node_id, "prompt 가 비어 있습니다")
        out_schema = node.get("output_schema_id")
        if not out_schema:
            if legacy_text:
                warnings.append({"code": "LEGACY_TEXT_NODE", "node_id": node_id,
                                 "message": "출력 형식이 없는 자유 텍스트 노드입니다"})
            else:
                err("MISSING_OUTPUT_SCHEMA", node_id, "output_schema_id 는 필수입니다")
        elif out_schema not in catalog.CATALOG:
            err("UNKNOWN_SCHEMA", node_id, f"카탈로그에 없는 형식입니다: {out_schema}")
        in_schema = node.get("input_schema_id")
        if in_schema and in_schema not in catalog.CATALOG:
            err("UNKNOWN_SCHEMA", node_id, f"카탈로그에 없는 형식입니다: {in_schema}")

    # 연결 호환 · 합류
    deps = branch_constraints(graph)
    for node_id in graph.order:
        node = graph.node(node_id)
        edges = graph.incoming.get(node_id) or []
        upstream_schemas = [_output_schema_of(graph, e["from"]) for e in edges]
        if is_join(graph, node_id, deps):
            if len(set(s for s in upstream_schemas if s)) > 1:
                err("JOIN_SCHEMA_MISMATCH", node_id,
                    "합류 지점에서 만나는 경로들의 output_schema_id 가 다릅니다: "
                    + ", ".join(sorted(set(s or "?" for s in upstream_schemas))))
        elif len(edges) > 1:
            if node.get("input_schema_id"):
                warnings.append({"code": "MERGE_INPUT_UNCHECKED", "node_id": node_id,
                                 "message": "병합 노드의 입력은 port 로 묶인 객체라 "
                                            "input_schema_id 로 검증하지 않습니다"})
            continue
        in_schema = node.get("input_schema_id")
        if in_schema and upstream_schemas and upstream_schemas[0]:
            if not catalog.is_compatible(upstream_schemas[0], in_schema):
                err("SCHEMA_INCOMPATIBLE", node_id,
                    f"{edges[0]['from']}의 출력({upstream_schemas[0]})이 "
                    f"{node_id}의 입력({in_schema})과 맞지 않습니다")

    # 에이전트 경계 — 내부 종단이 공개한 형식과 다르면 공급자 책임이다(계약 §5.2)
    for node_id in graph.order:
        boundary = expand_mod.boundary_of(graph.node(node_id))
        if boundary and graph.node(node_id).get("output_schema_id") != boundary["schema_id"]:
            err("AGENT_BOUNDARY_VIOLATION", node_id,
                f"에이전트가 공개한 출력({boundary['schema_id']})과 내부 종단의 출력"
                f"({graph.node(node_id).get('output_schema_id')})이 다릅니다")

    # 종단 노드와 output.schema_id
    output_schema = (wf.get("output") or {}).get("schema_id")
    terminals = [n for n in graph.order if not graph.outgoing.get(n)]
    if output_schema:
        terminal_schemas = {_output_schema_of(graph, n) for n in terminals}
        if terminal_schemas and output_schema not in terminal_schemas:
            err("OUTPUT_SCHEMA_MISMATCH", None,
                f"output.schema_id({output_schema})가 종단 노드의 출력과 다릅니다: "
                + ", ".join(sorted(s or "?" for s in terminal_schemas)))

    # 규모 상한
    path = longest_path(graph)
    stats = {"node_count": expand_stats["canvas_node_count"],
             "expanded_node_count": expand_stats["expanded_node_count"],
             "longest_path": path, "agent_depth": expand_stats["agent_depth"]}
    if stats["node_count"] > LIMIT_CANVAS_NODES:
        err("LIMIT_EXCEEDED", None, f"캔버스 노드는 {LIMIT_CANVAS_NODES}개까지입니다")
    if stats["expanded_node_count"] > LIMIT_EXPANDED_NODES:
        err("LIMIT_EXCEEDED", None, f"전개 후 노드는 {LIMIT_EXPANDED_NODES}개까지입니다")
    if path > LIMIT_LONGEST_PATH:
        err("LIMIT_EXCEEDED", None, f"최장 경로는 {LIMIT_LONGEST_PATH}단계까지입니다")
    elif path >= WARN_LONGEST_PATH:
        warnings.append({"code": "LONG_PATH", "node_id": None,
                         "message": f"최장 경로 {path}단계. 5분을 넘길 수 있습니다"})

    quote = estimate(wf)
    if quote["unpriced_nodes"]:
        warnings.append({"code": "UNPRICED_NODES", "node_id": None,
                         "message": "단가가 없는 노드가 있어 견적이 실제보다 낮습니다: "
                                    + ", ".join(quote["unpriced_nodes"])})
    return {"valid": not errors, "errors": errors, "warnings": warnings,
            "stats": stats, "estimate": quote}


def _output_schema_of(graph: Graph, node_id: str) -> str | None:
    """분기 노드는 받은 것을 그대로 넘기므로 상위의 출력 형식을 물려받는다."""
    node = graph.node(node_id)
    if node.get("type") != "branch":
        return node.get("output_schema_id")
    for e in graph.incoming.get(node_id) or []:
        found = _output_schema_of(graph, e["from"])
        if found:
            return found
    return None
