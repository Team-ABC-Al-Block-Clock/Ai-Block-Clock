# 정산금 출금

공급자가 **정산금**(실행에서 받은 ABC, 장부 `PAYOUT_BALANCE`)을 로그인한 지갑으로 abcToken 으로 보내 달라고 신청한다.
ABC 1 = abcToken 1개다. 본인 확인(ACC-004) → 신청(PAY-023) → 관리자 심사(PAY-024) → 체인 지급(PAY-025) 순이다.

이 문서는 **본인 확인 · 신청 · 취소 · 관리자 심사 · 체인 지급**을 다룬다.
정산금을 출금하지 않고 쓸 수 있는 ABC 로 옮기는 길도 여기 적는다(8장).
심사까지는 체인에 아무것도 보내지 않는다. 승인된 신청만 보관 지갑이 서명한 전송으로 체인에 보낸다(9장).

---

## 1. 흐름

```
⓪ 공급자가 실명 · 생년월일을 내고 관리자가 승인한다   POST /api/v1/users/me/identity-verification   (6장)
① 공급자가 금액과 요청 번호를 보낸다                 POST /api/v1/withdrawals
② 사용자 행을 잠그고 본인 확인 · 정책을 본다          WithdrawalService → WithdrawalPolicy
③ 신청을 적고(REQUESTED) 금액을 정산금에서 묶는다     WithdrawalWriter · LedgerTransaction.forWithdrawalHold
④ (심사 전) 공급자가 취소하면 묶은 금액을 돌려놓는다  POST /api/v1/withdrawals/{withdrawalId}/cancel
⑤ 관리자가 승인하거나 반려한다                        POST /api/v1/admin/withdrawals/{withdrawalId}/approve · reject   (7장)
⑥ 승인된 신청을 한 건씩 체인으로 보낸다              WithdrawalPayoutScheduler → WithdrawalPayoutService   (9장)
```

| 상태 | 뜻 | 처리 중 | 신청 주기에 셈 |
| --- | --- | --- | --- |
| `REQUESTED` | 심사 대기. 금액이 묶여 있다 | ✓ | ✓ |
| `CANCELED` | 공급자가 거둠. 금액이 정산금으로 돌아왔다 | | |
| `APPROVED` | 관리자가 승인. 지급을 기다린다. 금액은 계속 묶여 있다 | ✓ | ✓ |
| `REJECTED` | 관리자가 반려. 사유가 남고 금액이 정산금으로 돌아왔다 | | |
| `PAYING` | 서명한 전송을 체인에 보냈다. 거래 해시가 적혀 있고 금액은 계속 묶여 있다 | ✓ | ✓ |
| `PAID` | 체인에서 성공을 확인했다. 묶은 금액을 장부에서 내보냈다 | | ✓ |

상태가 늘 때 두 칸을 `WithdrawalStatus` 에 함께 정한다. 취소 · 반려처럼 돈이 나가지 않고 닫힌 것은 신청 주기에 세지 않는다.

```
REQUESTED ──취소(공급자)──▶ CANCELED
    │
    ├──승인(관리자)──▶ APPROVED ──서명 · 전송──▶ PAYING ──체인 성공──▶ PAID
    │
    └──반려(관리자)──▶ REJECTED
```

취소 · 승인 · 반려는 모두 `REQUESTED` 에서만 한다. 한 번 닫히거나 승인된 신청은 되돌리지 않는다.
승인한 뒤에 공급자가 취소하면 409 `PAYMENT_016` 이다. 지급이 시작된 신청을 멈추면 장부와 체인이 어긋난다.

---

## 2. 장부 — 신청하면 묶는다

신청하는 순간 금액을 정산금에서 **출금 예치**(`WITHDRAWAL_ESCROW`)로 옮긴다. 실행 예치(`EXECUTION_ESCROW`)와 같은 역할이다.
묶지 않으면 심사를 기다리는 동안 같은 정산금으로 또 신청할 수 있다.

| 거래 | 빠지는 칸 (−) | 들어가는 칸 (+) | 키 |
| --- | --- | --- | --- |
| `WITHDRAWAL_HOLD` (신청) | `PAYOUT_BALANCE` | `WITHDRAWAL_ESCROW` | `withdrawal-hold:{출금ID}` |
| `WITHDRAWAL_RELEASE` (취소 · 반려) | `WITHDRAWAL_ESCROW` | `PAYOUT_BALANCE` | `withdrawal-release:{출금ID}` |

두 거래 모두 **같은 사람의 칸 사이**에서만 옮긴다. 장부 생성자의 모양 검사(PAY-004,
[agent-pipeline.md](plans/completed/agent-pipeline.md) 8.8)가 막으므로 출금을 핑계로 남에게 ABC 를 넘길 수 없다.
취소와 반려는 같은 키를 써서, 둘이 겹쳐도 한 신청에 한 번만 풀린다.

승인은 장부를 건드리지 않는다. 금액은 지급(PAY-025)이 끝날 때까지 출금 예치에 남는다.

`GET /credits/balance` 의 `payoutAmount` 는 묶인 금액을 뺀 값이다. 묶인 금액은 출금 이력(PAY-026)에서 보여 준다.

---

## 3. 정책 (PAY-027)

본인 확인 → 금액 범위 → 처리 중인 출금 → 신청 주기 → 정산금 순으로 본다. 본인 확인은 금액을 고쳐도 풀리지 않아 가장 먼저 알린다.
그다음부터는 사용자가 금액만 고치면 되는 것부터 알려 준다.

| 규칙 | 기본값 | 어기면 |
| --- | --- | --- |
| 본인 확인을 승인받았다 (6장) | — | 403 `PAYMENT_017` |
| 한 번에 신청할 수 있는 금액 | 1 ~ 1,000 ABC | 400 `PAYMENT_010` (`data`: `minimumAmount` · `maximumAmount`) |
| 처리 중인 출금은 하나 (`REQUESTED` · `APPROVED`) | — | 409 `PAYMENT_012` |
| 마지막 신청에서 지나야 할 시간 | 24시간 | 409 `PAYMENT_013` (`data`: `availableAt`) |
| 정산금 안에서만 | — | 409 `PAYMENT_011` (`data`: `payoutAmount`) |

| 정한 것 | 까닭 |
| --- | --- |
| 최소 1 ABC | 실행 한 번에 쌓이는 정산금이 원가 수준(0.01 ABC 대)이다. 크게 잡으면 판매자가 한참 동안 출금하지 못한다 |
| 최대 1,000 ABC | 관리자가 한 번에 심사할 만한 크기다. 더 큰 금액은 나눠 신청한다 |
| 취소 · 반려한 신청은 주기에 안 셈 | 잘못 신청해 거뒀거나 반려된 사람이 하루를 기다리지 않게 한다 |
| 받을 지갑은 로그인 지갑 | 요청 본문으로 받으면 세션을 훔친 사람이 제 지갑으로 뺄 수 있다. 신청할 때 주소를 옮겨 적어 지갑이 바뀌어도 신청한 곳으로 보낸다 |
| 같은 `requestId` 재시도는 본인 확인보다 먼저 본다 | 이미 접수된 신청의 재시도가 그 사이 바뀐 상태 때문에 실패로 보이지 않게 한다 |

---

## 4. 동시에 들어온 신청

한 사람의 신청은 **사용자 행을 잠가**(`UserReader.findByIdForUpdate`) 한 줄로 세운다. 늦은 신청은 앞 신청이 커밋할 때까지
기다린 뒤 앞 신청을 보고 판단하므로 처리 중인 출금 · 정산금 검사가 겹치지 않는다. 정산금 칸은 받은 적 없는 사람에게 아직
없을 수 있어 잠글 대상으로 쓰지 않았다.

**잠금은 트랜잭션의 첫 조회여야 한다.** MySQL(REPEATABLE READ)은 첫 일반 조회 때 읽는 기준 시점을 정한다.
잠금 전에 한 번이라도 읽으면, 잠금을 얻은 뒤에도 앞 신청이 커밋한 출금이 보이지 않는다. 순서를 거꾸로 두고
`WithdrawalServiceMySqlTest` 를 돌리면 동시에 보낸 5건이 모두 묶였다. H2 에서는 이 차이가 드러나지 않아 MySQL 에서도 돌린다.

| 경우 | 결과 |
| --- | --- |
| 같은 `requestId` 가 다시 옴 (재시도 · 연타) | 새로 묶지 않고 앞의 신청을 돌려준다 |
| 같은 `requestId` 가 동시에 옴 | 늦은 쪽이 잠금 뒤 앞의 신청을 찾아 돌려준다. 그래도 겹치면 UNIQUE 가 막는다 (409 `PAYMENT_014`) |
| 다른 `requestId` 가 동시에 옴 | 하나만 묶이고 나머지는 409 `PAYMENT_012` |
| 취소가 두 번 옴 | 이미 취소했으면 그대로 돌려준다. 겹치면 늦은 쪽은 409 `PAYMENT_016` |
| 신청을 적은 뒤 장부에 못 적음 | 한 트랜잭션이라 신청도 함께 되돌려진다 |

심사가 겹치는 경우는 7장에 적는다.

---

## 5. 설정

`application.yaml` (소유: `core-api`). 값이 틀리면 기동하지 않는다.

| 설정 | 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- | --- |
| `payment.withdrawal.minimum-amount` | `PAYMENT_WITHDRAWAL_MINIMUM_AMOUNT` | `1` | 소수 6자리까지, 0 보다 커야 한다 |
| `payment.withdrawal.maximum-amount` | `PAYMENT_WITHDRAWAL_MAXIMUM_AMOUNT` | `1000` | 최소 이상이어야 한다 |
| `payment.withdrawal.request-interval` | `PAYMENT_WITHDRAWAL_REQUEST_INTERVAL` | `24h` | 0 이면 주기 없이 처리 중인 출금만 막는다. 시연에서 쓴다 |
| `admin.wallet-addresses` | `ADMIN_WALLET_ADDRESSES` | 비어 있음 | 관리자 지갑. 쉼표로 여럿. 비우면 아무도 심사할 수 없다 (7장) |
| `springdoc.*.enabled` (prod) | `API_DOCS_ENABLED` | `true` | 관리자가 배포 서버의 Swagger 로 심사한다. 끄면 심사할 화면이 없다 |
| `payment.withdrawal.payout.enabled` | `PAYMENT_WITHDRAWAL_PAYOUT_ENABLED` | `false` | 승인된 출금을 체인으로 보낸다. 보관 지갑 키가 있는 서버에서만 켠다 (9.4) |
| `payment.withdrawal.payout.poll-interval` | `PAYMENT_WITHDRAWAL_PAYOUT_POLL_INTERVAL` | `10s` | 지급 차례 간격. 블록이 약 10초마다 생긴다 |
| `ssafy.chain.signer.private-key` (소유: `:client:blockchain`) | `SSAFY_CHAIN_SIGNER_PRIVATE_KEY` | 비어 있음 | 보관 지갑 개인키. 서버의 `backend.env` 에만. 키의 주소가 `payment.charge.custody-address` 와 다르면 기동하지 않는다 |

체인 ID · 보관 지갑 · 토큰 주소는 충전 설정(`payment.charge.*`, [abc-charge.md](abc-charge.md) 4장)을 그대로 쓴다. 넣은 지갑에서 내보내기 때문이다.

---

## 6. 본인 확인 (ACC-004)

지갑 로그인은 **"이 지갑의 주인"** 까지만 확인한다. 그 지갑 뒤의 실제 사람은 모른다.
돈이 밖으로 나가기 전에 실명 · 생년월일을 받아 관리자가 확인한다. 사람마다 한 번 받는다.

| API | 누가 | 하는 일 |
| --- | --- | --- |
| `POST /api/v1/users/me/identity-verification` | 사용자 | 실명(1~50자) · 생년월일(오늘 이전)을 낸다. 202 `SUBMITTED` |
| `GET /api/v1/users/me/identity-verification` | 사용자 | 내 상태와 반려 사유. 낸 적 없으면 404 `USER_009` |
| `GET /api/v1/admin/identity-verifications` | 관리자 | 심사 대기를 오래 기다린 것부터 |
| `POST /api/v1/admin/identity-verifications/{verificationId}/approve` | 관리자 | 승인. 그 사용자가 출금을 신청할 수 있다 |
| `POST /api/v1/admin/identity-verifications/{verificationId}/reject` | 관리자 | 사유(1~200자, 필수)를 남기고 반려 |

```
(없음) ──제출──▶ SUBMITTED ──승인──▶ APPROVED
                   ▲   │
                   │   └──반려──▶ REJECTED
                   └────다시 제출────┘
```

| 경우 | 결과 |
| --- | --- |
| 심사 중에 다시 냄 | 409 `USER_007`. 관리자가 보던 정보가 바뀌지 않게 한다 |
| 승인된 뒤에 다시 냄 | 409 `USER_008`. 확인한 사람이 바뀌지 않게 한다 |
| 반려된 뒤에 다시 냄 | 같은 줄을 고친 정보로 덮고 `SUBMITTED` 로 돌아간다. 지난 심사 기록은 지운다 |
| 처음 제출이 동시에 두 번 옴 | 사용자당 한 줄 제약(`uq_identity_verification_user`)이 늦은 쪽을 막는다 (409 `USER_007`) |
| 심사 대기가 아닌 것을 승인 · 반려 | 409 `USER_010` |
| 이미 승인(반려)한 것을 다시 승인(반려) | 그대로 돌려준다. 반려 사유는 처음 것이 남는다 |

**개인정보를 암호화하지 않고 저장한다.** 실명 · 생년월일만 받고, 관리자 API 와 본인에게만 보인다.
암호화 키를 서버 설정으로 새로 들여야 해서 이번에는 넣지 않았다 (10장).

---

## 7. 관리자 심사 (PAY-024 · ADM-001)

### 7.1 관리자는 누구인가

`admin.wallet-addresses` 에 적힌 지갑으로 **로그인하면** 세션에 `ROLE_ADMIN` 권한이 붙는다(`SessionAuthenticationManager`).
`SecurityConfig` 가 `/api/v1/admin/**` 에 `hasRole("ADMIN")` 을 걸어, 로그인만 한 사용자는 403 을 받는다.

- 권한은 세션에 함께 저장된다. 목록을 바꾸면 그 지갑이 **다시 로그인해야** 반영된다.
- 관리자 가입 화면 · DB 역할 칸은 두지 않았다. 관리자는 운영자가 서버 설정(`backend.env`)으로 정한다.

### 7.2 출금 심사 API

| API | 하는 일 |
| --- | --- |
| `GET /api/v1/admin/withdrawals` | `REQUESTED` 신청을 오래된 것부터, 신청한 사람의 본인 확인(실명 · 상태)과 함께 |
| `POST /api/v1/admin/withdrawals/{withdrawalId}/approve` | `APPROVED`. 장부는 그대로, 지급을 기다린다 |
| `POST /api/v1/admin/withdrawals/{withdrawalId}/reject` | 사유(1~200자, 필수)를 남기고 `REJECTED`. 같은 트랜잭션에서 `WITHDRAWAL_RELEASE` 로 금액을 돌려놓는다 |

승인 · 반려한 관리자(`reviewer_id`)와 시각(`reviewed_at`)을 신청에 남긴다.

### 7.3 겹치는 심사

심사는 사용자 행을 잠그지 않는다. 대신 **상태 조건부 UPDATE**(`WithdrawalRepository.updateReviewIfCurrent`,
`WHERE status = 'REQUESTED'`)가 막는다. 취소도 같은 조건(`updateStatusIfCurrent`)이라 둘 중 먼저 커밋한 쪽만 된다.

| 경우 | 결과 |
| --- | --- |
| 공급자 취소와 관리자 반려가 동시에 옴 | 한쪽만 되고 늦은 쪽은 409 `PAYMENT_016`. 금액은 한 번만 풀린다 (키도 같다) |
| 관리자 여럿이 동시에 반려 | 하나만 적히고 나머지는 409 `PAYMENT_016` 이거나 앞의 결과를 그대로 받는다. 한 번만 풀린다 |
| 취소한 신청을 승인 · 반려 | 409 `PAYMENT_016` |
| 이미 승인(반려)한 신청을 다시 승인(반려) | 그대로 돌려준다 |
| 반려를 적은 뒤 장부에 못 적음 | 한 트랜잭션이라 반려도 함께 되돌려져 `REQUESTED` 로 남는다 |

`WithdrawalServiceIntegrationTest` 가 H2 에서, `WithdrawalServiceMySqlTest` 가 MySQL 에서 같은 경우를 돌린다.

### 7.4 Swagger 로 심사하기 (시연 절차)

관리자 화면이 없어 관리자는 **배포 서버의 Swagger** 로 심사한다.
Nginx 가 `/api/` 만 백엔드로 넘기므로 문서도 `/api/swagger-ui.html` · `/api/v3/api-docs` 에 둔다.

1. FE 에서 관리자 지갑으로 MetaMask 로그인한다.
2. **같은 브라우저**에서 `https://j15a403.p.ssafy.io/api/swagger-ui.html` 을 연다. 세션 쿠키(`SESSION`, 경로 `/api/v1`)가 자동으로 따라간다.
3. `GET /api/v1/auth/csrf` 를 실행해 응답의 `token` 을 복사한다.
4. **Authorize** 의 `X-CSRF-TOKEN` 칸에 붙여 넣는다. `SESSION` 칸은 비워 둔다(HttpOnly 쿠키라 브라우저가 보낸다).
5. `GET /api/v1/admin/identity-verifications` · `GET /api/v1/admin/withdrawals` 로 대기 목록을 보고 승인 · 반려한다.

- 로그아웃 · 재로그인하면 CSRF 토큰이 바뀐다. 3~4 를 다시 한다.
- 문서의 서버 주소는 `/`(연 곳 그대로)다. Nginx 뒤에서 `http://` 주소가 적혀 https 화면이 요청을 막는 일을 피한다.
- 문서는 누구나 볼 수 있다. 관리자 API 는 권한이 있어야 불린다. 심사가 필요 없어지면 `API_DOCS_ENABLED=false` 로 끈다.

---

## 8. 정산금을 쓸 수 있는 ABC 로 옮기기

정산금은 실행에 쓸 수 없다. 번 ABC 로 다른 에이전트를 쓰려면 출금(심사 · 24시간 주기) → 다시 충전을 거쳐야 해서,
장부 안에서 **같은 사람의 정산금을 쓸 수 있는 잔액으로 옮기는 길**을 따로 둔다. 체인에는 아무것도 보내지 않는다.

`POST /api/v1/credits/payout-conversions` — 본문 `{requestId, amount}`, 200 으로 옮긴 금액(`convertedAmount`)과 옮긴 뒤 잔액(`balance`)을 준다.

| 거래 | 빠지는 칸 (−) | 들어가는 칸 (+) | 키 |
| --- | --- | --- | --- |
| `PAYOUT_CONVERSION` | `PAYOUT_BALANCE` | `ABC_BALANCE` | `payout-conversion:{사용자ID}:{requestId}` |

옮긴 줄은 ABC 사용 내역(`GET /credits/transactions`)에 `PAYOUT_CONVERSION` 으로 나온다. 한 번 옮기면 정산금으로 되돌리지 않는다.

| 정한 것 | 까닭 |
| --- | --- |
| 금액은 0 보다 크고 소수 6자리까지, 상한 없음 | 돈이 서비스 밖으로 나가지 않아 한 번에 심사할 크기가 없다 |
| 본인 확인 · 신청 주기를 보지 않는다 | 같은 이유다. 본인 확인은 돈이 밖으로 나가기 전의 확인이다(6장) |
| 출금에 묶인 금액은 옮길 수 없다 | 묶인 금액은 이미 `payoutAmount` 에서 빠져 있다. 처리 중인 출금이 있어도 남은 정산금은 옮긴다 |
| 키에 사용자를 넣는다 | `requestId` 는 화면이 만든다. 남이 같은 값을 보내도 서로의 옮기기에 막히지 않는다 |
| 옮기기 기록을 따로 두지 않는다 | 장부 거래 하나로 끝나고, 재시도에 돌려줄 금액은 그 거래의 줄에서 읽는다(`PayoutConversionReader`). 마이그레이션이 없다 |

정산금을 줄이는 두 길(출금 신청 · 옮기기)은 **같은 사용자 행 잠금**(`UserReader.findByIdForUpdate`)으로 한 줄로 선다.
4장과 같은 까닭으로 잠금이 트랜잭션의 첫 조회다.

| 경우 | 결과 |
| --- | --- |
| 정산금보다 많이 옮김 | 409 `PAYMENT_018` (`data`: `payoutAmount`). 아무것도 옮기지 않는다 |
| 같은 `requestId` 가 다시 옴 | 또 옮기지 않고 처음 옮긴 금액과 지금 잔액을 준다. 줄어든 정산금으로 다시 재지 않아 재시도가 실패로 보이지 않는다 |
| 다른 `requestId` 로 동시에 옴 | 잠금 순서대로 정산금 안에서만 옮기고 나머지는 409 `PAYMENT_018` |
| 출금 신청과 동시에 옴 | 한쪽이 먼저 정산금을 쓰고, 늦은 쪽은 남은 정산금으로 판단한다 |

`PayoutConversionServiceIntegrationTest` 가 H2 에서, `PayoutConversionServiceMySqlTest` 가 MySQL 에서 같은 경우를 돌린다.

---

## 9. 지급 (PAY-025 · CHN-005/006)

승인된 신청을 **보관 지갑**(사용자가 충전해 넣은 지갑, `payment.charge.custody-address`)에서 신청한 지갑으로 abcToken 을 보낸다.
서버가 보관 지갑의 개인키로 abcToken `transfer(받는 지갑, ABC × 10^18)` 에 서명하고 SSAFY 체인 RPC 로 보낸다.

| 정한 것 | 까닭 |
| --- | --- |
| 토큰증권 API 가 아니라 RPC 로 보낸다 | 토큰증권 API 에는 일반 전송이 없다(발행 · 위임 전송 · 회수 · 문서뿐). 위임 전송 · 발행은 모든 토큰을 움직이거나 새로 찍는 권한 키를 서버에 둬야 한다. 토큰증권 API 는 운영자 발행 창구로만 쓰고, 충전 전송 · 확인도 이미 RPC 로 한다 |
| 보관 지갑 키만 서버에 둔다 | 권한 없는 일반 계정이라 키가 새도 잃는 것은 보관 지갑 잔액까지다 |
| 가스 가격 0, 가스는 보낼 때 잰다 | SSAFY 체인의 `eth_gasPrice` 가 0 이다. 가스 한도는 `eth_estimateGas` 로 그때그때 재서 근거 없는 숫자를 설정에 두지 않는다 |
| 한 번에 한 건 | 보관 지갑의 거래 번호(nonce)는 차례대로 써야 한다. 앞 거래가 끝나기 전에 다음 것을 서명하면 앞 거래가 사라졌을 때 번호가 꼬인다. 스레드 하나(`payoutScheduler`)가 10초마다 돈다 |
| 성공을 확인한 뒤에 장부에서 내보낸다 | 보낸 것만으로는 성공이 아니다(242 결과 판정 원칙). 확인 전까지 금액은 출금 예치에 묶여 있다 |

### 9.1 한 차례에 하는 일

```
① 지급 중(PAYING)인 출금이 있으면 그 거래를 체인에서 읽는다     OnChainPayoutSender.readStatus
   · 성공      → PAID + 장부 WITHDRAWAL_PAYOUT (한 트랜잭션)     WithdrawalPayoutManager
   · 아직      → 기다린다
   · 노드가 모름 → 적어 둔 거래를 그대로 다시 보낸다
   · 되돌려짐   → 멈춘다 (뒤의 출금도 보내지 않는다, 10장)
② 지급 중인 것이 없거나 방금 끝났으면, 승인이 오래된 APPROVED 하나를 고른다
③ 보관 지갑 잔액(토큰증권 API) → 다음 거래 번호 · 가스(RPC) → 서명     OnChainPayoutSender.prepare
④ 해시 · 서명한 거래를 적고 PAYING 으로 바꾼다 (APPROVED 일 때만)    WithdrawalWriter.startPayout
⑤ 체인에 보낸다                                                       OnChainPayoutSender.submit
```

체인 호출은 트랜잭션 밖이고, ④ 와 ①의 끝내기만 각자 짧은 트랜잭션이다(ARCHITECTURE 4.2).

### 9.2 두 번 보내지 않는 길

| 길 | 어떻게 |
| --- | --- |
| 보내기 **전에** 해시를 적는다 | 서명한 바이트의 keccak256 이 곧 거래 해시다. ④ 에서 적고 ⑤ 에서 보낸다. 보낸 직후 서버가 멈춰도 무엇을 보냈는지 남는다 |
| 다시 보낼 땐 같은 거래 | 노드가 거래를 모르거나 답이 끊기면 새로 서명하지 않고 적어 둔 `payout_raw_transaction` 을 그대로 보낸다. 체인은 같은 해시를 한 번만 처리한다 |
| 한 신청에 거래 하나 | `APPROVED → PAYING` 은 조건부 UPDATE(`updatePayoutStartIfApproved`)다. 두 차례가 겹치면 늦은 쪽은 서명한 거래를 버린다 |
| 한 거래는 한 출금에 | `withdrawal.payout_transaction_hash` UNIQUE |
| 장부에도 한 번 | 키 `withdrawal-payout:{출금ID}` UNIQUE, 체인 해시도 `(chain_id, transaction_hash)` UNIQUE. `PAYING → PAID` 도 조건부 UPDATE 다 |

### 9.3 장부 — 지급을 마치면 내보낸다

| 거래 | 빠지는 칸 (−) | 들어가는 칸 (+) | 키 |
| --- | --- | --- | --- |
| `WITHDRAWAL_PAYOUT` | `WITHDRAWAL_ESCROW` (신청한 사람) | `ABC_ISSUANCE` (`SYSTEM`) | `withdrawal-payout:{출금ID}` |

충전(`CHARGE`)이 발행 계정에서 꺼내 잔액에 넣은 것을 반대로 돌린다. 서비스 안의 ABC 가 그만큼 줄고 체인의 abcToken 이 신청한 지갑으로 나간다.

### 9.4 켜기 (운영 서버)

지급은 **기본으로 꺼져 있다.** 꺼져 있으면 승인된 출금은 `APPROVED` 로 기다린다.

1. MetaMask 에서 보관 지갑(`0x64e7…6B6`)의 개인키를 내보내 서버의 `backend.env` 에만 적는다 — 저장소 · 채팅 · 이미지에 넣지 않는다.
   `SSAFY_CHAIN_SIGNER_PRIVATE_KEY=…`, `PAYMENT_WITHDRAWAL_PAYOUT_ENABLED=true`
2. 보관 지갑에 보낼 abcToken 이 있는지 본다. 모자라면 그 출금은 보내지 않고 경고 로그만 남긴 채 기다린다.
3. 다시 띄운다. 키의 주소가 보관 지갑과 다르면 **기동하지 않는다**(`PAYMENT_019`) — 토큰이 없는 지갑으로 서명하는 것을 막는다.

`SsafyChainSignerProperties` 는 키를 로그에 찍지 않는다(`toString` 이 가린다).

`WithdrawalPayoutServiceIntegrationTest` 가 H2 에서, `WithdrawalPayoutServiceMySqlTest` 가 MySQL 에서 같은 경우를 돌린다.
체인 · 토큰증권 API 는 가짜로 두고 서명은 Hardhat 공개 테스트 키로 실제로 한다.

---

## 10. 아직 하지 않은 것

| 무엇 | 어디서 |
| --- | --- |
| 체인이 되돌린 거래(`PAYING` 에 멈춤)의 복구 · 오래 모르는 거래의 보류 · 재시도 한도 | PAY-026 · CHN-007/008 |
| 충전 · 출금을 한 목록으로 보는 내역 API (FE 가 한 목록을 원함) | PAY-026 |
| 실제 체인으로 1 ABC 를 보내 처음부터 끝까지 보기 | 보관 지갑 키를 서버에 넣은 뒤 |
| 본인 확인 실명 · 생년월일 암호화 저장 | 암호화 키를 서버 설정으로 들이는 작업과 함께 |
| 플랫폼 부담으로 준 정산금만큼 보관 지갑을 채우기 | 지급 전에 운영자가 한다 (엔진 계약 v2.0 대응의 플랫폼 부담 정산이 들어온 뒤) |
