# 에이전트 원본 증명

에이전트 버전을 공개하면 서버가 **"이 버전은 이 지갑이 올렸고, 내용의 지문은 이것이다"** 를 SSAFY 체인의
**등기부 토큰**에 적는다 (CHN-001). 마켓 상세 화면은 그 기록을 체인에서 다시 읽고, 지금 DB 의 내용으로 지문을
다시 만들어 대조해 보여 준다 (CHN-002 · CHN-003, MKT-006 `GET /api/v1/marketplace/agents/{agentId}/provenance`).

우리 DB 는 우리가 고칠 수 있다. 체인 기록은 우리도 고칠 수 없다. 그래서 체인 기록과 DB 가 같으면
"DB 가 말하는 원작자 · 내용이 공개 뒤에 바뀌지 않았다" 를 바깥에서 확인할 수 있다.

권한(수정 · 판매 · 정산)은 지금처럼 DB 가 판단한다. 체인 기록은 증명서이지 권리가 아니다 — NFT 처럼 넘길 수 있는 것이 아니다.

---

## 1. 등기부 토큰

SSAFY 토큰증권 API 가 배포해 주는 것은 증권형 토큰(ERC-1400) 하나뿐이다. 우리가 짠 컨트랙트는 올릴 수 없다.
ERC-1400 에는 **문서 칸(documents)** 이 들어 있어서 `이름 → (주소, 해시)` 를 적어 둘 수 있다. 이 칸을 등기부로 쓴다.

| | 값 |
| --- | --- |
| 토큰 | `ABC Agent Registry` (`abcAgentReg`). **발행하지 않는다.** 문서 칸만 쓴다 |
| 주소 | `0xBBC46459B7A364089A924cf5C091e6CD9B5AB934` (2026-09-27 배포, 블록 11376188, 거래 `0x80326b62…850f`) |
| 주인 | 등기부 지갑 「ABC 에이전트 등기부」 `0xFEAc90e2118B6A48ea46b0985eCf6b32D164f8C0` |
| 컨트롤러 | 주인 + SSAFY 시스템 지갑 `0xfa6dacC1eF321Ae9BBE4086e0693B2dD0716a1B4` (토큰증권 API 가 배포할 때 넣는다. abcToken 도 같다) |
| 배포 | `scripts/deploy-agent-registry.mjs` (한 번만. 끝났다) |

**abcToken 에 붙이지 않은 까닭.** abcToken 의 문서 칸에 쓰려면 abcToken 주인(발행 권한이 있는 지갑)의 키가 서버에
늘 있어야 한다. 등기부를 따로 두면 서버에 있는 키로 할 수 있는 일이 기록 쓰기뿐이다. 출금 보관 지갑의 키도 쓰지
않는다 — 그 키는 출금을 켜는 서버에만 두기로 했고, 기록은 늘 돌아야 한다.

**주인만 쓸 수 있다.** 문서 칸은 토큰 주인(컨트롤러)만 쓴다. 남이 우리 등기부에 가짜 기록을 적을 수 없다.
다만 SSAFY 시스템 지갑도 컨트롤러라, 플랫폼 운영자(SSAFY)는 믿는 구조다.

**가스는 우리가 내지 않는다.** 배포 영수증의 보낸 주소(`from`)가 SSAFY 시스템 지갑이고 가스 가격이 0 이었다.
토큰증권 API 가 우리 요청을 받아 자기 지갑으로 체인에 낸다. 등기부 지갑에 코인이 없어도 된다.

---

## 2. 흐름

```
① 사용자가 에이전트를 공개한다(처음 · 새 버전)           POST /api/v1/agents, PUT /api/v1/agents/{id}
② 공개 트랜잭션 안에서 PENDING 기록을 한 줄 만든다         AgentService → AgentProvenanceWriter.request
③ 15초마다 PENDING 을 집어 체인에 적는다                  AgentProvenanceScheduler → AgentProvenanceService.register
   - 버전 내용을 모아 해시를 만든다                       AgentVersionMetadataReader → AgentVersionMetadata.hash
   - 등기부 지갑 키로 서명해 문서 칸에 적는다             AgentProvenanceSender → SsafyStoDocumentClient.setDocument
④ 블록에 들어가 성공하면 CONFIRMED + 거래 · 해시 저장      AgentProvenanceWriter.confirm
⑤ 조회하면 체인에서 다시 읽어 DB 와 대조한다              AgentProvenanceQueryService → AgentProvenanceChainReader
```

- **공개 트랜잭션 안에서 체인을 부르지 않는다** (ARCHITECTURE 4.2). 체인이 느리거나 죽어도 공개는 막히지 않는다.
  공개가 되돌려지면 PENDING 기록도 같이 사라져, 없는 버전을 체인에 적지 않는다
- **버전 하나에 기록 하나.** `agent_version_id` 가 기본 키다. 공개된 버전은 바뀌지 않으므로 다시 적을 일이 없다
- **실패 상태가 없다.** 체인에 닿지 못했든, 되돌려졌든, 토큰증권 API 가 거절했든 PENDING 으로 두고 다음 차례에
  다시 보낸다. 같은 이름에 같은 값을 다시 적으면 덮어쓸 뿐 달라지는 것이 없어서(멱등) 두 번 보내도 안전하다.
  손댄 시각(`last_attempted_at`)이 오래된 것부터 집어, 계속 실패하는 한 건이 나머지를 막지 않는다
- 되돌려짐 · 거절은 설정(등기부 주인 · 키)을 사람이 고쳐야 풀린다. `ERROR` 로그로 남긴다. 닿지 못한 것은 `WARN`
- 이 기능 전에 공개한 버전은 기록이 없다. 조회하면 `BLOCKCHAIN_003` 이다

---

## 3. 체인에 적는 값

| 문서 칸 | 값 | 예 |
| --- | --- | --- |
| `name` | 에이전트 ID 16바이트를 URL-safe Base64 로 22자 + `-v버전` | `EjRWeJASNFZ4kBI0VniQEg-v1` |
| `docUri` | 에이전트 · 버전 · 원작자 지갑(소문자) | `abc://agents/{agentId}/versions/1?owner=0x535a…` |
| `docHash` | 버전 내용의 SHA-256 | `0x3f1c…` |

**이름을 줄이는 까닭.** ERC-1400 의 문서 이름은 `bytes32` 라 31자에 들어가야 한다. UUID(36자)를 그대로 쓰면 넘친다.

### 3.1 해시 규칙 (`abc-agent-metadata/1`)

322 무결성 검증이 **같은 계산을 다시 돌려** 체인 값과 대조한다. 그래서 규칙을 고정한다.

- **재료는 공개 뒤 바뀌지 않는 것만.** 에이전트 ID · 버전 ID · 버전 번호 · 원작자 지갑 · 얼려 둔 노드
  (키 · 이름 · 종류 · 모델 · 프롬프트 · 참고 파일 ID · 파일명 · 크기)
- **넣지 않는 것.** 프로필(이름 · 설명)은 버전 없이 고칠 수 있다. 등급 · 단가는 매일 다시 매긴다. 넣으면 정상적인 변경이 조작으로 보인다
- **순서.** 노드는 키 순서로 늘어놓는다. 참고 파일은 얼릴 때의 순서 그대로다
- **모양.** 칸마다 `이름=바이트 길이:값\n`. 길이를 먼저 적어서, 값 안에 어떤 글자가 있어도 두 다른 내용이 같은 글자열이 되지 않는다
- 규칙을 바꾸면 `abc-agent-metadata/2` 로 올린다. 예전 기록은 예전 규칙으로 다시 계산해야 대조가 맞는다

### 3.2 서명 규칙

토큰증권 API 의 쓰기는 `requestor` + `signature` 를 받는다. **본문에서 `signature` 를 뺀 JSON 을
MetaMask `personal_sign` 과 같은 방식으로 서명한다.** 칸 순서는 API 명세의 DTO 순서다.

```
{"requestor":"0xFEAc…","name":"…","docUri":"…","docHash":"0x…"}
```

`scripts/mint-abc-token.mjs` 가 발행에서 쓴 규칙과 같다. Swagger 에 적혀 있지 않아 **첫 실제 호출(등기부 배포)로 확인한다.**
서명한 본문과 보낸 본문이 한 글자도 달라지면 안 되므로, 이름 · 주소 · 해시는 JSON 이스케이프가 필요 없는 글자만 받는다.

---

## 4. 조회 응답

지금 공개된 버전의 기록을 보여 준다. **등록 시각만 처음 공개한 버전(v1)의 것**이다 — 원작자가 이 에이전트를 처음 내놓은 때다.

| 칸 | 어디서 |
| --- | --- |
| `owner` | DB 의 원작자 닉네임 · 지갑 |
| `blockchain.*` | DB 의 기록. PENDING 이면 계약 · 거래 · 블록 · `explorerUrl` 이 null |
| `blockchain.explorerUrl` | 토큰증권 API 의 문서 조회 주소. SSAFY 체인은 탐색기가 없어, 누구나 브라우저로 열어 체인 값을 보는 주소다 |
| `verification.offChainOwnerAddress` | DB 의 원작자 지금 지갑 |
| `verification.offChainMetadataHash` | **지금 DB 의 내용으로 방금 다시 만든 해시**(4.1). PENDING 이면 null |
| `verification.onChain*` | **조회할 때마다 체인에서 다시 읽은 값.** PENDING 이면 null |
| `verified` | CONFIRMED 이고 원작자 · 지금 내용 · 적을 때 남긴 해시가 모두 체인과 같다 |
| `verification.mismatchReason` | 검증되지 않은 까닭(4.1). `verified=true` 면 null |
| `verification.verifiedAt` | 이번에 대조한 시각. PENDING 이면 null |

| 상황 | 응답 |
| --- | --- |
| 공개된 에이전트가 없다 | 404 `MARKET_001` |
| 에이전트는 있는데 기록이 없다 | 404 `BLOCKCHAIN_003` |
| 체인에 닿지 못했다 | 503 `BLOCKCHAIN_004` — 불일치로 보여 주지 않고, 대조 결과도 남기지 않는다 |
| 체인에 기록이 없다(확정인데) | 200, `onChain*` null, `verified=false`, `mismatchReason=NOT_ON_CHAIN` |

### 4.1 무결성 검증 (CHN-003)

체인에 적힌 해시와 비교할 우리 쪽 값이 "적을 때 DB 에 남겨 둔 해시" 뿐이면, 누가 DB 에서 프롬프트를 고쳐도
남겨 둔 해시는 그대로라 일치로 나온다. 그래서 조회할 때마다 **지금 내용을 3.1 규칙으로 다시 해시**한다.
원본 기록을 보낼 때와 같은 `AgentVersionMetadataReader` · `AgentVersionMetadata.hash()` 를 쓴다.

```
AgentProvenanceQueryService.provenanceOf
  └ 확정된 기록이면 AgentIntegrityVerifier.verify
      ├ 체인에서 기록을 읽는다                  AgentProvenanceChainReader   (닿지 못하면 503, 아무것도 남기지 않는다)
      ├ 지금 내용을 모아 해시를 다시 만든다      AgentVersionMetadataReader → AgentVersionMetadata.hash
      ├ 세 해시 + 지갑을 대조한다               AgentIntegrity.check
      └ 결과를 기록 줄에 덮어쓴다               AgentProvenanceWriter.recordIntegrity
```

| 사유 | 뜻 | 대조 |
| --- | --- | --- |
| `NOT_CONFIRMED` | 아직 체인에 적히지 않았다. 대조하지 않는다 | — |
| `NOT_ON_CHAIN` | 확정했다는데 체인에 기록이 없다 | 체인 값 없음 |
| `OWNER_MISMATCH` | 원작자 지갑이 다르다 | DB 지갑 ≠ 체인 지갑 |
| `METADATA_CHANGED` | **공개 뒤 DB 의 내용이 바뀌었다** | 지금 해시 ≠ 체인 해시 |
| `RECORD_MISMATCH` | 내용은 맞는데 기록 칸의 해시가 다르다 | 지금 해시 = 체인 해시, 남긴 해시 ≠ 체인 해시 |

- 여럿이 겹치면 **표의 위쪽 것 하나**만 댄다. 지갑은 해시 재료라 지갑이 바뀌면 해시도 함께 어긋나서, 더 구체적인 지갑 쪽을 댄다
- 내용을 고치고 기록 칸의 해시까지 새 값으로 맞춰 둬도, 체인 해시와 다르므로 `METADATA_CHANGED` 로 잡힌다
- 프로필(이름 · 설명) · 등급 · 단가는 해시 재료가 아니다(3.1). 고쳐도 불일치가 아니다

**결과를 남기는 곳.** `agent_provenance` 의 세 칸이다(V41, 표는 V40). 버전 하나에 기록 하나라 이력 표를 따로 두지 않고,
조회할 때마다 마지막 결과로 덮어쓴다. 확정 전 기록에는 남기지 않는다.

| 칸 | 값 |
| --- | --- |
| `integrity_result` | `MATCHED` / `MISMATCHED` |
| `integrity_mismatch_reason` | `MISMATCHED` 일 때의 사유 (`NOT_CONFIRMED` 는 남지 않는다) |
| `integrity_checked_at` | 마지막으로 대조한 시각 |

불일치가 난 기록은 DB 에서 바로 찾을 수 있다.

```sql
select agent_id, version_no, integrity_mismatch_reason, integrity_checked_at
from agent_provenance
where integrity_result = 'MISMATCHED';
```

**조회가 쓰기를 하는 까닭.** 지금도 조회할 때마다 체인을 읽어 대조한다. 같은 대조를 따로 돌리는 배치를 두면
스케줄러와 설정이 하나씩 늘고, 결과는 조회 응답과 같다. 쓰기는 체인을 읽은 뒤 `Writer` 의 짧은 트랜잭션 하나다.
동시에 두 번 조회해도 같은 대조라 나중 것이 남아도 괜찮다.

---

## 5. 설정

| 설정 (소유) | 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- | --- |
| `ssafy.sto.agent-registry.contract-address` (`:client:blockchain`) | `SSAFY_AGENT_REGISTRY_CONTRACT_ADDRESS` | `0xBBC4…B934` | 등기부 토큰 주소. 배포한 것이 기본값이라 보통 넣지 않는다 |
| `ssafy.sto.agent-registry.signer-private-key` (`:client:blockchain`) | `SSAFY_AGENT_REGISTRY_SIGNER_PRIVATE_KEY` | 비어 있음 | 등기부 지갑 개인키. **서버 `backend.env` 에만** |
| `ssafy.sto.agent-registry.write-timeout` (`:client:blockchain`) | `SSAFY_AGENT_REGISTRY_WRITE_TIMEOUT` | `30s` | 쓰기 응답 제한 시간. 토큰증권 API 가 블록(약 10초)을 기다렸다 답한다 |
| `agent-provenance.registration-enabled` | `AGENT_PROVENANCE_REGISTRATION_ENABLED` | `false` | 체인에 보내는 주기 작업을 켠다. 꺼 두면 PENDING 으로 쌓인다 |
| `agent-provenance.registration-interval` | `AGENT_PROVENANCE_REGISTRATION_INTERVAL` | `15s` | 주기 |
| `agent-provenance.registration-batch-size` | `AGENT_PROVENANCE_REGISTRATION_BATCH_SIZE` | `5` | 한 차례에 보내는 최대 건수 |
| `agent-provenance.chain-id` | — | `payment.charge.chain-id` | 응답에 싣는 체인 ID |
| `agent-provenance.network-name` | `AGENT_PROVENANCE_NETWORK_NAME` | `SSAFY` | 응답에 싣는 네트워크 이름 |

- 키가 없으면 기록을 쓰지 않는 서버로 뜬다(조회는 한다). 키를 넣었으면 주소 · 제한 시간까지 맞아야 뜬다
- 보내기를 켰는데 키가 없으면 **기동하지 않는다.** 조용히 쌓이게 두지 않는다
- `compose.yaml` 에 주소를 `${…:-}` 로 넘기지 않는다. 값이 없을 때 빈 글자가 넘어가 기본값을 덮는다

---

## 6. 켜는 절차

1~3 은 2026-09-27 에 끝났다(1장의 주소). 다시 배포할 일이 생길 때를 위해 남긴다. Windows cmd 기준이다.

1. 등기부 지갑 주소로 배포할 본문을 출력한다

   ```bat
   set SSAFY_AGENT_REGISTRY_REQUESTOR=0xFEAc90e2118B6A48ea46b0985eCf6b32D164f8C0
   set SSAFY_AGENT_REGISTRY_PRINT_PAYLOAD=true
   node scripts/deploy-agent-registry.mjs
   ```

2. 출력된 한 줄을 등기부 지갑(MetaMask)으로 `personal_sign` 한다.
   서명 페이지는 저장소 밖(`.tmp/ssafy-registry-sign`)에 두었다. `node server.mjs` 로 띄우고 Chrome 에서 `http://127.0.0.1:5501` 을 연다
3. 출력 모드를 끄고 서명을 넣어 배포한다. 끝나면 `contractAddress` 가 찍힌다

   ```bat
   set SSAFY_AGENT_REGISTRY_PRINT_PAYLOAD=
   set SSAFY_AGENT_REGISTRY_SIGNATURE=0x…
   node scripts/deploy-agent-registry.mjs
   ```

4. 서버 `backend.env` 에 `SSAFY_AGENT_REGISTRY_SIGNER_PRIVATE_KEY` · `AGENT_PROVENANCE_REGISTRATION_ENABLED=true` 를
   넣고 다시 띄운다. 주소는 기본값이라 넣지 않는다
5. 에이전트를 하나 공개하고, 30초쯤 뒤 원본 증명 조회가 `CONFIRMED` · `verified=true` 인지 본다.
   `explorerUrl` 을 브라우저로 열면 체인에 적힌 값이 보인다
6. (무결성 검증 확인 · 시연용) 그 버전의 프롬프트를 DB 에서 직접 고치고 다시 조회하면
   `metadataMatched=false` · `mismatchReason=METADATA_CHANGED` 가 나온다. 확인한 뒤 프롬프트를 되돌리면 다시 `verified=true` 다

   ```sql
   update agent_version_node set prompt = concat(prompt, '.')
   where agent_version_id = '<공개된 버전 ID>';
   ```

---

## 7. 아직 확인하지 못한 것

| 무엇 | 왜 모르나 | 어떻게 확인하나 |
| --- | --- | --- |
| 문서 쓰기의 서명 규칙 | Swagger 에 없다. 발행과 같다고 보았다. 같은 규칙으로 서명한 배포는 받아들여졌지만, 예전에 서명 없이 보낸 abcToken 배포도 통과했어서 토큰증권 API 가 서명을 검사하는지는 모른다 | 6장 5번이 `CONFIRMED` 가 되면 맞다 |
| 문서 이름이 `bytes32` 로 옮겨지는 방식 | 토큰증권 API 내부라 보이지 않는다. 31자 · 영문 · 숫자 · `-` `_` 로 줄였다 | 6장 5번이 `CONFIRMED` 가 되면 맞다 |

가스는 확인했다(1장). 배포 거래를 SSAFY 시스템 지갑이 가스 가격 0 으로 냈다.
