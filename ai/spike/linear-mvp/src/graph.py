"""그래프 실행 — edges · 위상 정렬 · fan-in · 조건 분기 · 출력 선언 · 규모 상한. 계약 v2.1 §5.

**연결 호환 검사는 없다.** 어떤 노드 사이에도 선을 그을 수 있고, 다음 노드는 앞 결과 전체를
받는다(입력 형식 폐지, 09-25). 실행 규칙은 하나다.

> 노드는 **실제로 실행된** 상위 노드들의 결과를 받는다. 하나면 그 payload 그대로,
> 여럿이면 edge `port` 이름으로 묶은 객체로. 분기 노드는 받은 것을 그대로
> 선택된 경로에 넘긴다.

이 규칙 하나로 직선 · 분할 · 병합 · 합류가 전부 같은 코드를 탄다.
**분기 조건 평가는 코드가 한다. LLM 을 쓰지 않는다**(설계 원칙 1) — 같은 입력이면
항상 같은 경로가 나와야 정산과 견적의 근거가 된다.

노드의 출력은 판매자가 선언한다 — `output: {kind: "text"}` 또는 `{kind: "json", example: {...}}`.
플랫폼 견본(카탈로그)은 09-26 에 없앴다. JSON 예시의 모양이 곧 강제 규격이다(`src/shape`).
"""
from __future__ import annotations

import itertools
import operator
from dataclasses import dataclass, field
from typing import Any

from . import expand as expand_mod
from . import shape

# 계약 §5.6 — 넷 다 정의만 보면 정해지는 정적인 값이다
LIMIT_CANVAS_NODES = 8
LIMIT_EXPANDED_NODES = 20
LIMIT_LONGEST_PATH = 8
LIMIT_AGENT_DEPTH = 1
WARN_LONGEST_PATH = 7

# 계약 v2.0 §5.2 — 받으면 무시하고 DEPRECATED_FIELD 경고를 낸다
DEPRECATED_NODE_FIELDS = ("input_schema_id", "unit_price", "base_cost", "premium_rate", "context")
OUTPUT_KINDS = ("text", "json")

OPS = {
    "==": operator.eq, "!=": operator.ne, ">": operator.gt,
    ">=": operator.ge, "<": operator.lt, "<=": operator.le,
}

# 계약 v2.0 §5.4 — 필드 종류별로 허용하는 분기 연산자. 배열 · 객체는 분기 기준이 될 수 없다
BRANCH_OPS_BY_KIND = {
    "string": {"==", "!=", "in"},
    "number": set(OPS) | {"in"},
    "boolean": {"==", "!="},
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


def reaches(graph: Graph, src: str, dst: str) -> bool:
    """src 에서 간선을 따라 dst 에 닿는가 — dst 의 (임의) 상위인가."""
    seen, stack = set(), [src]
    while stack:
        cur = stack.pop()
        for e in graph.outgoing.get(cur) or []:
            if e["to"] == dst:
                return True
            if e["to"] not in seen:
                seen.add(e["to"])
                stack.append(e["to"])
    return False


def check_branch_ref(graph: Graph, node_id: str, condition: dict) -> tuple[str, str] | None:
    """분기 조건 `left` 가 상위 노드 **출력 예시의 스칼라 필드**를 가리키고 연산자가 타입에 맞는가.

    저장 시점 판정(계약 v2.1 §5.4 · §5.7). 어기면 `(오류 코드, 메시지)`, 맞으면 None.
    실행 시점의 `BRANCH_CONDITION_INVALID`(선택 필드가 비어 옴)는 여기서 다 막지 못하므로 남겨 둔다.
    """
    left = str(condition.get("left") or "")
    src = next((k for k in sorted(graph.nodes, key=len, reverse=True)
                if left == k or left.startswith(k + ".")), None)
    if src is None:
        return "BRANCH_REF_UNKNOWN_FIELD", f"조건이 가리키는 노드가 없습니다: {left}"
    if graph.is_branch(src):
        return "BRANCH_REF_UNKNOWN_FIELD", f"분기 노드({src})를 직접 가리킬 수 없습니다 — 원천 노드를 가리키세요"
    if not reaches(graph, src, node_id):
        return "BRANCH_REF_UNKNOWN_FIELD", f"{src} 는 이 분기의 상위 노드가 아닙니다"
    spec = output_spec_of(graph.node(src))
    if not spec or spec.get("kind") != "json":
        return "BRANCH_ON_TEXT_OUTPUT", f"{src} 는 글 출력이라 분기 기준이 될 수 없습니다"
    if not isinstance(spec.get("example"), dict):
        return None                                   # OUTPUT_EXAMPLE_INVALID · UNKNOWN_SAMPLE 이 따로 잡는다
    path = left[len(src):].lstrip(".")
    kind = shape.field_kind(spec["example"], path)
    if kind is None:
        return "BRANCH_REF_UNKNOWN_FIELD", f"{src} 의 출력 예시에 {path or '(필드 없음)'} 필드가 없습니다"
    if kind not in shape.SCALAR_KINDS:
        return "BRANCH_REF_UNKNOWN_FIELD", f"{src}.{path} 은(는) {kind} 라 비교할 수 없습니다 — 문자 · 숫자 · 참거짓 필드를 쓰세요"
    op = condition.get("op")
    if op not in BRANCH_OPS_BY_KIND[kind]:
        allowed = " ".join(sorted(BRANCH_OPS_BY_KIND[kind]))
        return "BRANCH_OP_TYPE_MISMATCH", f"{path} 은(는) {kind} 라 {op} 을(를) 쓸 수 없습니다 (허용: {allowed})"
    return None


def longest_path(graph: Graph) -> int:
    """가능한 가장 긴 경로의 노드 수. 실제로 도는 경로가 아니다(계약 §5.6)."""
    depth = {n: 1 for n in graph.order}
    for node_id in graph.order:
        for e in graph.outgoing[node_id]:
            depth[e["to"]] = max(depth[e["to"]], depth[node_id] + 1)
    return max(depth.values()) if depth else 0


# --- 출력 · 입력 선언 (계약 v2.0 §5.2 · §5.5) ---------------------------------

def output_spec_of(node: dict) -> dict[str, Any] | None:
    """노드가 선언한 출력 `{kind, example}`. 분기 노드는 None. 계약 v2.1 §5.5.

    옛 형식은 한 버전 동안 읽는다(경고) — `output.sample` · `output_schema_id` 의 견본 id 를
    `shape.LEGACY_EXAMPLES` 의 예시로 바꾼다. `common.text@1` 은 글. 모르는 id 면 `example: None`
    과 `legacy` 를 담아 돌려주고 검증이 `UNKNOWN_SAMPLE` 로 잡는다.
    """
    if node.get("type") == "branch":
        return None
    out = node.get("output")
    legacy = None
    if isinstance(out, dict) and out.get("kind"):
        if out.get("kind") != "json" or "example" in out:
            return {"kind": out.get("kind"), "example": out.get("example")}
        legacy = out.get("sample")
    legacy = legacy or node.get("output_schema_id")
    if not legacy:
        return {"kind": "json", "example": None} if isinstance(out, dict) and out.get("kind") == "json" else None
    return _legacy_spec(legacy)


def _legacy_spec(sample_id: str) -> dict[str, Any]:
    if sample_id in shape.LEGACY_TEXT_IDS:
        return {"kind": "text", "example": None, "legacy": sample_id}
    return {"kind": "json", "example": shape.LEGACY_EXAMPLES.get(sample_id), "legacy": sample_id}


def output_alias_of(node: dict) -> str | None:
    """옛 견본 id 로 출력을 선언했으면 그 id. 경고 문구에 쓴다."""
    spec = output_spec_of(node)
    return spec.get("legacy") if spec else None


def public_output(spec: dict[str, Any] | None) -> dict[str, Any] | None:
    """응답 · 마켓 카드용 — 내부 표시(`legacy`)를 뺀 `{kind, example}`."""
    return {"kind": spec["kind"], "example": spec.get("example")} if spec else None


def entry_input_example(node: dict) -> dict | None:
    """진입 노드의 사용자 입력 예시(`input.example`). 옛 `input.sample` · `input_schema_id` 도 읽는다."""
    inp = node.get("input")
    if isinstance(inp, dict) and isinstance(inp.get("example"), dict):
        return inp["example"]
    legacy = (inp.get("sample") if isinstance(inp, dict) else None) or node.get("input_schema_id")
    return shape.LEGACY_EXAMPLES.get(legacy) if legacy else None


def input_alias_of(node: dict) -> str | None:
    inp = node.get("input")
    if isinstance(inp, dict) and isinstance(inp.get("example"), dict):
        return None
    return (inp.get("sample") if isinstance(inp, dict) else None) or node.get("input_schema_id")


def upstream_output_spec(graph: Graph, node_id: str) -> dict[str, Any] | None:
    """노드가 내는 형식. 분기 노드는 받은 것을 그대로 넘기므로 상위의 형식을 물려받는다."""
    node = graph.node(node_id)
    if node.get("type") != "branch":
        return output_spec_of(node)
    for e in graph.incoming.get(node_id) or []:
        found = upstream_output_spec(graph, e["from"])
        if found:
            return found
    return None


# --- fan-in · 분기 -----------------------------------------------------------

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


def fan_in_sources(graph: Graph, node_id: str, payloads: dict[str, Any],
                   branch_taken: dict[str, bool] | None = None) -> list[tuple[str, str | None]]:
    """받는 결과가 어느 노드에서 왔는지 — `(원천 노드 id, port)` 목록. 분기 노드는 통과시킨다.

    다음 노드 LLM 에게 "## 입력" 블록의 절 제목을 붙일 때 쓴다(계약 v2.0 §5.4). `fan_in` 이
    `{port: payload}` 로 묶은 것과 같은 순서 · 같은 키다.
    """
    out: list[tuple[str, str | None]] = []
    for e in live_edges(graph, node_id, payloads, branch_taken):
        src = e["from"]
        if graph.is_branch(src):
            out.extend(fan_in_sources(graph, src, payloads, branch_taken))
        else:
            out.append((src, e.get("port")))
    return out


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
    # 문자열은 대소문자 · 앞뒤 공백을 무시한다 — AI 가 "High " 로 써도 high 로 본다(09-26 결정)
    value, right = _fold(value), _fold(right)
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


def _fold(v: Any) -> Any:
    if isinstance(v, str):
        return v.strip().lower()
    if isinstance(v, (list, tuple)):
        return [_fold(x) for x in v]
    return v


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
    """실행 전 견적 — **가능한 분기 조합 중 가장 많은 노드가 도는 경우**의 노드 목록. 금액은 없다.

    경로 길이가 아니라 "그 경우에 실제로 도는 노드들"이다. 분할 · 병합이 있으면 여러 노드가
    함께 돌기 때문에 최장 경로로는 답이 안 나온다. 단가 합산은 백엔드가 이 목록으로 한다
    (계약 v2.0 §7.5). 분기 노드 자체는 과금하지 않으므로 목록에서 뺀다.
    """
    wf, _ = expand_mod.expand(wf)
    graph = build(wf)
    branches = [n for n in graph.order if graph.is_branch(n)]
    best: list[str] = []
    for combo in itertools.product([True, False], repeat=len(branches)):
        taken = dict(zip(branches, combo))
        ran = [n for n in executed_nodes(graph, taken) if not graph.is_branch(n)]
        if len(ran) > len(best):
            best = ran
    return {"worst_case_nodes": best, "worst_case_node_count": len(best)}


def validate(wf: dict) -> dict:
    """실행하지 않고 정의만 검사한다(계약 v2.0 §5.7 · §7.5).

    백엔드 · 프론트가 상한 · 순환 · 분기 참조 규칙을 복제하지 않아도 되게 하려는
    엔드포인트의 본체다. 저장 시점과 실행 시점의 판정이 갈리지 않는다.
    """
    errors: list[dict] = []
    warnings: list[dict] = []
    nodes_list = wf.get("nodes")
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

    def warn(code: str, node_id: str | None, message: str) -> None:
        warnings.append({"code": code, "node_id": node_id, "message": message})

    # 노드 자체
    for node_id in graph.order:
        node = graph.node(node_id)
        kind = node.get("type", "llm")
        if kind == "branch":
            if not isinstance(node.get("condition"), dict):
                err("BRANCH_CONDITION_MISSING", node_id, "분기 노드에 condition 이 없습니다")
            else:
                problem = check_branch_ref(graph, node_id, node["condition"])
                if problem:
                    err(problem[0], node_id, problem[1])
            whens = [bool(e.get("when")) for e in graph.outgoing.get(node_id) or []]
            if sorted(whens) != [False, True]:
                err("BRANCH_EDGES_INVALID", node_id,
                    "분기 노드는 when: true · when: false 간선이 각각 하나여야 합니다")
            continue
        if not str(node.get("prompt", "")).strip():
            err("MISSING_PROMPT", node_id, "prompt 가 비어 있습니다")

        spec = output_spec_of(node)
        if spec is None:
            err("MISSING_OUTPUT", node_id,
                'output 이 없습니다 — {"kind": "text"} 또는 {"kind": "json", "example": {...}}')
        elif spec["kind"] not in OUTPUT_KINDS:
            err("OUTPUT_KIND_INVALID", node_id, "output.kind 는 text · json 이어야 합니다")
        elif spec["kind"] == "json" and spec.get("legacy") and spec.get("example") is None:
            err("UNKNOWN_SAMPLE", node_id,
                f"견본 목록은 없어졌습니다(v2.1) — output.example 에 JSON 예시를 주세요: {spec['legacy']}")
        elif spec["kind"] == "json":
            problem = shape.check_example(spec.get("example"))
            if problem:
                err("OUTPUT_EXAMPLE_INVALID", node_id, f"output.example — {problem}")

        in_alias = input_alias_of(node)
        if in_alias and in_alias not in shape.LEGACY_EXAMPLES:
            err("UNKNOWN_SAMPLE", node_id,
                f"견본 목록은 없어졌습니다(v2.1) — input.example 에 JSON 예시를 주세요: {in_alias}")
        inp = node.get("input")
        if isinstance(inp, dict) and "example" in inp:
            problem = shape.check_example(inp.get("example"))
            if problem:
                err("OUTPUT_EXAMPLE_INVALID", node_id, f"input.example — {problem}")

        # 없어진 필드 — 무시(견본 id 는 예시로 바꿔 읽음)하고 경고만. 백엔드가 옛 형식을 보내도 실행은 된다
        deprecated = [k for k in DEPRECATED_NODE_FIELDS if k in node and k != "input_schema_id"]
        if output_alias_of(node):
            deprecated.append(f"견본 id {output_alias_of(node)}(출력 — 예시로 바꿔 읽음)")
        if in_alias:
            deprecated.append(f"견본 id {in_alias}(입력 — 예시로 바꿔 읽음)")
        if deprecated:
            warn("DEPRECATED_FIELD", node_id,
                 ", ".join(deprecated) + " — 견본 · 옛 필드는 v2.1 에서 쓰지 않습니다. 다음 버전에서 사라집니다")

    # 연결 호환 검사는 없다(계약 v2.0 §5.3). 어떤 노드든 이어지고 합류 양쪽 형식이 달라도 된다.
    for canvas_node in nodes_list:
        agent = (canvas_node or {}).get("agent") or {}
        dep = [k for k in ("input_schema_id", "output_schema_id") if k in agent]
        if canvas_node.get("type") == "agent" and dep:
            warn("DEPRECATED_FIELD", canvas_node.get("id"),
                 "agent." + " · agent.".join(dep)
                 + " 은(는) v2.0 에서 쓰지 않습니다 — 공개 입출력은 내부 진입 · 종단 노드에서 파생됩니다")
    if isinstance(wf.get("output"), dict) and "schema_id" in wf["output"]:
        warn("DEPRECATED_FIELD", None,
             "output.schema_id 은(는) v2.0 에서 쓰지 않습니다 — 종단 노드의 봉투가 산출물입니다")

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
        warn("LONG_PATH", None, f"최장 경로 {path}단계. 5분을 넘길 수 있습니다")

    return {"valid": not errors, "errors": errors, "warnings": warnings,
            "stats": stats, "estimate": estimate(wf)}
