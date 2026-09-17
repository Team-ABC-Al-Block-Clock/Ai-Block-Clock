import { matchPath } from 'react-router-dom'

import { routePatterns, type ScreenKey } from '@/routes/paths'

/**
 * 라우트별 화면 껍데기 설정 — 배경·로고·프로필 메뉴.
 *
 * 배경은 라우트 밖에 한 번만 만들어 두고 값만 바꾼다. 화면마다 <ocean-bg> 를 새로
 * 만들면 라우트가 바뀔 때 셰이더를 다시 컴파일해 캔버스가 잠깐 까맣게 보이기 때문이다.
 * 로고·프로필 메뉴도 화면 내용과 같이 올라오지 않도록 포함한다.
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
  /** 우상단 My Agent Studio 버튼 */
  studio: boolean
  /** 화면별 어둡기(CSS background). 캔버스 안에 깔면 경계에 이음선이 보이므로 이 창 전체에 깔아준다. */
  vignette: string
  /** 프레임이 자르지 않고 `--edge` 를 내려주어 사이드바를 창 끝까지 밀어준다. */
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
 *  Record<ScreenKey, …> 라서 화면을 추가하면 여기를 채울 때까지 컴파일이 실패한다. */
const BY_SCREEN: Record<ScreenKey, Partial<Shell>> = {
  home: {
    sunHeight: 0.45,
    menu: true,
    studio: true,
    vignette: 'radial-gradient(120% 80% at 50% 42%, rgba(0,0,0,0) 35%, rgba(26,12,40,.34) 100%)',
  },
  // 로그인은 로고를 가운데에 직접 그린다. 이때, 좌상단 로고는 보이지 않게 숨긴다.
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
    // 폼이 길어 굴림 창을 창 끝까지 늘린다 — 프레임이 자르면 1024 에서 잘린다.
    bleed: true,
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

/** 편집·실행 화면: 작업면 채움을 `vignette` 로 올리고 패널의 `backdrop-filter` 만큼 `blur` 를 더해 캔버스 경계에 세로선이 보이지 않게 한다. */

/** 404 — 배경을 눌러 카드가 읽히게 한다. */
const FALLBACK: Partial<Shell> = { dim: 0.4, blur: 4 }

const SCREEN_KEYS = Object.keys(routePatterns) as ScreenKey[]

export function shellFor(pathname: string): Shell {
  const key = SCREEN_KEYS.find((k) => routePatterns[k].some((p) => matchPath(p, pathname)))
  return { ...BASE, ...(key ? BY_SCREEN[key] : FALLBACK) }
}
