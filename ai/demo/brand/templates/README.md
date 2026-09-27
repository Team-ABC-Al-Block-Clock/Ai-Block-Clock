# 시연용 사용자 서식

| 파일 | 출처 | 쓰는 곳 |
| --- | --- | --- |
| **`파란색_모던_보고서.html`** | 사용자가 캔바에서 만든 `파란색 모던한 비전 브랜드 브랜딩 전략 보고서 A4.pdf`(1쪽)를 변환. 빈칸 10개, 09-27 실측 10/10 배치 — **시연은 이것으로** | 같음 |
| `브랜드_전략_보고서_A.html` | `../output/pdf/brand_strategy_design_a.pdf`(트랙 B 디자인 샘플 A)를 `POST /v1/templates/convert`(Gemini 2.5 Pro)로 변환. 빈칸 24개 | 백엔드에 서식으로 등록하거나 실행 요청 `output.template.html`에 넣는다 |

실행 끝에 결과 JSON 의 키가 빈칸 이름과 달라도 AI 가 한 번 경로를 짝지어 채운다(계약 v2.2 §5.5). 09-27 실측: 24개 중 21개 배치, 나머지 3개(작성기관 · 전략메모 · 총평_제목)는 결과에 없는 고정 칸이라 비어 있다.

```bash
cd ai/spike/linear-mvp
uv run python -m src.runner --workflow brand_agent --input ../../demo/brand/brief_high.json \n    --template ../../demo/brand/templates/브랜드_전략_보고서_A.html
```

**09-27 E2E(API 경로 · 실제 Gemini)** — 변환 69초 → 1단계(사진 · 참고 파일 3 · 이 서식) 105초 · $0.031, 빈칸 10/10 → 2단계(판매자 3) 161초 · $0.057. 산출물 예시는 `_e2e/`(커밋 안 함).
