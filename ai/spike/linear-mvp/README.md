# linear-mvp — 선형 워크플로우 스파이크

규격(A2A · H2A)과 분기 없이, 노드가 직선으로 이어진 워크플로우 하나를 끝까지 실행해 흐름을 확인하는 실험 코드다. 팀 결정(2026-09-08)에 따른 것이며, 설계된 Runtime(`ai/runtime/`)과 섞이지 않게 여기에 격리했다.

- 백엔드 · 프론트 연동 계약: [`../../docs/ai-runtime-api-v0.1.md`](../../docs/ai-runtime-api-v0.1.md)
- 실험 계획과 관찰 결과: 팀 문서 폴더의 `MVP 스파이크 계획서.md`

## 무엇을 하는가

```
강사 코멘트 + 성적 엑셀
   → 엑셀을 마크다운 표로 변환        (코드)
   → 노드 1 "성적 분석"                (Gemini)
   → 노드 2 "가정통신문 작성"           (Gemini, 노드 1 출력을 그대로 입력)
   → 마크다운을 PDF로 렌더링           (코드)
```

노드 사이에 스키마 검증이 없다. 다음 노드는 자기 프롬프트에 이전 노드의 출력 텍스트를 붙여 호출한다. **PDF는 LLM이 만들지 않는다.** 마지막 노드가 낸 마크다운을 코드가 HTML을 거쳐 PDF로 그린다.

## 설치

Python 3.11과 [uv](https://docs.astral.sh/uv/)가 필요하다.

```bash
cd ai/spike/linear-mvp
uv sync
cp .env.example .env      # GEMINI_API_KEY 를 채운다
```

`.env`

```
GEMINI_API_KEY=발급받은키
GEMINI_MODEL=gemini-2.5-flash
ABC_SERVICE_TOKEN=          # 비우면 API 인증 검사를 건너뛴다
```

키는 [Google AI Studio](https://aistudio.google.com/apikey)에서 발급한다. 무료 등급이라 분당 · 일일 요청 상한이 있으니 반복 실행은 `--mock`으로 한다.

### PyCharm에서 열 때

`.venv`는 **공식 CPython으로 만들어야 한다.** uv가 만든 가상환경의 `python.exe`는 uv 런처라서 PyCharm이 인터프리터로 인식하지 못한다("No interpreters were found" 또는 exit code 103).

```powershell
winget install -e --id Python.Python.3.11
cd ai\spike\linear-mvp
Remove-Item -Recurse -Force .venv
& "$env:LOCALAPPDATA\Programs\Python\Python311\python.exe" -m venv .venv
uv export --format requirements-txt --no-hashes --no-emit-project -o .venv\requirements.lock.txt
uv pip install --python .venv\Scripts\python.exe -r .venv\requirements.lock.txt
```

그다음 PyCharm에서 **Add Local Interpreter → Select existing → Type: Python**을 고르고 `.venv\Scripts\python.exe`를 지정한다. 이렇게 만들어도 `uv run`은 이 가상환경을 그대로 쓴다. 패키지 추가는 계속 `uv add`로 한다.

## 실행

### CLI

```bash
# 합성 데모 데이터 만들기 (최초 1회)
uv run python data/make_demo_data.py

# LLM 호출 없이 파이프라인만 확인
uv run python -m src.runner --workflow report --file data/demo_scores.xlsx \
    --comment "이번 달은 전반적으로 시험 난이도가 높았음" --mock

# 실제 Gemini 호출
uv run python -m src.runner --workflow report --file data/demo_scores.xlsx \
    --comment "이번 달은 전반적으로 시험 난이도가 높았음"
```

결과는 `out/<run_id>/`에 쌓인다.

| 파일 | 내용 |
| --- | --- |
| `input.md` | 코멘트와 엑셀을 합쳐 첫 노드에 넣은 텍스트 |
| `nodes/<id>.request.md` | 그 노드에 **실제로 보낸** system · user 메시지 |
| `nodes/<id>.md` | 그 노드가 낸 응답 원문 |
| `run.json` | 노드별 시간 · 토큰 · 상태와 전체 요약 |
| `report.pdf` | 최종 산출물 |

결과가 이상하면 `*.request.md`부터 본다. 무엇을 보냈는지가 그대로 남아 있다.

### API 서버

```bash
uv run uvicorn src.api:app --host 0.0.0.0 --port 8000
```

Swagger는 `http://localhost:8000/docs`에서 열린다. 엔드포인트와 요청 · 응답 형식은 [연동 계약 문서](../../docs/ai-runtime-api-v0.1.md)를 따른다.

```bash
# 견본 워크플로우를 그대로 써서 한 번 실행
WF=$(curl -s http://localhost:8000/v1/workflows/report)
curl -X POST http://localhost:8000/v1/runs \
  -F "workflow=$WF" -F "comment=난이도가 높았음" -F "mock=true" \
  -F "file=@data/demo_scores.xlsx"

# 진행 상황 조회
curl http://localhost:8000/v1/runs/<run_id>

# 결과 PDF
curl -o report.pdf http://localhost:8000/v1/runs/<run_id>/pdf
```

## 구조

```
src/excel_to_text.py   엑셀·CSV → 요약 한 줄 + 마크다운 표 (100행 상한)
src/llm.py             Gemini 어댑터. 타임아웃 60초, 429/503 시 10초 대기 후 2회 재시도, mock 모드
src/runner.py          워크플로우 순차 실행. 진행 상태를 state dict 에 실시간 기록
src/pdf.py             마크다운 → HTML → PDF. 한글 폰트 임베딩
src/api.py             FastAPI. 202 + 조회 방식
templates/report.html  PDF 템플릿
workflows/report.json  노드 2개 정의 (견본)
prompts/*.md           노드별 프롬프트
data/make_demo_data.py 합성 성적표 생성 (실제 학생 정보 없음)
```

## 알아 둘 것

- **실행 상태는 메모리에만 있다.** 서버를 재시작하면 `run_id` 조회가 404가 된다. 이력의 원본은 백엔드 DB다.
- **단일 프로세스 전제다.** 워커를 늘리면 조회가 다른 워커로 가서 실패한다.
- `src/pdf.py`에 Windows 전용 몽키패치가 있다. xhtml2pdf가 폰트를 열린 임시 파일에 복사한 뒤 reportlab이 다시 열게 하는데, Windows는 그걸 막는다. Linux에서는 원본 코드가 그대로 돈다.
- `out/`과 `.env`는 커밋하지 않는다.

## 관찰 결과 요약 (실호출 5회)

| 항목 | 결과 |
| --- | --- |
| 전체 소요 | 평균 60초 (노드당 약 25초) |
| 학생 20명 전원 포함 | 분석 · 통신문 모두 5회 전부 20/20 |
| 학생별 3월 평균 정합성 | 5회 전부 20/20 엑셀과 일치 |
| 지어낸 숫자 | 4회 0개, 1회 3개(가상 전화번호) |
| PDF 한글 · 표 | 이상 없음 |

이 규모에서는 스키마 없이도 정보가 온전히 전달됐다. 다만 노드 3개 이상, 프롬프트 지시를 뺀 대조군, 분기는 측정하지 않았다.
