# 지갑 인증 API

기준 경로는 `/api/v1`이다. 브라우저 요청은 쿠키를 포함해야 하며 FE에서는 모든 요청에
`credentials: "include"`를 사용한다. 상태를 변경하는 요청은 허용된 FE 출처에서 보내야 한다.

## 1. 서명 메시지 발급

`POST /auth/wallet/message`

```json
{
  "walletAddress": "0x1234...abcd"
}
```

성공 응답의 `message` 원문을 수정하지 않고 그대로 지갑에 전달해 서명받는다.

```json
{
  "status": "SUCCESS",
  "data": {
    "message": "...",
    "nonce": "..."
  },
  "error": null
}
```

메시지는 기본 5분 동안 한 번만 사용할 수 있다. 같은 지갑에 새 메시지를 발급하면 이전 메시지는
로그인에 사용할 수 없다.

## 2. 지갑 서명 로그인

`POST /auth/wallet/login`

```json
{
  "walletAddress": "0x1234...abcd",
  "signature": "0x..."
}
```

성공하면 응답 헤더에 `SESSION` HttpOnly 쿠키가 설정된다. 응답 JSON에는 토큰 원문이 없다.

```json
{
  "status": "SUCCESS",
  "data": {
    "expiresAt": "2026-09-26T00:00:00Z"
  },
  "error": null
}
```

처음 로그인한 지갑이면 사용자와 ABC 잔액 계정이 함께 생성된다. 로그인하면 세션 ID가 새로 발급되므로
로그인 전에 받은 세션 쿠키는 더 이상 쓰이지 않는다. 기기 수는 제한하지 않아 여러 브라우저에서 동시에
로그인할 수 있다.

## 3. 보호 API 호출

별도의 `Authorization` 헤더를 만들지 않는다. 브라우저가 `SESSION` 쿠키를 자동으로 보내며,
서버가 쿠키로 로그인 사용자를 확인한다.

```javascript
await fetch(`${API_BASE_URL}/api/v1/workflows`, {
  method: "GET",
  credentials: "include",
});
```

세션 쿠키가 없거나 MySQL에 해당 Spring Session이 없거나 만료됐으면 `AUTH_005`와 함께 HTTP 401을
반환한다.

## 4. 로그아웃

`POST /auth/logout`

요청 본문은 없다. 브라우저의 세션 쿠키를 함께 보내야 한다.

```javascript
await fetch(`${API_BASE_URL}/api/v1/auth/logout`, {
  method: "POST",
  credentials: "include",
});
```

서버는 HttpSession을 무효화한다. Spring Session JDBC가 DB 세션을 삭제하고 `SESSION` 쿠키를
만료시킨다.

## 제거된 API와 헤더

- `POST /auth/reissue`는 없다.
- `Authorization: Bearer ...`를 사용하지 않는다.
- Access Token과 Refresh Token을 저장하거나 전달하지 않는다.

세션은 마지막 요청으로부터 2시간 동안 유지되고, 요청을 보낼 때마다 그만큼 연장된다. 2시간 동안 요청이
없으면 만료되므로 지갑 서명 로그인을 다시 한다. 로그인 응답의 `expiresAt`은 추가 요청이 없을 때의 만료
시각이다.

## 주요 오류 코드

| 코드 | 의미 |
|---|---|
| `AUTH_005` | 세션 쿠키가 없거나 만료되어 사용자를 확인할 수 없음 |
| `AUTH_008` | 지갑 서명이 올바르지 않음 |
| `AUTH_009` | 지갑 인증 메시지가 만료됨 |
| `AUTH_010` | 이미 사용한 인증 메시지 |
| `AUTH_011` | 인증 메시지를 사용할 수 없음 |
| `AUTH_012` | 먼저 발급받은 인증 메시지가 없음 |

Swagger는 `/api/swagger-ui.html`, OpenAPI JSON은 `/api/v3/api-docs`에서 확인한다. Nginx가 `/api/` 아래만
백엔드로 넘기므로 배포 서버에서도 같은 주소다. Swagger의 보호 API에는 `SESSION` 쿠키와 `X-CSRF-TOKEN`
헤더 인증 방식이 표시된다. 바꾸는 요청을 Swagger로 보낼 때는 `GET /api/v1/auth/csrf`의 `token`을
Authorize의 `X-CSRF-TOKEN`에 넣는다.
