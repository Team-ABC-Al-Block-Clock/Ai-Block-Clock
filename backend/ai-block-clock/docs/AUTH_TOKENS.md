# 인증 토큰 생성·검증 설정

## 현재 구현 범위

- `AuthTokenProperties`: 발급자, Access Token 15분, 로그인 세션 7일, 서버 비밀키 설정.
- `AccessTokenGenerator`: 저장된 `LoginSession`으로 HS256 JWT 생성.
- `AccessToken`: JWT 문자열과 실제 JWT 만료 시각 반환. DB에 저장하지 않는다.
- `AccessTokenValidator.readClaims`: 서명·발급자·만료 시각을 검증하고 사용자·세션 번호 반환.
- `AccessTokenClaims`: 검증된 토큰의 `UserId`, `LoginSessionId`를 담는 순수 객체.
- 로그인 서비스 호출, 토큰 재발급·로그아웃 API, 요청 인증은 이후 연결한다.

JWT의 `sub`는 사용자 번호 문자열, `sid`는 세션 번호 문자열이다.
`iss`, `iat`, `exp`는 발급자, 발급 시각, 만료 시각이다.
JWT 내용은 암호화하지 않으며 서명으로 변경 여부를 검증한다.

Access Token 만료는 발급 시각 + 설정된 유효 시간과 기존 세션 만료 중 빠른 시각으로 제한한다.
JWT 발급·만료 시각은 초 단위이고, 반환하는 `expiresAt`도 JWT와 일치한다.
세션의 남은 시간이 초 단위로 유효한 JWT를 만들기에 부족하면 `AUTH_018`로 거절한다.
세션·Refresh Token의 종료 시각은 최초 로그인부터 7일이며 재발급으로 연장하지 않는 정책이다.
현재 생성기는 기존 세션 만료를 읽기만 한다. 최초 만료 계산과 Refresh Token 재발급은 이후 연결한다.

## Access Token 검증

검증기는 토큰 원문을 받아 HS256만 허용하고, 설정된 비밀키로 서명을 확인한다.
발급자는 `AUTH_TOKEN_ISSUER`와 일치해야 하며 만료 시각은 필수다.
현재 시각이 만료 시각과 같거나 이후이면 `AUTH_020`으로 거절한다. 시간 오차를 허용하지 않는다.
`sub`, `sid`는 양수 `long` 범위의 십진수 문자열이어야 한다. 선행 0·부호·공백은 허용하지 않는다.
잘못된 서명·발급자·형식·필수값은 `AUTH_019`로 거절하며 토큰 원문이나 JWT 예외를 응답에 담지 않는다.

이 단계는 DB를 조회하지 않는다. 반환된 번호로 세션을 조회해 사용자 번호 일치와
세션 만료·종료 상태를 확인하는 요청 인증 연결은 후속 작업이다.
토큰 검증만 통과했다고 로그아웃·다른 위치 로그인으로 종료된 세션의 요청을 허용하면 안 된다.

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

`src/test/resources/application.properties`의 키는 테스트 전용이다.
이 파일은 운영 `bootJar`에 들어가지 않고 실제 실행의 기본 비밀키로 사용하지 않는다.
테스트는 서버 서명 검증, Java HMAC 결과 비교, 변조·다른 키·만료 거절,
세션 종료·만료 거절, 세션 만료 상한, 설정 바인딩·잘못된 설정 거절을 확인한다.
검증기 테스트는 생성기와의 연결, 만료 경계, 변조·다른 키·다른 알고리즘·서명 없는 토큰,
발급자와 필수 정보 누락, 사용자·세션 번호의 타입·형식·범위 오류를 확인한다.
