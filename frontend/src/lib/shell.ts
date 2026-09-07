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
}

const BY_ROUTE: Record<string, Partial<Shell>> = {
  '/': { sunHeight: 0.45, menu: true, studio: true },
  '/login': { sunHeight: 0.5, dim: 0.1 },
  '/signup': { sunHeight: 0.5, dim: 0.12, header: false },
  '/register': { sunHeight: 0.5, dim: 0.5, blur: 6, header: false, studio: true },
  '/search': { dim: 0.26, menu: true, studio: true },
  '/agent': { dim: 0.34, menu: true, studio: true },
  '/edit/searching': { dim: 0.34, blur: 3 },
  '/edit/nodes': { dim: 0.48, blur: 5 },
  '/edit/nodes-seller': { dim: 0.48, blur: 5 },
  '/run/waiting': { dim: 0.52, blur: 6 },
  '/run/running': { dim: 0.52, blur: 6 },
  '/my/agents': { dim: 0.44, blur: 4, menu: true, studio: true },
  '/my/edit': { dim: 0.46, blur: 5, menu: true, studio: true },
  '/my/usage': { dim: 0.46, blur: 5, menu: true, studio: true },
  '/my/sales': { dim: 0.46, blur: 5, menu: true, studio: true },
  '/my/credit': { dim: 0.46, blur: 5, menu: true, studio: true },
}

export function shellFor(pathname: string): Shell {
  return { ...BASE, ...BY_ROUTE[pathname] }
}
