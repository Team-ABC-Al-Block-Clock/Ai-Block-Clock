---
name: tdd-pipeline
description: 백엔드(ai-block-clock) 기능을 TDD 파이프라인으로 구현한다 — 명세 정리 → 실패하는 테스트(RED) → 구현(GREEN) → PIT 돌연변이 테스트와 테스트 리뷰 → Playwright E2E(화면이 있으면 브라우저를 열어 스크린샷으로 확인, 없으면 API). 역할별 서브에이전트(tdd-test-writer · tdd-implementer · tdd-mutation-reviewer · tdd-e2e-tester)에게 일을 나눠 맡기고, 단계마다 스크립트로 통과 여부를 판정한다. 백엔드에 새 기능 · API · 도메인 규칙을 "구현해줘 / 만들어줘 / 추가해줘" 라고 하면 TDD 라는 말이 없어도 이 스킬을 쓴다. 테스트 먼저, 돌연변이 테스트, PIT, 테스트 보강, E2E 테스트를 말할 때도 쓴다. 한두 줄짜리 버그 수정, 문서 · 설정만 바꾸는 일, 프론트엔드 · AI 파트 작업에는 쓰지 않는다.
---

# TDD 파이프라인

이 스킬을 따르는 메인 세션이 **흐름 관리자**다. 관리자는 코드를 쓰지 않는다. 하는 일은 셋이다.

1. 역할 에이전트에게 넘길 입력을 파일로 준비한다
2. 단계가 끝날 때마다 통과 기준을 **명령을 직접 돌려** 확인한다
3. 다음 단계로 갈지, 어느 역할에게 되돌릴지 정한다

역할을 나누는 이유는 하나다. 테스트를 쓴 쪽이 구현까지 하면, 테스트가 구현에 맞춰 휘어진다. test-writer 는 명세만 보고 테스트를 쓰고,
implementer 는 테스트를 고칠 수 없다. 관리자가 "사소하니까" 직접 고치면 이 분리가 무너진다 — 오타 하나라도 해당 역할에게 돌려보낸다.

에이전트의 "다 통과했다" 는 보고는 판정이 아니다. 판정은 관리자가 돌린 명령의 결과로 한다.

## 준비물

모든 명령은 백엔드 루트 `backend/ai-block-clock` 에서 실행한다. 아래에서 `$S` 는 `.claude/skills/tdd-pipeline/scripts` 다.

시작할 때 한 번 확인하고, 없으면 멈춰서 사용자에게 알린다. 팀 빌드 설정을 바꾸는 일이라 스킬이 몰래 깔지 않는다.

- `build.gradle` 에 `info.solidsoft.pitest` 플러그인이 있다
- 저장소 루트의 `e2e/` (여기서 `../../e2e`) 에 Playwright 패키지가 있고 `node_modules` 가 깔려 있다. `../../frontend/node_modules` 도 있다 (E2E 가 프론트 dev 서버를 띄운다).
  Chromium 이 없으면 `npx playwright install chromium`. 설치 안내는 `../../e2e/README.md`
- `.claude/settings.json` 의 `additionalDirectories` 에 `../../e2e` · `../../frontend` 가 있다. 없으면 이 세션과 에이전트가 그 폴더를 고치거나 읽지 못한다
- `python` 이 있다 (스크립트는 표준 라이브러리만 쓴다)

| 스크립트 | 쓰는 때 |
| --- | --- |
| `test_results.py <모듈폴더> [--task integrationTest] [--classes ...]` | RED · GREEN 판정. 테스트별 통과 · 실패와 실패 원인(예외 타입) |
| `test_guard.py snapshot\|verify\|restore <파일>` | 구현 단계에서 테스트 파일이 바뀌지 않았는지 확인하고, 바뀌었으면 되돌린다 |
| `changed_classes.py [--json]` | 바뀐 운영 클래스와 그 범위로 PIT 를 돌리는 명령 |
| `pit_summary.py --report <xml> --classes ... --threshold 80` | 돌연변이 점수와 살아남은 돌연변이 표 |
| `e2e_results.py ../../e2e/test-results/results.json` | E2E 테스트별 결과, 실패 원인, 스크린샷 경로 |

## 역할 에이전트

| subagent_type | 맡는 일 | 고칠 수 있는 것 |
| --- | --- | --- |
| `tdd-test-writer` | 실패하는 테스트, 돌연변이 · 버그 재현 테스트 | 테스트. RED 모드에서만 운영 코드의 빈 껍데기(스텁) |
| `tdd-implementer` | 테스트를 통과시키는 구현, 버그 수정 | 운영 코드 (테스트는 못 고친다) |
| `tdd-mutation-reviewer` | PIT 실행, 생존 돌연변이 분류, 테스트 품질 리뷰 | 없음. 보고서만 돌려준다 |
| `tdd-e2e-tester` | Playwright E2E — 브라우저(스크린샷) 또는 API | `../../e2e/tests/` 의 테스트와 `support/` |

정의는 `.claude/agents/<이름>.md` 에 있고, Claude Code 를 `backend/ai-block-clock` 에서 열었을 때 등록된다.
다른 위치에서 열어 위 이름이 사용 가능한 에이전트 목록에 없으면 `general-purpose` 로 부르고, 프롬프트 첫 줄에
"`backend/ai-block-clock/.claude/agents/<이름>.md` 를 읽고 그 지침대로 일한다" 를 넣는다.

에이전트는 이 대화를 모른다. 프롬프트에는 매번 다음을 넣는다: 작업 폴더 경로, 읽을 파일(명세 등), 모드, 대상 모듈,
이번에 하지 말 것, 남길 출력 파일. 긴 맥락은 프롬프트에 붙이지 말고 작업 폴더의 파일로 넘긴다.

## 작업 폴더

`build/tdd/<slug>/` 에 단계 사이에 주고받는 파일을 둔다. slug 는 지라 키(`S15P21A403-375`)나 기능 이름(kebab-case).
`build/` 는 git 이 무시하므로 커밋에 섞이지 않는다. `./gradlew clean` 을 하면 지워지니 파이프라인 도중에는 하지 않는다.

| 파일 | 쓰는 쪽 | 내용 |
| --- | --- | --- |
| `spec.md` | 관리자 | 동작 목록 B1, B2 … 와 HTTP 표면 |
| `tests.md` | test-writer | 명세 항목 ↔ 테스트 대응표, 만든 스텁 |
| `test-snapshot.json` | 관리자 | 구현 전 테스트 파일 해시와 사본 |
| `mutation-round-<N>.md` | 관리자 (reviewer 의 보고서를 저장) | 점수, 생존 돌연변이 분류, 테스트 리뷰 |
| `e2e.md` | e2e-tester | 방식(브라우저 · API), 시나리오, 스크린샷 경로와 화면 확인 결과, 구현 버그 재현 |
| `report.md` | 관리자 | 최종 보고 |

## 흐름

```
0 시작 점검 → 1 명세 → 2 RED → 3 사용자 확인 → 4 GREEN → 5 돌연변이(≤3라운드) → 6 E2E → 7 보고
                          ↑__________|            ↑ 버그 발견 시 test-writer(bug) → implementer(fix)
```

### 0. 시작 점검

1. `git status --short` — 이미 바뀐 파일이 있으면 알린다. 그 파일들도 돌연변이 범위(`changed_classes.py`)에 섞인다. 진행할지 묻는다.
2. 대상 모듈의 단위 테스트가 지금 초록인지 `./gradlew :<모듈>:test --console=plain` 으로 확인한다.
   이미 빨간 상태에서 시작하면 RED 판정을 믿을 수 없다.

### 1. 명세 — 관리자

입력은 사용자 요청이다. 지라 키가 있으면 Atlassian MCP 의 `getJiraIssue` 로 읽는다 — 읽기만 하고 상태 · 담당자 · 댓글은 건드리지 않는다.
관련 설계가 있으면 `docs/` 와 `docs/plans/` 를 읽고, 바뀔 코드 주변(같은 도메인 패키지)을 훑어 기존 구조를 파악한다.

`spec.md` 형식:

```markdown
# <기능 이름>
목표: <한 문장>

## 동작
- B1. <조건> → <기대 결과>
- B2. <잘못된 입력> → CoreException(ErrorType.XXX)
- B3. <경계값> → …

## HTTP 표면
- POST /api/v1/... (새로 생김) — 없으면 "없음". 없으면 6단계를 건너뛴다

## 화면
- 이 기능을 쓰는 프론트 화면이 이미 있는가: `/my/agents` 의 … 버튼 — 없으면 "없음" (6단계가 API 방식으로 간다)

## 범위 밖
## 가정
## 열린 질문
```

동작은 관찰 가능한 결과로 쓴다 ("저장한다" 보다 "다시 조회하면 상태가 APPROVED 다"). 오류 경우와 경계값을 빠뜨리지 않는다 — 여기서 빠진 동작은 끝까지 테스트되지 않는다.
결과물이 갈리는 열린 질문은 이 단계에서 3 ~ 4개씩 묶어 사용자에게 묻는다. 사소한 것은 기본값을 골라 "가정" 에 적는다.

### 2. RED — tdd-test-writer (모드: red)

끝나면 관리자가 확인한다. 테스트가 실패하는 게 정상이므로 `BUILD FAILED` 가 나와야 맞다.

```bash
./gradlew :<모듈>:test --console=plain
python $S/test_results.py <모듈폴더>
```

통과 기준:

- 테스트가 컴파일된다. 결과 폴더가 없다고 나오면 컴파일 실패다 — 오류를 붙여 test-writer 에게 돌려보낸다
- 실패한 테스트가 전부 이번에 추가한 것이다. 기존 테스트가 깨졌다면 스텁이 기존 동작을 건드린 것이다
- 새 테스트의 실패 원인이 `UnsupportedOperationException`(스텁) 또는 단언 실패다. "정상 RED 가 아닐 수 있다" 가 붙은 것은 이유를 확인한다
- 새 테스트가 이미 통과한다면 동작이 이미 있거나 테스트가 아무것도 검증하지 않는 것이다. `tests.md` 에 설명이 없으면 돌려보낸다

통합 테스트(`@Tag("integration")`)를 썼다면 `./gradlew :<모듈>:integrationTest --tests '<클래스>'` 도 같은 기준으로 본다.
test-writer 에게 되돌리는 것은 2번까지. 그래도 안 되면 사용자에게 상황을 알린다.

### 3. 사용자 확인 — 파이프라인에서 단 한 번

`tests.md` 의 대응표와 RED 결과(실패 수, 원인)를 요약해 보여주고 승인을 받는다. 이 뒤로는 자동이므로 여기서 빠진 동작은 끝까지 빠진다.
사용자가 볼 것을 짚어준다: 빠진 동작, 잘못 이해한 동작, 필요 이상으로 내부 구현을 고정하는 테스트.
수정 요청은 test-writer(red) 에게 넘기고 2단계 확인을 다시 한다.

### 4. GREEN — tdd-implementer (모드: green)

부르기 직전에 테스트 파일을 찍는다.

```bash
python $S/test_guard.py snapshot build/tdd/<slug>/test-snapshot.json
```

끝나면:

```bash
python $S/test_guard.py verify build/tdd/<slug>/test-snapshot.json
./gradlew test --console=plain
python $S/test_results.py <모듈폴더>
```

통과 기준: 테스트 파일 변경 없음, 그리고 전체 단위 테스트 통과(이번에 쓴 통합 테스트가 있으면 그것도 통과).

- 테스트 파일이 바뀌었으면 `test_guard.py restore` 로 되돌리고, 그 사실을 알려 다시 맡긴다. `git checkout` 으로 되돌리지 않는다 — 아직 커밋하지 않은 새 테스트까지 사라진다
- implementer 가 "테스트가 명세와 맞지 않는다" 고 보고하면 관리자가 판단한다. 명세 해석 문제면 사용자에게 묻고,
  테스트 결함이면 test-writer(red) 에게 고치게 한 뒤 스냅샷을 다시 찍는다
- 실패가 남으면 `test_results.py` 출력을 붙여 다시 맡긴다. 3번 넘게 안 되면 멈추고 보고한다

### 5. 돌연변이 — tdd-mutation-reviewer (기준 80%, 최대 3라운드)

reviewer 는 파일을 고치지 않고 보고서를 최종 메시지로 돌려준다. 관리자가 `mutation-round-<N>.md` 로 저장한다.
reviewer 의 판정을 그대로 받지 말고, 보고서의 점수가 `pit_summary.py` 출력과 같은지 확인한다.

- **PASS**: 점수가 80% 이상이거나, 남은 생존 돌연변이가 전부 "동등" 으로 분류되고 근거가 설득력 있다
- **FAIL**: test-writer 를 모드 mutation 으로 부른다. 입력은 `mutation-round-<N>.md` 의 "test-writer 에게" 목록이다.
  이 모드에서 추가하는 테스트는 **현재 구현에서 통과해야** 한다 (동작을 새로 정하는 게 아니라 이미 있는 동작을 고정하는 것이다).
  끝나면 `./gradlew test` 와 `test_results.py` 로 확인한다.
  - 새 테스트가 모두 통과 → 다음 라운드
  - 새 테스트가 실패 → 버그를 찾은 것이다. 그 테스트가 `spec.md` 에 맞는지 관리자가 확인하고, 맞으면 4단계 방식(snapshot → implementer(fix) → verify)으로 고친다.
    명세로 판단이 안 서면 사용자에게 묻는다
- 3라운드 뒤에도 FAIL 이면 멈춘다. 남은 돌연변이와 분류를 보여주고 E2E 로 넘어갈지 사용자에게 묻는다

"동등" 분류는 엄격해야 한다. "테스트하기 번거롭다" 는 동등이 아니다. 입력을 아무리 바꿔도 관찰 결과가 같을 때만 동등이다.
reviewer 가 짚은 구현 쪽 문제(죽은 코드, 규칙 위반)는 자동으로 고치지 않고 최종 보고의 "결정할 것" 에 넣는다.

### 6. E2E — tdd-e2e-tester

`spec.md` 의 HTTP 표면이 "없음" 이면 건너뛰고 보고서에 이유를 적는다. 그 밖에는 e2e-tester 가 방식을 고른다.

- **브라우저** — 이 기능을 쓰는 화면이 프론트에 있을 때. 브라우저를 열어 화면을 실제로 조작하고, 화면이 바뀌는 지점마다 스크린샷을 남기고,
  그 스크린샷을 직접 열어 화면이 기대대로인지 확인한다. 단언이 통과해도 화면이 깨져 있을 수 있기 때문이다
- **API** — 화면이 아직 없을 때. 같은 `e2e/` 패키지에서 엔드포인트 흐름만 확인하고, 보고서에 "화면이 생기면 브라우저 테스트로 넓힌다" 를 남긴다

E2E 는 백엔드(`bootRun`, H2)와 프론트 dev 서버(mock 끔)를 직접 띄우므로 **8080 · 5173 포트가 비어 있어야** 한다.
막혀 있으면 사용자의 개발 서버가 떠 있다는 뜻이니 내려도 되는지 묻는다. 서버를 띄우느라 한 번에 1 ~ 3분 걸린다.

끝나면 관리자가 확인한다. `cd` 가 남지 않도록 괄호로 감싼다.

```bash
(cd ../../e2e && npx playwright test <spec 파일> --project=<browser|api>)
python $S/e2e_results.py ../../e2e/test-results/results.json
```

- 통과 → 스크린샷 중 핵심 화면 한두 장을 관리자도 직접 열어 e2e-tester 의 화면 판단이 맞는지 본다. 그다음 7단계
- e2e-tester 가 **구현 버그**로 보고한 실패: 단위 테스트가 놓친 동작이므로 먼저 단위 수준에서 재현한다.
  test-writer(bug, 입력: `e2e.md` 의 재현 내용) → RED 확인 → snapshot → implementer(fix) → verify → 단위 테스트 → E2E 다시.
  보안 설정 · 직렬화처럼 단위 테스트로 재현할 수 없는 연결 문제면 test-writer 를 건너뛰고 implementer 에게 바로 맡긴다. 최대 2회
- e2e-tester 가 자기 테스트 문제로 판단한 실패는 e2e-tester 가 고친다

### 7. 최종 보고 — 관리자

`report.md` 에 쓰고 대화에는 요약을 낸다.

```markdown
# <기능> TDD 결과
## 명세 대응 — 동작 B1… 별 테스트 이름과 상태
## RED → GREEN — 돌린 명령과 결과 수
## 돌연변이 — 라운드별 점수, 남은 생존 돌연변이와 분류
## E2E — 방식, 시나리오별 결과, 스크린샷 경로 (건너뛰었으면 이유). HTML 리포트: `cd e2e && npm run report`
## 바뀐 파일 — git status 기준
## 결정할 것 — 가정, 남은 돌연변이, 구현 쪽 제안
```

커밋은 하지 않는다. 사용자가 원하면 저장소 루트 `git-workflow.md` 3장의 커밋 규칙을 따른다.

## 되돌림 한도

| 어디서 | 누구에게 | 최대 |
| --- | --- | --- |
| RED 기준 미달 | test-writer(red) | 2회 |
| GREEN 기준 미달 | implementer(green) | 3회 |
| 돌연변이 FAIL | test-writer(mutation) | 3라운드 |
| E2E 구현 버그 | test-writer(bug) → implementer(fix) | 2회 |

한도에 닿으면 멈추고, 무엇을 시도했고 무엇이 막혔는지 사용자에게 보여준다. 계속 같은 자리에서 실패하면 명세가 모호하다는 신호일 때가 많다.

## 관리자가 하지 않는 것

- 코드나 테스트를 직접 고치지 않는다
- 통과 기준을 낮추지 않는다. 테스트 삭제, `@Disabled`, 단언 완화, 기준 변경은 사용자만 결정한다
- 에이전트의 보고만 믿고 판정하지 않는다
