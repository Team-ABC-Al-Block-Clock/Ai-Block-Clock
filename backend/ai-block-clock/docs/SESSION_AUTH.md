# Spring Session JDBC 쿠키 인증 구조

## 선택한 방식

브라우저 로그인에는 Spring Security와 Spring Session JDBC를 사용한다. 지갑 서명 검증에 성공하면
Spring Security의 `Authentication`을 HttpSession에 저장하고, Spring Session이 세션을 MySQL의
`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` 테이블에 보관한다.

브라우저에는 Spring Session ID가 `sessionToken` HttpOnly 쿠키로 전달된다. 응답 JSON과 서버 로그에는
세션 ID를 출력하지 않는다. Access Token과 Refresh Token은 사용하지 않으며 `/auth/reissue` API도 없다.

세션은 로그인 시점부터 7일 동안 유효하다. Spring Session의 미사용 만료 시간과 별도로 고정 만료 시각을
세션 속성에 저장하므로, 사용 중이어도 로그인 후 7일이 지나면 다시 지갑 로그인을 해야 한다.

## 로그인 흐름

1. FE가 `GET /api/v1/auth/csrf`로 CSRF 토큰을 받는다.
2. FE가 토큰을 `X-CSRF-TOKEN` 헤더에 넣어 `POST /api/v1/auth/wallet/message`로 서명 메시지를 받는다.
3. FE가 지갑에 메시지 서명을 요청한다.
4. FE가 같은 CSRF 헤더와 함께 지갑 주소와 서명값을 `POST /api/v1/auth/wallet/login`으로 보낸다.
5. `WalletAuthService`가 메시지 만료·재사용 여부와 지갑 서명을 확인한다.
6. 신규 지갑이면 사용자와 ABC 잔액 계정을 만든다.
7. `SessionAuthenticationManager`가 같은 사용자의 기존 Spring Session을 삭제한다.
8. 사용자의 `OwnerId`로 Spring Security `Authentication`과 `SecurityContext`를 만든다.
9. Spring Session JDBC가 SecurityContext와 고정 만료 시각을 MySQL에 저장한다.
10. Spring Session이 `sessionToken` 쿠키를 응답에 추가한다.
11. 로그인으로 세션이 교체되었으므로 FE가 CSRF 토큰을 다시 발급받는다.

## 보호 API 인증 흐름

브라우저는 `/api/v1` 요청마다 `sessionToken` 쿠키를 자동으로 보낸다. Spring Session 필터가 쿠키의
세션 ID로 MySQL을 조회하고 HttpSession을 복원한다. Spring Security는 세션에 저장된
`SecurityContext`에서 `OwnerId`를 읽는다.

`SecurityConfig`는 지갑 인증 메시지 발급과 로그인을 제외한 API에 인증을 요구한다. Controller의
`@CurrentOwner`는 인증을 수행하지 않고 Spring Security가 복원한 `OwnerId`만 전달한다. 새 보호 API에
`@CurrentOwner`를 빼먹더라도 `.anyRequest().authenticated()`가 먼저 차단한다.

`SessionExpirationFilter`는 우리 서비스의 고정 7일 만료 정책만 확인한다. 세션 생성·조회·저장·삭제는
직접 구현하지 않고 Spring Session JDBC에 맡긴다.

## 쿠키 정책

| 속성 | 값 | 이유 |
|---|---|---|
| 이름 | `sessionToken` | 기존 FE 계약을 유지한 Spring Session ID 쿠키 |
| Path | `/api/v1` | 인증 API와 모든 보호 API에 전달 |
| HttpOnly | `true` | 브라우저 JavaScript에서 세션 ID 접근 차단 |
| Secure | 운영 `true`, 로컬 `false` | 운영에서는 HTTPS에서만 전송 |
| SameSite | `Lax` | 일반적인 교차 사이트 요청의 쿠키 전송 제한 |
| Max-Age | 7일 | 브라우저를 닫아도 고정 만료 전까지 유지 |

로그인 응답 JSON에는 `expiresAt`만 들어간다.

## 로그아웃·재로그인·만료

- 로그아웃: 현재 HttpSession을 무효화한다. Spring Session이 DB 세션을 삭제하고 쿠키를 만료시킨다.
- 재로그인: 현재 세션과 같은 사용자의 기존 세션을 삭제한 뒤 새 세션을 만든다.
- 다른 브라우저 로그인: 새 로그인이 완료되면 이전 브라우저의 세션 ID는 더 이상 조회되지 않는다.
- 7일 만료: 고정 만료 시각이 지나면 세션을 무효화하고 `AUTH_013`을 반환한다.
- 쿠키가 없거나 DB에 세션이 없으면 `AUTH_005`를 반환한다.

## CSRF 방어

쿠키는 브라우저가 자동 전송하므로 상태 변경 요청에는 CSRF 방어가 필요하다. `POST`, `PUT`, `PATCH`,
`DELETE` 요청은 Spring Security가 세션에 저장한 CSRF 토큰과 `X-CSRF-TOKEN` 헤더를 비교한다.
FE는 앱 시작 시, 로그인 성공 후, 로그아웃 후 `GET /api/v1/auth/csrf`로 토큰을 발급받는다.
토큰이 없거나 일치하지 않으면 Spring Security가 Controller 실행 전에 `403 Forbidden`으로 차단한다.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `AUTH_SESSION_TTL` | `7d` | Spring Session 미사용 만료 시간과 고정 로그인 유효 시간 |
| `AUTH_COOKIE_SECURE` | 운영 `true`, 로컬 `false` | Secure 쿠키 사용 여부 |

## DB 마이그레이션

`V10__replace_login_session_with_spring_session.sql`은 기존 커스텀 `login_session` 테이블을 삭제하고
Spring Session JDBC 표준 테이블을 만든다. 배포 시 기존 로그인 세션은 유지되지 않으며 사용자는 한 번
지갑 서명 로그인을 다시 해야 한다. 이후 세션 행의 저장·조회·삭제는 Spring Session JDBC가 담당한다.
