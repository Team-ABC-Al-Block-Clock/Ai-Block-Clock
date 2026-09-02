# Frontend

ABC Agent Studio 프론트엔드입니다.

## 요구 사항

- **Node.js 20.19+ 또는 22.12+** (Vite 8 요구 사항)
- npm 10+

```bash
node -v   # 버전 확인
```

## 시작하기

```bash
cd frontend
cp .env.example .env    # 환경변수 준비
npm install             # 의존성 설치
npm run dev             # 개발 서버 실행 → http://localhost:5173
```

> `.env` 는 저장소에 커밋되지 않습니다. 클론 후 위 명령으로 직접 생성해야 합니다.
> (없어도 기본값으로 동작하지만, 백엔드 주소를 바꾸려면 필요합니다.)

## 스크립트

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 실행 (HMR) |
| `npm run build` | 타입 검사 후 프로덕션 빌드 → `dist/` |
| `npm run preview` | 빌드 결과물을 로컬에서 확인 |
| `npm run lint` | ESLint 검사 |
| `npm run format` | Prettier 로 코드 포맷 적용 |
| `npm run format:check` | 포맷 위반 여부만 검사 (수정하지 않음) |

## 환경 변수

`VITE_` 접두사가 붙은 값만 클라이언트 코드에서 읽을 수 있습니다.
**이 값들은 빌드 결과물에 그대로 포함되므로 비밀값을 넣으면 안 됩니다.**

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `VITE_APP_NAME` | `ABC Agent Studio` | 애플리케이션 이름 |
| `VITE_API_BASE_URL` | `/api` | API 요청 기본 경로 |
| `VITE_API_PROXY_TARGET` | `http://localhost:8080` | 개발 서버 프록시가 바라볼 백엔드 주소 |

개인 설정값은 `.env.local` 에 작성합니다. `.local` 로 끝나는 파일은 커밋되지 않습니다.

## 백엔드 연동

개발 서버는 `/api` 로 시작하는 요청을 백엔드로 프록시합니다. 별도의 CORS 설정 없이 개발할 수 있습니다.

```
브라우저 → http://localhost:5173/api/users
         → http://localhost:8080/api/users   (프록시)
```

백엔드 포트가 8080 이 아니면 `.env` 의 `VITE_API_PROXY_TARGET` 을 변경하세요.

## 기술 스택

| 구분 | 라이브러리 |
| --- | --- |
| 빌드 도구 | Vite 8 |
| 프레임워크 | React 19 · TypeScript 6 |
| 라우팅 | React Router 7 |
| 서버 상태 | TanStack Query 5 |
| 전역 상태 | Zustand 5 |
| HTTP | Axios |
| 코드 품질 | ESLint 10 · Prettier 3 |

## 폴더 구조

```
frontend/
├─ public/              정적 파일 (빌드 시 그대로 복사)
├─ src/
│  ├─ assets/           이미지·폰트 등 번들에 포함되는 리소스
│  ├─ api/              axios 인스턴스 및 도메인별 API 함수
│  ├─ components/       재사용 컴포넌트
│  ├─ pages/            라우트 단위 페이지
│  ├─ hooks/            커스텀 훅
│  ├─ stores/           zustand 전역 상태
│  ├─ styles/           전역 스타일
│  ├─ App.tsx           루트 컴포넌트 (라우팅 정의)
│  ├─ main.tsx          진입점
│  └─ index.css         전역 기본 스타일
├─ .env.example         환경변수 템플릿
└─ vite.config.ts       Vite 설정 (프록시·플러그인)
```

## 코드 컨벤션

- Prettier 설정은 `package.json` 의 `prettier` 필드에 있습니다.
  (세미콜론 없음 · 작은따옴표 · 100자 · 들여쓰기 2칸)
- ESLint 는 문법과 버그를, Prettier 는 포맷을 담당합니다.
  `eslint-config-prettier` 로 두 도구가 충돌하지 않도록 설정되어 있습니다.
- 커밋 전 `npm run lint` 와 `npm run format` 을 실행해 주세요.
