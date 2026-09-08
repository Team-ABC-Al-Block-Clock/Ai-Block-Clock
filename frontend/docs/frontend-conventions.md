# 프론트엔드 코드 컨벤션

> 대상: `frontend/` · 스택: React 19 + TypeScript + Vite + Tailwind v4
> Git·브랜치·커밋 규칙은 [`git-workflow.md`](../../git-workflow.md) 를 따른다.

이름만 보고 **컴포넌트인지, 로직인지, 타입인지** 알 수 있게 하는 것이 목표다.

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

- 인터페이스에 `I` 를 붙이지 않는다. `IUserData` (✗) → `UserData` (○)
- Props 타입이 한 줄이면 인라인도 허용하되, 두 개를 넘으면 이름을 붙인다

```tsx
// ○ 짧으면 인라인
function Sidebar({ active }: { active?: Tab }) {}

// ○ 늘어나면 이름을 붙인다
interface SidebarProps {
  active?: Tab
  editing?: boolean
  onSelect?: (tab: Tab) => void
}
```

### 예외 — 웹 컴포넌트는 kebab-case

커스텀 엘리먼트는 **HTML 표준이 태그명에 하이픈을 요구한다.** 파일명을 태그명과 맞춘다.

```
src/components/screen-frame.ts   →  <screen-frame>
src/components/ocean-bg.ts       →  <ocean-bg>
src/components/app-header.ts     →  <app-header>
src/components/profile-menu.ts   →  <profile-menu>
```

React 컴포넌트(`Sidebar.tsx`)와 웹 컴포넌트(`app-header.ts`)를 파일명만으로 구분할 수 있다.

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
├─ pages/          화면 단위 (라우트 하나 = 파일 하나)
├─ components/     여러 화면이 쓰는 것
├─ routes/         라우트 패턴 · 경로 생성기
├─ lib/            공용 헬퍼·설정 (env, queryClient, queryKeys, shell)
├─ api/            서버 통신
├─ stores/         전역 상태
├─ hooks/          커스텀 훅
├─ index.css       전역 CSS · Tailwind 토큰
└─ assets/         이미지·폰트
```

- 한 파일에 컴포넌트 하나. `export default` 로 내보낸다
- 두 화면이 같은 것을 쓰기 시작하면 `components/` 로 올린다. **세 번째가 나오기 전에는 올리지 않는다**

---

## 3. 화면 구조

껍데기와 내용을 분리한다.

```tsx
// App.tsx — 라우트가 바뀌어도 유지되는 것
<screen-frame>
  <ocean-bg /> {/* 배경: 다시 만들면 셰이더 재컴파일 */}
  <div className="animate-screen-in">
    <Routes>…</Routes> {/* 내용만 갈아끼움 */}
  </div>
  <app-header /> {/* 로고 */}
  <profile-menu /> {/* 프로필 메뉴 */}
</screen-frame>
```

- 라우트별 설정(배경 밝기·로고 표시 여부 등)은 `lib/shell.ts` 한 곳에 모은다
- 화면(`pages/*.tsx`)은 **자기 내용만** 그린다. 배경·로고·헤더를 각자 넣지 않는다

---

## 4. 라우트

경로는 `routes/paths.ts` 에서만 정의한다. 나머지는 전부 여기서 파생된다.

```ts
// routes/paths.ts — 유일한 정의
export const routePatterns = {
  agentDetail: ['/agent', '/agent/:agentId'],
  // …
}
```

| 쓰는 곳         | 무엇을                                           |
| --------------- | ------------------------------------------------ |
| `App.tsx`       | `SCREENS[key]` 로 `<Route>` 를 만든다            |
| `lib/shell.ts`  | `BY_SCREEN[key]` 로 배경·헤더·프로필 메뉴를 건다 |
| 화면 · 컴포넌트 | `paths.agentDetail(id)` 로 링크 주소를 만든다    |

- **경로 문자열을 컴포넌트에 직접 쓰지 않는다**

```tsx
<a href={paths.my.agents}>   {/* ○ */}
<a href="/my/agents">        {/* ✗ */}
```

- 화면을 추가하려면 `routePatterns` 에 키를 하나 넣는다. `SCREENS` 와 `BY_SCREEN` 이 `Record<ScreenKey, …>` 라서 둘 다 채울 때까지 컴파일이 실패한다
- 리소스 ID 를 받는 화면은 `[ID 없는 경로, ID 있는 경로]` 두 패턴을 갖는다. 목 데이터가 붙기 전까지 옛 경로를 살려 둔 것이고, 전환이 끝나면 앞쪽을 지운다

```tsx
paths.agentDetail(agentId) // '/agent/{id}' — ID 가 있으면 언제나 이쪽
paths.agentDetail.withoutId // '/agent'      — 아직 ID 가 없는 화면만
```

`paths.agentDetail(id)` 의 인자는 **필수**다. 옛날처럼 인자를 선택으로 두면 ID 를 빠뜨렸을 때 에러 없이 옛 경로로 착지해 버린다. 전환이 끝나면 `routePatterns` 의 앞쪽 패턴과 `.withoutId` 를 같이 지운다 — 남은 호출부가 전부 컴파일 에러로 드러난다

### 왜 이렇게까지 하나

패턴이 `App.tsx`·`paths.ts`·`shell.ts` 세 곳에 손으로 복제돼 있던 적이 있다. `App.tsx` 에만 `/agent/:agentId` 를 추가하고 `shell.ts` 를 빠뜨렸는데 **컴파일이 통과했고**, 상세 화면의 배경 밝기와 프로필 메뉴가 사라졌다. 사람이 지키는 규약 대신 타입으로 묶는다.

---

## 5. 환경변수 · 경로 alias

### `@/` 로 임포트한다

`vite.config.ts` 와 `tsconfig.app.json` 에 `@/*` → `src/*` 가 걸려 있다.

```ts
import { paths } from '@/routes/paths' // ○
import { paths } from '../../routes/paths' // ✗
```

같은 폴더 안(`./base`)은 상대 경로 그대로 둔다.

### `import.meta.env` 를 직접 읽지 않는다

`lib/env.ts` 를 거친다.

```ts
env.apiBaseUrl // ○
import.meta.env.VITE_API_BASE_URL // ✗ — 없을 수 있다
```

`.env` 는 커밋되지 않는다 (`.env.example` 만 예외). 클론 직후에는 **어느 값도 없을 수 있으므로** `ImportMetaEnv` 의 필드는 전부 선택이고, 기본값은 `lib/env.ts` 가 채운다.

값이 없을 때 조용히 잘못된 값으로 굴러가지 않게 하는 것이 이 모듈의 목적이다. `VITE_API_BASE_URL` 을 그냥 읽으면 `baseURL` 이 `undefined/v1` 이 되는데, 상대 경로라서 요청이 dev 서버로 붙고 index.html 이 **200 으로** 돌아온다 — 네트워크 탭은 성공인데 JSON 파싱만 실패한다.

| 변수                    | 기본값                  | 읽는 곳          | 비고                                |
| ----------------------- | ----------------------- | ---------------- | ----------------------------------- |
| `VITE_APP_NAME`         | `ABC AI Block Clock`    | `lib/env.ts`     |                                     |
| `VITE_API_BASE_URL`     | `/api`                  | `lib/env.ts`     | vite 프록시가 받는다                |
| `VITE_USE_MOCK`         | `true`                  | `lib/env.ts`     | 끄려면 `false` 를 **명시**해야 한다 |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | `vite.config.ts` | 빌드타임 전용 — 아래 참고           |

> `VITE_USE_MOCK` 을 실제로 읽어 목 서버를 켜는 연동은 S15P21A403-178 에서 붙는다. 그 전까지 이 값은 아무 동작도 하지 않는다.

변수를 추가하면 `.env.example` · `vite-env.d.ts` · `lib/env.ts` 세 곳을 같이 고친다.

**단, 브라우저에 실리는 변수만 그렇다.** `VITE_API_PROXY_TARGET` 은 dev 서버의 프록시 설정값이라 `vite.config.ts` 가 `loadEnv` 로 읽는다. 번들에 들어가지 않으므로 `ImportMetaEnv` 에도 `lib/env.ts` 에도 없는 것이 맞다 — `.env.example` 에만 적는다.

---

## 6. 서버 상태 — TanStack Query

서버에서 온 것은 TanStack Query, 화면 안에서만 사는 것은 `useState`·zustand. **서버 응답을 `useState` 로 복사하지 않는다.**

### 기본 옵션 (`lib/queryClient.ts`)

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

**규칙**

- 키 구조를 API 경로와 일치시킨다. `/api/v1/marketplace/agents` → `queryKeys.marketplace.agents`
- 도메인마다 `all` 을 최상위에 둔다. **`all` 은 언제나 배열, 그 아래는 언제나 함수** — 괄호 유무로 헷갈리지 않게
- `all` 을 갖는 것은 도메인뿐이다. 하위 리소스(`credits.transactions`, `settlements.statistics`)는 `lists()` 가 그 역할을 하므로 따로 두지 않는다
- 하위 리소스가 `all` 을 갖는 유일한 예외는 `marketplace.agents` 다. 이때 키는 **상위에서 파생**한다 — 리터럴로 다시 적으면 상위를 고쳤을 때 두 키가 조용히 갈라진다

```ts
all: [...MARKETPLACE, 'agents'] as const // ○
all: ['marketplace', 'agents'] as const // ✗
```

> `all` 은 함수가 아니라 배열이라 즉시 평가된다. 그 시점의 `queryKeys` 는 아직 TDZ 라 `[...queryKeys.marketplace.all, …]` 이 `ReferenceError` 가 된다 — 그래서 루트만 모듈 상단의 `const` 로 뺀다. 함수 안(`lists()` 등)에서는 `queryKeys` 를 그대로 참조해도 된다

- 목록은 예외 없이 `lists()` → `list(params)`, 상세는 `details()` → `detail(id)`
- 중간 계층이 있어야 부분 무효화가 된다

```ts
invalidateQueries({ queryKey: queryKeys.agents.lists() }) // 목록만
invalidateQueries({ queryKey: queryKeys.agents.all }) // 도메인 전체
```

- 서버가 받는 query parameter 를 그대로 키에 넣는다. `undefined` 필드는 TanStack Query 의 해시에서 자동으로 빠진다

---

## 7. API 레이어

### 요청은 `api/<도메인>.ts` 의 함수로만

화면에서 URL 문자열을 쓰지 않는다. 엔드포인트 하나당 함수 하나를 export 한다. 경로가 바뀌어도 고칠 곳이 하나다.

```ts
// api/auth.ts
export const getMyProfile = () => api.get<UserProfile>('/users/me')
```

### 응답은 봉투가 벗겨진 채로 온다

서버는 `{ status, data }` 로 감싸 준다. 응답 인터셉터가 `data` 만 넘기므로 화면은 `res.data.data` 를 몰라도 된다.

### 오류는 `ApiError` 로 정규화된다

HTTP status 대신 **`code` 로 분기한다.** 400 하나에도 `COMMON_001`(입력값 누락)과 `AGENT_003`(visibility 값 오류)이 섞여 있어 status 만으로는 무엇이 잘못됐는지 알 수 없다.

```ts
if (e instanceof ApiError && e.code === 'AGENT_003') …
if (e instanceof ApiError && e.isNetworkError) …   // 응답 자체를 못 받음
```

### 토큰

- Access Token 은 **메모리에만** 둔다 (`lib/tokenStorage.ts`). localStorage 는 XSS 로 탈취된다
- Refresh Token 은 HttpOnly 쿠키다. JS 는 값을 알 수도 없고 알 필요도 없다
- 401 중 `AUTH_007`(Access Token 만료)만 재발급 대상이다. 나머지는 재발급해도 결과가 같다
- 재발급은 **동시에 두 번 나가면 안 된다.** 서버가 이전 Refresh Token 을 폐기하므로 두 번째 요청은 `AUTH_004` 가 되고 세션이 끝난다. `client.ts` 가 진행 중인 재발급을 공유한다

### `Content-Type` 을 인스턴스 기본값으로 박지 않는다

axios 가 본문 타입을 보고 알아서 붙인다. `application/json` 을 고정해두면 FormData 를 넘겼을 때 axios 가 그것을 **JSON 문자열로 바꿔** 보낸다 — 요청은 200 으로 성공하고 파일만 사라진다.

---

## 8. Tailwind

### 반복되는 것만 Tailwind, 1회성 좌표는 인라인

이 프로젝트 화면은 1440×1024 고정 좌표다. 좌표를 Tailwind 임의값으로 옮기면 길어지기만 하고 재사용 이득이 없다.

```tsx
// ○ 반복되는 것 = Tailwind
className="flex items-center bg-accent text-accent-ink hover:bg-white/16"

// ○ 화면마다 다른 좌표 = 인라인 style
style={{ left: '504px', top: '322px', width: '432px' }}
```

기준: **2회 이상 나오는 선언은 Tailwind, 1회성 수치는 인라인.**

### 색·그림자·반경은 토큰으로

`index.css` 의 `@theme` 에 정의하고 클래스로 쓴다. 값을 직접 박지 않는다.

```tsx
className="bg-surface-card text-muted shadow-menu rounded-card"   // ○
style={{ background: 'rgba(255,255,255,.08)' }}                    // △ 토큰에 있으면 쓰지 않는다
```

### 기본 스타일은 반드시 `@layer base` 안에

Tailwind v4 는 유틸리티를 `@layer utilities` 에 넣는다. **레이어 밖 CSS 가 레이어 안 CSS 를 이긴다.**

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

### 클래스 정렬은 Prettier 에 맡긴다

`prettier-plugin-tailwindcss` 를 설치하면 저장할 때 레이아웃 → 여백 → 색 순으로 자동 정렬된다. 손으로 맞추지 않는다.

### 조건부 클래스

지금은 조건부 스타일이 세 곳 미만이라 삼항 연산자로 충분하다.

```tsx
className={`${TAB} ${isActive ? 'bg-white/90 text-accent-ink' : 'bg-white/9'}`}
```

**조건부가 다섯 곳을 넘으면** `clsx` + `tailwind-merge` 로 만든 `cn()` 유틸리티를 도입한다. 그 전에는 의존성을 늘리지 않는다.

---

## 9. 하지 말 것 — 이 프로젝트에서 실제로 터진 것들

### 유리 패널의 조상에 `transform` · `opacity` 를 걸지 않는다

`backdrop-filter` 는 **backdrop 루트** 안쪽만 샘플링한다. `transform`·`opacity`·`filter` 가 걸린 요소는 그 루트가 되므로, 그 안의 유리 패널은 **바깥의 배경을 못 가져와 검게 보인다.**

```tsx
// ✗ 등장 모션이 도는 동안 안쪽 유리 패널이 전부 납작해진다
@keyframes screenIn { from { transform: translateY(22px) } }

// ○ top 은 backdrop 루트를 만들지 않는다
@keyframes screenIn { from { top: 22px } }
```

### 커스텀 엘리먼트는 shadow DOM 에 그린다

웹 컴포넌트가 light DOM 에 직접 쓰면, React 가 모르는 자식이 생겨 **리렌더 때 `removeChild` 에서 터진다.**

```ts
protected override usesShadow = true;   // BaseElement 가 shadowRoot 를 만들어 준다
this.root.innerHTML = …                 // this.innerHTML (✗)
```

shadow 안에는 문서의 `@keyframes` 가 닿지 않으므로 `addStyle()` 로 함께 넣는다.

### 커스텀 엘리먼트 속성명에 DOM 메서드명을 쓰지 않는다

React 19 는 해당 프로퍼티가 엘리먼트에 있으면 **속성이 아니라 프로퍼티로 넣는다.** `blur` 는 `HTMLElement.blur()` 와 겹쳐 속성이 아예 붙지 않는다.

```tsx
<ocean-bg blur={5} />               // ✗ 속성이 안 붙는다
<ocean-bg {...{ 'blur-px': 5 }} />  // ○
```

### shadow DOM 을 넘는 이벤트는 `composedPath()` 로 잡는다

shadow 경계를 넘으면 `e.target` 이 호스트로 바뀐다. `closest('a')` 가 못 찾는다.

```ts
const a = e.composedPath().find((n): n is HTMLAnchorElement => n instanceof HTMLAnchorElement)
```

### `value` 를 넘길 때는 `onChange` 를 같이 준다

`value` 만 주면 React 가 제어 컴포넌트로 보고 **읽기 전용**이 된다. 초기값만 채울 때는 `defaultValue`.

```tsx
<input value="김선미" />          // ✗ 입력이 안 된다
<input defaultValue="김선미" />   // ○
```

### 화면 이동은 라우터로

`<a href="https://…">` 처럼 외부 주소를 쓰면 **전체 페이지가 재로드**되어 배경이 깜빡인다. `App.tsx` 의 전역 클릭 인터셉터가 `/` 로 시작하는 내부 링크를 클라이언트 라우팅으로 넘기고 있으므로, 내부 이동은 `paths` 가 만든 주소만 쓴다 (4절).

---

## 10. 커밋 전 확인

```bash
npm run build      # tsc -b && vite build — 타입 에러 0
npm run lint
npm run format
```
