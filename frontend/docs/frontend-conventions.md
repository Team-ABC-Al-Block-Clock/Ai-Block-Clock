# 프론트엔드 코드 컨벤션

> 대상: `frontend/` · 스택: Node 24 + React 19 + TypeScript + Vite + Tailwind v4
> Git·브랜치·커밋 규칙은 [`git-workflow.md`](../../git-workflow.md) 를 따른다.

이름만 보고 컴포넌트인지, 로직인지, 타입인지 알 수 있게 하는 것이 목표다.

---

## 1. 네이밍

| 대상                  | 표기법                      | 예시                                           |
| --------------------- | --------------------------- | ---------------------------------------------- |
| 컴포넌트 함수         | PascalCase                  | `function MyPageUsage() {}`                    |
| 타입 · 인터페이스     | PascalCase                  | `interface AgentSummary {}` · `type Theme = …` |
| Props 타입            | `컴포넌트명 + Props`        | `interface SidebarProps {}`                    |
| 커스텀 훅             | `use` + camelCase           | `function useInternalLinks() {}`               |
| 이벤트 핸들러 (내부)  | `handle` + 이벤트명         | `const handleSubmit = () => {}`                |
| 이벤트 핸들러 (props) | `on` + 이벤트명             | `<Button onSubmit={handleSubmit} />`           |
| 상수                  | UPPER_SNAKE_CASE            | `const TABS = [...]`                           |
| 불리언                | `is` · `has` · `can` 접두사 | `isActive` · `hasError`                        |

- 인터페이스에 `I` 를 붙이지 않는다. `IUserData`(✗) → `UserData`(○)
- Props 가 한 줄이면 인라인, 두 개를 넘으면 이름을 붙인다
- 웹 컴포넌트만 kebab-case 다. 커스텀 엘리먼트는 태그명에 하이픈이 필요해서 파일명을 태그명과 맞춘다(`components/ocean-bg.ts` → `<ocean-bg>`). React 컴포넌트(`Sidebar.tsx`)와는 파일명으로 구분된다

---

## 2. 파일 · 폴더

| 종류             | 확장자 | 표기법           | 예시                              |
| ---------------- | ------ | ---------------- | --------------------------------- |
| React 컴포넌트   | `.tsx` | PascalCase       | `Sidebar.tsx` · `MyPageUsage.tsx` |
| 훅 · 유틸 · 타입 | `.ts`  | camelCase        | `useAuth.ts` · `formatDate.ts`    |
| 웹 컴포넌트      | `.ts`  | kebab-case       | `ocean-bg.ts`                     |
| 폴더             | —      | 소문자 camelCase | `components` · `pages` · `lib`    |

```
frontend/src/
├─ pages/          화면 단위 (라우트 하나 = 파일 하나, 하위 컴포넌트가 생기면 폴더 하나)
├─ components/     여러 화면이 쓰는 것
├─ routes/         라우트 패턴 · 경로 생성기
├─ lib/            공용 헬퍼·설정 (env, queryClient, queryKeys, shell, format)
├─ api/            서버 통신 · 응답 타입
├─ mocks/          MSW mock 서버 (핸들러 · 픽스처)
├─ stores/         전역 상태
├─ hooks/          커스텀 훅
├─ index.css       전역 CSS · Tailwind 토큰
└─ assets/         이미지·폰트
```

- 한 파일에 컴포넌트 하나. `export default` 로 내보낸다
- 두 화면이 같은 것을 쓰기 시작하면 `components/` 로 올린다. 세 번째가 나오기 전에는 올리지 않는다
- 한 화면에서만 쓰는 하위 컴포넌트는 그 화면의 폴더에 둔다. `pages/EditNodes.tsx` → `pages/EditNodes/index.tsx` — `App.tsx` 가 확장자 없이 부르므로 라우팅은 그대로다. 폴더 안에 있다는 것이 곧 그 화면 전용이라는 뜻이고, 다른 화면이 쓰기 시작하면 `components/` 로 올린다

---

## 3. 화면 구조

껍데기와 내용을 분리한다. `App.tsx` 가 배경·껍데기·내용을 세 겹으로 두고 `<Routes>` 의 내용만 갈아끼운다 — 배경을 다시 만들면 셰이더가 재컴파일되어 캔버스가 잠깐 까맣게 보인다.

| 겹     | 좌표 기준                                  | 들어가는 것                                                        |
| ------ | ------------------------------------------ | ------------------------------------------------------------------ |
| 배경   | 창 전체(`fixed inset-0`)                   | `<ocean-bg>`, `shell.vignette`                                     |
| 내용   | 설계 캔버스 1440×1024(`<screen-frame>`)    | `pages/*.tsx`                                                      |
| 껍데기 | 창 전체, 캔버스와 같은 배율(`useShellBox`) | `<app-header>`, My Agent Studio, `<profile-menu>`, `<ChainBanner>` |

- 라우트별 설정(배경 밝기·로고 표시 등)은 `lib/shell.ts` 한 곳에 모은다
- 화면(`pages/*.tsx`)은 자기 내용만 그린다. 배경·로고·헤더를 각자 넣지 않는다

### 겹을 나눈 이유 — 캔버스는 창을 꽉 채우지 못한다

`screen-frame` 이 캔버스를 비율 유지로 맞추므로 창 비율이 다르면 여백이 남는다. 1440×900 노트북(실사용 높이 765)에서 배율 0.747, 좌우로 182px씩이다. 없앨 수 없으니 배경으로 채우고, 창 끝에 붙어야 하는 것만 캔버스 밖으로 내보낸다.

- 배경과 화면별 어둡기는 프레임 밖에 `fixed inset-0` 으로 깐다. 캔버스 안에 깔면 경계에 세로 이음선이 보인다. 화면 전체를 덮는 어둡기는 `shell.vignette` 로 올린다
- 화면 폭을 꽉 채우는 패널도 같다. 편집·실행 화면의 작업면(1076)+사이드바(460)가 캔버스를 정확히 덮어 밖에 밝은 바다가 남았다 — 채움과 `backdrop-filter` 를 `vignette`+`blur` 로 올리고 패널에는 구분선만 남겼다. 패널이 안 보이게 되면 그 패널의 테두리 하이라이트도 지운다. 배경 위에 뜬 선이 된다
- 껍데기는 창 모서리에 그리되 배율은 캔버스와 같게 한다(`useShellBox`). 창 좌표에 1:1 로 그리면 글자만 커져 내용과 어긋난다. 배율은 `screen-frame` 이 던지는 `framefit` 에서 받는다 — `min()` 계산을 두 벌 두면 리사이즈 중 한쪽만 갱신된다. 우상단은 오른쪽 여백으로 잡고(`shellBox.width - STUDIO_RIGHT`), 겹은 `pointer-events-none` + 조각만 `auto` 로 둔다
- 창 끝에 붙어야 하는 화면은 `shell.bleed` 다. 프레임의 `overflow` 를 풀고 `--edge`(한쪽 여백, 설계 단위)를 내려준다. `overflow` 는 늘 명시한다(`bleed ? 'visible' : 'hidden'`) — 빼면 React 가 값을 지워 이후 모든 화면이 잘리지 않는다

```tsx
left: 'calc(1076px + var(--edge, 0px))'          // 사이드바를 창 끝까지
left: 'calc(-1 * var(--edge, 0px))', right: …    // 격자·어둡기를 캔버스 밖까지
```

- 글자 크기는 배율을 곱해서 본다. 설계 11px 은 화면에서 8px 이라 못 읽는다. 올린 뒤 넘치는지 눈으로 확인한다
- 캔버스 전체를 스크롤하는 화면은 막대를 숨긴다(`scroll-quiet`). 캔버스 오른쪽 끝은 창 끝이 아니라서 막대가 화면 한가운데 뜬 선처럼 보인다. 패널 안 목록은 `scroll-thin` 그대로 — 막대가 놓일 테두리가 있다
- `screen-frame` 에 `bg-abyss` 를 얹지 않는다. 배경을 가린다 — 창 바탕은 `index.css` 의 `html, body` 가 칠한다

---

## 4. 라우트

경로는 `routes/paths.ts` 에서만 정의한다. 나머지는 전부 여기서 파생된다.

| 쓰는 곳            | 무엇을                                           |
| ------------------ | ------------------------------------------------ |
| `App.tsx`          | `SCREENS[key]` 로 `<Route>` 를 만든다            |
| `lib/shell.ts`     | `BY_SCREEN[key]` 로 배경·헤더·프로필 메뉴를 건다 |
| `routes/access.ts` | `REQUIRES_AUTH[key]` 로 인증 요구를 정한다       |
| 화면 · 컴포넌트    | `paths.agentDetail(id)` 로 링크 주소를 만든다    |

- 경로 문자열을 컴포넌트에 직접 쓰지 않는다. `<a href={paths.my.agents}>`(○) · `<a href="/my/agents">`(✗)
- 화면을 추가하려면 `routePatterns` 에 키를 하나 넣는다. 위 세 곳이 모두 `Record<ScreenKey, …>` 라서 다 채울 때까지 컴파일이 실패한다
- ID 를 받는 화면은 인자가 필수인 함수다(`paths.agentDetail(id)`). ID 없이도 열리는 화면만 `[ID 없는 경로, ID 있는 경로]` 두 패턴을 갖고, 지금은 편집 화면 하나다 — ID 가 없으면 새 워크플로우다

```tsx
paths.edit.nodes(workflowId) // 저장된 워크플로우 — 인자는 필수다
paths.edit.nodes.withoutId // 새 워크플로우
```

인자를 선택으로 두면 ID 를 빠뜨렸을 때 에러 없이 새 워크플로우로 착지한다.

> 이 패턴이 `App.tsx`·`paths.ts`·`shell.ts` 세 곳에 손으로 복제돼 있던 적이 있다. `App.tsx` 에만 `/agent/:agentId` 를 추가하고 `shell.ts` 를 빠뜨렸는데 컴파일이 통과했고, 상세 화면의 배경 밝기와 프로필 메뉴가 사라졌다. 사람이 지키는 규약 대신 타입으로 묶는 이유다.

### 보호 라우트

인증이 필요한 화면은 `routes/access.ts` 의 `REQUIRES_AUTH` 에 적고 `App.tsx` 가 그 값을 보고 `<ProtectedRoute>` 로 감싼다. 화면 안에서 로그인 여부를 확인하지 않는다. 여기도 `Record<ScreenKey, boolean>` 이라 새 화면이 판단 없이 조용히 공개로 열리는 것을 막는다.

| 상태                 | 화면                                        |
| -------------------- | ------------------------------------------- |
| `idle` · `restoring` | "세션을 확인하고 있어요" — 아직 모르는 상태 |
| `unauthenticated`    | 로그인 화면으로 `replace` 이동              |
| `authenticated`      | 화면 그대로                                 |

- 확인이 끝나기 전을 미인증으로 보지 않는다. 쿠키가 살아 있는지는 부팅 때 `GET /users/me` 로 물어봐야 안다(7절). 그 사이를 미인증으로 그리면 새로고침마다 로그인 화면이 한 번 스친다
- 막힌 경로를 history state 로 넘기고, 로그인이 끝나면 `redirectTargetFrom(state)` 으로 그 경로로 돌아간다. state 는 사용자가 만질 수 있으므로 내부 경로인지 확인한다 — `//evil.com` 과 `/\evil.com` 은 `/` 로 시작하지만 브라우저가 외부 주소로 읽는다
- 이동은 `replace` 로 한다. 막힌 경로나 로그인 화면이 히스토리에 남으면 뒤로 가기가 다시 그 자리로 튕긴다

### 가입을 마치지 않은 계정은 가입 화면에 붙잡아 둔다

서버가 첫 서명에서 계정을 먼저 만들므로 "로그인은 됐지만 이름이 없는" 상태가 실재한다. `components/OnboardingGate.tsx` 가 라우트 전체를 감싸 `onboardingCompleted === false` 인 세션을 `/signup` 으로 되돌린다 — 로그인 직후 한 번 보내는 것만으로는 뒤로가기·주소 입력을 막지 못한다.

`ProtectedRoute` 와 나눈 이유는 그쪽이 세션이 있는지만 보기 때문이다. 홈·검색처럼 공개된 화면도 가입 전에는 막고, 둘러보기만 하려면 가입 화면의 뒤로가기로 세션을 끊고 나간다.

---

## 5. 환경변수 · 경로 alias

`@/` 로 임포트한다. `vite.config.ts` 와 `tsconfig.app.json` 에 `@/*` → `src/*` 가 걸려 있다. 같은 폴더 안(`./base`)은 상대 경로 그대로 둔다.

`import.meta.env` 를 직접 읽지 않고 `lib/env.ts` 를 거친다. `.env` 는 커밋되지 않으므로(`.env.example` 만 예외) 클론 직후에는 어느 값도 없을 수 있다 — `ImportMetaEnv` 의 필드는 전부 선택이고 기본값은 `lib/env.ts` 가 채운다.

값이 없을 때 조용히 잘못된 값으로 굴러가지 않게 하는 것이 이 모듈의 목적이다. `VITE_API_BASE_URL` 을 그냥 읽으면 `baseURL` 이 `undefined/v1` 이 되는데, 상대 경로라서 요청이 dev 서버로 붙고 index.html 이 200 으로 돌아온다 — 네트워크 탭은 성공인데 JSON 파싱만 실패한다.

| 변수                    | 기본값                  | 읽는 곳          | 비고                            |
| ----------------------- | ----------------------- | ---------------- | ------------------------------- |
| `VITE_APP_NAME`         | `ABC AI Block Clock`    | `lib/env.ts`     |                                 |
| `VITE_API_BASE_URL`     | `/api`                  | `lib/env.ts`     | vite 프록시가 받는다            |
| `VITE_USE_MOCK`         | `true`                  | `lib/env.ts`     | 끄려면 `false` 를 명시해야 한다 |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | `vite.config.ts` | 빌드타임 전용                   |

변수를 추가하면 `.env.example` · `vite-env.d.ts` · `lib/env.ts` 세 곳을 같이 고친다. 단 브라우저에 실리는 변수만 그렇다 — `VITE_API_PROXY_TARGET` 은 dev 서버 프록시 설정값이라 `vite.config.ts` 가 `loadEnv` 로 읽고 번들에 들어가지 않으므로 `.env.example` 에만 적는다.

---

## 6. 서버 상태 — TanStack Query

서버에서 온 것은 TanStack Query, 화면 안에서만 사는 것은 `useState`·zustand. 서버 응답을 `useState` 로 복사하지 않는다.

기본 옵션은 `lib/queryClient.ts` 에 있다.

| 옵션                   | 값                  | 이유                                                              |
| ---------------------- | ------------------- | ----------------------------------------------------------------- |
| `staleTime`            | 60초                | 목록·상세는 실시간성이 낮다. 즉시성이 필요하면 훅에서 따로 낮춘다 |
| `retry`                | 2회 (4xx 계열 제외) | 400·401·403·404·409·413·415 는 재시도해도 같은 응답이다           |
| `refetchOnWindowFocus` | `false`             | 탭을 옮길 때마다 요청이 나가지 않게                               |
| mutations `retry`      | `false`             | 결제·등록이 두 번 나가면 안 된다                                  |

### 쿼리 키는 `lib/queryKeys.ts` 를 거친다

```ts
useQuery({ queryKey: queryKeys.marketplace.agents.list({ page }) }) // ○
useQuery({ queryKey: ['agents', page] }) // ✗
```

- 키 구조를 API 경로와 일치시킨다. `/api/v1/marketplace/agents` → `queryKeys.marketplace.agents`
- 도메인마다 `all` 을 최상위에 둔다. `all` 은 언제나 배열, 그 아래는 언제나 함수 — 괄호 유무로 헷갈리지 않게
- `all` 을 갖는 것은 도메인뿐이다. 하위 리소스(`credits.transactions` 등)는 `lists()` 가 그 역할을 한다. 유일한 예외인 `marketplace.agents` 는 키를 상위에서 파생한다 — `all: [...MARKETPLACE, 'agents']`(○) · `all: ['marketplace', 'agents']`(✗). 리터럴로 다시 적으면 상위를 고쳤을 때 두 키가 조용히 갈라진다
- 목록은 예외 없이 `lists()` → `list(params)`, 상세는 `details()` → `detail(id)`. 중간 계층이 있어야 부분 무효화(`invalidateQueries({ queryKey: queryKeys.agents.lists() })`)가 된다
- 서버가 받는 query parameter 를 그대로 키에 넣는다. `undefined` 필드는 TanStack Query 의 해시에서 자동으로 빠진다

> `all` 은 함수가 아니라 배열이라 즉시 평가된다. 그 시점의 `queryKeys` 는 아직 TDZ 라 `[...queryKeys.marketplace.all, …]` 이 `ReferenceError` 가 된다 — 그래서 루트만 모듈 상단의 `const` 로 뺀다. 함수 안(`lists()` 등)에서는 `queryKeys` 를 그대로 참조해도 된다.

### 불러오는 중에는 "없음" 을 띄우지 않는다

`data` 가 아직 `undefined` 인 동안을 빈 목록으로 보면 매 요청마다 "결과 없음" 이 한 번씩 깜빡인다.

```ts
const empty = list.length === 0 // ✗ 로딩 중에도 참
const empty = !isPending && !isError && list.length === 0 // ○
```

---

## 7. API 레이어

요청은 `api/<도메인>.ts` 의 함수로만 한다. 엔드포인트 하나당 함수 하나 — 화면에 URL 문자열이 없어야 경로가 바뀔 때 고칠 곳이 하나다. 응답 타입도 `api/` 에 둔다. 숫자를 문자열로 바꾸는 것은 `lib/format.ts` 가 한다(API 는 `runCount: 1248`, 화면은 `1,248회`).

응답은 봉투가 벗겨진 채로 온다. 서버가 `{ status, data }` 로 감싸고 응답 인터셉터가 `data` 만 넘기므로 화면은 `res.data.data` 를 몰라도 된다.

```ts
// api/auth.ts
export const getMyProfile = () => api.get<UserProfile>('/users/me')
```

### 명세가 확정되지 않은 필드는 추정임을 적는다

추정 필드는 `api/` 안에만 두고 추정이라고 적는다. 화면에 필드명을 직접 박지 않는다 — 명세가 오면 고칠 곳이 `api/` 로 한정되어야 한다. 지금 추정인 필드는 없다.

### 목록은 전부 커서다 — 전체 개수를 만들지 않는다

페이지 번호 방식(`page`·`totalElements`·`totalPages`)은 쓰지 않는다. 무한 스크롤로 불러오는 동안 새 항목이 등록되면 항목이 밀려 중복·누락이 생기기 때문이다. `api/page.ts` 에는 `CursorPage`·`CursorParams` 만 있다.

- 커서 목록은 `totalElements` 를 주지 않는다. 전수를 만들지 않는 것이 설계 의도이므로 화면이 "N개" 를 세어 만들지 않는다 — 불러온 만큼만 세고 더 있으면 `+` 를 붙인다
- 서버가 받지 않는 조건을 화면에서 거르지 않는다. 검색·필터·정렬은 목록마다 명세가 정한 것만 쓴다. 화면에서 걸러 두면 첫 장은 걸러지고 스크롤해서 받은 다음 장은 안 걸러진다
- 기본 `size` 를 한쪽으로 박지 않는다. 목록마다 명세가 다르므로 `mocks/cursor.ts` 는 기본값을 인자로 받는다. 박아 두면 화면이 `size` 를 안 넘길 때 mock 과 실서버의 페이지 크기가 갈린다

### 오류는 `ApiError` 로 정규화된다

HTTP status 대신 `code` 로 분기한다. 400 하나에도 `COMMON_001`(입력값 누락)과 `WORKFLOW_005`(순환 연결)이 섞여 있어 status 만으로는 무엇이 잘못됐는지 알 수 없다.

- 다시 시도해도 통과하지 않는 코드는 문구를 따로 가른다. `WORKFLOW_016`(보던 회차와 어긋남)이나 `AGENT_002`(남의 노드가 섞임)를 "잠시 후 다시 시도해 주세요" 로 뭉개면 사용자가 몇 번을 눌러도 안 된다(`pages/RegisterForm.tsx`)
- 코드는 서버 `ErrorCode` 에 있는 것만 쓴다. 없는 코드로 분기하면 그 줄은 영원히 타지 않고, 정작 오는 코드는 기본 문구로 떨어진다
- `Content-Type` 을 인스턴스 기본값으로 박지 않는다. axios 가 본문 타입을 보고 붙인다. `application/json` 을 고정하면 FormData 가 JSON 문자열로 바뀌어 나간다 — 요청은 200 으로 성공하고 파일만 사라진다

```ts
if (e instanceof ApiError && e.code === 'WORKFLOW_016') …
if (e instanceof ApiError && e.isNetworkError) …   // 응답 자체를 못 받음
```

확정된 `GET /users/me` 에서 지갑은 평평한 필드가 아니라 `wallet: { provider, address, network, chainId }` 다.

- 지갑 주소는 `profile.wallet.address` 다. 평평하게 읽으면 mock 은 통과하고 실서버에서만 조용히 `undefined` 가 된다
- 프로필에 표시하는 제공자·네트워크는 서버 값을 쓴다(`wallet.provider`·`wallet.chainId`). 배포 설정이라 화면이 정할 값이 아니다. 「정상 연결」 표시만 브라우저 지갑 상태라서 화면이 판단한다

### 세션 쿠키

로그인 응답의 `Set-Cookie` 로 `SESSION` 이 내려오고, 브라우저가 이후 요청에 자동으로 붙인다. Max-Age 가 없어 브라우저를 닫으면 사라지고, 서버는 마지막 요청으로부터 2시간 미사용이면 만료시킨다(백엔드 `docs/SESSION_AUTH.md`).

- 토큰을 다루는 코드는 없다. Access Token·Refresh Token·재발급(`POST /auth/reissue`)은 전부 사라졌다
- 쿠키는 읽지 않는다. HttpOnly 라 `document.cookie` 로 보이지 않는 것이 정상이다
- `withCredentials: true` 는 인스턴스 기본값이다(`api/client.ts`). 이게 없으면 쿠키가 안 붙어 모든 요청이 `AUTH_005` 다
- 401 은 전부 회복 불가다. 자동 재발급 수단이 없으므로 지갑 서명 로그인부터 다시 한다
- 401 코드로 문구를 가르지 않는다. 서버는 쿠키가 없든 세션이 만료됐든 알 수 없는 세션이든 `AUTH_005` 하나로 답한다. 만료 판정을 Spring Session 이 하고 만료된 세션은 조회조차 되지 않아 서버도 구분하지 않는다. 안내는 `invalid` 하나뿐이고, `disconnected`·`account` 는 지갑 쪽이라 화면이 아는 값이다
- 세션 확인은 `GET /users/me` 뿐이다. 쿠키가 있는지 JS 가 알 수 없어, 부팅 때 프로필을 물어보는 것이 유일한 방법이다(`hooks/useSessionRestore.ts`)

### CSRF 토큰은 api 계층이 알아서 붙인다

쿠키 세션이라 서버가 Spring Security CSRF 검사를 얹었다. 화면이나 훅에서 토큰을 다룰 일은 없다 — `api/client.ts` 의 요청 인터셉터와 `api/csrf.ts` 의 캐시가 전부 처리한다.

- 쓰기에만 붙는다. POST·PUT·PATCH·DELETE 만 대상이고 GET·HEAD·OPTIONS 는 서버가 면제한다
- 부팅 때 미리 받지 않는다. 첫 쓰기에서 `GET /auth/csrf` 로 받는다 — 읽고 떠나는 방문자에게 요청을 하나 더 들이지 않는다
- 발급은 한 번이다. 저장 버튼 두 개가 동시에 눌려도 in-flight 를 공유한다. 세션 쿠키가 없는 상태에서 발급이 두 번 나가면 세션이 두 개 열리고, 브라우저에 남는 쿠키는 나중 것 하나뿐이라 먼저 받은 토큰이 죽는다
- 발급마다 문자열이 달라지는 것은 회전이 아니다. 서버가 BREACH 방어로 매번 다른 값을 XOR 해 내보낼 뿐이고 먼저 받은 값도 계속 유효하다. 값이 다르다고 다시 받으러 가면 왕복만 는다
- 버리는 곳은 네 군데다. 로그인·로그아웃(`api/auth.ts`), 401, 그리고 봉투 없는 403(`api/client.ts`). 세션이 갈리면 거기 매인 토큰도 죽는다
- 봉투 없는 403 만 한 번 재시도한다. 서버의 CSRF 거절은 컨트롤러 앞에서 막혀 우리 오류 봉투를 만들지 못하고 Spring 기본 본문(`{ timestamp, status, error: 'Forbidden', path }`)으로 온다. 도메인 코드가 붙은 403(권한 없음 등)은 건드리지 않는다. 백엔드가 `accessDeniedHandler` 를 붙여 403 에도 봉투를 씌우면 이 판별이 죽는다
- 발급 응답은 검증하고 캐시한다. `headerName` 이나 `token` 이 비면 그대로 실패시킨다 — axios 는 헤더 이름이 비면 조용히 무시해서, 그냥 넣으면 쓰기가 헤더 없이 나가고 빈 값이 캐시에 굳는다
- mock 은 CSRF 를 강제하지 않는다. 발급 핸들러만 있고 검사는 없어서, 403 재시도 경로는 `api/csrf.test.ts` 로만 확인한다

### 인증 세션은 스토어 하나가 들고 있다

`stores/authStore.ts` 가 지갑 주소·프로필·연결 상태(`AuthStatus`)의 단일 소스다. 세션을 여닫는 것도 이 스토어의 액션(`signIn`·`signOut`·`restore`)으로만 한다.

- 스토어가 들고 있는 것은 토큰이 아니라 「서버가 나를 알아보는가」의 결과다. 쿠키는 JS 가 읽을 수 없다
- 늦게 도착한 응답이 지나간 세션을 되살리지 못하게 한다. 로그인은 로그인 → 프로필 두 번을 기다리고 그 사이 세션이 갈릴 수 있다. 스토어가 세션마다 올라가는 번호를 들고, 시작할 때 받은 번호가 최신일 때만 상태를 쓴다
- 버려진 로그인은 서버 세션까지 끊는다. 쿠키는 이미 저장됐고 JS 가 지울 수 없어, 그냥 두면 화면은 로그아웃인데 새로고침 한 번에 되살아난다. 단 뒤이어 시작된 로그인이 있으면 끊지 않는다 — 로그아웃은 이름이 같은 「현재 쿠키」를 끊으므로 방금 성공한 로그인을 대신 끊는다
- `endedBy` 는 인증된 적이 있을 때만 남긴다. 첫 방문의 401 에 사유가 붙으면 한 번도 로그인하지 않은 사람이 「세션이 만료되었습니다」를 본다
- 먼저 적힌 사유가 이긴다. 한 화면이 목록 여러 개를 동시에 부르면 401 도 함께 오는데, 두 번째부터는 이미 미인증이라 사유를 새로 적을 수 없다 — 덮어쓰게 두면 안내가 통째로 사라진다. 사용자가 누른 로그아웃(`signOut`)만 사유를 확정한다
- 인증됐던 세션을 끊을 때 쿼리 캐시도 비운다(`queryClient.clear()`). 스토어만 비우면 `staleTime`(60초) 동안 마이페이지·잔액이 이전 계정 값을 보여 준다 — 같은 브라우저에서 지갑을 갈아타는 일이 흔하다. 비로그인 방문도 부팅 복구 401 로 같은 길을 지나가므로 그때는 비우지 않는다. 공개 화면(상세·검색)의 쿼리까지 날아간다
- 보호 화면은 프로필을 스토어에서 읽는다. `restore`·`signIn` 이 이미 받아 두고 보호 화면은 `authenticated` 일 때만 그려지므로, `GET /users/me` 를 쿼리로 다시 부르지 않는다(`components/Sidebar.tsx`)
- 껍데기는 화면 구성을 세션에 따라 바꾸지 않는다. 로그인 여부와 무관하게 같은 화면을 보여 주고 보호 라우트에서 막는다. 예외는 프로필 메뉴 안의 아바타 이니셜과 로그아웃 행 둘뿐이다 — 로그아웃한 사람에게 다른 사람의 이니셜과 로그아웃을 보여줄 수는 없다. 메뉴는 shadow DOM 안이라 스토어를 못 읽으므로 `App.tsx` 가 속성으로 넘긴다
- api 계층은 라우터를 알지 않는다. 회복 불가능한 401 은 `setOnAuthFailure` 로 스토어에 알리고 스토어는 상태만 비운다. 화면 이동은 `ProtectedRoute` 의 몫이다(4절)

타이밍이 맞아야 재현되는 것이라 손으로 눌러서는 확인할 수 없다. `stores/authStore.test.ts` 가 api 모듈을 대역으로 바꿔 응답 도착 순서를 직접 정한다 — 스토어를 고치면 이걸 돌린다. 시나리오마다 `vi.resetModules()` 로 모듈을 새로 읽는다. 세션 번호가 모듈 전역이라 공유되면 결과가 섞인다.

### 지갑 연결과 서버 세션은 별개다

`lib/wagmi.ts` 가 체인과 커넥터를 들고 있다. MetaMask 하나만 붙이므로 RainbowKit·WalletConnect 를 쓰지 않는다.

- 네트워크 이름을 손으로 적지 않는다. 앱이 요구하는 체인은 `chainLabel`(배너·안내), 사용자 지갑이 붙은 체인은 프로필의 `wallet.chainId` 다. 화면 네 곳에 `Polygon Mainnet` 이 박혀 있었는데 실제 체인은 Amoy 였다
- 체인은 `defineChain` 으로 만든다. SSAFY 체인(31221)은 `wagmi/chains` 프리셋에 없다. 탐색기가 없어 `blockExplorers` 를 두지 않는다 — 지갑에 체인을 추가할 때도 그대로 빠진다. 체인을 옮길 때 고치는 곳은 이 파일 하나다
- 스스로 누른 로그아웃은 지갑까지 끊고, 세션 만료(401)는 세션만 끊는다. 만료 때 지갑까지 풀면 다시 연결부터 해야 한다
- 지갑이 바뀌거나 떨어지면 세션도 끝난다. 계정 교체는 다른 사람의 세션이 되고, MetaMask 에서 연결을 끊으면 우리에게 남은 것은 쿠키뿐이다. 둘 다 서버 쿠키까지 지운다 — 클라이언트 상태만 비우면 새로고침 한 번에 되살아난다
- 연결 해제는 `connected` 를 본 적이 있을 때만 센다. 부팅 직후엔 wagmi 가 재연결 중이라 `disconnected` 로 보이는데, 그때 끊으면 쿠키로 되살린 세션이 매번 날아간다
- 끊을 때는 이유를 남긴다(`endedBy`). 로그인 화면이 그 값으로 안내를 고른다. 사유 목록의 단일 출처는 `SESSION_END_REASONS` 이고 `routes/access.ts` 가 거기서 검사한다 — 손으로 베낀 목록이 하나 빠져 `revoked`·`invalid` 안내가 화면에 닿지 못한 적이 있다. 스스로 누른 로그아웃은 이유가 없다. 본인이 아는 일에 안내를 띄우면 사고처럼 보인다
- 로그아웃은 세션을 먼저 끊고 지갑을 끊는다. 순서가 반대면 위 가드가 "연결이 해제됐다" 로 읽어 엉뚱한 안내가 붙는다

### 워크플로우 저장 · 불러오기

캔버스가 들고 있는 모양과 서버가 받는 모양이 다르다. 변환은 `lib/workflowPayload.ts` 한 곳에서만 한다 — 화면이 직접 `nodeKey` 를 만들면 규칙이 갈린다.

- 노드는 서버 원본을 함께 들고 있어야 한다. 팔레트 항목의 `itemId`·`itemType` 을 `CanvasNodeData` 의 `sourceId`·`sourceType` 으로 싣는다. 이게 없으면 화면에는 멀쩡히 그려지는데 저장할 방법이 없다
- 키는 저장할 때 새로 매긴다(`n1…nN` · `e1…eM`). 캔버스 id 를 그대로 쓰면 서버의 64자 제한에 걸린다 — 노드 id 가 UUID 가 되는 순간 선 키가 73자다. 차례는 `treeOrder` 가 정하고 분기는 참 갈래부터다
- 좌표는 보내지 않는다. 서버가 저장하지 않아 다시 열면 이어진 모양만 남는다. 깊이마다 오른쪽, 같은 깊이는 아래로 다시 늘어놓는다(`toCanvasWorkflow`)
- 이름·판매 판정은 서버가 준다. 단가·종류·프롬프트는 주지 않아 노드 팔레트를 따로 받아 `sourceId` 로 맞춘다. 못 찾은 노드는 자리를 남기고 단가는 `null` 로 둔다 — 카드를 빼면 저장된 구성이 조용히 바뀌고, 0 으로 채우면 공짜로 보여 견적과 어긋난다
- `size` 는 최대 30 이다. 넘기면 `COMMON_001` 로 거절돼 조회가 통째로 실패한다. 이름표는 커서로 끝까지 넘겨 모은다
- 연결 규칙은 그을 때 막는다(`canConnect`). 서버는 어긴 연결을 `WORKFLOW_013` 하나로만 돌려줘 다녀와서는 원인을 짚을 수 없다. 분기 · 합류는 되고, 순환과 분기 갈래를 종료에 바로 잇는 것은 안 된다
- 순번은 놓은 순서가 아니라 이어진 차례다(`chainPositions`). 갈래가 있어 차례를 정할 수 없으면 번호를 감춘다 — 지어내면 "이 순서로 실행된다" 로 읽힌다
- `revision` 은 화면이 들고 있다가 그대로 돌려준다. 어긋나면 `WORKFLOW_016` 이고, 덮어쓰지 않고 다시 열게 안내한다
- 빈 캔버스로 시작한다. 자리표시자를 깔아 두면 사용자가 놓지 않은 것이 저장된다. `CANVAS_NODES` 는 검사용 표본이지 초기값이 아니다

편집·대기·진행 세 화면이 `hooks/useSavedWorkflow.ts` 로 같은 워크플로우를 읽는다. 대기·진행은 `?workflowId=` 로 받고, 없으면 빈 캔버스를 그린다.

### 노드 출력 형식

기본 노드는 출력을 글(`TEXT`) 또는 JSON 예시(`output.example`)로 선언한다. 분기 조건은 JSON 예시의 칸만 읽는다. 변환 · 검사는 `lib/nodeOutput.ts` 한 곳에서 한다.

- JSON 은 폼(이름 · 타입 · 예시 값)으로 적고 프론트가 예시 객체로 바꿔 보낸다. 서버 · 엔진이 받는 값은 직접 적은 JSON 과 같다
- 폼은 한 단계짜리 글자 · 숫자 · 참거짓만 다룬다. 목록 · 하위 객체 · null 이 든 예시는 「JSON 으로 직접 적기」로 연다. 전환하다 값을 잃는 입력(숫자가 아닌 값 · 겹친 이름 · 이름 없는 값)이 있으면 넘어가지 않는다
- 예시는 엔진 `check_example` 과 같은 규칙으로 검사한다 — 빈 하위 객체 금지, 중첩 5단계, 키 60개. 서버는 맨 위 객체만 보고 저장해, 걸러내지 않으면 실행할 때마다 `OUTPUT_EXAMPLE_INVALID` 다
- 예시 객체는 `Object.fromEntries` 로 만든다. `{}` 에 대입하면 `__proto__` 키가 사라진다
- 기존 노드를 고칠 때는 `PUT /nodes/{id}` 에 `output` 을 늘 싣는다. 빠지면 서버가 글로 되돌린다

### 실행 견적

실행 대기 화면의 「실행 예상 비용」은 `POST /executions/cost-estimates` 가 준 값이다. 금액은 서버와 같이 소수점 여섯 자리 문자열이라 숫자로 바꾸는 곳은 `Number()` 한 번뿐이고, 표시는 `lib/format.ts` 를 거친다.

- 견적은 계산만 하고 차감하지 않는다. 그 사이 다른 실행이 잔액을 잡을 수 있어 창에 돌아올 때마다 다시 받는다(`refetchOnWindowFocus`)
- `executable: false` 일 때만 실행을 막는다. 견적을 못 받았다고 막지 않는다 — 최종 판정은 서버가 실행을 접수할 때 다시 하므로, 화면이 모른다는 이유로 멈추면 통과할 실행까지 못 하게 된다
- 경로 앞 조각이 갈린다. 견적은 `/executions`, 실행 시작·조회는 `/runs` 다. 서버가 이유를 밝힌 적이 없어 그대로 따르고 있다

### 실행 시작과 진행

시작은 `POST /runs` 이고 `multipart/form-data` 다. 첫 단계가 읽을 파일을 같이 싣기 때문이다. 진행은 `GET /runs/{executionId}/stream` 을 `EventSource` 로 받고, 붙지 못하면 `GET /runs/{executionId}` 를 2초 간격으로 묻는 쪽으로 내려간다.

- `Content-Type` 을 손대지 않는다. FormData 를 주면 브라우저가 boundary 까지 붙인다. 여기에 값을 박으면 폼이 문자열로 나간다
- 빈 칸은 넣지 않는다. 빈 문자열을 실으면 서버가 「값이 왔는데 비었다」로 받는다
- `requestId` 는 누름마다 하나다. 접수되는 순간 ABC 가 빠져 재시도에 새 값을 만들면 두 번 빠진다. 보낼 내용이 같으면 같은 값을 쓰고, 코멘트·파일이 바뀌면 새로 만든다
- `PAYMENT_008`(402) 은 충전 안내를 붙이고, `EXECUTION_006`(409) 은 감춘다. 409 는 연타가 거의 동시에 간 것뿐이다
- 끝난 실행에서는 스트림을 직접 닫는다. `EventSource` 는 닫히면 스스로 다시 붙어, 종료 이벤트만 되풀이해 받는다
- 붙지 못하면 상한을 두고 폴링으로 내려간다. 응답이 비-2xx 면 `CLOSED` 지만 서버에 닿지도 못하면 `CONNECTING` 으로 끝없이 재시도한다. MSW 가 `EventSource` 를 가로채지 못해 mock 에는 스트림이 아예 없다
- 단계와 카드는 수가 같을 때만 짝짓는다. `steps` 에 `nodeKey` 가 없고 에이전트는 안의 단계 수만큼 펼쳐진다. 어긋나면 진행률로만 칠한다
- jsdom 에서는 multipart 가 나가지 못한다. `FormData` 가 undici 의 것과 달라 문자열로 직렬화된다. 접수 테스트는 폼 조립까지만 보고 요청 자체는 브라우저로 확인한다

### ABC 충전

화면 용어는 「충전」이다. 백엔드의 「예치」는 실행을 접수할 때 잔액에서 떼어 묶어 두는 금액이라 뜻이 달라 화면에 쓰지 않는다.

지갑이 abcToken 을 보관 지갑으로 보내고, 서버가 그 거래를 체인에서 확인해 같은 수만큼 적립한다. 접수는 `POST /credits/charges`, 확인은 `GET /credits/charges/{chargeId}` 이고 두 응답이 같은 모양이라 화면도 한 코드로 그린다. 흐름은 `hooks/useAbcCharge.ts`, 주소·자릿수는 `lib/abcToken.ts`, 실패 문구는 `lib/chargeMessage.ts` 한 곳에 모은다.

- 해시를 받자마자 접수한다. 영수증을 기다리는 사이 새로고침하거나 창을 닫으면 해시가 어디에도 남지 않아 토큰만 나간 상태가 된다. 접수보다 `localStorage` 에 먼저 적는 것도 같은 이유다
- 접수 실패와 전송 실패를 갈라 둔다(`unsubmitted`). 해시가 손에 있으면 다시 하는 것은 접수뿐이다. 「다시 시도」 하나로 묶으면 토큰을 두 번 보낸다
- 같은 해시를 다시 보내도 된다. 서버가 새로 만들지 않고 앞의 요청을 돌려주므로 재시도를 화면에서 막을 필요가 없다
- 금액은 ABC 단위로 보낸다. `10^18` 을 곱한 값은 지갑에만 싣는다(`toTokenValue`). 소수는 7자리부터 버린다 — 올리면 지갑 잔액을 넘겨 전송 자체가 실패한다
- 화면 금액은 `chargeAmountLabel` 로 적는다. `priceLabel` 은 소수 2자리에서 끊어 `1.123456` 을 보내면서 `1.12` 로 보인다
- 단계는 서버 응답이 정한다(`STATUS_PHASE`). 30분 한도도 서버가 센다. 화면이 따로 세면 두 값이 어긋난다
- 폴링은 배경에서도 돈다(`refetchIntervalInBackground`). 기본값이면 탭을 옮기는 순간 멈춰서 「이 화면을 떠나도 됩니다」 안내가 거짓이 된다. 간격은 5초다 — 블록이 10초에 하나씩 생기고 서버도 10초마다 다시 본다
- 보내기 전에 막을 것은 다 막는다. 로그인한 지갑이 아니면 서버가 `SENDER_WALLET_MISMATCH` 로 닫는데, 그때는 토큰이 이미 나간 뒤다
- 가스비가 0 인 체인이라 레거시 + `gasPrice: 0` 으로 보낸다. 지갑이 EIP-1559 로 수수료를 잡지 않게 못박는다
- 실패 문구를 「다시 시도해 주세요」로 끝내지 않는다. 어느 사유든 토큰은 이미 지갑에서 나갔으므로 사유마다 다음에 할 일을 적는다(`ChargeFailureReason` 7종)
- 두 주소는 서버 설정(`payment.charge`)과 같아야 한다. 어긋나면 토큰은 나가고 `CUSTODY_ADDRESS_MISMATCH` 로 닫혀 되돌릴 방법이 없다
- 지갑에 토큰을 등록해 둔다(`wallet_watchAsset`). 등록하지 않으면 전송 창의 기호 자리가 「알 수 없음」 으로 떠 무엇을 보내는지 확인할 수 없다. 거절해도 로그인·충전은 그대로 된다
- mock 은 체인을 보지 않는다. 8초가 지나면 무조건 완료로 넘기므로 되돌려진 거래도 mock 에서는 「충전 완료」가 된다. 실패 경로는 실서버에서 확인한다

---

## 8. mock 서버 — MSW

백엔드 없이 화면을 개발하기 위한 mock 이다. `VITE_USE_MOCK` 이 `'false'` 가 아니면 켜진다(5절).

```
src/mocks/
├─ start.ts            켜기 — main.tsx 가 렌더 전에 await 한다
├─ browser.ts          setupWorker
├─ response.ts         응답 봉투 헬퍼 (ok · fail)
├─ scenario.ts         지연 · 강제 실패 스위치
├─ cursor.ts           목록 커서 계산 (cursor/size)
├─ fixtures/           도메인별 데이터
└─ handlers/           응답 만들기 — api/<도메인>.ts 와 짝
   └─ index.ts         도메인 핸들러를 모은다
```

- 도메인 하나가 파일 하나다. `api/<도메인>.ts` 와 이름을 맞추고 `handlers/index.ts` 의 배열에 펼쳐 넣는다
- 데이터는 `fixtures/`, 응답 만들기는 `handlers/` 다. 핸들러에 데이터를 인라인으로 두면 두 핸들러가 같은 데이터를 쓰기 시작할 때 조용히 갈라진다
- 파생값은 계산해서 만든다. `unitPrice: Math.round(seed.baseCost * (1 + premiumRate))`(○) · `unitPrice: 540`(✗) — 손으로 적으면 등급을 바꿀 때 같이 안 바뀌어 거짓 데이터가 된다. 단, 화면에 안 보이는 값에서 파생시키지 않는다. 응답에도 화면에도 없는 필드를 근거로 계산하면 규칙의 전제가 사라지고 mock 이 서버 정책을 재구현하는 꼴이 된다
- 서버가 정책으로 산정해 주는 값은 그대로 적는다. 단가·누적 수익이 그렇다 — 수수료율·할인을 mock 이 흉내 내기 시작하면 실서버와 갈라지고, 갈라진 줄도 모른다
- 평점·사용량이 0 인 콜드스타트 건을 반드시 섞는다. 화면이 NaN·빈칸을 내는지는 그때만 드러난다
- 목록은 핸들러 안에서 실제로 걸러낸다. 전체를 한 번에 돌려주면 검색어·필터·페이지 이동이 아무 효과가 없어 검증 자체가 불가능하다
- 봉투를 그대로 흉내 낸다. `ok`·`fail` 만 쓴다. 알맹이만 돌려주면 화면이 받는 모양이 실서버와 달라져 mock 에서만 되는 코드가 만들어진다. 오류는 실제 도메인 코드를 쓴다 — 화면이 `code` 로 분기하기 때문에(7절) 코드를 대충 주면 분기를 검증할 수 없다
- URL 은 `API_BASE_URL`(`api/client.ts`)에서 만든다. 한쪽만 바뀌면 mock 이 요청을 못 잡고 조용히 실서버로 새어 나간다
- 핸들러가 없는 요청은 통과시킨다(`onUnhandledRequest: 'bypass'`). `'warn'` 은 이미지·폰트까지 콘솔을 채운다. 요청이 프록시로 나간다면 핸들러가 없는 것이지 mock 이 꺼진 게 아니다
- 프로덕션 번들에 들어가지 않는다. `start.ts` 만 `import.meta.env.DEV` 를 직접 읽는다(5절의 예외) — Vite 가 이 값을 `false` 로 치환해 블록을 통째로 지워야 devDependency 인 `msw` 가 번들에서 빠진다. `env.useMock` 으로는 정적으로 지울 수 없다
- `public/mockServiceWorker.js` 는 `npx msw init public/ --save` 가 만든 벤더 스크립트다. 손으로 고치지 않고 린트·포맷 대상에서도 뺀다

### 세션과 프로필은 새로고침을 넘어 남긴다

mock 상태는 대개 새로고침하면 픽스처로 돌아가지만, 쓰기 결과를 새로고침 뒤에도 확인해야 하는 것은 localStorage 에 둔다. 모듈 변수로 들고 있으면 "저장했는데 안 남는다" 로 보여서 그 경로를 검증할 수 없다. `handlers/auth.ts`·`handlers/workflows.ts`·`handlers/executions.ts` 가 그렇게 들고 있다.

| 키                  | 무엇을                     | 없으면                     |
| ------------------- | -------------------------- | -------------------------- |
| `mockWalletSession` | `sessionToken` 쿠키 대역   | `/users/me` 가 401         |
| `mockProfile`       | 이름·소개 등 수정된 프로필 | 픽스처 초기값              |
| `mockKnownWallets`  | 한 번이라도 로그인한 지갑  | 모든 주소가 온보딩 전 취급 |
| `mockWorkflows`     | 저장한 워크플로우          | 저장한 id 가 404           |
| `mockExecutions`    | 접수한 실행의 시작 시각    | 진행 조회가 404            |
| `mockFailStep`      | 멎게 할 단계 번호(0부터)   | 실행이 끝까지 성공한다     |

| 요청                      | 세션 없음      | 세션 있음          |
| ------------------------- | -------------- | ------------------ |
| `GET /users/me`           | 401 `AUTH_005` | 세션 주소의 프로필 |
| `POST /auth/wallet/login` | —              | `expiresAt`(7일)   |
| `POST /auth/logout`       | 401 `AUTH_005` | 세션을 지운다      |

되돌리려면 `localStorage.removeItem('mockWalletSession')` · `removeItem('mockProfile')` · `removeItem('mockKnownWallets')` 를 실행한다. 셋을 다 지워야 처음 보는 지갑으로 돌아가 가입 화면 분기를 다시 탈 수 있다 — `mockKnownWallets` 를 남기면 로그인이 곧장 홈으로 간다. 저장한 워크플로우만 비우려면 `removeItem('mockWorkflows')`.

### mock 이 안 켜지면 조용히 프록시로 샌다

`onUnhandledRequest: 'bypass'` 라서 mock 이 꺼져 있으면 요청이 vite 프록시로 나가고, 백엔드가 없으면 502 로만 보인다. 콘솔에 `[MSW] Mocking enabled.` 가 없으면 mock 이 안 켜진 것이다. `main.tsx` 가 시작 실패를 삼키지 않고 남기니 그것부터 확인한다.

`src/mocks/` 는 HMR 로 갈아끼우지 않는다. 핫 교체하면 새 모듈이 생겨도 이미 떠 있는 워커는 옛 핸들러 목록을 들고 있어, 그 뒤 요청이 전부 프록시로 새어 나간다 — mock 을 고치자마자 502 가 나는 이유였다. `vite.config.ts` 의 `reloadOnMockChange` 가 이 폴더 변경 시 페이지를 다시 읽는다.

### 로딩·실패는 시나리오 스위치로 확인한다

핸들러는 맨 앞에서 `scene()` 을 부른다. URL 쿼리로 켜고 localStorage 에 남아 화면을 옮겨도 유지된다.

```
?mockDelay=1500                        모든 응답을 늦춘다
?mockFail=COMMON_001                   모든 응답을 실패시킨다 (기본 400)
?mockFail=AUTH_005&mockFailStatus=401
?mockDelay=0&mockFail=                 끈다
```

`mockWalletSession` 을 지우면 세션 없음 상태가 된다 — 쿠키를 흉내 내는 대역이라 `?mockFail=` 과 달리 실제로 로그인 화면으로 밀려나는 경로를 볼 수 있다.

CSRF 는 mock 이 강제하지 않는다. 발급(`GET /auth/csrf`)만 응답하고 쓰기 요청의 헤더는 보지 않으므로, 토큰이 죽었을 때의 403 재시도는 브라우저에서 재현되지 않는다(`api/csrf.test.ts` 가 확인한다).

---

## 9. Tailwind

- 반복되는 것만 Tailwind, 1회성 좌표는 인라인이다. 이 프로젝트 화면은 1440×1024 고정 좌표라 좌표를 Tailwind 임의값으로 옮기면 길어지기만 하고 재사용 이득이 없다. 기준은 2회 이상 나오는 선언은 Tailwind, 1회성 수치는 인라인 style
- 색·그림자·반경은 토큰으로 쓴다. `index.css` 의 `@theme` 에 정의하고 클래스로 쓴다(`bg-surface-card` · `text-muted` · `shadow-menu`). 토큰에 있는 값을 인라인으로 다시 박지 않는다
- 클래스 정렬은 Prettier 에 맡긴다. `prettier-plugin-tailwindcss` 가 저장할 때 정렬하므로 손으로 맞추지 않는다
- 조건부는 지금 세 곳 미만이라 삼항으로 충분하다. 다섯 곳을 넘으면 `clsx` + `tailwind-merge` 로 `cn()` 을 도입하고, 그 전에는 의존성을 늘리지 않는다

### `@theme` 는 `static` 으로 둔다

Tailwind v4 는 쓰이지 않는 `@theme` 변수를 빌드에서 지운다. 게다가 반경·그림자 유틸리티는 값을 인라인해 버려서(`.shadow-card{--tw-shadow:0 10px 34px …}`) 클래스로 쓰고 있어도 그 변수는 `:root` 에 나오지 않는다. 섀도 DOM 컴포넌트는 Tailwind 클래스를 못 써서 `var(--radius-menu)` 처럼 변수를 직접 부르는데, 그러면 토큰의 생존이 "그 컴포넌트가 아직 그 줄을 들고 있는가" 에 달린다. `static` 은 정의한 토큰을 사용 여부와 무관하게 내보내 이 의존을 끊는다.

### 토큰 `var()` 에 예비값을 쓰지 않는다

`var(--radius-menu, 16px)` 의 예비값은 두 가지를 한다. 변수명 오타를 덮어 화면을 멀쩡하게 만들고(`--surface-menu` 오타가 이렇게 오래 남았다), 토큰 값의 두 번째 사본이 되어 시간이 지나면 갈라진다(한때 같은 `--color-accent` 에 예비값이 `#e9b7d2` 와 `#b98da9` 두 가지로 붙어 있었다). `@theme static` 이 토큰을 항상 내보내므로 예비값은 필요 없다.

예외는 런타임에 주입되는 변수뿐이다. `--edge` 는 `App.tsx` 가 인라인 style 로 내려주므로 주입 전에는 정말로 없다 — 여기에는 `var(--edge, 0px)` 처럼 예비값을 둔다.

두 규칙은 `src/tokens.test.ts` 가 강제한다. 오타와 예비값 둘 다 CI 에서 파일·줄과 함께 실패한다.

### 기본 스타일은 반드시 `@layer base` 안에

Tailwind v4 는 유틸리티를 `@layer utilities` 에 넣는다. 레이어 밖 CSS 가 레이어 안 CSS 를 이긴다.

```css
/* ✗ 레이어 밖 — 모든 <a> 의 text-* 클래스가 무력화된다 */
a {
  color: inherit;
}

/* ○ */
@layer base {
  a {
    color: inherit;
  }
}
```

---

## 10. 하지 말 것 — 이 프로젝트에서 실제로 터진 것들

### 유리 패널의 조상에 `transform` · `opacity` 를 걸지 않는다

`backdrop-filter` 는 backdrop 루트 안쪽만 샘플링한다. `transform`·`opacity`·`filter` 가 걸린 요소는 그 루트가 되므로, 그 안의 유리 패널은 바깥의 배경을 못 가져와 검게 보인다.

```tsx
// ✗ 등장 모션이 도는 동안 안쪽 유리 패널이 전부 납작해진다
@keyframes screenIn { from { transform: translateY(22px) } }

// ○ top 은 backdrop 루트를 만들지 않는다
@keyframes screenIn { from { top: 22px } }
```

### 커스텀 엘리먼트는 shadow DOM 에 그린다

웹 컴포넌트가 light DOM 에 직접 쓰면 React 가 모르는 자식이 생겨 리렌더 때 `removeChild` 에서 터진다.

```ts
protected override usesShadow = true;   // BaseElement 가 shadowRoot 를 만들어 준다
this.root.innerHTML = …                 // this.innerHTML (✗)
```

shadow 안에는 문서의 `@keyframes` 가 닿지 않으므로 `addStyle()` 로 함께 넣는다. `addStyle()` 은 자식 `<style>` 이 아니라 `adoptedStyleSheets` 로 넣는다 — 자식으로 붙이면 `update()` 가 `root.innerHTML` 로 다시 그릴 때 같이 지워진다. 오류가 나지 않고 애니메이션만 조용히 멈추므로, 서명 대기 스피너가 한참 동안 돌지 않는 채로 있었다.

### 커스텀 엘리먼트 속성명이 프로퍼티와 겹치지 않게 한다

React 19 는 해당 프로퍼티가 엘리먼트에 있으면 속성이 아니라 프로퍼티로 넣는다. `blur` 는 `HTMLElement.blur()` 와 겹쳐 속성이 아예 붙지 않는다.

```tsx
<ocean-bg blur={5} />               // ✗ 속성이 안 붙는다
<ocean-bg {...{ 'blur-px': 5 }} />  // ○
```

`nonce` 도 같다 — `HTMLElement.nonce`(CSP)와 겹쳐 프로퍼티로 들어가고, 브라우저가 보안상 content 속성을 비워서 `getAttribute` 가 `null` 이다. 표준 프로퍼티와 이름이 겹치는지 의심되면 접미사를 붙인다(`nonce-value`·`blur-px`). 겹치면 조용히 기본값이 그려진다.

내가 만든 getter 도 프로퍼티다. 관찰 속성과 같은 이름의 getter 를 두면 React 가 거기에 대입하려 하고, setter 가 없으면 그 자리에서 터진다 — `Cannot set property phase of #<WalletDialog> which has only a getter`. 속성은 `str('phase')` 로 읽고 이름이 겹치는 프로퍼티는 두지 않는다(`components/wallet-dialog.ts`).

### shadow DOM 을 넘는 이벤트는 `composedPath()` 로 잡는다

shadow 경계를 넘으면 `e.target` 이 호스트로 바뀌어 `closest('a')` 가 못 찾는다.

```ts
const a = e.composedPath().find((n): n is HTMLAnchorElement => n instanceof HTMLAnchorElement)
```

### `value` 를 넘길 때는 `onChange` 를 같이 준다

`value` 만 주면 React 가 제어 컴포넌트로 보고 읽기 전용이 된다. 초기값만 채울 때는 `defaultValue`.

```tsx
<input value={nickname} />          // ✗ 입력이 안 된다
<input defaultValue={nickname} />   // ○
```

### 화면 이동은 라우터로

`<a href="https://…">` 처럼 외부 주소를 쓰면 전체 페이지가 재로드되어 배경이 깜빡인다. `App.tsx` 의 전역 클릭 인터셉터가 `/` 로 시작하는 내부 링크를 클라이언트 라우팅으로 넘기므로, 내부 이동은 `paths` 가 만든 주소만 쓴다(4절).

---

## 11. 커밋 전 확인

```bash
npm test           # vitest run
npm run build      # tsc -b && vite build — 타입 에러 0
npm run lint
npm run format
```

테스트는 mock 핸들러를 실제로 호출한다(`src/test/server.ts` 가 `mocks/handlers` 를 그대로 쓴다). 핸들러를 테스트용으로 따로 만들지 않는다 — 갈라지면 테스트가 통과해도 화면이 받는 응답은 다를 수 있다.
