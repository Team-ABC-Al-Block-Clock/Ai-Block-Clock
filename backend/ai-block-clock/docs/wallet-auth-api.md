# 지갑 인증 API

## 범위와 실행 순서

Jira S15P21A403-237은 BE 지갑 인증 업무다. 아래 완료 조건을 HTTP 및 도메인 테스트로 확인한다.

| 완료 조건 | 구현 및 검증 |
| --- | --- |
| 신규 사용자 생성과 기존 사용자 로그인 | 실제 EOA 서명으로 두 번 로그인하고 같은 사용자·ABC 계정 재사용 확인 |
| 잘못된 서명·만료·재사용 nonce 거절 | 다른 지갑 서명, 5분 만료 경계, 사용한 메시지와 교체 전 메시지 거절 |
| 지갑의 사용자·결제 계정 중복 방지 | DB UNIQUE, 신규 동시 로그인 도메인 테스트, 기존 로그인 HTTP 행 개수 확인 |
| 로그아웃한 Refresh Token 사용 불가 | 로그아웃 후 재발급과 해당 세션 Access Token 요청 거절 |

로그인 본문은 기존 FE 계약인 walletAddress와 signature를 유지한다.
nonce는 FE가 서명한 메시지 원문에 포함되며, 서버가 최신 원문을 검증하고 DB 사용 처리 조건에서 같은 nonce를 확인한다.
로그아웃은 세션 종료로 연결된 Refresh Token의 재발급을 차단한다. 토큰 기록과 원래 만료 시각은 감사용으로 유지한다.

모든 경로는 `/api/v1` 아래에 있다. EOA 지갑의 personal_sign 서명을 검증한다.
스마트 계약 지갑 검증 및 사용자 프로필 API는 이 문서의 구현 범위에 포함되지 않는다.

1. `POST /auth/wallet/message`: 지갑 주소로 메시지를 발급받는다.
2. 지갑이 응답의 `message` 원문을 그대로 서명한다.
3. `POST /auth/wallet/login`: 지갑 주소와 서명값을 보낸다.
4. Access Token을 API 요청의 `Authorization: Bearer <accessToken>`에 넣는다.
5. 만료되면 본문 없이 `POST /auth/reissue`를 호출한다.
6. 로그아웃할 때 본문 없이 `POST /auth/logout`를 호출한다.

로그인·재발급 응답의 JSON에는 Refresh Token이 없다. 브라우저가 HttpOnly 쿠키를 보관하고 자동 전송한다.
FE의 axios 설정은 `withCredentials: true`여야 한다. 동시에 발생한 재발급은 FE에서 하나로 합쳐야 한다.

## 요청과 응답

### 메시지 발급

```json
{ "walletAddress": "0x0000000000000000000000000000000000000001" }
```

```json
{
  "status": "SUCCESS",
  "data": { "message": "서버에서 발급한 메시지 원문", "nonce": "64자리 난수" },
  "error": null
}
```

메시지는 5분 동안 유효하다. 같은 지갑에서 다시 발급하면 이전 메시지는 사용할 수 없다.
nonce는 메시지 안에도 포함된다. 로그인 요청에는 nonce나 메시지를 다시 보내지 않는다.
서버가 해당 지갑의 최신 메시지 원문을 DB에서 조회하여 검증한다.

### 로그인

```json
{ "walletAddress": "지갑 주소", "signature": "0x로 시작하는 130자리 16진수 서명" }
```

```json
{
  "status": "SUCCESS",
  "data": { "accessToken": "JWT", "tokenType": "Bearer", "expiresIn": 900 },
  "error": null
}
```

`expiresIn`은 응답 생성 시점에 남은 초다. 처리 시간과 초 단위 JWT 만료 시각 때문에 900보다 작을 수 있다.
처음 보는 지갑이면 사용자와 ABC 잔액 계정을 생성한다. 기존 지갑이면 같은 사용자로 로그인한다.
사용자마다 기존 세션을 종료하고 새 세션을 만든다.

로그인과 재발급 성공 응답에는 `Cache-Control: no-store`와 `Set-Cookie`가 포함된다.

### 재발급과 로그아웃

브라우저가 `refreshToken` 쿠키를 보낸다. Access Token이나 JSON 본문은 필요하지 않다.
쿠키가 없거나 잘못된 형식이면 `401 / AUTH_017`이다.

재발급 응답 형식은 로그인과 같다. Refresh Token은 새 쿠키로 교체되며 이전 토큰은 재발급에 사용할 수 없다.
로그아웃은 해당 세션을 종료하고 같은 이름·경로의 쿠키를 `Max-Age=0`으로 삭제한다.

```json
{ "status": "SUCCESS", "data": null, "error": null }
```

FE는 로그아웃 성공 후 메모리의 Access Token도 지워야 한다.
실패한 재발급에서 쿠키를 지우지 않는다. 동시 요청의 실패 응답이 성공 응답의 새 쿠키를 지우는 것을 방지한다.

## 쿠키와 배포 설정

- 이름: `refreshToken`
- 경로: `/api/v1/auth`
- HttpOnly: 항상 사용
- SameSite: `Lax`
- Domain: 지정하지 않음 (응답 서버의 호스트에만 전달)
- Secure: 배포 기본값 true, local 프로필 기본값 false
- Max-Age: 최초 로그인부터 7일인 종료 시각까지 남은 초

Access Token은 15분, 세션 및 Refresh Token은 최초 로그인부터 7일이다.
재발급해도 세션 종료 시각과 쿠키의 절대 수명은 연장하지 않는다.
이 설정은 현재 배포의 동일 출처 /api 프록시와 로컬 localhost 구성을 기준으로 한다.
서로 다른 사이트에 FE와 BE를 배포한다면 쿠키·출처 설정을 별도로 검토해야 한다.

`auth-web.yml`의 환경변수:

| 변수 | 용도 |
| --- | --- |
| `AUTH_COOKIE_SECURE` | HTTPS 쿠키 전용 여부. 배포는 true, 로컬 HTTP만 false |
| `AUTH_ALLOWED_ORIGINS` | 허용할 출처를 쉼표로 나열. 경로와 끝 슬래시 없이 작성 |

배포 기본 출처는 `https://j15a403.p.ssafy.io`이다.
local 기본 출처는 `http://localhost:5173,http://localhost:8080`이다.
FE 포트, Swagger 접속 주소 또는 서비스 도메인이 바뀌면 허용 출처도 변경한다.
배포에서는 local 대신 실제 배포 프로필을 지정하고 HTTPS 및 Secure 설정을 확인한다.

기본 Compose는 DB 접속을 위해 dev 프로필을 사용하므로 인증의 로컬 설정을 컨테이너 환경변수로 전달한다.
`compose.prod.yaml`은 지갑 메시지 도메인·URI, Secure, 허용 출처를 운영 기본값으로 덮어쓴다.
토큰 발급자·유효 시간과 지갑 네트워크·메시지 유효 시간도 Compose를 통해 전달한다.
Spring의 설정 파일은 `.env`를 자동으로 읽지 않는다. Compose가 환경 파일 값을 컨테이너에 전달한다.

## 출처 검사 및 CORS

인증 API의 POST는 허용된 `Origin`을 요구한다. Origin이 없으면 Referer의 출처를 확인한다.
두 헤더가 없거나 허용되지 않은 Referer라면 `400 / COMMON_001`로 거절한다.
허용되지 않은 Origin을 가진 교차 출처 요청은 Spring CORS에서 먼저 403으로 거절될 수 있다.
CORS 거절 응답은 Spring이 처리하므로 ApiResult 오류 봉투를 보장하지 않는다.

이 검사는 쿠키가 자동으로 붙는 요청의 출처를 확인하기 위한 것이다.
CORS는 허용 출처만 지정하고 credentials를 허용한다. 와일드카드 출처는 사용하지 않는다.
Postman 및 curl에서도 허용된 `Origin` 헤더를 명시해야 한다.
Swagger에서의 재발급·로그아웃은 실제 로그인 응답으로 브라우저에 저장된 쿠키를 사용한다.

## 주요 오류

API 오류는 기존 프로젝트 봉투를 유지한다.

```json
{
  "status": "FAIL",
  "data": null,
  "error": { "code": "AUTH_020", "message": "Access Token이 만료되었습니다. 토큰을 재발급받아 주세요.", "data": null }
}
```

| 코드 | 의미 |
| --- | --- |
| COMMON_001 | 요청 형식 또는 출처 오류 |
| AUTH_007 | 메시지 동시 변경 (409) |
| AUTH_008 | 지갑 서명 오류 |
| AUTH_009~012 | 인증 메시지 만료, 사용됨, 사용 불가 또는 없음 |
| AUTH_013~014, AUTH_018 | 세션 만료, 종료 또는 사용 불가 |
| AUTH_015~017 | Refresh Token 만료, 사용됨 또는 사용 불가 |
| AUTH_019 | Access Token 오류 |
| AUTH_020 | Access Token 만료. FE가 한 번 재발급을 시도할 수 있음 |

사용된 Refresh Token의 재사용은 거절한다. 재사용 감지만으로 현재 세션 전체를 종료하지는 않는다.
로그아웃과 새 로그인에 따른 기존 세션 종료가 세션을 실제로 종료한다.

## FE 연결 확인 사항

로컬에 저장된 `origin/develop-FE`의 `c62576c`에서 다음을 확인했다. 최신 원격 상태를 새로 조회한 결과는 아니다.

- auth.ts 요청 및 성공 응답 data 필드는 이 구현과 일치한다.
- client.ts는 성공 응답의 data를 꺼내므로 SUCCESS의 대소문자 차이만으로 성공 처리가 중단되지는 않는다.
  성공 응답 타입과 mock의 status는 BE의 SUCCESS로 맞춰야 한다.
- errors.ts는 `status: error` 및 최상위 code/message를 기대한다.
  BE는 `status: FAIL` 및 `error.code / error.message`이므로 오류 판별과 추출을 수정해야 한다.
- FE는 Access 만료를 AUTH_007로 판단하지만 BE는 AUTH_020이다.
  FE 재발급 조건을 AUTH_020으로 맞춰야 자동 재발급이 실행된다.
- FE의 Refresh 오류 코드 및 재사용 시 전체 세션 종료라는 주석은 위 BE 정책에 맞춰야 한다.
- mock의 expiresIn 3600은 실제 15분과 다르므로 900으로 맞춘다.
- 로그인 후 호출하는 `GET /users/me`는 별도 프로필 API 구현 여부를 확인해야 한다.

현재 BE 브랜치에서는 FE 코드를 변경하지 않았다.

## 전체 코드 구조

```text
FE
  → AuthOriginInterceptor: 인증 POST의 출처 확인
  → AuthController: 요청 DTO를 받고 응답 DTO·쿠키 생성
  → WalletAuthService: 각 인증 작업의 흐름 실행
      ├─ issueMessage
      │   ├─ WalletAuthMessageGenerator → WalletNonceGenerator
      │   └─ WalletAuthMessageWriter → 메시지 저장
      ├─ login
      │   ├─ WalletAuthMessageReader → 최신 메시지 조회
      │   ├─ WalletAuthMessage.checkUsable → 만료·사용 여부 확인
      │   ├─ WalletSignatureValidator → 서명에서 복원한 주소 비교
      │   ├─ RefreshTokenGenerator / RefreshTokenHasher → 원문·해시 생성
      │   ├─ UserReader → 기존 사용자 여부 확인
      │   └─ WalletLoginManager → 인증 성공의 DB 변경과 토큰 생성
      │       ├─ WalletAuthMessageWriter → 사용 처리
      │       ├─ 신규면 UserRegistrationManager
      │       │   ├─ UserWriter → 사용자 생성
      │       │   └─ LedgerAccountWriter → ABC 잔액 계정 생성
      │       ├─ LoginSessionManager
      │       │   ├─ UserReader → 사용자 행 잠금
      │       │   ├─ LoginSessionWriter → 이전 세션 종료·새 세션 저장
      │       │   └─ RefreshTokenWriter → 새 세션에 토큰 해시 저장
      │       └─ AccessTokenGenerator → Access Token 생성
      ├─ reissue
      │   ├─ RefreshTokenHasher / RefreshTokenReader → 토큰 조회·상태 확인
      │   ├─ LoginSessionReader → 세션 조회·상태 확인
      │   ├─ RefreshTokenGenerator / RefreshTokenHasher → 새 원문·해시 생성
      │   └─ TokenReissueManager
      │       ├─ LoginSessionReader → 세션 잠금·상태 재확인
      │       ├─ RefreshTokenWriter → 기존 토큰 사용·새 해시 저장
      │       └─ AccessTokenGenerator → 같은 세션의 Access Token 생성
      └─ logout
          ├─ RefreshTokenHasher / RefreshTokenReader → 현재 토큰 조회·확인
          └─ LoginSessionWriter → 해당 세션 종료

기존 보호 API의 별도 요청 흐름
  → CurrentOwnerArgumentResolver: Bearer 헤더 읽기
  → WalletAuthService.authenticate
      ├─ AccessTokenValidator → 서명·만료 검증, 사용자·세션 번호 반환
      └─ LoginSessionReader → 세션 유효성과 사용자 일치 확인
  → 검증한 사용자 번호를 OwnerId로 전달
```

Reader·Writer는 Repository를 통해 DB에 접근한다. Service는 Repository·Entity를 참조하지 않는다.
Controller의 Request DTO는 FE가 보낸 값, Response DTO는 FE로 보낼 값이다. DTO를 받는 것 자체는 DB 저장이 아니다.
DB용 Entity는 Repository를 통해 저장하고, Service에는 Converter가 변환한 순수 객체를 반환한다.

### Manager가 묶는 범위

- `WalletLoginManager`: 메시지 사용 처리부터 신규 가입·세션·Refresh Token 저장 및 Access Token 생성까지 하나의 트랜잭션.
- `UserRegistrationManager`: 사용자와 ABC 잔액 계정 생성. 로그인에서는 바깥 트랜잭션에 참여.
- `LoginSessionManager`: 사용자 잠금·기존 세션 종료·새 세션 및 Refresh Token 해시 저장. 로그인에서는 바깥 트랜잭션에 참여.
- `TokenReissueManager`: 세션 잠금·기존 토큰 사용 처리·새 해시 저장 및 Access Token 생성까지 하나의 트랜잭션.

중간에 실패하면 해당 트랜잭션의 DB 변경을 되돌린다.
응답 쿠키는 Service의 트랜잭션이 성공한 후 Controller에서 설정한다.

### DB 번호 연결 예시

아래 번호는 설명용이다. 각 테이블의 기본키는 별도로 생성된다.

```text
app_user
  id = 10, wallet_address = 해당 지갑
    ├─ ledger_account: id = 40, account_type = ABC_BALANCE, reference_id = "10"
    └─ login_session: id = 100, user_id = 10
          └─ refresh_token: id = 500, session_id = 100, refresh_token_hash = 계산한 해시

Access Token에는 sub = "10", sid = "100"을 넣고 서명한다.
재발급은 session_id = 100을 유지하고 새 Refresh Token 행을 추가한다.
```

사용자 번호, 장부 계정 번호, 세션 번호, Refresh Token 기록 번호는 서로 다른 번호다.
장부는 사용자 번호를 문자열 reference_id로, 세션은 숫자 user_id로 연결한다.

## 최종 확인 및 남은 인계

- `gradlew.bat build integrationTest --console=plain`: 489개, 실패·오류·건너뜀 0개.
- 인증 Controller HTTP 통합 테스트: 15개 통과.
- Compose YAML 파싱 및 로컬·운영 인증 기본값 정적 검사 통과.
- `git diff --check` 통과.
- DB 통합 테스트는 H2 기준. 현재 PC에 Docker가 없으므로 실제 MySQL·Compose 실행 결과는 아니다.

237의 BE 완료 조건은 위 구현 및 테스트로 확인했다.
Git add·commit·push와 MR은 사용자가 진행한다. MR 관련 업무는 237과 142이며 스토리는 넣지 않는다.
커밋에는 237을 사용한다.
FE 오류 처리 수정과 프로필 API 연결은 별도 연동 사항이며, 237에 프로필 구현을 추가하지 않는다.
실제 브라우저 지갑, 쿠키 및 운영 MySQL 연결 확인은 배포 환경에서 수행해야 한다.
