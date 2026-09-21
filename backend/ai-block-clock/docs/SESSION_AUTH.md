# 쿠키 세션 인증 구조

## 선택한 방식

브라우저 로그인에는 서버 세션 방식을 사용한다. 로그인에 성공하면 서버가 예측하기 어려운
32바이트 난수를 만들고, 원문은 `sessionToken` HttpOnly 쿠키로 브라우저에 전달한다.
DB의 `login_session.session_token_hash`에는 원문을 SHA-256으로 계산한 해시만 저장한다.

Access Token과 Refresh Token은 사용하지 않는다. 따라서 JWT 비밀키와 `/auth/reissue` API도 없다.
세션은 로그인 시점부터 7일 동안 유효하며 사용 중이라고 만료 시각이 연장되지 않는다.

## 로그인 흐름

1. FE가 `POST /api/v1/auth/wallet/message`로 서명 메시지를 받는다.
2. FE가 지갑에 메시지 서명을 요청한다.
3. FE가 지갑 주소와 서명값을 `POST /api/v1/auth/wallet/login`으로 보낸다.
4. `WalletAuthService`가 메시지 만료·재사용과 지갑 서명을 확인한다.
5. 신규 지갑이면 사용자와 ABC 잔액 계정을 만든다.
6. 사용자 행을 잠그고 해당 사용자의 기존 로그인 세션을 모두 종료한다.
7. 새 세션 토큰 원문과 해시를 만들고, 해시·사용자 ID·7일 만료 시각을 DB에 저장한다.
8. 원문은 `Set-Cookie` 응답 헤더로만 브라우저에 전달한다.

사용자 행 잠금은 같은 사용자의 로그인 두 건이 동시에 처리돼 활성 세션이 두 개 생기는 것을
막는다. 한 트랜잭션 안에서 이전 세션 종료와 새 세션 저장이 끝나야 결과가 반영된다.

## 보호 API 인증 흐름

브라우저는 `/api/v1` 요청마다 `sessionToken` 쿠키를 자동으로 보낸다.
`CurrentOwnerArgumentResolver`는 쿠키 원문을 읽고 같은 SHA-256 해시를 만든 뒤
`login_session.session_token_hash`로 세션을 조회한다. 세션이 만료되지 않았고 `revoked_at`이
비어 있으면 세션의 `user_id`를 `OwnerId`로 변환해 Controller에 전달한다.

이 구조에서는 보호 API 요청마다 DB 조회가 한 번 발생한다. 대신 로그아웃과 재로그인의 세션
종료 상태가 즉시 반영되고, 단일 로그인 정책을 단순하게 유지할 수 있다.

## 쿠키 정책

| 속성 | 값 | 이유 |
|---|---|---|
| 이름 | `sessionToken` | 인증 세션 원문 |
| Path | `/api/v1` | 인증 API뿐 아니라 모든 보호 API에 전달 |
| HttpOnly | `true` | 브라우저 JavaScript에서 원문 접근 차단 |
| Secure | 운영 `true`, 로컬 `false` | 운영에서는 HTTPS에서만 전송 |
| SameSite | `Lax` | 일반적인 교차 사이트 요청의 쿠키 전송 제한 |
| Max-Age | 로그인 시점부터 남은 7일 | 브라우저를 닫아도 만료 전까지 유지 |

로그인 응답 JSON에는 토큰 원문이 없고 `expiresAt`만 들어간다. 서버 로그에서도 원문을 출력하지
않도록 `IssuedSessionToken.toString()`은 값을 가린다.

## 로그아웃·재로그인·만료

- 로그아웃: 현재 세션의 `revoked_at`을 기록하고 같은 경로의 만료 쿠키를 내려 브라우저에서 삭제한다.
- 재로그인: 이전 세션을 모두 종료하고 새 토큰과 새 7일 만료 시각을 만든다.
- 다른 브라우저 로그인: 새 브라우저의 로그인이 완료되면 이전 브라우저 세션도 종료된다.
- 7일 만료: 토큰을 재발급하지 않고 지갑 서명 로그인부터 다시 수행한다.

## CSRF 방어

쿠키는 브라우저가 자동 전송하므로 상태 변경 요청에는 CSRF 방어가 필요하다.
`POST`, `PUT`, `PATCH`, `DELETE /api/v1/**` 요청은 설정된 `Origin`과 일치해야 한다.
허용 출처는 `AUTH_ALLOWED_ORIGINS`로 설정한다.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `AUTH_SESSION_TTL` | `7d` | 고정 로그인 세션 유효 시간 |
| `AUTH_COOKIE_SECURE` | 운영 `true`, 로컬 `false` | Secure 쿠키 사용 여부 |
| `AUTH_ALLOWED_ORIGINS` | 환경별 설정 | 상태 변경 요청에서 허용할 FE 출처 목록 |

JWT 관련 `AUTH_JWT_SECRET_BASE64`, `AUTH_TOKEN_ISSUER`, `AUTH_ACCESS_TOKEN_TTL`은 사용하지 않는다.

## DB 마이그레이션

`V7__replace_refresh_token_with_session_token.sql`은 기존 `refresh_token` 테이블을 삭제하고,
개발 중 발급된 기존 세션을 비운 뒤 `login_session.session_token_hash` 고유 컬럼을 추가한다.
배포 후 기존 로그인은 유지되지 않으며 한 번 지갑 서명 로그인을 다시 해야 한다.
