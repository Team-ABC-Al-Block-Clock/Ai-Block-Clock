# 정산금 출금

공급자가 **정산금**(실행에서 받은 ABC, 장부 `PAYOUT_BALANCE`)을 로그인한 지갑으로 abcToken 으로 보내 달라고 신청한다.
ABC 1 = abcToken 1개다. 본인 확인(ACC-004) → 신청(PAY-023) → 관리자 심사(PAY-024) → 체인 지급(PAY-025) 순이다.

이 문서는 **본인 확인 · 신청 · 취소 · 관리자 심사**까지다. 지급은 그 작업에서 이어 적는다.
심사까지는 체인에 아무것도 보내지 않는다. SSAFY 토큰증권 API 는 승인된 신청을 지급할 때 처음 부른다.

---

## 1. 흐름

```
⓪ 공급자가 실명 · 생년월일을 내고 관리자가 승인한다   POST /api/v1/users/me/identity-verification   (6장)
① 공급자가 금액과 요청 번호를 보낸다                 POST /api/v1/withdrawals
② 사용자 행을 잠그고 본인 확인 · 정책을 본다          WithdrawalService → WithdrawalPolicy
③ 신청을 적고(REQUESTED) 금액을 정산금에서 묶는다     WithdrawalWriter · LedgerTransaction.forWithdrawalHold
④ (심사 전) 공급자가 취소하면 묶은 금액을 돌려놓는다  POST /api/v1/withdrawals/{withdrawalId}/cancel
⑤ 관리자가 승인하거나 반려한다                        POST /api/v1/admin/withdrawals/{withdrawalId}/approve · reject   (7장)
⑥ 승인된 신청만 지급한다                            PAY-025
```

| 상태 | 뜻 | 처리 중 | 신청 주기에 셈 |
| --- | --- | --- | --- |
| `REQUESTED` | 심사 대기. 금액이 묶여 있다 | ✓ | ✓ |
| `CANCELED` | 공급자가 거둠. 금액이 정산금으로 돌아왔다 | | |
| `APPROVED` | 관리자가 승인. 지급을 기다린다. 금액은 계속 묶여 있다 | ✓ | ✓ |
| `REJECTED` | 관리자가 반려. 사유가 남고 금액이 정산금으로 돌아왔다 | | |

상태가 늘 때 두 칸을 `WithdrawalStatus` 에 함께 정한다. 취소 · 반려처럼 돈이 나가지 않고 닫힌 것은 신청 주기에 세지 않는다.

```
REQUESTED ──취소(공급자)──▶ CANCELED
    │
    ├──승인(관리자)──▶ APPROVED ──지급(PAY-025)──▶ …
    │
    └──반려(관리자)──▶ REJECTED
```

취소 · 승인 · 반려는 모두 `REQUESTED` 에서만 한다. 한 번 닫히거나 승인된 신청은 되돌리지 않는다.
승인한 뒤에 공급자가 취소하면 409 `PAYMENT_016` 이다. 지급이 시작된 신청을 멈추면 장부와 체인이 어긋난다.

**신청 주기는 아직 걸리지 않는다.** 주기에 세는 상태가 처리 중인 `REQUESTED` · `APPROVED` 뿐이라, 그런 출금이 있으면
처리 중인 출금 검사(`PAYMENT_012`)에서 먼저 막히고 없으면 마지막 신청 시각이 비어 주기 검사를 건너뛴다.
"끝났지만 세는" 지급 완료 상태가 생기는 지급(PAY-025)부터 걸린다. 그때 주입한 `Clock` 으로 주기 경계를 통합 테스트한다.

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
| 마지막 신청에서 지나야 할 시간 | 24시간 | 409 `PAYMENT_013` (`data`: `availableAt`). 지급(PAY-025)부터 걸린다 (1장) |
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

한 사람의 신청과 취소는 **사용자 행을 잠가**(`UserReader.findByIdForUpdate`) 한 줄로 세운다. 늦은 요청은 앞 요청이 커밋할 때까지
기다린 뒤 앞 요청의 결과를 보고 판단하므로 처리 중인 출금 · 정산금 검사가 겹치지 않는다. 정산금 칸은 받은 적 없는 사람에게 아직
없을 수 있어 잠글 대상으로 쓰지 않았다.

취소도 같은 잠금을 쓴다. 잠그지 않으면 두 가지가 어긋난다.

- 연타로 두 취소가 겹치면 둘 다 심사 대기를 읽고 들어와, 늦은 쪽이 상태를 못 바꿔 409 로 떨어진다. 잠그면 늦은 쪽이 이미 취소된 신청을 읽고 그대로 돌려받는다.
- 신청이 잠금을 쥔 사이에 취소가 커밋되면, 신청은 방금 사라진 출금을 처리 중으로 보고 `PAYMENT_012` 를 낸다. 잠그면 취소가 신청 뒤에 서서, 신청이 본 그대로가 맞는 답이 된다.

**잠금은 트랜잭션의 첫 조회여야 한다.** MySQL(REPEATABLE READ)은 첫 일반 조회 때 읽는 기준 시점을 정한다.
잠금 전에 한 번이라도 읽으면, 잠금을 얻은 뒤에도 앞 신청이 커밋한 출금이 보이지 않는다. 순서를 거꾸로 두고
`WithdrawalServiceMySqlTest` 를 돌리면 동시에 보낸 5건이 모두 묶였다. H2 에서는 이 차이가 드러나지 않아 MySQL 에서도 돌린다.

| 경우 | 결과 |
| --- | --- |
| 같은 `requestId` 가 다시 옴 (재시도 · 연타) | 새로 묶지 않고 앞의 신청을 돌려준다 |
| 같은 `requestId` 가 동시에 옴 | 늦은 쪽이 잠금 뒤 앞의 신청을 찾아 돌려준다. 그래도 겹치면 UNIQUE 가 막는다 (409 `PAYMENT_014`) |
| 다른 `requestId` 가 동시에 옴 | 하나만 묶이고 나머지는 409 `PAYMENT_012` |
| 취소가 두 번 옴 (순서대로 · 동시에) | 늦은 쪽은 이미 취소된 신청을 그대로 돌려받는다. 정산금은 한 번만 돌아온다 |
| 취소와 관리자 심사가 겹침 (PAY-024) | 상태가 심사 대기일 때만 바꾸므로 한쪽만 된다. 늦은 쪽은 409 `PAYMENT_016` |
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
암호화 키를 서버 설정으로 새로 들여야 해서 이번에는 넣지 않았다 (8장).

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

심사는 사용자 행을 잠그지 않는다. 관리자는 신청한 사람이 아니라 그 잠금으로 줄 설 까닭이 없다.
대신 **상태 조건부 UPDATE**(`WithdrawalRepository.updateReviewIfCurrent`, `WHERE status = 'REQUESTED'`)가 막는다.
취소도 같은 조건(`updateStatusIfCurrent`)으로 쓰므로, 취소가 사용자 행을 잠가도 취소와 심사 가운데 먼저 커밋한 쪽만 된다.

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

## 8. 아직 하지 않은 것

| 무엇 | 어디서 |
| --- | --- |
| 승인된 신청을 보관 지갑에서 abcToken 으로 지급, 묶은 금액을 장부에서 내보내기, 중복 지급 막기 | PAY-025 · CHN-005/006 |
| 출금 이력 · 상세 조회 (사용자가 반려 사유를 보는 자리) | PAY-026 |
| 본인 확인 실명 · 생년월일 암호화 저장 | 암호화 키를 서버 설정으로 들이는 작업과 함께 |
| 플랫폼 부담으로 준 정산금만큼 보관 지갑을 채우기 | 지급 전에 운영자가 한다 (엔진 계약 v2.0 대응의 플랫폼 부담 정산이 들어온 뒤) |
