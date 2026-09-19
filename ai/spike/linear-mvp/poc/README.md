# poc/ — 템플릿 PoC (09-17, fork 세션 산출물 · 기록에서 복구)

MVP **마지막 항목**인 "캔바형 템플릿"의 참고 구현. 09-22 ~ 23에 `src/`로 이식한다.

| 파일 | 역할 | 이식 시 |
| --- | --- | --- |
| `library.py` | 템플릿 보관함(스키마별 · 플랫폼 기본 / 사용자) · 사용 필드 추출 | `src/templates.py` |
| `step3_render.py` | JSON → Jinja HTML → PDF. 안전망 3중(사전 검사 · StrictUndefined · 기본 템플릿 폴백). 렌더 실패는 실행 실패가 아님 | `src/render.py` |
| `step1_template.py` | 사용자 디자인(PDF · 이미지) → HTML 변환. **Claude 비전 반복 호출 — 실행 금지**, Gemini 이미지 입력으로 바꿔 이식 | `src/template_import.py` |
| `step2_node.py` | 노드 1개 실호출 PoC(기본 gemini) | 폐기(Runtime이 대체) |
| `schema.py` | `biz.brand_brief@1` · `biz.brand_strategy@1` 초안 | 카탈로그로 대체 |
| `library/tpl_default.html` | 플랫폼 기본 문서형 템플릿 | `templates/` |
| `template/sample_template.pdf` | 사용자 템플릿 샘플(파란 모던 보고서) | 시연 자료 |

`out/`와 변환된 `library/tpl_*.html`은 커밋하지 않는다.
