#!/bin/sh
#
# Git Hook 적용 스크립트 (macOS / Linux / Windows Git Bash)
#
#   sh .githooks/install.sh
#
set -e

cd "$(git rev-parse --show-toplevel)"

git config core.hooksPath .githooks
chmod +x .githooks/commit-msg .githooks/pre-commit 2>/dev/null || true

printf '\n  ✔ Git Hook 적용 완료 (core.hooksPath = %s)\n\n' "$(git config core.hooksPath)"
printf '    · commit-msg : 커밋 메시지 컨벤션 검사\n'
printf '    · pre-commit : 보호 브랜치 차단, 충돌 마커 검사\n\n'
printf '    해제하려면: git config --unset core.hooksPath\n\n'
