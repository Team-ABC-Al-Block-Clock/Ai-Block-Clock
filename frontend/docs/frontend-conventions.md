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
├─ lib/            프레임워크와 무관한 헬퍼·설정
├─ api/            서버 통신
├─ stores/         전역 상태
├─ hooks/          커스텀 훅
├─ styles/         전역 CSS
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

## 4. Tailwind

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

## 5. 하지 말 것 — 이 프로젝트에서 실제로 터진 것들

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

`<a href="/search">` 를 그냥 쓰면 **전체 페이지가 재로드**되어 배경이 깜빡인다. `App.tsx` 의 전역 클릭 인터셉터가 내부 링크를 클라이언트 라우팅으로 넘기고 있으므로, `href` 는 `/` 로 시작하는 경로만 쓴다.

---

## 6. 커밋 전 확인

```bash
npm run build      # tsc -b && vite build — 타입 에러 0
npm run lint
npm run format
```
