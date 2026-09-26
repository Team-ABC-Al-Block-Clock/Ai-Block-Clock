"""AI Runtime 스파이크 API — 백엔드(Spring)가 호출하는 계약.

실행:  uv run uvicorn src.api:app --host 0.0.0.0 --port 8000 --reload
문서:  http://localhost:8000/docs

흐름
  1) 백엔드가 POST /v1/runs (multipart) 로 워크플로우 정의 + 입력 + 파일을 보낸다
  2) 즉시 202 와 run_id 를 받는다. 실행은 백그라운드에서 돈다
  3) 백엔드가 GET /v1/runs/{run_id} 를 2초 간격으로 조회해 진행 상황을 프론트에 전달한다
  4) status 가 succeeded 가 되면 GET /v1/runs/{run_id}/pdf 로 결과물을 받는다
"""
from __future__ import annotations

import json
import os
import shutil
import tempfile
import threading
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from fastapi import (BackgroundTasks, Depends, FastAPI, File, Form, Header, HTTPException, Request,
                     UploadFile)
from fastapi.responses import FileResponse, JSONResponse

from . import graph, runner

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")

SERVICE_TOKEN = os.getenv("ABC_SERVICE_TOKEN", "")          # 비어 있으면 인증 검사를 건너뛴다(스파이크 기본값)
ALLOWED_SUFFIXES = {".xlsx", ".xls", ".csv"}
MAX_UPLOAD_MB = 10

app = FastAPI(
    title="ABC AI Runtime",
    version="2.0.0",
    description="워크플로우 실행기 — 그래프 실행 · 분기 · 에이전트 전개 · 판매자 선언 출력 · 토큰/USD 집계. 계약 v2.0.",
)

# 스파이크는 단일 프로세스 인메모리 저장이다. 재시작하면 목록이 사라진다.
_runs: dict[str, dict[str, Any]] = {}
_lock = threading.Lock()


def auth(x_abc_service_token: str | None = Header(default=None)) -> None:
    if SERVICE_TOKEN and x_abc_service_token != SERVICE_TOKEN:
        raise HTTPException(status_code=401, detail={"code": "UNAUTHORIZED", "message": "X-ABC-Service-Token 이 올바르지 않습니다"})


def _public(run: dict, include_node_output: bool = False) -> dict:
    """백엔드에 돌려줄 형태 — 계약 §7.2 그대로. 노드 출력 전문은 요청할 때만 포함한다.

    러너 내부 상태에는 프롬프트 · 마크다운 · 파일 경로가 있지만 밖으로는 내지 않는다.
    필드를 더하면 계약 문서도 같은 MR 에서 고친다.
    """
    public_keys = ("id", "name", "type", "status", "output", "agent_id", "owner_id",
                   "started_at", "finished_at", "duration_ms", "input_tokens", "output_tokens",
                   "retries", "repair_count", "model", "cost_usd", "skip_reason", "branch_taken")
    nodes = []
    for n in run["nodes"]:
        item = {k: n.get(k) for k in public_keys}
        item["type"] = n.get("type") or ("branch" if n.get("branch_taken") is not None else "llm")
        if include_node_output:
            item["output_payload"] = n.get("output_payload")
        nodes.append(item)

    # 진행률은 "돌아야 하는 LLM 노드" 기준이다 — 분기 노드와 건너뛴 노드는 세지 않는다(계약 §7.2)
    countable = [n for n in run["nodes"]
                 if n.get("type") != "branch" and n.get("branch_taken") is None
                 and n.get("status") != "skipped"]
    done = sum(1 for n in countable if n["status"] == "succeeded")
    total = len(countable)
    current = next((i for i, n in enumerate(run["nodes"]) if n["status"] == "running"), None)
    cur_node = run["nodes"][current] if current is not None else None

    result = None
    if run.get("result"):
        has_pdf = bool(run["result"].get("pdf"))
        has_envelope = bool(run["result"].get("envelope"))
        result = {
            "type": run["result"].get("type"),
            "output": run["result"].get("output"),          # 종단 노드의 선언 {kind, example}
            "artifact_url": f"/v1/runs/{run['run_id']}/artifact" if has_envelope else None,
            "pdf_url": f"/v1/runs/{run['run_id']}/pdf" if has_pdf else None,
        }

    return {
        "run_id": run["run_id"],
        "status": run["status"],                     # queued | running | succeeded | failed
        "mode": run.get("mode", "run"),              # run | test — test 는 contribution 이 항상 []
        "workflow": run["workflow"],
        "mock": run["mock"],
        "progress": {
            "done": done,
            "total": total,
            "percent": int(done * 100 / total) if total else 0,
            "current_index": current,
            "current_node_id": cur_node["id"] if cur_node else None,
            "current_node_name": cur_node["name"] if cur_node else None,
        },
        "nodes": nodes,
        "agents": run.get("agents", []),             # 에이전트(판매자)별 토큰 · USD 합계 (계약 §7.2)
        "totals": run["totals"],
        "started_at": run["started_at"],
        "finished_at": run["finished_at"],
        "total_duration_ms": run["total_duration_ms"],
        # 정산 근거 (계약 §8)
        "contribution": run.get("contribution", []),
        "not_executed": run.get("not_executed", []),
        "failure": run.get("failure"),
        "result": result,
        "error": run["error"],
    }


def _execute(run_id: str, wf: dict, comment: str, file_path: str | None, mock: bool,
             input_payload: dict | None = None, mode: str = "run",
             ref_files: dict[str, list[str]] | None = None) -> None:
    try:
        # state 로 같은 dict 를 넘겨 러너가 제자리에서 갱신하게 한다.
        # 그래야 폴링으로 노드별 진행 상황이 실시간으로 보인다.
        with _lock:
            state = _runs[run_id]
        runner.execute(wf, comment=comment, file_path=file_path, mock=mock, run_id=run_id,
                       input_payload=input_payload, state=state, mode=mode, ref_files=ref_files)
    except Exception as e:  # 러너가 삼키지 못한 예외까지 방어
        with _lock:
            _runs[run_id]["status"] = "failed"
            _runs[run_id]["error"] = {"code": "RUNTIME_INTERNAL", "message": str(e)[:500], "node_id": None}
    finally:
        if file_path and Path(file_path).exists():
            Path(file_path).unlink(missing_ok=True)


@app.get("/v1/health", summary="상태 확인")
def health() -> dict:
    return {"status": "ok", "auth_required": bool(SERVICE_TOKEN), "runs_in_memory": len(_runs)}


@app.get("/v1/schemas", summary="(없어짐) 견본 목록 — 빈 목록", dependencies=[Depends(auth)])
def get_schemas() -> dict:
    """v2.1 에서 견본을 없앴다. 판매자가 노드마다 `output.example` 에 JSON 예시를 준다.

    한 버전 동안 빈 목록을 돌려준다 — 프론트가 이 API 를 부르고 있어도 화면이 깨지지 않게.
    """
    return {"deprecated": True, "schemas": [],
            "message": "견본 목록은 v2.1 에서 없어졌습니다 — 노드의 output.example 에 JSON 예시를 주세요"}


@app.get("/v1/schemas/{schema_id}", summary="(없어짐) 견본 상세", dependencies=[Depends(auth)])
def get_schema(schema_id: str) -> dict:
    raise HTTPException(status_code=404, detail={
        "code": "SCHEMA_NOT_FOUND",
        "message": f"견본은 v2.1 에서 없어졌습니다 — output.example 에 JSON 예시를 주세요 ({schema_id})"})


@app.get("/v1/workflows", summary="예시 워크플로우 목록", dependencies=[Depends(auth)])
def get_workflows() -> dict:
    return {"workflows": runner.list_workflows()}


@app.get("/v1/workflows/{workflow_id}", summary="예시 워크플로우 정의 (백엔드가 복사해 쓰는 견본)",
         dependencies=[Depends(auth)])
def get_workflow(workflow_id: str) -> dict:
    try:
        return runner.resolve_prompts(runner.load_workflow(workflow_id))
    except FileNotFoundError:
        raise HTTPException(status_code=404, detail={"code": "WORKFLOW_NOT_FOUND", "message": workflow_id})


@app.post("/v1/workflows/validate", summary="워크플로우 정의 검증 (실행하지 않음)",
          dependencies=[Depends(auth)])
def validate_workflow_definition(body: dict) -> dict:
    """저장 버튼에서 부르면 상한 · 순환 · 스키마 호환 규칙을 백엔드가 복제하지 않아도 된다.

    계약 §7.5. 실행하지 않고 판정만 돌려주므로 과금도 없다.
    """
    wf = body.get("workflow") if isinstance(body, dict) else None
    if not isinstance(wf, dict):
        raise HTTPException(status_code=400,
                            detail={"code": "INVALID_REQUEST",
                                    "message": "workflow 객체가 필요합니다"})
    return graph.validate(wf)


@app.post("/v1/runs", status_code=202, summary="워크플로우 실행 요청", dependencies=[Depends(auth)])
async def create_run(
    request: Request,
    background: BackgroundTasks,
    workflow: str = Form(..., description='워크플로우 정의 JSON 문자열. {"name":..., "nodes":[...], "output":{...}}'),
    input: str = Form("", description="첫 노드 입력 payload 를 JSON 문자열로 (폼에서 받은 값)"),
    comment: str = Form("", description="사용자가 입력한 자연어 코멘트"),
    mock: bool = Form(False, description="true 면 LLM 을 호출하지 않고 파이프라인만 실행"),
    mode: str = Form("run", description="run(기본) | test — 판매자 사전 테스트. 결과는 같고 contribution 만 비운다"),
    file: UploadFile | None = File(None, description="사용자가 올리는 엑셀 또는 CSV (선택)"),
) -> JSONResponse:
    try:
        wf = json.loads(workflow)
    except json.JSONDecodeError as e:
        raise HTTPException(status_code=400, detail={"code": "INVALID_WORKFLOW_JSON", "message": str(e)})
    if mode not in ("run", "test"):
        raise HTTPException(status_code=400, detail={"code": "INVALID_MODE",
                                                     "message": f"mode 는 run 또는 test 입니다 (받은 값: {mode})"})

    input_payload = None
    if input.strip():
        try:
            input_payload = json.loads(input)
        except json.JSONDecodeError as e:
            raise HTTPException(status_code=400,
                                detail={"code": "INVALID_INPUT_JSON", "message": str(e)})
        if not isinstance(input_payload, dict):
            raise HTTPException(status_code=400,
                                detail={"code": "INVALID_INPUT_JSON",
                                        "message": "input 은 JSON 객체여야 합니다"})

    # 정의 검증은 그래프 규칙 하나로 한다(계약 §5.7). 분기 · edges 도 여기서 걸린다
    report = graph.validate(wf)
    if not report["valid"]:
        raise HTTPException(status_code=400, detail={"code": "INVALID_WORKFLOW",
                                                     "message": "워크플로우 정의 오류",
                                                     "errors": report["errors"]})

    saved_path: str | None = None
    if file and file.filename:
        suffix = Path(file.filename).suffix.lower()
        if suffix not in ALLOWED_SUFFIXES:
            raise HTTPException(status_code=400, detail={
                "code": "UNSUPPORTED_FILE_TYPE",
                "message": f"허용 확장자: {', '.join(sorted(ALLOWED_SUFFIXES))} (받은 값: {suffix or '없음'})"})
        fd, tmp = tempfile.mkstemp(suffix=suffix)
        with os.fdopen(fd, "wb") as out:
            shutil.copyfileobj(file.file, out)
        size_mb = Path(tmp).stat().st_size / 1024 / 1024
        if size_mb > MAX_UPLOAD_MB:
            Path(tmp).unlink(missing_ok=True)
            raise HTTPException(status_code=400, detail={
                "code": "FILE_TOO_LARGE", "message": f"최대 {MAX_UPLOAD_MB}MB (받은 값: {size_mb:.1f}MB)"})
        saved_path = tmp

    # 판매자 참고 파일 — 폼 필드 `ref_file_<node_id>_<n>` (계약 §7.1). 노드 id 로 묶어 러너에 넘긴다.
    # 검사(mime · 크기)는 러너가 실행 시점에 하고 위반은 REF_FILE_REJECTED · supplier 로 실패시킨다.
    ref_files: dict[str, list[str]] = {}
    ref_dir: str | None = None
    for key, value in (await request.form()).multi_items():
        if not key.startswith("ref_file_") or not getattr(value, "filename", None):
            continue
        node_id = key[len("ref_file_"):].rsplit("_", 1)[0]
        ref_dir = ref_dir or tempfile.mkdtemp(prefix="abc-ref-")
        # 원래 파일명을 지킨다 — 러너가 "### 파일명" 절 제목과 첨부 이름으로 쓴다
        dest = Path(ref_dir) / node_id / str(len(ref_files.get(node_id, []))) / Path(value.filename).name
        dest.parent.mkdir(parents=True, exist_ok=True)
        with open(dest, "wb") as out:
            shutil.copyfileobj(value.file, out)
        ref_files.setdefault(node_id, []).append(str(dest))

    run_id = runner.new_run_id()
    nodes = wf.get("nodes", [])
    with _lock:
        _runs[run_id] = {
            "run_id": run_id,
            "workflow": {"id": wf.get("id"), "name": wf.get("name"), "node_count": len(nodes)},
            "mock": mock, "mode": mode, "status": "queued", "started_at": None, "finished_at": None,
            "total_duration_ms": None,
            "nodes": [{"id": n.get("id"), "name": n.get("name") or n.get("id"), "status": "pending",
                       "type": n.get("type") or "llm", "output": graph.public_output(graph.output_spec_of(n)),
                       "duration_ms": None, "input_tokens": None,
                       "output_tokens": None, "retries": 0, "output_payload": None,
                       "model": None, "cost_usd": None, "owner_id": n.get("owner_id")}
                      for n in nodes],
            "totals": {"input_tokens": 0, "output_tokens": 0, "cost_usd": 0.0},
            "agents": [],
            "contribution": [], "not_executed": [], "failure": None,
            "result": None, "error": None,
        }

    background.add_task(_execute, run_id, wf, comment, saved_path, mock, input_payload, mode,
                        ref_files or None)
    return JSONResponse(status_code=202, content={
        "run_id": run_id,
        "status": "queued",
        "mode": mode,
        "node_count": len(nodes),
        "poll_url": f"/v1/runs/{run_id}",
        "poll_interval_ms": 2000,
    })


@app.get("/v1/runs/{run_id}", summary="실행 상태 · 진행 상황 · 결과 조회", dependencies=[Depends(auth)])
def get_run(run_id: str, include_node_output: bool = False) -> dict:
    with _lock:
        run = _runs.get(run_id)
    if not run:
        raise HTTPException(status_code=404, detail={"code": "RUN_NOT_FOUND", "message": run_id})
    return _public(run, include_node_output=include_node_output)


@app.get("/v1/runs/{run_id}/artifact", summary="결과물 조회 (봉투 JSON)",
         dependencies=[Depends(auth)])
def get_artifact(run_id: str) -> dict:
    """종단 노드의 봉투를 그대로 돌려준다(계약 §6.2 · §7.5).

    템플릿이 붙으면 이 payload 가 PDF 로 렌더된다. 지금은 JSON 이 원본이다.
    """
    run = _runs.get(run_id)
    if not run:
        raise HTTPException(status_code=404, detail={"code": "RUN_NOT_FOUND", "message": run_id})
    envelope = (run.get("result") or {}).get("envelope")
    if run.get("status") != "succeeded" or not envelope:
        raise HTTPException(status_code=409, detail={
            "code": "ARTIFACT_NOT_READY",
            "message": f"실행 상태: {run.get('status')}"})
    return envelope


@app.get("/v1/runs/{run_id}/pdf", summary="결과 PDF 내려받기", response_class=FileResponse,
         dependencies=[Depends(auth)])
def get_run_pdf(run_id: str) -> FileResponse:
    with _lock:
        run = _runs.get(run_id)
    if not run:
        raise HTTPException(status_code=404, detail={"code": "RUN_NOT_FOUND", "message": run_id})
    pdf = (run.get("result") or {}).get("pdf")
    if not pdf or not Path(pdf).exists():
        raise HTTPException(status_code=409, detail={
            "code": "PDF_NOT_READY", "message": f"현재 상태: {run['status']}"})
    return FileResponse(pdf, media_type="application/pdf", filename=f"{run_id}.pdf")
