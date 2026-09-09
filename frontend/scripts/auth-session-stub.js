/**
 * `verify-auth-session.mjs` 가 `@/api/client`·`@/api/auth`·`@/lib/tokenStorage` 자리에
 * 끼워 넣는 대역. 세 모듈이 필요한 export 를 한 파일에 모아 뒀다 — ESM 은 요청한
 * 이름만 있으면 되므로 alias 세 개가 이 파일 하나를 가리켜도 된다.
 *
 * 값은 전부 `globalThis.__authHarness` 를 거친다. 그래야 시나리오가 응답이 언제
 * 도착할지(=세션이 갈리는 순간)를 직접 정할 수 있다.
 */

// ── @/api/client ────────────────────────────────────────────────
export const restoreSession = (...args) => globalThis.__authHarness.restoreSession(...args)

export const setOnAuthFailure = (handler) => {
  globalThis.__authHarness.onAuthFailure = handler
}

// ── @/api/auth ──────────────────────────────────────────────────
export const getMyProfile = (...args) => globalThis.__authHarness.getMyProfile(...args)
export const loginWithWallet = (...args) => globalThis.__authHarness.loginWithWallet(...args)
export const logout = (...args) => globalThis.__authHarness.logout(...args)

// ── @/lib/tokenStorage ──────────────────────────────────────────
export const tokenStorage = {
  get: () => globalThis.__authHarness.token,
  set: (token) => {
    globalThis.__authHarness.token = token
  },
  clear: () => {
    globalThis.__authHarness.token = null
  },
}
