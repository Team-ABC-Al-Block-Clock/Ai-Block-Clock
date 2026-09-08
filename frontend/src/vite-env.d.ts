/// <reference types="vite/client" />

/* .env 는 커밋되지 않는다. 클론 직후에는 어느 값도 없을 수 있으므로 전부 선택이다.
   기본값은 lib/env.ts 에서 채운다 — import.meta.env 를 직접 읽지 말 것.

   브라우저에 실리는 변수만 여기 적는다. VITE_API_PROXY_TARGET 은 dev 서버 프록시
   설정값이라 vite.config.ts 가 loadEnv 로 읽고, 번들에 들어가지 않아 여기 없다. */
interface ImportMetaEnv {
  readonly VITE_APP_NAME?: string
  readonly VITE_API_BASE_URL?: string
  /** 'false' 를 명시하지 않으면 MSW 목 서버를 사용한다. */
  readonly VITE_USE_MOCK?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
