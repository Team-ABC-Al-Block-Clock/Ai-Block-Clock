# Spring Session JDBC 쿠키 인증 구조

## 선택한 방식

브라우저 로그인에는 Spring Security와 Spring Session JDBC를 사용한다. 지갑 서명 검증에 성공하면
Spring Security의 `Authentication`을 HttpSession에 저장하고, Spring Session이 세션을 MySQL의
`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` 테이블에 보관한다.

브라우저에는 Spring Session ID가 `SESSION` HttpOnly 쿠키로 전달된다. 응답 JSON과 서버 로그에는
세션 ID를 출력하지 않는다. Access Token과 Refresh Token은 사용하지 않으며 `/auth/reissue` API도 없다.

세션 유지 정책은 미사용 만료 하나다. 마지막 요청으로부터 2시간 동안 요청이 없으면 만료되고, 요청이
오면 그만큼 뒤로 밀린다. 세션 행은 서버가 언제든 지울 수 있으므로 로그인 시점 기준의 고정 만료는 두지
않는다. 만료 판정은 Spring Session이 하고, 우리 코드는 만료를 확인하지 않는다.

## 로그인 흐름

1. FE가 `GET /api/v1/auth/csrf`로 CSRF 토큰을 받는다.
2. FE가 토큰을 `X-CSRF-TOKEN` 헤더에 넣어 `POST /api/v1/auth/wallet/message`로 서명 메시지를 받는다.
3. FE가 지갑에 메시지 서명을 요청한다.
4. FE가 같은 CSRF 헤더와 함께 지갑 주소와 서명값을 `POST /api/v1/auth/wallet/login`으로 보낸다.
5. `WalletAuthenticator`가 메시지 만료·재사용 여부와 지갑 서명을 확인하고 메시지를 사용 처리한다.
6. 신규 지갑이면 사용자와 ABC 잔액 계정을 만든다.
7. 사용자의 `UserId`로 Spring Security `Authentication`을 만든다.
8. `ChangeSessionIdAuthenticationStrategy`가 세션 ID를 교체하고 `SecurityContext`를 만든다.
9. Spring Session JDBC가 SecurityContext를 MySQL에 저장한다.
10. Spring Session이 `SESSION` 쿠키를 응답에 추가한다.
11. 로그인으로 세션이 교체되었으므로 FE가 CSRF 토큰을 다시 발급받는다.

## 보호 API 인증 흐름

브라우저는 `/api/v1` 요청마다 `SESSION` 쿠키를 자동으로 보낸다. Spring Session 필터가 쿠키의
세션 ID로 MySQL을 조회하고 HttpSession을 복원한다. Spring Security는 세션에 저장된
`SecurityContext`에서 `UserId`를 읽는다.

`SecurityConfig`는 지갑 인증 메시지 발급과 로그인을 제외한 API에 인증을 요구한다. Controller의
`@CurrentUser`는 인증을 수행하지 않고 Spring Security가 복원한 `UserId`만 전달한다. 새 보호 API에
`@CurrentUser`를 빼먹더라도 `.anyRequest().authenticated()`가 먼저 차단한다.

세션 생성·조회·저장·삭제와 미사용 만료 판정은 직접 구현하지 않고 Spring Session JDBC에 맡긴다.
만료된 세션은 조회되지 않으므로 인증 실패로 처리된다.

## 관리자 권한

로그인한 지갑이 `admin.wallet-addresses`(환경변수 `ADMIN_WALLET_ADDRESSES`, 쉼표 구분)에 있으면
`SessionAuthenticationManager`가 `Authentication`에 `ROLE_ADMIN` 권한을 붙여 세션에 저장한다.
`SecurityConfig`는 `/api/v1/admin/**`에 `hasRole("ADMIN")`을 걸어 관리자가 아닌 로그인 사용자를 `403`으로 막는다.
권한 판정은 Spring Security 기본 기능에 맡기고, 우리 코드는 로그인 때 권한을 붙이는 일만 한다.

- 권한은 세션에 함께 저장되므로 목록을 바꾸면 그 지갑이 다시 로그인해야 반영된다.
- 관리자 화면이 없어 관리자는 배포 서버의 Swagger(`/api/swagger-ui.html`)로 심사한다.
  절차는 [abc-withdrawal.md](abc-withdrawal.md) 7.4 를 따른다.

## 쿠키 정책

| 속성 | 값 | 이유 |
|---|---|---|
| 이름 | `SESSION` | Spring Session 기본값. FE 는 HttpOnly 라 이름을 알 필요가 없다 |
| Path | `/api/v1` | 인증 API와 모든 보호 API에 전달 |
| HttpOnly | `true` | 브라우저 JavaScript에서 세션 ID 접근 차단 |
| Secure | 운영 `true`, 로컬 `false` | 운영에서는 HTTPS에서만 전송 |
| SameSite | `Lax` | 일반적인 교차 사이트 요청의 쿠키 전송 제한 |
| Max-Age | 없음 | 브라우저를 닫으면 삭제. 세션 유지 기간은 서버의 미사용 만료가 정한다 |

로그인 응답 JSON에는 `expiresAt`만 들어간다.

## 로그아웃·재로그인·만료

- 로그아웃: 현재 HttpSession을 무효화한다. Spring Session이 DB 세션을 삭제하고 쿠키를 만료시킨다.
- 재로그인: 세션 ID만 교체되므로 로그인 전 세션 ID는 더 이상 조회되지 않는다.
- 다른 브라우저 로그인: 한 사용자가 여러 세션을 동시에 가질 수 있다. 기기 수를 제한하지 않는다.
- 미사용 만료: 마지막 요청으로부터 2시간이 지나면 Spring Session이 세션을 만료시킨다.
- 쿠키가 없거나 DB에 세션이 없거나 만료됐으면 `AUTH_005`를 반환한다.

## CSRF 방어

쿠키는 브라우저가 자동 전송하므로 상태 변경 요청에는 CSRF 방어가 필요하다. `POST`, `PUT`, `PATCH`,
`DELETE` 요청은 Spring Security가 세션에 저장한 CSRF 토큰과 `X-CSRF-TOKEN` 헤더를 비교한다.
FE는 앱 시작 시, 로그인 성공 후, 로그아웃 후 `GET /api/v1/auth/csrf`로 토큰을 발급받는다.
토큰이 없거나 일치하지 않으면 Spring Security가 Controller 실행 전에 `403 Forbidden`으로 차단한다.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `AUTH_SESSION_TIMEOUT` | `2h` | 세션 미사용 만료 시간 (`spring.session.timeout`) |
| `AUTH_COOKIE_SECURE` | 운영 `true`, 로컬 `false` | Secure 쿠키 사용 여부 |
| `ADMIN_WALLET_ADDRESSES` | 비어 있음 | 로그인하면 `ROLE_ADMIN`을 받는 지갑(쉼표 구분). 형식이 틀리면 기동하지 않는다 |

## DB 마이그레이션

`V8__create_spring_session_tables.sql`이 Spring Session JDBC 표준 테이블 `SPRING_SESSION`,
`SPRING_SESSION_ATTRIBUTES`를 만든다. 세션 행의 저장·조회·삭제는 Spring Session JDBC가 담당하므로
우리가 만드는 세션 테이블은 없다.
