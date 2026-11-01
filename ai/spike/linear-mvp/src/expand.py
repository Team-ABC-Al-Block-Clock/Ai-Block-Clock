"""에이전트 전개 — 백엔드가 끼워 보낸 에이전트 정의를 Runtime 이 펼친다. 계약 v2.0 §5.2.

사용자에게는 블록 하나로 보이지만 그 안에 노드 여럿과 분기가 들어 있다. 백엔드는 저장된
정의를 **그대로** 보내고, 펼치는 것도 정산을 귀속하는 것도 Runtime 이 한다.
평평한 프롬프트 배열로는 펼 수 없기 때문이다 — 안에 분기 · 분할 · 병합이 있다.

펼치는 규칙
  1. 내부 노드 id 에 `<에이전트 id>.` 접두를 붙인다. 부모 그래프와 섞이지 않는다
  2. 들어오는 엣지는 **내부 진입 노드**로, 나가는 엣지는 **내부 종단 노드**로 잇는다
  3. 내부 노드에 `owner_id` 가 없으면 에이전트 소유자로 채운다 — 정산 귀속 키다
  4. 중첩은 깊이 1까지. 에이전트 안의 에이전트는 거부한다

에이전트의 **공개 입출력은 따로 선언하지 않는다** — 내부 진입 노드의 `input` 과 종단 노드의
`output` 에서 파생된다(`public_io`). 옛 `agent.input_schema_id` · `output_schema_id` 는 무시한다.
"""
from __future__ import annotations

from typing import Any

SEP = "."


class ExpandError(Exception):
    """에이전트 정의가 펼칠 수 없는 모양이다."""

    def __init__(self, code: str, node_id: str | None, message: str):
        super().__init__(message)
        self.code = code
        self.node_id = node_id
        self.message = message


def expand(wf: dict) -> tuple[dict, dict]:
    """에이전트를 펼친 워크플로우와 통계를 돌려준다. 에이전트가 없으면 그대로 돌려준다."""
    nodes = wf.get("nodes") or []
    agents = [n for n in nodes if (n or {}).get("type") == "agent"]
    stats = {
        "canvas_node_count": len(nodes),
        "expanded_node_count": len(nodes),
        "agent_depth": 1 if agents else 0,
    }
    if not agents:
        return wf, stats

    edges = list(wf.get("edges") or _linear_edges(nodes))
    flat_nodes: list[dict] = []
    flat_edges: list[dict] = []
    rewired: dict[str, dict[str, list[str]]] = {}      # 에이전트 id → 내부 진입 · 종단

    for node in nodes:
        if (node or {}).get("type") != "agent":
            flat_nodes.append(node)
            continue
        inner_nodes, inner_edges, entries, terminals = _expand_one(node)
        flat_nodes.extend(inner_nodes)
        flat_edges.extend(inner_edges)
        rewired[node["id"]] = {"entries": entries, "terminals": terminals}

    for edge in edges:
        flat_edges.extend(_rewire(edge, rewired))

    stats["expanded_node_count"] = len(flat_nodes)
    flat = dict(wf)
    flat["nodes"] = flat_nodes
    flat["edges"] = flat_edges
    return flat, stats


def public_io(node: dict) -> dict[str, Any]:
    """에이전트의 공개 입출력 — 내부 진입 노드의 `input` · 종단 노드의 `output` 에서 파생(계약 v2.0 §5.2).

    마켓 카드 · 백엔드 저장용. 진입 노드에 `input.example` 이 없으면 입력은 "자유"(None)다.
    """
    from . import graph as graph_mod        # graph 가 expand 를 쓰므로 여기서만 지역 import

    inner_nodes, _, entries, terminals = _expand_one(node)
    by_id = {n["id"]: n for n in inner_nodes}
    examples = [graph_mod.entry_input_example(by_id[e]) for e in entries]
    outputs = [graph_mod.public_output(graph_mod.output_spec_of(by_id[t]))
               for t in terminals if by_id[t].get("type") != "branch"]
    return {"input": {"example": examples[0]} if examples and examples[0] else None,
            "output": outputs[0] if outputs else None}


def _linear_edges(nodes: list[dict]) -> list[dict]:
    ids = [n.get("id") for n in nodes]
    return [{"from": a, "to": b} for a, b in zip(ids, ids[1:])]


def _expand_one(node: dict) -> tuple[list[dict], list[dict], list[str], list[str]]:
    agent = node.get("agent") or {}
    agent_id = node["id"]
    inner = agent.get("nodes") or []
    if not inner:
        raise ExpandError("AGENT_DEFINITION_MISSING", agent_id, "에이전트에 nodes 가 없습니다")
    if any((n or {}).get("type") == "agent" for n in inner):
        raise ExpandError("AGENT_NESTING_TOO_DEEP", agent_id,
                          "에이전트 안에 에이전트를 넣을 수 없습니다 (중첩 깊이 1)")

    inner_edges_src = agent.get("edges") or _linear_edges(inner)
    prefixed = {n["id"]: f"{agent_id}{SEP}{n['id']}" for n in inner}

    incoming_inner = {prefixed[n["id"]]: 0 for n in inner}
    outgoing_inner = {prefixed[n["id"]]: 0 for n in inner}
    inner_edges = []
    for e in inner_edges_src:
        src, dst = prefixed.get(e.get("from")), prefixed.get(e.get("to"))
        if not src or not dst:
            raise ExpandError("AGENT_EDGE_UNKNOWN_NODE", agent_id,
                              f"에이전트 내부 엣지가 없는 노드를 가리킵니다: {e}")
        inner_edges.append({**e, "from": src, "to": dst})
        outgoing_inner[src] += 1
        incoming_inner[dst] += 1

    entries = [nid for nid, count in incoming_inner.items() if count == 0]
    terminals = [nid for nid, count in outgoing_inner.items() if count == 0]

    inner_nodes = []
    for n in inner:
        copied = dict(n)
        copied["id"] = prefixed[n["id"]]
        if copied.get("type") == "branch" and isinstance(copied.get("condition"), dict):
            # 조건이 가리키는 내부 노드도 접두를 따라간다. 백엔드는 내부 id 로 저장한다
            copied["condition"] = dict(copied["condition"])
            copied["condition"]["left"] = _prefix_ref(copied["condition"].get("left"), prefixed)
        copied.setdefault("owner_id", agent.get("owner_id"))
        copied.setdefault("owner_name", node.get("owner_name"))
        copied["agent_id"] = agent.get("agent_id")
        inner_nodes.append(copied)

    return inner_nodes, inner_edges, entries, terminals


def _rewire(edge: dict, rewired: dict[str, dict[str, list[str]]]) -> list[dict]:
    """에이전트를 가리키는 엣지를 내부 진입 · 종단 노드로 바꿔 단다."""
    src, dst = edge.get("from"), edge.get("to")
    sources = rewired.get(src, {}).get("terminals", [src])
    targets = rewired.get(dst, {}).get("entries", [dst])
    out = []
    for s in sources:
        for t in targets:
            new_edge = {**edge, "from": s, "to": t}
            # 병합 키는 캔버스에서 보이는 이름(에이전트 id)을 유지한다
            if src in rewired:
                new_edge.setdefault("port", src)
            out.append(new_edge)
    return out


def _prefix_ref(ref: object, prefixed: dict[str, str]) -> object:
    """`n1.competition_level` → `a1.n1.competition_level`. 내부 노드를 가리킬 때만 바꾼다."""
    if not isinstance(ref, str):
        return ref
    head, sep, rest = ref.partition(SEP)
    if head in prefixed:
        return prefixed[head] + sep + rest
    return ref
