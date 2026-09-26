# ABC 충전

사용자가 MetaMask 로 **abcToken 을 보관 지갑에 보내면**, 서버가 그 거래를 SSAFY 체인에서 확인하고
**같은 수만큼 ABC 를 장부에 적립한다.** abcToken 1개 = ABC 1 이다.

ABC 잔액·차감·환불·정산은 계속 서버 DB 장부에서 한다. 체인은 "토큰이 실제로 들어왔는가" 를 확인할 때만 쓴다.
실행마다 체인을 부르지 않아야 체인이 느려져도 서비스가 멈추지 않는다.

---

## 1. 흐름

```
① 운영자가 사용자 지갑에 abcToken 을 발행한다         scripts/mint-abc-token.mjs
② 사용자가 MetaMask 로 transfer(보관 지갑, 금액 × 10^18)  FE
③ FE 가 받은 거래 해시를 보낸다                         POST /api/v1/credits/charges
④ 서버가 요청을 저장하고(PENDING) 바로 한 번 체인에 묻는다  ChargeRequestService → ChargeProcessingService
⑤ 체인 답을 입금 한 건으로 바꾸고 대조한다                OnChainTransferReader → OnChainChargeVerifier
⑥ 맞으면 ABC 를 적립하고 CONFIRMED                        ChargeConfirmationManager
⑦ 아직 블록에 없으면 PENDING 으로 두고 10초마다 다시 묻는다  ChargeVerificationScheduler
⑧ FE 는 상태를 다시 묻는다                              GET /api/v1/credits/charges/{chargeId}
```

| 상태 | 뜻 |
| --- | --- |
| `PENDING` | 확인 중. 체인이 "아직" 이라고 했거나 체인에 닿지 못했다 |
| `CONFIRMED` | 적립 완료. `creditedAmount` 가 온다 |
| `FAILED` | 실패. `failureReason` 이 온다. ABC 는 늘지 않는다 |

---

## 2. 체인에서 무엇을 읽는가

SSAFY 토큰증권 API(`sto.ssafy.io`)에는 **거래 해시로 결과를 읽는 기능이 없다.** 그래서 체인 노드에
JSON-RPC 로 직접 묻는다 (`:client:blockchain` 의 `SsafyChainClient`).

| RPC | 무엇을 아나 |
| --- | --- |
| `eth_getTransactionByHash` | 노드가 이 해시를 아는가, 체인 ID |
| `eth_getTransactionReceipt` | 블록에 적혔는가, 성공했는가, 몇 번 블록인가, 이벤트 로그 |
| `eth_blockNumber` | 지금 몇 번 블록인가 → 확인 블록 수 = 지금 − 거래 블록 + 1 |

**보낸 사람·받은 사람·수량은 영수증의 `from` · `to` 가 아니라 `Transfer` 이벤트 로그에서 읽는다.**
토큰 전송 거래의 `to` 는 받는 사람이 아니라 토큰 컨트랙트다. 로그 중 **우리 abcToken 컨트랙트가 남긴
`Transfer(from, to, value)`** 만 본다. abcToken(ERC-1400)은 `transfer` 와 `transferByPartition` 모두
이 이벤트를 함께 남긴다.

`value` 는 체인에 적힌 정수(최소 단위)다. abcToken 은 decimals 18 이라 **10^18 으로 나눈 것이 토큰 개수**다.

### 2.1 연결

| | 값 | 근거 |
| --- | --- | --- |
| RPC | `https://rpc.ssafy-blockchain.com` | 문서에는 `wss://ws.ssafy-blockchain.com` 만 있다. HTTPS 주소는 직접 호출해 확인했다 (2026-09-25) — 체인 ID 31221, 블록 번호가 wss 와 같고, CORS 가 `j15a403.p.ssafy.io` 를 허용한다. **SSAFY 쪽 공식 확인은 아직 없다** |
| 방식 | HTTP (web3j `HttpService`) | 10초에 한 번 묻기만 해서 연결을 붙잡아 둘 이유가 없다. WebSocket 은 끊김·재연결을 따로 챙겨야 한다 |
| 합의 | QBFT | `rpc_modules` 에 `qbft` 가 있다. 확정된 블록이 되돌려지지 않아 **확인 블록 1** 로 충분하다 |
| 블록 간격 | 약 10초 | 블록 100개가 1000초였다. 그래서 재확인 간격도 10초다 |
| 가스비 | 0 | `eth_gasPrice` 가 `0x0`, `baseFeePerGas` 가 없다(레거시 거래). 사용자에게 수수료 코인이 필요 없다 |

### 2.2 체인에 닿지 못할 때

연결 실패·시간 초과·노드 오류·깨진 응답은 **"실패" 가 아니라 "모름"(`UNKNOWN`)** 으로 둔다.
거래가 이미 성공했을 수도 있기 때문이다. 요청은 `PENDING` 으로 남고 **대기 한도도 재지 않는다.**

재확인은 체인에 닿지 못하면 그 차례의 남은 요청을 더 묻지 않고, 다음 차례까지의 간격을
**10초 → 20초 → 40초 … 5분**까지 두 배씩 늘린다. 체인에 다시 닿으면 10초로 돌아온다.

해시 형식이 틀린 것만은 체인에 있을 수 없는 거래라 "없음" 으로 두어 대기 한도에 닫히게 한다.

---

## 3. 대조 규칙

`OnChainChargeVerifier` 가 아래를 차례로 본다. 하나라도 어긋나면 `FAILED` 와 그 사유가 남는다.

| 순서 | 대조 | 어긋나면 |
| --- | --- | --- |
| 1 | 체인 ID = 31221 | `CHAIN_ID_MISMATCH` |
| 2 | 거래 해시 | `TRANSACTION_HASH_MISMATCH` |
| 3 | 보낸 사람 = **로그인한 지갑** | `SENDER_WALLET_MISMATCH` |
| 4 | 받은 사람 = 보관 지갑 | `CUSTODY_ADDRESS_MISMATCH` |
| 5 | 토큰 = abcToken | `DEPOSIT_TOKEN_CONTRACT_MISMATCH` |
| 6 | 수량 = 요청 금액 | `AMOUNT_MISMATCH` |
| 7 | 확인 블록 ≥ 1 | (모자라면 기다린다) |

- 거래가 되돌려졌으면(영수증 status 0) `ONCHAIN_TRANSACTION_FAILED`
- 요청을 받고 **30분**이 지나도록 블록에 적히지 않으면 `VERIFICATION_TIMEOUT`
- **보낸 지갑은 요청 본문으로 받지 않는다.** 로그인한 사용자의 지갑이다. 남의 입금 해시를 들고 와도 3번에서 걸린다
- 한 거래에 보관 지갑으로 가는 abcToken 전송이 둘 이상이면 요청 금액과 한 건씩 맞출 수 없어 금액을 비워 6번에서 걸리게 한다
- 수량은 소수 6자리까지다(장부 금액 칸 `DECIMAL(…, 6)`). 6자리를 넘는 자투리가 있는 입금은 반올림하지 않고 6번에서 걸린다

### 3.1 요청 금액을 받는 이유

체인 금액을 그대로 적립하지 않고 FE 가 보낸 금액과 비교한다. FE 가 단위를 잘못 계산해(예: 10^18 을 빠뜨림)
엉뚱한 양을 보냈을 때 **조용히 적립되지 않고 드러나게** 하려는 것이다. 불일치로 실패하면 보관 지갑에 들어간
토큰은 사용자가 보낸 금액으로 같은 해시를 다시 요청하면 적립된다 (3.3). FE 가 MetaMask 에 넣은 값과 같은 변수를 보내면 어긋날 일은 없다.

### 3.2 보관 지갑은 충전할 수 없다

로그인한 지갑이 보관 지갑이면 요청 자체를 막는다 (`PAYMENT_009`, 403). 보관 지갑이 스스로에게 보내면
토큰은 제자리인데 ABC 만 늘어나 **같은 토큰으로 끝없이 충전**할 수 있다.

### 3.3 같은 거래로 두 번

- 해시 하나에 요청은 한 줄이다(`(chain_id, transaction_hash)` UNIQUE).
- 그 줄이 **확인 중이거나 적립됐으면** 주인에게는 그 줄을 그대로 돌려주고, 다른 사용자는 막는다 (`PAYMENT_003`, 409)
- 그 줄이 **요청이 거래와 어긋나 닫혔으면** — 보낸 사람 다름 · 금액 다름 · 대기 한도 초과 — **누가 요청하든 그 줄을 지우고
  새 요청으로 바꿔 끼워 다시 확인한다** (`ChargeStatusReason.isRetryable`). 해시는 체인에서 누구나 볼 수 있어서,
  이 길이 없으면 남이 먼저 올려 닫힌 해시를 진짜 입금자가 되살릴 수 없다. 금액을 잘못 적었거나 거래가 30분을 넘겨 성사된
  본인도 같은 해시로 다시 요청하면 된다
  - 누가 바꿔 끼워도 ABC 는 체인에 찍힌 보낸 사람 = 로그인 지갑일 때만 적립되므로 가로챌 수 없다
  - 지우는 줄은 적립된 적 없는 실패 줄이라 장부에 흔적이 없다. 새 줄은 번호(`chargeId`)가 달라지므로 FE 는 마지막 요청 응답의 번호로 묻는다
  - 두 사람이 동시에 바꿔 끼우려 하면 버전 조건부 삭제가 한 명만 통과시킨다. 진 쪽은 이긴 쪽의 줄을 기준으로 판단한다
- 거래 자체의 사실로 닫힌 줄(되돌려짐 · 받는 곳 다름 · 토큰 다름 · 체인 다름)은 다시 봐도 같으므로 닫힌 채 둔다
- 적립은 장부의 멱등 키 `charge-request:{id}` 로 한 번만 일어난다. 요청 직후 확인과 주기 재확인이 동시에
  옮기면 낙관적 락(`version`)이 뒤쪽을 막고, 뒤쪽은 먼저 옮긴 결과를 다시 읽어 돌려준다

---

## 4. 설정

`application.yaml` 의 `payment.charge.*` (소유: `core-api`) 와 `blockchain.yml` 의 `ssafy.chain.*` ·
`ssafy.sto.token.*` (소유: `:client:blockchain`). 값이 틀리면 기동하지 않는다.

| 설정 | 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- | --- |
| `payment.charge.chain-id` | `PAYMENT_CHARGE_CHAIN_ID` | `31221` | 로그인(`wallet-auth.chain-id`)과 같은 체인 |
| `payment.charge.custody-address` | `PAYMENT_CHARGE_CUSTODY_ADDRESS` | `0x64e7…6B6` | 입금 전용 지갑. **권한 없는 일반 계정**이고 발행(오너·민터) 계정과 나눴다 |
| `payment.charge.deposit-token-contract-address` | (`SSAFY_TOKEN_CONTRACT_ADDRESS`) | abcToken | 토큰 주소는 `ssafy.sto.token.contract-address` 하나만 둔다 |
| `payment.charge.required-confirmations` | `PAYMENT_CHARGE_REQUIRED_CONFIRMATIONS` | `1` | QBFT 라 1 로 충분하다 |
| `payment.charge.poll-interval` | `PAYMENT_CHARGE_POLL_INTERVAL` | `10s` | 블록 간격 |
| `payment.charge.max-poll-interval` | `PAYMENT_CHARGE_MAX_POLL_INTERVAL` | `5m` | 체인에 닿지 못할 때 늘리는 간격의 상한 |
| `payment.charge.verification-timeout` | `PAYMENT_CHARGE_VERIFICATION_TIMEOUT` | `30m` | 이 시간 안에 블록에 적히지 않으면 실패 |
| `payment.charge.polling-enabled` | `PAYMENT_CHARGE_POLLING_ENABLED` | `true` | 테스트에서만 끈다 (`src/test/resources/config/application.yaml`) |
| `ssafy.chain.rpc-url` | `SSAFY_CHAIN_RPC_URL` | `https://rpc.ssafy-blockchain.com` | |
| `ssafy.chain.connect-timeout` · `read-timeout` | `SSAFY_CHAIN_CONNECT_TIMEOUT` · `SSAFY_CHAIN_READ_TIMEOUT` | `3s` · `10s` | |

보관 지갑을 운영 중에 바꾸면 안 된다. 바꾸기 전 주소로 보낸 대기 중 충전이 `CUSTODY_ADDRESS_MISMATCH` 로 실패한다.
보관 지갑의 개인키는 출금 지급을 켜는 운영 서버에만 둔다(`SSAFY_CHAIN_SIGNER_PRIVATE_KEY`, [abc-withdrawal.md](abc-withdrawal.md) 9.4).
충전 확인은 키 없이 돈다.

---

## 5. 운영자가 사용자에게 토큰을 넣어 줄 때

구매나 무료 지급 기능은 없다. 운영자가 발행 스크립트로 넣는다. 수량은 **ABC 단위**로 넣는다.

```bash
# 서명할 본문을 먼저 출력한다. 출력된 한 줄을 민터 계정으로 서명한다
SSAFY_TOKEN_CONTRACT_ADDRESS=0x56DC… SSAFY_TOKEN_MINT_REQUESTOR=0x7afB… SSAFY_TOKEN_MINT_RECIPIENT=0x… \
SSAFY_TOKEN_MINT_ABC=1000 SSAFY_TOKEN_MINT_DATA=… SSAFY_TOKEN_MINT_PRINT_PAYLOAD=true node scripts/mint-abc-token.mjs

# 서명을 넣어 발행한다. 발행 전후 잔액·총공급량이 정확히 그만큼 늘었는지 스스로 확인한다
… SSAFY_TOKEN_MINT_SIGNATURE=0x… node scripts/mint-abc-token.mjs
```

스크립트는 수량을 부동소수(`Number`)로 바꾸지 않고 정수 글자 그대로 보낸다. `Number` 를 거치면
2^53 을 넘는 수의 끝자리가 뭉개지고, 10^21 이상은 `1e+21` 로 나간다. 1 ABC 가 이미 10^18 이다.

---

## 6. 아직 확인하지 않은 것

| 무엇 | 왜 |
| --- | --- |
| SSAFY 가 `https://rpc.ssafy-blockchain.com` 을 써도 된다고 확인 | 문서에 없는 주소다. 동작만 확인했다 |
| RPC · 토큰증권 API 의 호출 제한 | 문서에 없다. 막히면 2.2 의 간격 늘리기가 받친다 |
| 발행 API 가 10^18 단위의 큰 정수를 그대로 받는지 | 실제로 발행해 보기 전에는 모른다. 스크립트가 발행 뒤 잔액 증가량을 대조해 어긋나면 알린다 |
| 실제 MetaMask 전송으로 처음부터 끝까지 | 가짜 체인 답으로만 시험했다. 발행 → 전송 → 요청을 한 번 해 봐야 한다 |
| 30분 · 금액 제한 없음 | 기획 확인 전 기본값이다. 전부 설정이다. 30분을 넘겨 성사된 거래는 사용자가 같은 해시로 다시 요청하면 된다 (3.3) |
