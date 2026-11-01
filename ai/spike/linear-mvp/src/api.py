"""AI Runtime 스파이크 API — 백엔드(Spring)가 부르는 HTTP 계약.

실행:  uv run uvicorn src.api:app --host 0.0.0.0 --port 8000 --reload
문서:  http://localhost:8000/docs

실행 접수 · 진행 · 완료는 RabbitMQ 메시지다(docs/adr/0001-execution-messaging-rabbitmq.md).
  1) 백엔드가 abc.runs 로 SubmitRun 을 보낸다. 런타임이 받아 돌린다(src/broker.py → src/messaging.py)
  2) 런타임은 accepted · progress · succeeded | failed | rejected 를 abc.events 로 낸다. 결과물은 결과 볼륨에 쓴다
  3) 아래 HTTP 는 남는다 — 대조(GET /v1/runs/{execution_id})와 결과물 조회, 운영용 취소, 정의 검증, 서식 변환
"""
from __future__ import annotations

import asyncio
import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse

from . import broker, graph, messaging, run_store, runner, template_import
from .llm import LLM

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")

log = logging.getLogger(__name__)

SERVICE_TOKEN = os.getenv("ABC_SERVICE_TOKEN", "")          # 비어 있으면 인증 검사를 건너뛴다(스파이크 기본값)
MAX_UPLOAD_MB = 10

# 끝난 실행을 메모리 · out/ · 결과 볼륨에 들고 있는 시간. 백엔드가 succeeded 를 받을 때 결과물을 옮겨 두므로
# 오래 들고 있을 이유가 없다. 조회 때와 EVICT_INTERVAL_SECONDS 마다 정리한다.
RUN_RETENTION_SECONDS = float(os.getenv("RUN_RETENTION_HOURS", "24")) * 3600
EVICT_INTERVAL_SECONDS = 600


async def _evict_periodically() -> None:
    """조회가 드물어져도(접수가 메시지라) 끝난 실행과 결과 볼륨이 쌓이지 않게 한다."""
    while True:
        await asyncio.sleep(EVICT_INTERVAL_SECONDS)
        try:
            await asyncio.to_thread(_evict_expired)
        except Exception:
            log.exception("보관 기한 정리에 실패했다")


@asynccontextmanager
async def lifespan(_app: FastAPI):
    evictor = asyncio.create_task(_evict_periodically())

    try:
        async with broker.running():
            yield
    finally:
        evictor.cancel()


app = FastAPI(
    title="ABC AI Runtime",
    version="2.0.0",
    description="워크플로우 실행기 — 그래프 실행 · 분기 · 에이전트 전개 · 판매자 선언 출력 · 토큰/USD 집계. 계약 v2.0.",
    lifespan=lifespan,
)


def auth(x_abc_service_token: str | None = Header(default=None)) -> None:
    if SERVICE_TOKEN and x_abc_service_token != SERVICE_TOKEN:
        raise HTTPException(status_code=401, detail={"code": "UNAUTHORIZED", "message": "X-ABC-Service-Token 이 올바르지 않습니다"})


def _find(run_id: str) -> dict:
    with run_store.lock:
        run = run_store.runs.get(run_id)

    if run is None:
        raise HTTPException(status_code=404, detail={"code": "RUN_NOT_FOUND", "message": run_id})

    return run


def _evict_expired() -> None:
    """보관 기한이 지난 끝난 실행을 지운다 — 메모리 · out/ · 결과 볼륨 · 취소 기억(R21)."""
    messaging.evict_expired(RUN_RETENTION_SECONDS)


@app.get("/v1/health", summary="상태 확인")
def health() -> dict:
    return {"status": "ok", "broker": broker.status(), "auth_required": bool(SERVICE_TOKEN),
            "runs_in_memory": len(run_store.runs)}


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


@app.post("/v1/runs/{run_id}/cancel", summary="실행 취소 (운영 도구)", dependencies=[Depends(auth)])
def cancel_run(run_id: str) -> dict:
    """다음 노드로 넘어가기 전에 멈춘다. 지금 부르고 있는 LLM 호출은 끝까지 간다.

    백엔드는 CancelRun 메시지(abc.control)로 취소한다. 이 경로는 운영자가 도는 실행을 직접 멈출 때 쓴다.
    run_id 는 SubmitRun 의 execution_id 다.
    """
    with run_store.lock:
        run = run_store.runs.get(run_id)
        if run is None:
            raise HTTPException(status_code=404, detail={"code": "RUN_NOT_FOUND", "message": run_id})
        finished = run["status"] in ("succeeded", "failed")
        if not finished:
            run["cancel_requested"] = True
        return {"run_id": run["run_id"], "status": run["status"], "cancel_requested": not finished}


@app.get("/v1/runs/{run_id}", summary="실행 상태 · 진행 상황 · 결과 조회", dependencies=[Depends(auth)])
def get_run(run_id: str, include_node_output: bool = False) -> dict:
    """run_id 는 SubmitRun 의 execution_id 다. 백엔드가 이벤트를 놓친 실행을 대조할 때 부른다."""
    _evict_expired()
    return run_store.public(_find(run_id), include_node_output=include_node_output)


@app.get("/v1/runs/{run_id}/artifact", summary="결과물 조회 (봉투 JSON)",
         dependencies=[Depends(auth)])
def get_artifact(run_id: str) -> dict:
    """종단 노드의 봉투를 그대로 돌려준다(계약 §6.2 · §7.5).

    템플릿이 붙으면 이 payload 가 PDF 로 렌더된다. 지금은 JSON 이 원본이다.
    """
    run = _find(run_id)
    envelope = (run.get("result") or {}).get("envelope")
    if run.get("status") != "succeeded" or not envelope:
        raise HTTPException(status_code=409, detail={
            "code": "ARTIFACT_NOT_READY",
            "message": f"실행 상태: {run.get('status')}"})
    return envelope


@app.get("/v1/runs/{run_id}/html", summary="서식을 입힌 결과 HTML", dependencies=[Depends(auth)])
def get_run_html(run_id: str):
    """결과 JSON 에 서식(사용자 서식 또는 기본 서식)을 입힌 HTML. 화면에 바로 띄우거나 저장한다(계약 v2.2 §7.4)."""
    from fastapi.responses import HTMLResponse
    run = _find(run_id)
    html = (run.get("result") or {}).get("html")
    if not html or not Path(html).exists():
        raise HTTPException(status_code=409, detail={"code": "HTML_NOT_READY", "message": f"현재 상태: {run['status']}"})
    return HTMLResponse(Path(html).read_text(encoding="utf-8"))


@app.post("/v1/templates/convert", summary="디자인(PDF · 사진) → 빈칸 뚫린 HTML 서식", dependencies=[Depends(auth)])
async def convert_template(
    design: list[UploadFile] = File(..., description="디자인 파일 — PDF · png · jpg · webp. 여러 장이면 페이지 순서대로"),
    hint: str = Form("", description="서식에 대한 사용자 설명(선택). 예: '2쪽 보고서, 표지 제목은 브랜드명'"),
    mock: bool = Form(False, description="true 면 AI 없이 기본 서식을 돌려준다(형식 확인용)"),
) -> dict:
    """캔바형 서식 등록. 올릴 때 한 번 AI 를 부르고, 결과 HTML 은 **백엔드가 보관**해 실행 때 `output.template.html` 로 보낸다.
    Runtime 은 보관하지 않는다(stateless). 과금 대상 실행이 아니다(계약 v2.2 §7.8)."""
    files: list[tuple[bytes, str, str]] = []
    for up in design:
        suffix = Path(up.filename or "").suffix.lower()
        mime = runner.ATTACH_MIME.get(suffix)
        if not mime:
            raise HTTPException(status_code=400, detail={
                "code": "UNSUPPORTED_FILE_TYPE", "message": f"디자인은 pdf · png · jpg · webp 만 (받은 값: {suffix or '없음'})"})
        data = await up.read()
        if len(data) > MAX_UPLOAD_MB * 1024 * 1024:
            raise HTTPException(status_code=400, detail={"code": "FILE_TOO_LARGE", "message": f"최대 {MAX_UPLOAD_MB}MB"})
        files.append((data, mime, Path(up.filename).name))
    try:
        llm = LLM(mock=mock)
    except Exception as e:
        raise HTTPException(status_code=500, detail={"code": "LLM_INIT_FAILED", "message": str(e)[:200]})
    try:
        out = template_import.convert(llm, files, hint=hint)
    except Exception as e:
        raise HTTPException(status_code=502, detail={"code": runner._error_code(e), "message": str(e)[:300]})
    return {"html": out.html, "placeholders": out.placeholders, "repaired": out.repaired,
            "problems": out.problems, "usage": out.usage}


@app.get("/v1/runs/{run_id}/pdf", summary="결과 PDF 내려받기", response_class=FileResponse,
         dependencies=[Depends(auth)])
def get_run_pdf(run_id: str) -> FileResponse:
    run = _find(run_id)
    pdf = (run.get("result") or {}).get("pdf")
    if not pdf or not Path(pdf).exists():
        raise HTTPException(status_code=409, detail={
            "code": "PDF_NOT_READY", "message": f"현재 상태: {run['status']}"})
    return FileResponse(pdf, media_type="application/pdf", filename=f"{run_id}.pdf")
