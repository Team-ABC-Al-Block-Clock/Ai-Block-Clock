#!/usr/bin/env node
// PostToolUse 훅 — GitLab CI YAML 을 고친 직후 파서로 검증한다.
//
// 규칙 원문은 ai/CLAUDE.md §8 "CI". 스크립트 줄에 `콜론+공백` 이 들어가면 YAML 이 키로 읽어
// 파이프라인이 "yaml invalid" 로 죽는다. 그 실수를 push 전이 아니라 저장 직후에 잡는다.
//
// 입력: stdin 훅 JSON. Edit·Write 는 tool_input.file_path, Bash·PowerShell 은 tool_input.command 를 본다.
// 대상: 그 입력이 .gitlab-ci.yml 또는 .gitlab/ci/ 를 언급할 때만. 그 외에는 즉시 종료(비용 0).
// 검증: uv run --no-project --with pyyaml python …  (CLAUDE.md 의 수동 명령과 같은 파서)
// 출력: 실패 → decision=block + 원인 (모델이 바로 고친다). 성공 → 한 줄 systemMessage.

import { readFileSync, readdirSync, existsSync, statSync } from "node:fs";
import { spawnSync } from "node:child_process";
import path from "node:path";

let input = {};
try {
  input = JSON.parse(readFileSync(0, "utf8"));
} catch {
  process.exit(0);
}
const ti = input.tool_input ?? {};
const mentioned = [ti.file_path, ti.command].filter((v) => typeof v === "string").join("\n");
if (!/\.gitlab-ci\.ya?ml|\.gitlab[/\x5c]ci[/\x5c]/i.test(mentioned)) process.exit(0);

// CI 파일 찾기 — cwd 가 레포 밖(개인 폴더)이든 ai/ 이든 레포 루트든 찾히도록
// 프로젝트 폴더 · 그 상위 · 그 하위 한 단계를 본다.
const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();
const roots = new Set([projectDir, path.dirname(projectDir)]);
try {
  for (const d of readdirSync(projectDir, { withFileTypes: true })) {
    if (d.isDirectory() && !d.name.startsWith(".") && d.name !== "node_modules") roots.add(path.join(projectDir, d.name));
  }
} catch {}

const files = new Set();
for (const r of roots) {
  const top = path.join(r, ".gitlab-ci.yml");
  if (existsSync(top)) files.add(top);
  const ciDir = path.join(r, ".gitlab", "ci");
  if (existsSync(ciDir) && statSync(ciDir).isDirectory()) {
    for (const f of readdirSync(ciDir)) if (/\.ya?ml$/i.test(f)) files.add(path.join(ciDir, f));
  }
}
if (files.size === 0) process.exit(0);

// String.raw 라서 '\n' 은 파이썬에 그대로 두 글자로 전달된다. GitLab 의 !reference 태그는 무시한다.
const py = String.raw`
import sys, yaml
class L(yaml.SafeLoader): pass
L.add_multi_constructor('!', lambda loader, suffix, node: None)
bad = []
for f in sys.argv[1:]:
    try:
        yaml.load(open(f, encoding='utf-8'), Loader=L)
    except Exception as e:
        bad.append(f + '\n  ' + str(e))
print('\n'.join(bad))
sys.exit(1 if bad else 0)
`.trim();

const r = spawnSync("uv", ["run", "--no-project", "--with", "pyyaml", "python", "-c", py, ...files], {
  encoding: "utf8",
  timeout: 80_000,
});
const list = [...files].map((f) => path.relative(projectDir, f).replace(/\x5c/g, "/")).join(", ");

if (r.error || r.status !== 0) {
  const detail = (r.stdout || "") + (r.stderr || "") + (r.error ? String(r.error) : "");
  process.stdout.write(
    JSON.stringify({
      decision: "block",
      reason:
        `[validate-ci-yaml] CI YAML 파싱 실패. 고치기 전에는 push 하지 않는다.\n` +
        `${detail.trim().slice(-2000)}\n` +
        `힌트: 스크립트 줄에 '콜론+공백' 이 있으면 '- |' 블록 스칼라로 감싼다 (ai/CLAUDE.md §8).`,
    }),
  );
  process.exit(0);
}
process.stdout.write(JSON.stringify({ systemMessage: `CI YAML OK — ${list}` }));
