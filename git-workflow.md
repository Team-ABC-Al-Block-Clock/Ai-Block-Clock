# Git 브랜치 전략 및 협업 컨벤션

> 팀: S15P21A403 · 저장소: `lab.ssafy.com/s15-blochain-sub1/S15P21A403`
> Jira 이슈 키 접두사: `S15P21A403`

본 문서는 팀의 Git 사용 규칙을 정의한다.

- 브랜치 관리 기준을 통일해 충돌과 혼란을 줄인다
- 코드 리뷰를 통해 품질을 보장하고 지식을 공유한다
- 배포 가능한 상태의 코드를 항상 유지한다
- Jira 이슈와 커밋·MR을 추적할 수 있게 한다

---

## 1. 브랜치 전략 — GitHub Flow

### 1.1 브랜치 종류

| 브랜치 | 역할 | 분기 원본 | 병합 대상 | 생명주기 |
| --- | --- | --- | --- | --- |
| `master` | 배포(운영) 가능한 상태만 유지 | - | - | 영구 |
| `develop-FE` | 프론트엔드 통합 개발 | `master` | `master` | 영구 |
| `develop-BE` | 백엔드 통합 개발 | `master` | `master` | 영구 |
| `develop-AI` | AI 통합 개발 | `master` | `master` | 영구 |
| `feat/*` | 기능(스토리) 단위 작업 | 소속 파트의 `develop-*` | 소속 파트의 `develop-*` | 완료 후 삭제 |
| `hotfix/*` | 운영 중 긴급 버그 수정 | `master` | `master` + 전체 `develop-*` | 완료 후 삭제 |

### 1.2 브랜치 흐름

```
master          ──●─────────────────────●───────────────●──▶
                  │ \                 ↗ │ \           ↗
                  │  \               /  │  (hotfix 반영)
develop-FE/BE/AI  ──●───●───●───●───●───●─┴─────●───●───●──▶
                       │ \       ↗       │ \   ↗
feat/*                 ●───●────┘        ●───●┘
```

- `feat/*` 는 소속 파트의 `develop-*` 에서 분기해 같은 브랜치로 병합한다
- `hotfix/*` 는 `master` 에서 분기하고, 수정 후 `master` 와 모든 `develop-*` 에 반영한다
- 파트 간 코드가 필요하면 `develop-*` 끼리 직접 병합하지 않고 `master` 를 경유한다

### 1.3 브랜치 네이밍

브랜치는 스토리 단위로 생성한다. 작업(Task) 단위로 브랜치를 만들지 않는다.

```
feat/{내용}        예) feat/signup, feat/login-api
hotfix/{내용}      예) hotfix/payment-error
```

- 브랜치명에 이슈 번호를 넣지 않는다
- 내용은 영문 소문자 + 하이픈으로, 무엇을 하는지 한 줄로 알 수 있게 작성한다
- 스토리 이슈 설명란에 생성한 브랜치명을 기록한다

```
예: (S15P21A403-100) [AUTH-001] 이메일 회원가입

`feat/signup`
```

### 1.4 보호 규칙

- `master`, `develop-*` 은 직접 커밋·직접 push 금지. 반드시 MR을 통해서만 반영한다
- `master`, `develop-*` 브랜치는 삭제하지 않는다
- 병합이 끝난 `feat/*`, `hotfix/*` 는 원격에서 삭제한다
- 원격에 push한 브랜치는 rebase 하지 않는다

---

## 2. 작업 흐름

### 2.1 전체 순서

```
Jira 스토리 생성
  └─ 하위 작업(Task) 생성·담당자 지정
      └─ 스토리 기준으로 feat 브랜치 생성
          └─ 작업 단위로 커밋 ([이슈키] type: subject)
              └─ develop-{파트} 로 MR 생성
                  └─ 리뷰 승인 → Merge → 브랜치 삭제
```

### 2.2 명령어

```bash
# 1. 최신 develop 동기화
git switch develop-FE
git pull origin develop-FE

# 2. 스토리 단위 브랜치 생성
git switch -c feat/signup

# 3. 작업 단위로 커밋
git add .
git commit -m "[S15P21A403-101] feat: 이메일 회원가입 화면 및 입력 상태 구현"

# 4. 원격 푸시
git push -u origin feat/signup

# 5. GitLab 에서 develop-FE 대상 MR 생성
```

### 2.3 develop 변경사항 따라잡기

작업 중 `develop-*` 이 앞서갔다면 merge 로 따라잡는다.

```bash
git switch feat/signup
git fetch origin
git merge origin/develop-FE
# 충돌 해결 후
git push
```

---

## 3. 커밋 메시지 컨벤션

커밋은 작업(Task) 단위로 작성하며, 맨 앞에 Jira 이슈 키를 붙인다.

```
[<JIRA-KEY>] <type>: <subject>
```

### type 종류

| type | 의미 |
| --- | --- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅, 세미콜론 등 (로직 변경 없음) |
| `refactor` | 리팩토링 (기능 변화 없음) |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드, 설정, 패키지 매니저 등 |

### 예시

```
[S15P21A403-15] docs: README 작성
[S15P21A403-23] feat: 카카오 로그인 API 연동
[S15P21A403-31] fix: 장바구니 수량 0일 때 삭제 안 되는 버그 수정
[S15P21A403-40] chore: React 개발 환경 및 ESLint 설정 구성
```

### 규칙

- 이슈 키는 대괄호 `[ ]` 로 감싸 맨 앞에 위치시킨다
- subject 는 한글 기준 50자 이내
- 명령형/서술형 중 팀 내 하나로 통일한다
- 커밋에 사용하는 이슈 키는 브랜치의 스토리 번호가 아니라 실제 수행한 작업(Task) 번호다

---

## 4. MR(Merge Request) 규칙

### 4.1 기본 원칙

- 모든 `feat/*` 작업은 소속 파트의 `develop-*` 으로 MR을 생성한다. 직접 push 금지
- 리뷰어 2인 이상의 승인(Approve) 후 병합한다
- 작성자 본인은 승인 카운트에 포함되지 않는다
- 승인 후에도 미해결(unresolved) 코멘트가 있으면 병합하지 않는다

### 4.2 MR 타이틀

```
[에픽 코드] 작업 타이틀

예) [INFRA] AI 개발 환경 구축
    [CHAT] 채팅 agent 아키텍처 구현
    [DEPLOY] develop-BE → master
    [SYNC] master → develop-AI
```

### 4.3 MR 본문 템플릿

```markdown
## 작업 내용
- 무엇을 했는지 요약

## 관련 이슈
- `S15P21A403-22`
- `S15P21A403-23`

## 변경 사항
- [ ] 주요 변경점 1
- [ ] 주요 변경점 2

## 테스트
- 어떻게 테스트했는지 (선택)
```

이 MR에 포함된 커밋의 이슈 번호를 전부 기재한다.

### 4.4 리뷰 가이드

- 24시간 이내 1차 리뷰 응답을 원칙으로 한다
- 같은 파트의 팀원이 Approve 하는 것을 기본으로 한다
- 24시간 내 응답이 불가능한 경우 작업을 막지 않고 진행한다
- 리뷰어 확인 관점
  - 요구사항 / Jira 이슈 내용과 일치하는가
  - 컨벤션(네이밍, 포맷, 커밋 메시지)을 지켰는가
  - 사이드 이펙트가 없는가

### 4.5 머지 방식

- 병합 방식은 Merge 를 기본으로 한다
- 병합 후 원격 `feat/*` 브랜치는 삭제한다

---

## 5. 배포 및 동기화 MR

`develop-*` ↔ `master` 사이의 이동도 MR로 처리하며, 타이틀 접두사로 목적을 구분한다.

| 접두사 | 방향 | 목적 |
| --- | --- | --- |
| `[DEPLOY]` | `develop-*` → `master` | 배포 가능한 상태를 운영에 반영 |
| `[SYNC]` | `master` → `develop-*` | 핫픽스·타 파트 변경사항을 개발 브랜치에 동기화 |

```
[DEPLOY] develop-BE → master
- 메인에 배포

[SYNC] master → develop-AI
- 메인-디벨롭 동기화
```

---

## 6. 핫픽스 프로세스

운영(`master`)에서 발생한 긴급 버그에만 사용한다.

1. `master` 에서 `hotfix/{내용}` 분기
2. 긴급 수정 후 `master` 대상 MR 생성
3. 리뷰어 2인 이상 승인 (BE 1 / FE 1 / 필요 시 AI 1)
4. `master` 에 병합
5. 모든 `develop-*` 브랜치에 `[SYNC]` MR로 반영
6. `hotfix/*` 브랜치 삭제

```bash
git switch master
git pull origin master
git switch -c hotfix/payment-error
# 수정 후
git commit -m "[S15P21A403-132] fix: 결제 승인 응답 파싱 오류 수정"
git push -u origin hotfix/payment-error
```

---

## 7. Jira 연계

### 7.1 이슈 구조와 Git의 대응 관계

```
에픽        →  MR 타이틀의 접두사      [AUTH] 인증
├─ 스토리   →  브랜치 단위             feat/signup
└─ 작업     →  커밋 단위               [S15P21A403-101] feat: ...
```

- 서브 작업은 사용하지 않는다
- 스토리에는 스토리 포인트를 입력하지 않으며, 실제 작업량은 `작업` 에서 관리한다
- 상세한 이슈 작성 규칙(제목 형식, 스토리 포인트, 레이블, 스프린트)은 별도의 Jira 컨벤션 문서를 따른다

### 7.2 연동 설정

1. Jira 프로젝트 설정 → 앱(Apps) → **GitLab for Jira** 설치
2. 연동할 그룹/저장소 선택 후 권한 승인
3. 연동 완료 시 이슈 상세 화면의 개발(Development) 탭에 커밋·MR이 자동 표시

| 구분 | 필요한 것 | 결과 |
| --- | --- | --- |
| 연동만 | 커밋 메시지에 이슈 키 포함 | 개발 탭에 커밋/MR 표시 |
| 자동화까지 | 이슈 키 + Smart Commit 명령어 | 이슈 상태·댓글·작업시간 자동 반영 |

---

## 8. 체크리스트

### 작업 시작 전

- [ ] Jira 에 스토리와 작업이 생성되어 있고, 작업에 담당자·스토리 포인트·스프린트·기한이 입력되어 있다
- [ ] 최신 `develop-{파트}` 를 pull 했다
- [ ] 브랜치명이 `feat/{내용}` 규칙을 따른다

### MR 생성 전

- [ ] 커밋 메시지가 `[JIRA-KEY] type: subject` 형식을 지킨다
- [ ] `master`, `develop-*` 에 직접 커밋하지 않았다
- [ ] 로컬에서 빌드/린트가 통과한다
- [ ] MR 대상이 `develop-{파트}` 다 (핫픽스는 `master`)
- [ ] MR 본문에 관련 이슈 번호를 전부 기재했다

### 병합 후

- [ ] 리뷰어 2인 이상 승인을 받았다
- [ ] 미해결 코멘트가 없다
- [ ] 원격 `feat/*` 브랜치를 삭제했다
- [ ] (핫픽스인 경우) 모든 `develop-*` 에 `[SYNC]` 반영을 마쳤다
