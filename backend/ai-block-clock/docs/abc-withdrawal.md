# 정산금 출금

공급자가 **정산금**(실행에서 받은 ABC, 장부 `PAYOUT_BALANCE`)을 로그인한 지갑으로 abcToken 으로 보내 달라고 신청한다.
ABC 1 = abcToken 1개다. 신청(PAY-023) → 관리자 심사(PAY-024) → 체인 지급(PAY-025) 순이다.

이 문서는 **신청과 취소**까지다. 심사와 지급은 그 작업에서 이어 적는다.

---

## 1. 흐름

```
① 공급자가 금액과 요청 번호를 보낸다                 POST /api/v1/withdrawals
② 사용자 행을 잠그고 정책을 본다                     WithdrawalService → WithdrawalPolicy
③ 신청을 적고(REQUESTED) 금액을 정산금에서 묶는다     WithdrawalWriter · LedgerTransaction.forWithdrawalHold
④ (심사 전) 공급자가 취소하면 묶은 금액을 돌려놓는다  POST /api/v1/withdrawals/{withdrawalId}/cancel
⑤ 관리자 심사 → 지급                                PAY-024 · PAY-025
```

| 상태 | 뜻 | 처리 중 | 신청 주기에 셈 |
| --- | --- | --- | --- |
| `REQUESTED` | 심사 대기. 금액이 묶여 있다 | ✓ | ✓ |
| `CANCELED` | 공급자가 거둠. 금액이 정산금으로 돌아왔다 | | |

상태가 늘 때 두 칸을 `WithdrawalStatus` 에 함께 정한다. 반려처럼 돈이 나가지 않고 닫힌 것은 신청 주기에 세지 않는다.

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

`GET /credits/balance` 의 `payoutAmount` 는 묶인 금액을 뺀 값이다. 묶인 금액은 출금 이력(PAY-026)에서 보여 준다.

---

## 3. 정책 (PAY-027)

금액 범위 → 처리 중인 출금 → 신청 주기 → 정산금 순으로 본다. 사용자가 금액만 고치면 되는 것부터 알려 준다.

| 규칙 | 기본값 | 어기면 |
| --- | --- | --- |
| 한 번에 신청할 수 있는 금액 | 1 ~ 1,000 ABC | 400 `PAYMENT_010` (`data`: `minimumAmount` · `maximumAmount`) |
| 처리 중인 출금은 하나 | — | 409 `PAYMENT_012` |
| 마지막 신청에서 지나야 할 시간 | 24시간 | 409 `PAYMENT_013` (`data`: `availableAt`) |
| 정산금 안에서만 | — | 409 `PAYMENT_011` (`data`: `payoutAmount`) |

| 정한 것 | 까닭 |
| --- | --- |
| 최소 1 ABC | 실행 한 번에 쌓이는 정산금이 원가 수준(0.01 ABC 대)이다. 크게 잡으면 판매자가 한참 동안 출금하지 못한다 |
| 최대 1,000 ABC | 관리자가 한 번에 심사할 만한 크기다. 더 큰 금액은 나눠 신청한다 |
| 취소한 신청은 주기에 안 셈 | 잘못 신청해 거둔 사람이 하루를 기다리지 않게 한다 |
| 받을 지갑은 로그인 지갑 | 요청 본문으로 받으면 세션을 훔친 사람이 제 지갑으로 뺄 수 있다. 신청할 때 주소를 옮겨 적어 지갑이 바뀌어도 신청한 곳으로 보낸다 |

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

---

## 5. 설정

`application.yaml` 의 `payment.withdrawal.*` (소유: `core-api`). 값이 틀리면 기동하지 않는다.

| 설정 | 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- | --- |
| `payment.withdrawal.minimum-amount` | `PAYMENT_WITHDRAWAL_MINIMUM_AMOUNT` | `1` | 소수 6자리까지, 0 보다 커야 한다 |
| `payment.withdrawal.maximum-amount` | `PAYMENT_WITHDRAWAL_MAXIMUM_AMOUNT` | `1000` | 최소 이상이어야 한다 |
| `payment.withdrawal.request-interval` | `PAYMENT_WITHDRAWAL_REQUEST_INTERVAL` | `24h` | 0 이면 주기 없이 처리 중인 출금만 막는다. 시연에서 쓴다 |

---

## 6. 아직 하지 않은 것

| 무엇 | 어디서 |
| --- | --- |
| 본인 확인을 마쳐야 신청할 수 있게 | PAY-024 · ACC-004 (본인 확인 저장이 먼저다) |
| 관리자 승인 · 반려. 반려는 `forWithdrawalRelease` 로 푼다 | PAY-024 · ADM-001 |
| 보관 지갑에서 abcToken 지급, 묶은 금액을 장부에서 내보내기 | PAY-025 · CHN-005/006 |
| 출금 이력 · 상세 조회 | PAY-026 |
| 플랫폼 부담으로 준 정산금만큼 보관 지갑을 채우기 | 지급 전에 운영자가 한다 (엔진 계약 v2.0 대응의 플랫폼 부담 정산이 들어온 뒤) |
