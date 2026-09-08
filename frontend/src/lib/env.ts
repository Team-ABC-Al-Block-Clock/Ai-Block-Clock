/**
 * 값이 없으면 목 서버를 켠다.
 *
 * .env 는 커밋되지 않으므로 새로 클론한 팀원에게 VITE_USE_MOCK 이 없을 수 있다.
 * 이때 목이 꺼지면 존재하지 않는 백엔드로 요청이 나가 원인을 찾기 어렵다.
 * 반대로 목을 끄는 것은 'false' 를 명시해야만 가능하게 해서,
 * 오타 때문에 목이 조용히 켜진 채로 개발하는 상황을 막는다.
 */
function resolveUseMock(raw: string | undefined): boolean {
  if (raw === undefined || raw === '') return true
  return raw === 'true'
}

/**
 * 값이 없으면 baseURL 이 'undefined/v1' 이 되고, 상대 경로라서 요청이 dev 서버로
 * 붙어 index.html 이 200 으로 돌아온다 — 성공처럼 보이고 파싱만 실패한다.
 * 끝의 '/' 도 떼어낸다. 붙어 있으면 '/api//v1' 이 된다.
 */
function resolveApiBaseUrl(raw: string | undefined): string {
  const url = raw?.trim() || '/api'
  return url.endsWith('/') ? url.slice(0, -1) : url
}

/**
 * 앱 전역 환경변수.
 * 컴포넌트에서 import.meta.env 에 직접 접근하지 않고 이 모듈을 거친다.
 */
export const env = {
  appName: import.meta.env.VITE_APP_NAME?.trim() || 'ABC AI Block Clock',
  apiBaseUrl: resolveApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
  /** MSW 목 서버 사용 여부. 백엔드 연동 후 .env 에 false 를 명시해 끈다. */
  useMock: resolveUseMock(import.meta.env.VITE_USE_MOCK),
} as const
