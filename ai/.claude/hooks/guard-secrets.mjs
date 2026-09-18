#!/usr/bin/env node
// PreToolUse 훅 — 비밀 파일을 읽거나 내용을 출력하려는 도구 호출을 막는다.
//
// 규칙 원문은 ai/CLAUDE.md §4 "비밀". 이 스크립트는 그 규칙을 강제할 뿐이다.
// 입력: stdin 으로 Claude Code 훅 JSON ({ tool_name, tool_input }).
// 출력: 차단할 때만 permissionDecision=deny JSON. 그 외에는 아무것도 내지 않는다.
//
// 대상 파일
//   .env 계열   .env  .env.local  .jira.env  .gemini.env   (단, .env.example 은 허용)
//   키 파일     *.pem  *api key*.txt
//
// Bash · PowerShell 은 "파일을 읽어 보여주거나 복사하는" 명령만 막는다.
//   막음   cat .env / grep KEY .env / python -c "open('.env')" / cp J15A403T.pem ~/.ssh/ / tr -d x < .env
//   허용   ssh -i J15A403T.pem … / docker compose --env-file .env up / ls -la / git check-ignore .env
// grep -r 로 키 이름을 훑는 것도 막는다 — .env 줄이 결과에 섞인다 (Grep 도구는 .gitignore 를 따라 안전).
//
// 주의: 정규식의 백슬래시 문자는 \x5c 로 쓴다. 에이전트가 파일을 쓸 때 이중 백슬래시가 한 겹 벗겨진 사고가 있었다.

import { readFileSync } from "node:fs";

let input = {};
try {
  input = JSON.parse(readFileSync(0, "utf8"));
} catch {
  process.exit(0);
}

const tool = String(input.tool_name ?? "");
const ti = input.tool_input ?? {};

function deny(reason) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: {
        hookEventName: "PreToolUse",
        permissionDecision: "deny",
        permissionDecisionReason:
          `[guard-secrets] ${reason} — 비밀 파일은 열지 않는다 (ai/CLAUDE.md §4). ` +
          `키 이름만 필요하면 사용자에게 묻는다.`,
      },
    }),
  );
  process.exit(0);
}

// ── 1. 경로를 받는 도구 (Read · Edit · Write · Grep · NotebookEdit) ─────────────
const SECRET_PATH_RE = /(^|[/\x5c])([\w.-]*\.env|[^/\x5c]*\.pem|[^/\x5c]*api[ _-]?key[^/\x5c]*\.txt)$/i;
const EXAMPLE_RE = /\.env\.example$/i;

for (const key of ["file_path", "path", "notebook_path"]) {
  const p = ti[key];
  if (typeof p === "string" && SECRET_PATH_RE.test(p) && !EXAMPLE_RE.test(p)) {
    deny(`${tool} 이 비밀 파일 경로를 가리킨다: ${p}`);
  }
}

// ── 2. 명령을 받는 도구 (Bash · PowerShell) ─────────────────────────────────────
const cmd = typeof ti.command === "string" ? ti.command : "";
if (cmd) {
  // 파일 이름을 "언급"하는 것만으로는 막지 않는다 — 문서에 파일 이름을 적는 heredoc 도 막혀 버린다.
  // 읽기 명령 + 비밀 파일 토큰이 한 세그먼트에 같이 있을 때만 막는다.
  // --exclude=.env / --exclude-dir= 뒤의 .env 는 "읽지 않겠다"는 뜻이라 토큰으로 치지 않는다
  const TOKEN = String.raw`(?:(?<!--exclude=)(?<!--exclude-dir=)[\w.-]*\.env(?!\.example)\b|[\w.-]*\.pem\b|["'][^"']*api[ _-]?key[^"']*\.txt["'])`;
  const READERS = String.raw`(?:cat|bat|head|tail|less|more|type|tac|nl|od|xxd|hexdump|strings|base64|grep|rg|egrep|fgrep|awk|sed|cut|tr|sort|uniq|wc|sha\d*sum|md5sum|cp|copy|mv|move|tee|python3?|node|perl|ruby|source|Get-Content|gc|Select-String|sls|Copy-Item|Move-Item|Set-Content|Out-File)`;
  const readsSecret = new RegExp(String.raw`(^|[\s;&|(])${READERS}\b[^|;&\n]*?${TOKEN}`, "i");
  const redirectsSecret = new RegExp(String.raw`<\s*["']?${TOKEN}`, "i");
  const dotSources = new RegExp(String.raw`(^|[;&|]\s*)\.\s+${TOKEN}`, "im");

  if (readsSecret.test(cmd) || redirectsSecret.test(cmd) || dotSources.test(cmd)) {
    deny("명령이 .env / *.pem 파일의 내용을 읽거나 복사한다");
  }

  // grep -r 로 .env 의 키 이름을 찾으면 값이 결과에 섞인다
  const recursiveGrep = /(^|[\s;&|(])(grep|egrep|fgrep)\b[^|;&\n]*\s-[a-zA-Z]*[rR]/;
  const envKeyish = /API_KEY|SECRET|PASSW|SERVICE_TOKEN|BASE_URL/i;
  if (recursiveGrep.test(cmd) && envKeyish.test(cmd) && !/--exclude(-dir)?=/.test(cmd)) {
    deny("grep -r 로 키 이름을 찾으면 .env 값이 결과에 섞인다. Grep 도구(rg, .gitignore 존중)를 쓰거나 --exclude=.env 를 붙인다");
  }
}

process.exit(0);
