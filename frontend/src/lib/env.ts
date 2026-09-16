/**
 * 값이 없으면 켠다. .env 가 커밋되지 않아 새로 클론한 팀원에게는 이 값이 없는데,
 * 그때 mock 이 꺼지면 없는 백엔드로 요청이 나가 원인을 찾기 어렵다.
 */
function resolveUseMock(raw: string | undefined): boolean {
  if (raw === undefined || raw === '') return true
  return raw === 'true'
}

/**
 * 값이 없으면 baseURL 이 'undefined/v1' 이 되어 dev 서버의 index.html 이 200 으로
 * 돌아온다 — 성공처럼 보이고 파싱만 실패한다. 끝의 '/' 도 떼어낸다.
 */
function resolveApiBaseUrl(raw: string | undefined): string {
  const url = raw?.trim() || '/api'
  return url.endsWith('/') ? url.slice(0, -1) : url
}

/** 컴포넌트에서 `import.meta.env` 에 직접 접근하지 않고 이 모듈을 거친다. */
export const env = {
  appName: import.meta.env.VITE_APP_NAME?.trim() || 'ABC AI Block Clock',
  apiBaseUrl: resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
  /** 백엔드 연동 후 .env 에 false 를 명시해 끈다 */
  useMock: resolveUseMock(import.meta.env.VITE_USE_MOCK),
} as const
