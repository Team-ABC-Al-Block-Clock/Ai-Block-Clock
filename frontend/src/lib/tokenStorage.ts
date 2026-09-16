/**
 * Access Token 은 메모리에만 둔다 — localStorage 에 두면 XSS 로 탈취된다.
 * 새로고침하면 사라지지만 Refresh Token 쿠키로 다시 받아온다.
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
