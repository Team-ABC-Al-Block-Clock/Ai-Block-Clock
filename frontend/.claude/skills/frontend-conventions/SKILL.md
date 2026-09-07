---
name: frontend-conventions
description: ABC Agent Studio 프론트엔드(React 19 + TypeScript + Vite + Tailwind v4) 코드를 쓰거나 고칠 때 반드시 먼저 읽는다. 컴포넌트·훅·타입 네이밍, 파일 배치, Tailwind 사용 경계, 그리고 이 프로젝트에서 실제로 터진 함정(backdrop-filter·shadow DOM·커스텀 엘리먼트 속성)을 담고 있다. frontend/ 안의 .tsx·.ts·.css 를 만들거나 수정할 때 사용.
---

# 프론트엔드 컨벤션

**규칙 원본은 `frontend/docs/frontend-conventions.md` 다.**
프론트엔드 코드를 쓰기 전에 그 파일을 읽고 따를 것. 이 스킬은 원본을 대체하지 않는다.

```
frontend/docs/frontend-conventions.md
```

## 자주 틀리는 것 — 코드 쓰기 전 확인

원본에 전부 설명돼 있으나, 아래 다섯 개는 지키지 않으면 **동작이 깨지거나 화면이 잘못 그려진다.** 겪은 적 있는 실제 버그다.

1. **유리 패널 조상에 `transform`·`opacity` 금지**
   `backdrop-filter` 가 backdrop 루트 안쪽만 샘플링해서, 그 안 유리 패널이 배경을 못 가져와 검게 보인다.
   등장 모션은 `transform` 이 아니라 `top` 으로 움직인다.

2. **커스텀 엘리먼트는 shadow DOM 에 그린다** (`usesShadow = true`, `this.root.innerHTML`)
   light DOM 에 직접 쓰면 React 리렌더 때 `removeChild` 에서 터진다.
   shadow 안에는 문서 `@keyframes` 가 안 닿으니 `addStyle()` 로 함께 넣는다.

3. **커스텀 엘리먼트 속성명에 DOM 메서드명 금지**
   `blur` 는 `HTMLElement.blur()` 와 겹쳐 React 19 가 속성 대신 프로퍼티로 넣는다 → 속성이 안 붙는다.
   `blur-px` 처럼 이름을 피한다.

4. **기본 스타일은 `@layer base` 안에**
   Tailwind v4 는 유틸리티를 `@layer utilities` 에 넣는다. 레이어 밖 CSS 가 이겨서
   `a { color: inherit }` 하나로 모든 `<a>` 의 `text-*` 가 무력화된다.

5. **좌표는 인라인 `style`, 반복되는 것만 Tailwind**
   화면이 1440×1024 고정 좌표다. `left-[504px]` 로 옮겨도 이득이 없다.
   기준은 2회 이상 나오는 선언만 Tailwind.

## 네이밍 요약

- 컴포넌트 `.tsx` PascalCase · 훅·유틸 `.ts` camelCase
- **웹 컴포넌트 `.ts` 는 kebab-case** (HTML 표준이 태그명에 하이픈을 요구) — `ocean-bg.ts` → `<ocean-bg>`
- 훅 `use*` · 핸들러 `handle*`(내부) / `on*`(props) · Props 타입 `컴포넌트명Props`
- 인터페이스에 `I` 접두사 금지

## 구조

`App.tsx` 가 껍데기(`screen-frame`·`ocean-bg`·`app-header`·`profile-menu`)를 들고,
화면(`pages/*.tsx`)은 자기 내용만 그린다. 라우트별 설정은 `lib/shell.ts` 한 곳.
