/**
 * Access Token 저장소.
 *
 * 메모리에만 둔다. localStorage 에 두면 XSS 로 탈취돼 다른 곳에서 재사용될 수 있다.
 * 새로고침하면 사라지지만, Refresh Token 이 HttpOnly 쿠키로 살아 있으므로
 * 앱 시작 시 POST /auth/reissue 로 다시 받아오면 된다. (S15P21A403-180)
 *
 * Refresh Token 은 여기서 다루지 않는다. 서버가 HttpOnly 쿠키로 내려주고
 * 브라우저가 자동 전송하므로 JS 는 값을 알 수도 없고 알 필요도 없다.
 */
let accessToken: string | null = null

export const tokenStorage = {
  get: () => accessToken,
  set: (token: string) => {
    accessToken = token
  },
  clear: () => {
    accessToken = null
  },
}
