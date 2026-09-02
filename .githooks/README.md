# Git Hook 가이드

커밋 컨벤션과 보호 브랜치 규칙을 **커밋 단계에서** 검사한다.
규칙 자체는 [`git-workflow.md`](../git-workflow.md) 를 따르며, 이 폴더의 스크립트는 그 규칙을 강제할 뿐이다.

## 적용

저장소를 clone 한 뒤 **한 번만** 실행한다.

```bash
# macOS / Linux / Windows Git Bash
sh .githooks/install.sh
```

```powershell
# Windows PowerShell
powershell -ExecutionPolicy Bypass -File .githooks\install.ps1
```

스크립트가 하는 일은 `core.hooksPath` 를 `.githooks` 로 지정하는 것뿐이라, 아래 명령과 동일하다.

```bash
git config core.hooksPath .githooks
```

`.git/hooks` 는 clone 시 따라오지 않지만 `.githooks` 는 저장소에 포함되므로, 팀원 전원이 같은 훅을 쓰게 된다.

적용 여부는 이렇게 확인한다.

```bash
git config core.hooksPath   # .githooks 가 출력되면 적용된 것
```

## 검사 항목

### `commit-msg`

커밋 메시지 제목이 아래 형식인지 검사하고, 어긋나면 커밋을 중단한다.

```
[<JIRA-KEY>] <type>: <subject>

예) [S15P21A403-15] docs: README 작성
```

- type: `feat` `fix` `docs` `style` `refactor` `test` `chore`
- 이슈 키는 브랜치의 스토리 번호가 아니라 실제 수행한 작업(Task) 번호
- subject 가 50자를 넘으면 **경고만** 하고 커밋은 통과시킨다
- `Merge` / `Revert` / `fixup!` / `squash!` 로 시작하는 메시지는 Git이 자동 생성하므로 검사하지 않는다

### `pre-commit`

| 검사 | 동작 |
| --- | --- |
| 보호 브랜치(`master`, `develop-*`) 직접 커밋 | 중단 |
| 충돌 마커(`<<<<<<<`), 공백 오류 | 중단 |

외부 의존성이 없어 즉시 끝난다.

**린트/포맷 검사는 훅에서 하지 않는다.** 훅은 `--no-verify` 로 우회할 수 있어 게이트가 되지 못하고,
매 커밋마다 비용을 치르면서 우회 습관을 만들어 정작 중요한 `commit-msg` 검사까지 함께 꺼지게 만든다.
린트는 MR 파이프라인(CI)에서 강제하고, 로컬에서는 각자 IDE의 저장 시 포맷을 쓴다.

## 우회

훅을 건너뛰고 커밋해야 하는 예외 상황에는 `--no-verify` 를 쓴다.

```bash
git commit --no-verify -m "..."
```

다만 보호 브랜치는 원격에서도 직접 push가 막혀 있으므로, `--no-verify` 로 `master` 에 커밋해도 push 단계에서 거부된다.

## 해제

```bash
git config --unset core.hooksPath
```

## 문제 해결

**훅이 동작하지 않는다**
`git config core.hooksPath` 가 `.githooks` 인지 확인한다. 비어 있으면 적용 스크립트를 다시 실행한다.

**`Permission denied` 가 뜬다 (macOS / Linux)**
실행 권한을 준다.

```bash
chmod +x .githooks/commit-msg .githooks/pre-commit
```

**Windows에서 훅이 무시된다**
Git for Windows에 포함된 `sh` 로 실행되므로 별도 설정은 필요 없다. GUI 클라이언트(GitHub Desktop, SourceTree 등)는 훅을 건너뛰는 경우가 있으니 커밋은 CLI나 IDE의 Git 기능을 쓰는 것을 권장한다.
