# 인증 토큰 생성·검증 설정

## 현재 구현 범위

- `AuthTokenProperties`: 발급자, Access Token 15분, 로그인 세션 7일, 서버 비밀키 설정.
- `AccessTokenGenerator`: 저장된 `LoginSession`으로 HS256 JWT 생성.
- `AccessToken`: JWT 문자열과 실제 JWT 만료 시각 반환. DB에 저장하지 않는다.
- `AccessTokenValidator.readClaims`: 서명·발급자·만료 시각을 검증하고 사용자·세션 번호 반환.
- `AccessTokenClaims`: 검증된 토큰의 `UserId`, `LoginSessionId`를 담는 순수 객체.
- `WalletAuthService.authenticate`: 토큰 검증과 DB 세션 확인을 묶어 요청자를 식별한다.
- `WalletAuthService.login`: 지갑 서명 검증 뒤 세션·Refresh Token 저장과 Access Token 발급까지 잇는다.
- `WalletLoginManager`: 메시지 사용 처리·가입·세션·Refresh Token 저장과 Access Token 생성을 한 트랜잭션으로 묶는다.
- `CurrentOwnerArgumentResolver`: `Authorization: Bearer` 헤더를 읽어 `OwnerId` 로 넘긴다.
- 토큰 재발급·로그아웃과 인증 Controller는 이후 연결한다.

JWT의 `sub`는 사용자 번호 문자열, `sid`는 세션 번호 문자열이다.
`iss`, `iat`, `exp`는 발급자, 발급 시각, 만료 시각이다.
JWT 내용은 암호화하지 않으며 서명으로 변경 여부를 검증한다.

Access Token 만료는 발급 시각 + 설정된 유효 시간과 기존 세션 만료 중 빠른 시각으로 제한한다.
JWT 발급·만료 시각은 초 단위이고, 반환하는 `expiresAt`도 JWT와 일치한다.
세션의 남은 시간이 초 단위로 유효한 JWT를 만들기에 부족하면 `AUTH_018`로 거절한다.
세션·Refresh Token의 종료 시각은 최초 로그인부터 7일이며 재발급으로 연장하지 않는 정책이다.
현재 생성기는 기존 세션 만료를 읽기만 한다. 최초 만료 계산은 로그인에 연결했으며 Refresh Token 재발급은 이후 연결한다.

## Access Token 검증

검증기는 토큰 원문을 받아 HS256만 허용하고, 설정된 비밀키로 서명을 확인한다.
발급자는 `AUTH_TOKEN_ISSUER`와 일치해야 하며 만료 시각은 필수다.
현재 시각이 만료 시각과 같거나 이후이면 `AUTH_020`으로 거절한다. 시간 오차를 허용하지 않는다.
`sub`, `sid`는 양수 `long` 범위의 십진수 문자열이어야 한다. 선행 0·부호·공백은 허용하지 않는다.
잘못된 서명·발급자·형식·필수값은 `AUTH_019`로 거절하며 토큰 원문이나 JWT 예외를 응답에 담지 않는다.

이 단계는 DB를 조회하지 않는다. 반환된 번호로 세션을 조회하는 것은 아래 요청 인증 연결이 담당한다.

## 요청 인증 연결

`WalletAuthService.authenticate` 가 토큰 원문 하나로 요청자를 식별한다. 인증 도메인의 흐름은 기존 Service 하나에 모은다. 순서는 다음과 같다.

1. `AccessTokenValidator.readClaims` 로 서명·발급자·만료를 검증하고 사용자·세션 번호를 얻는다.
2. `LoginSessionReader.findById` 로 세션을 조회한다. 없으면 `AUTH_018` 로 거절한다.
3. `LoginSession.checkUsable` 로 세션 만료(`AUTH_013`)와 종료(`AUTH_014`)를 확인한다.
4. 토큰의 사용자 번호와 세션에 저장된 사용자 번호가 다르면 `AUTH_019` 로 거절한다.

세션 조회를 거치므로 로그아웃·다른 위치 로그인으로 종료된 세션은 Access Token 이 남아 있어도 즉시 거절된다.
요청마다 세션 조회가 한 번 발생하며, 이는 종료를 즉시 반영하기 위한 비용이다.

`CurrentOwnerArgumentResolver` 는 `@CurrentOwner` 가 붙은 핸들러 파라미터를 만들 때 이 검증을 실행한다.
`Authorization` 헤더가 없거나 비어 있으면 `AUTH_005`, `Bearer ` 접두어가 아니거나 뒤가 비어 있으면 `AUTH_019` 다.
인증 방식 이름은 대소문자를 구분하지 않으므로 `Bearer`, `bearer`, `BEARER` 등을 허용한다. JWT 원문은 대소문자를 변경하지 않는다.
확인한 사용자 번호는 그대로 `OwnerId` 로 전달한다. 기존 `X-Owner-Id` 헤더는 더 이상 읽지 않는다.
현재는 `@CurrentOwner` 가 붙은 핸들러만 인증한다. 새 API에는 인증 적용 여부를 명시해야 한다.
로그인·메시지 발급처럼 토큰 없이 호출하는 API를 추가할 때는 Swagger의 전역 Bearer 요구사항도 해당 경로에서 제외한다.

## 로그인 토큰 발급 연결

`WalletAuthService.login` 은 지갑 서명을 검증한 뒤 다음 순서로 로그인을 완성한다.

1. `RefreshTokenGenerator` 로 Refresh Token 원문을 만들고 `RefreshTokenHasher` 로 해시를 계산한다.
2. 로그인 시각 + `AUTH_SESSION_TTL` 을 세션 만료 시각으로 정하고 마이크로초로 자른다.
3. 기존 사용자면 `WalletLoginManager.login`, 처음 온 지갑이면 `WalletLoginManager.register` 를 호출한다.
4. Manager 안에서 저장된 세션으로 `AccessTokenGenerator` 가 Access Token 을 만든다. 생성까지 성공하면 트랜잭션을 커밋한다.
5. `WalletLogin` 에 사용자·Access Token·Refresh Token 원문을 담아 반환한다.

Refresh Token 은 원문을 저장하지 않는다. DB 에는 해시만 넣고 원문은 응답으로만 내보낸다.
`IssuedRefreshToken` 과 `AccessToken` 은 `toString` 에서 값을 가려 로그로 새지 않게 한다.
Refresh Token 의 만료 시각은 그 세션의 만료 시각과 같다. Access Token 만료는 최대 `AUTH_ACCESS_TOKEN_TTL` 이다.

### 한 트랜잭션으로 묶는 범위

`WalletLoginManager` 의 두 메서드는 아래 순서로 처리하고, Access Token 생성까지 성공해야 DB 변경을 커밋한다.

```
인증 메시지 사용 처리
  → (처음 온 지갑이면) 사용자와 ABC 잔액 계정 생성
  → 그 사용자의 미종료 세션 일괄 종료
  → 새 로그인 세션 저장
  → Refresh Token 해시 저장
  → Access Token 생성
  → DB 변경 커밋
```

저장 또는 Access Token 생성이 실패하면 앞의 DB 변경도 되돌린다. 신규 가입이면 사용자와 ABC 계정도
함께 취소하고, 재로그인이면 이전 세션을 유지한다. 인증 메시지도 다시 미사용 상태로 돌아간다.
Access Token 생성은 서버 안에서 수행하는 JWT 서명 작업이며, 생성한 토큰 자체를 DB에 저장하지 않는다.

기존·신규 사용자 분기는 Service 가 정하고, Manager 는 각 분기의 DB 변경과 커밋 전 Access Token 생성을 담당한다.
같은 지갑으로 동시에 로그인해도 인증 메시지의 조건부 UPDATE 가 하나만 통과시키므로 중복 가입은 생기지 않는다.
다시 로그인하면 새 세션을 만들고 이전 세션을 종료한다. 재발급으로 최초 만료 시각을 연장하지 않는 정책은 이후 재발급 작업에서 연결한다.

## 실행 환경 설정

| 환경변수 | 기본값 | 의미 |
| --- | --- | --- |
| `AUTH_TOKEN_ISSUER` | `ai-block-clock` | 토큰 발급자 |
| `AUTH_ACCESS_TOKEN_TTL` | `15m` | Access Token 유효 시간 |
| `AUTH_SESSION_TTL` | `7d` | 최초 로그인 세션 유효 시간 |
| `AUTH_JWT_SECRET_BASE64` | 없음 | 32바이트 이상의 난수 비밀키를 Base64로 인코딩한 값 |

비밀키가 없거나 Base64 형식·길이가 맞지 않으면 애플리케이션 시작 시 설정 검증이 실패한다.
실제 비밀키는 Git에 올리지 않고 서버 환경변수 또는 Git에서 제외된 `.env`에 보관한다.
`compose.yaml`은 `.env`의 `AUTH_JWT_SECRET_BASE64`를 backend 컨테이너로 전달한다.
운영에서는 `--env-file`로 지정하는 기존 서버 환경 파일에 같은 변수를 설정한다.

### 로컬 최초 설정

PowerShell에서 `backend/ai-block-clock` 폴더로 이동한다.
`.env`가 없으면 `.env.example`을 복사하고, 아래 코드는 실제 키가 아직 없을 때 한 번만 실행한다.
이미 키가 있다면 그대로 사용한다.

```powershell
if (-not (Test-Path -LiteralPath .env)) {
    Copy-Item -LiteralPath .env.example -Destination .env
}

if (Select-String -LiteralPath .env -Pattern '^AUTH_JWT_SECRET_BASE64=.+$' -Quiet) {
    throw '이미 JWT 비밀키가 설정되어 있습니다. 기존 키를 사용하세요.'
}

$jwtKeyBytes = [byte[]]::new(32)
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRandom.GetBytes($jwtKeyBytes)
} finally {
    $jwtRandom.Dispose()
}

$env:AUTH_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtKeyBytes)
Add-Content -LiteralPath .env -Value ('AUTH_JWT_SECRET_BASE64=' + $env:AUTH_JWT_SECRET_BASE64)
```

`.env.example`의 빈 값 뒤에 실제 키를 추가하며 Docker Compose는 마지막 값을 사용한다.
위 명령은 같은 PowerShell 창에서 직접 실행할 JVM에도 환경변수를 전달한다.
새 터미널에서 직접 JVM을 실행한다면 환경변수를 다시 로드해야 한다. Spring이 `.env`를 자동으로 읽지는 않는다.
키는 실행마다 새로 만들지 않고 해당 환경에서 계속 보관·사용한다.

## 테스트

로그인 통합 테스트는 실제 Manager 트랜잭션의 커밋·롤백을 확인한다. Refresh Token 저장 실패와
Access Token 생성 실패 모두에서 신규 가입 취소·메시지 미사용 복구·기존 세션 유지를 검사한다.
테스트가 만든 지갑의 데이터만 별도 트랜잭션으로 정리한다.

요청 인증·워크플로우의 MockMvc 통합 테스트는 테스트 트랜잭션 안에서 동기 요청을 실행한다.
사용자·세션·워크플로우 변경은 테스트 종료 시 롤백되며 다른 테스트의 데이터를 일괄 삭제하지 않는다.

`src/test/resources/application.properties`의 키는 테스트 전용이다.
이 파일은 운영 `bootJar`에 들어가지 않고 실제 실행의 기본 비밀키로 사용하지 않는다.
테스트는 서버 서명 검증, Java HMAC 결과 비교, 변조·다른 키·만료 거절,
세션 종료·만료 거절, 세션 만료 상한, 설정 바인딩·잘못된 설정 거절을 확인한다.
검증기 테스트는 생성기와의 연결, 만료 경계, 변조·다른 키·다른 알고리즘·서명 없는 토큰,
발급자와 필수 정보 누락, 사용자·세션 번호의 타입·형식·범위 오류를 확인한다.
