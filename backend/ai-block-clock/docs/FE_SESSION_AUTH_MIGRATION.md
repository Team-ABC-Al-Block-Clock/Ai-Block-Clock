# FE 전달용: 지갑 로그인을 쿠키 세션 방식으로 변경

## 변경 요약

기존 Access Token + Refresh Token 방식이 삭제되고 `sessionToken` HttpOnly 쿠키 방식으로 바뀐다.
FE는 토큰을 JSON에서 꺼내거나 Local Storage에 저장하지 않는다. 로그인 응답의 `Set-Cookie`를
브라우저가 저장하고 이후 API 요청에 자동으로 보낸다.

## FE에서 삭제할 코드

- Access Token을 Local Storage, Session Storage, 상태 관리 저장소에 보관하는 코드
- `Authorization: Bearer ${accessToken}` 헤더를 추가하는 코드
- `POST /api/v1/auth/reissue` 호출과 Refresh Token 재발급 처리
- Access Token 만료를 계산해 재발급을 시도하는 interceptor

## FE에서 추가·변경할 코드

모든 BE 요청에 쿠키 포함 옵션을 사용한다.

### fetch

```javascript
fetch(url, {
  ...options,
  credentials: "include",
});
```

### axios

```javascript
const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});
```

`sessionToken`은 HttpOnly이므로 `document.cookie`로 읽으려 하지 않는다. 정상적인 보안 동작이다.

## 로그인 순서

1. `POST /api/v1/auth/wallet/message`에 `walletAddress`를 보낸다.
2. 응답의 `data.message` 원문을 그대로 지갑에 전달해 서명받는다.
3. `POST /api/v1/auth/wallet/login`에 `walletAddress`, `signature`를 보낸다.
4. 성공하면 브라우저가 `sessionToken` 쿠키를 저장한다.
5. 응답의 `data.expiresAt`은 화면에 로그인 만료 시간을 보여줄 때만 사용한다.

```javascript
const response = await api.post("/api/v1/auth/wallet/login", {
  walletAddress,
  signature,
});

const expiresAt = response.data.data.expiresAt;
```

로그인 응답에는 `accessToken`, `refreshToken`, `tokenType`, `expiresIn`이 없다.

## 로그인 후 API 호출

추가 인증 헤더 없이 평소처럼 호출한다. `withCredentials: true` 또는
`credentials: "include"`가 있으면 브라우저가 쿠키를 붙인다.

```javascript
const workflows = await api.get("/api/v1/workflows");
```

## 로그아웃

```javascript
await api.post("/api/v1/auth/logout");
```

성공하면 서버가 DB 세션을 종료하고 쿠키를 삭제한다. FE도 메모리에 가진 사용자 화면 상태를
비우고 로그인 화면으로 이동한다.

## 401 처리

다음 오류는 로그인 화면으로 이동하고 지갑 서명 로그인을 다시 시작한다.

- `AUTH_005`: 쿠키 없음
- `AUTH_013`: 7일 만료
- `AUTH_014`: 로그아웃 또는 다른 곳에서 재로그인해 기존 세션 종료
- `AUTH_018`: 알 수 없거나 잘못된 세션 토큰

자동 재발급 API는 호출하지 않는다.

## 동작 기준

- 같은 브라우저는 쿠키가 남아 있으면 브라우저를 다시 열어도 최대 7일간 로그인 상태가 유지된다.
- 다른 브라우저에서는 지갑 서명 로그인을 해야 한다.
- 같은 사용자가 새로 로그인하면 이전 브라우저의 세션은 즉시 종료된다.
- 7일이 지나면 지갑 서명 로그인을 다시 해야 한다.
- 로컬 FE 출처가 기본 `http://localhost:5173`과 다르면 BE의 `AUTH_ALLOWED_ORIGINS`에 추가해야 한다.

## 확인할 API

| 기능 | API |
|---|---|
| 서명 메시지 발급 | `POST /api/v1/auth/wallet/message` |
| 지갑 로그인·쿠키 발급 | `POST /api/v1/auth/wallet/login` |
| 로그아웃·쿠키 삭제 | `POST /api/v1/auth/logout` |

`POST /api/v1/auth/reissue`는 제거됐다.
