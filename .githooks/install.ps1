#
# Git Hook 적용 스크립트 (Windows PowerShell)
#
#   powershell -ExecutionPolicy Bypass -File .githooks\install.ps1
#
$ErrorActionPreference = "Stop"

Set-Location (git rev-parse --show-toplevel)

git config core.hooksPath .githooks

Write-Host ""
Write-Host "  [OK] Git Hook 적용 완료 (core.hooksPath = $(git config core.hooksPath))"
Write-Host ""
Write-Host "    - commit-msg : 커밋 메시지 컨벤션 검사"
Write-Host "    - pre-commit : 보호 브랜치 차단, 충돌 마커 검사"
Write-Host ""
Write-Host "    해제하려면: git config --unset core.hooksPath"
Write-Host ""
