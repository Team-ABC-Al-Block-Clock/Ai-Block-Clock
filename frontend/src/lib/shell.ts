import { matchPath } from 'react-router-dom'

import { routePatterns, type ScreenKey } from '@/routes/paths'

/** 라우트별 화면 껍데기 설정 — 배경·로고·프로필 메뉴.
 *
 *  배경은 라우트 밖(App)에 한 번만 만들어 두고 값만 바꿉니다.
 *  화면마다 <ocean-bg>를 새로 만들면 라우트가 바뀔 때 셰이더를 다시 컴파일하면서
 *  캔버스가 잠깐 비어 화면이 까맣게 보입니다.
 *  로고·프로필 메뉴도 여기 둡니다 — 화면 내용과 같이 올라오면 안 되니까요.
 */
export interface Shell {
  waveIntensity: number
  sunHeight: number
  motionSpeed: number
  dim: number
  blur: number
  /** 좌상단 로고 */
  header: boolean
  /** 우상단 프로필 메뉴 */
  menu: boolean
  /** 우상단 My Agent Studio 버튼 — 메뉴 없는 등록 화면에도 뜹니다 */
  studio: boolean
  /** 배경 위에 덮는 화면별 어둡기(CSS background). 빈 문자열이면 없음.
   *
   *  화면 안이 아니라 여기 두는 이유: 설계 캔버스(1440×1024)와 창 비율이 다르면
   *  좌우나 위아래에 여백이 남고, 어둡기를 캔버스 안에만 깔면 그 경계에 이음선이
   *  보인다. App 이 창 전체에 한 번 깔아 준다.
   *
   *  편집·실행 화면의 가로 그라디언트처럼 레이아웃에 묶인 것은 화면에 그대로 둔다 —
   *  창 폭으로 늘리면 어두워지는 지점이 패널과 어긋난다. */
  vignette: string
  /** 캔버스 밖으로 흘러나가도 되는 화면인가.
   *
   *  편집·실행 화면은 오른쪽 사이드바가 창 끝에 붙어야 한다. 캔버스(1440)에
   *  묶어 두면 여백만큼 안쪽에서 끊겨 떠 보인다. 이 값이 참이면 프레임이 자르지
   *  않고, 화면은 `var(--edge)`(캔버스와 창 사이 한쪽 여백)로 밖까지 늘린다. */
  bleed: boolean
}

const BASE: Shell = {
  waveIntensity: 1,
  sunHeight: 0.42,
  motionSpeed: 1,
  dim: 0,
  blur: 0,
  header: true,
  menu: false,
  studio: false,
  vignette: '',
  bleed: false,
}

/** BASE 위에 덮어쓰는 화면별 설정.
 *  Record<ScreenKey, …> 라서 화면을 추가하면 여기를 채울 때까지 컴파일이 실패합니다. */
const BY_SCREEN: Record<ScreenKey, Partial<Shell>> = {
  home: {
    sunHeight: 0.45,
    menu: true,
    studio: true,
    vignette: 'radial-gradient(120% 80% at 50% 42%, rgba(0,0,0,0) 35%, rgba(26,12,40,.34) 100%)',
  },
  // 로그인은 로고를 가운데에 직접 그린다(시안 5) — 좌상단 로고와 겹치지 않게 끈다
  login: {
    sunHeight: 0.5,
    dim: 0.1,
    header: false,
    vignette:
      'linear-gradient(180deg, rgba(20,9,34,.3) 0%, rgba(20,9,34,.12) 34%, rgba(20,9,34,.28) 100%)',
  },
  signup: {
    sunHeight: 0.5,
    dim: 0.12,
    header: false,
    vignette:
      'linear-gradient(180deg, rgba(20,9,34,.32) 0%, rgba(20,9,34,.14) 34%, rgba(20,9,34,.3) 100%)',
  },
  register: {
    sunHeight: 0.5,
    dim: 0.5,
    blur: 6,
    header: false,
    studio: true,
    vignette:
      'linear-gradient(180deg, rgba(18,8,32,.44) 0%, rgba(18,8,32,.22) 30%, rgba(18,8,32,.26) 100%)',
  },
  search: {
    dim: 0.26,
    menu: true,
    studio: true,
    // 두 겹을 한 값에 쌓는다 — 먼저 쓴 것이 위에 온다(화면에서 나중에 그려졌던 순서)
    vignette:
      'linear-gradient(180deg, rgba(22,10,36,.30) 0%, rgba(22,10,36,.16) 22%, rgba(22,10,36,.10) 55%, rgba(22,10,36,.20) 100%),' +
      'radial-gradient(130% 90% at 50% 44%, rgba(0,0,0,0) 46%, rgba(26,12,40,.22) 100%)',
  },
  agentDetail: {
    dim: 0.34,
    menu: true,
    studio: true,
    vignette:
      'linear-gradient(180deg, rgba(22,10,36,.34) 0%, rgba(22,10,36,.18) 24%, rgba(22,10,36,.14) 58%, rgba(22,10,36,.26) 100%)',
  },
  editSearching: { dim: 0.34, blur: 8, vignette: 'rgba(15,8,30,.48)', bleed: true },
  editNodes: { dim: 0.48, blur: 10, vignette: 'rgba(13,8,28,.58)', bleed: true },
  editNodesSeller: { dim: 0.48, blur: 10, vignette: 'rgba(13,8,28,.58)', bleed: true },
  runWaiting: {
    dim: 0.52,
    blur: 11,
    vignette: 'linear-gradient(160deg, rgba(16,9,32,.52), rgba(10,6,24,.68))',
    bleed: true,
  },
  runRunning: {
    dim: 0.52,
    blur: 11,
    vignette: 'linear-gradient(160deg, rgba(16,9,32,.52), rgba(10,6,24,.68))',
    bleed: true,
  },
  myAgents: {
    dim: 0.44,
    blur: 4,
    menu: true,
    studio: true,
    vignette:
      'linear-gradient(180deg, rgba(20,9,34,.40) 0%, rgba(20,9,34,.22) 26%, rgba(20,9,34,.18) 62%, rgba(20,9,34,.30) 100%)',
  },
  myEdit: {
    dim: 0.46,
    blur: 5,
    menu: true,
    studio: true,
    vignette:
      'linear-gradient(180deg, rgba(20,9,34,.42) 0%, rgba(20,9,34,.22) 30%, rgba(20,9,34,.30) 100%)',
  },
  myUsage: {
    dim: 0.46,
    blur: 5,
    menu: true,
    studio: true,
    vignette:
      'linear-gradient(180deg, rgba(20,9,34,.42) 0%, rgba(20,9,34,.22) 30%, rgba(20,9,34,.30) 100%)',
  },
  mySales: {
    dim: 0.46,
    blur: 5,
    menu: true,
    studio: true,
    vignette:
      'linear-gradient(180deg, rgba(20,9,34,.42) 0%, rgba(20,9,34,.22) 30%, rgba(20,9,34,.30) 100%)',
  },
  myCredit: {
    dim: 0.46,
    blur: 5,
    menu: true,
    studio: true,
    vignette:
      'linear-gradient(180deg, rgba(20,9,34,.42) 0%, rgba(20,9,34,.22) 30%, rgba(20,9,34,.30) 100%)',
  },
}

/** 편집·실행 화면의 작업면 채움은 `vignette` 로 창 전체에 깐다 — 1076 폭 판에만
 *  두면 캔버스 경계에 밝은 바다가 남아 세로선이 생긴다. 판이 갖고 있던
 *  `backdrop-filter: blur(6~7px)` 도 걷어내고 그만큼 `blur` 를 올렸다.
 *  오른쪽 사이드바(364)는 판으로 남긴다 — 그건 경계가 있어야 하는 UI 다. */

/** 404 — 배경을 눌러 카드가 읽히게 합니다. */
const FALLBACK: Partial<Shell> = { dim: 0.4, blur: 4 }

const SCREEN_KEYS = Object.keys(routePatterns) as ScreenKey[]

export function shellFor(pathname: string): Shell {
  const key = SCREEN_KEYS.find((k) => routePatterns[k].some((p) => matchPath(p, pathname)))
  return { ...BASE, ...(key ? BY_SCREEN[key] : FALLBACK) }
}
