import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactElement,
  type RefObject,
} from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import logo from './assets/logo.png'
import ChainBanner from './components/ChainBanner'
import { ErrorBoundary } from './components/ErrorBoundary'
import OnboardingGate from './components/OnboardingGate'
import ProtectedRoute from './components/ProtectedRoute'
import { BANNER_HEIGHT, useChainBanner } from './hooks/useChainBanner'
import { useSessionRestore } from './hooks/useSessionRestore'
import { shellFor } from './lib/shell'
import { REQUIRES_AUTH } from './routes/access'
import { paths, routePatterns, type ScreenKey } from './routes/paths'
import { useAccount, useDisconnect } from 'wagmi'
import { useAuthStore } from './stores/authStore'
import './components/index'

import Detail from './pages/Detail'
import EditNodes from './pages/EditNodes'
import EditSearching from './pages/EditSearching'
import Login from './pages/Login'
import Main from './pages/Main'
import MyAgents from './pages/MyAgents'
import MyPageCredit from './pages/MyPageCredit'
import MyPageEdit from './pages/MyPageEdit'
import MyPageSales from './pages/MyPageSales'
import MyPageUsage from './pages/MyPageUsage'
import NotFound from './pages/NotFound'
import RegisterForm from './pages/RegisterForm'
import RunRunning from './pages/RunRunning'
import RunWaiting from './pages/RunWaiting'
import Search from './pages/Search'
import Signup from './pages/Signup'

/** 화면 안 링크는 전부 평범한 <a href="/...">. 하나하나 <Link>로 바꾸는 대신
 *  루트에서 한 번 가로채 클라이언트 라우팅으로 넘긴다 — 전체 재로드가 사라진다.
 *
 *  문서 전역 클릭 리스너 하나. 링크마다 <Link>가 필요해지면 그때 바꾸세요.
 */
function useInternalLinks() {
  const navigate = useNavigate()
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey)
        return
      // shadow DOM 안의 링크(profile-menu)는 e.target 이 호스트로 바뀐다 —
      // composedPath 로 경계를 넘어 실제 <a> 를 찾는다.
      const a = e.composedPath().find((n): n is HTMLAnchorElement => n instanceof HTMLAnchorElement)
      if (!a) return
      const href = a.getAttribute('href')
      if (!href?.startsWith('/') || a.target === '_blank' || a.hasAttribute('download')) return
      e.preventDefault()
      navigate(href)
    }
    document.addEventListener('click', onClick)
    return () => document.removeEventListener('click', onClick)
  }, [navigate])
}

/** `<profile-menu>` 의 로그아웃 행이 던지는 이벤트를 받는다. 메뉴는 shadow DOM 안이라
 *  스토어를 못 읽으니 이벤트만 던지고, 세션과 이동은 여기서 정한다. */
function useLogout() {
  const navigate = useNavigate()
  const { disconnectAsync } = useDisconnect()

  useEffect(() => {
    const onLogout = () => {
      // 스스로 누른 로그아웃은 지갑 연결까지 끊는다. 세션 만료(401)로 끊길 때는
      // 지갑을 그대로 두어 다시 연결할 필요 없이 로그인만 하면 되게 한다.
      //
      // 세션을 먼저 끊는다 — 지갑을 먼저 끊으면 아래 가드가 "연결이 해제됐다" 로
      // 읽어, 본인이 누른 로그아웃에 엉뚱한 안내가 붙는다.
      void useAuthStore
        .getState()
        .signOut()
        .catch(() => undefined)
        .then(() => disconnectAsync().catch(() => undefined))
        .finally(() => navigate(paths.home, { replace: true }))
    }
    document.addEventListener('logout', onLogout)
    return () => document.removeEventListener('logout', onLogout)
  }, [navigate, disconnectAsync])
}

/** 세션의 근거는 지갑이다 — 계정이 바뀌거나 연결이 끊기면 서버 쿠키까지 끊는다(7절).
 *
 *  연결 해제는 `connected` 를 본 적이 있을 때만 센다. 부팅 직후 wagmi 재연결 중에
 *  끊으면 쿠키로 되살린 세션이 매번 날아간다. */
function useWalletSessionGuard() {
  const { address, status } = useAccount()
  const sawConnected = useRef(false)

  useEffect(() => {
    const session = useAuthStore.getState()

    if (status === 'connected' && address) {
      sawConnected.current = true
      if (session.status !== 'authenticated' || !session.walletAddress) return
      if (session.walletAddress.toLowerCase() === address.toLowerCase()) return
      void session.signOut('account').catch(() => undefined)
      return
    }

    if (status === 'disconnected' && sawConnected.current) {
      sawConnected.current = false
      if (session.status === 'authenticated') {
        void session.signOut('disconnected').catch(() => undefined)
      }
    }
  }, [address, status])
}

/** 껍데기를 놓을 창 크기 — 설계 단위로 환산해 돌려준다(3절).
 *
 *  배율은 `screen-frame` 의 `framefit` 에서 받는다. 같은 계산을 두 벌 두면
 *  리사이즈 중 한쪽만 갱신돼 껍데기가 화면과 어긋난다. */
function useShellBox(frame: RefObject<HTMLElement | null>) {
  const [box, setBox] = useState({ scale: 1, width: 1440, height: 1024 })

  // 레이아웃 단계에서 재야 첫 프레임에 배율 1 로 한 번 커졌다 줄지 않는다.
  useLayoutEffect(() => {
    const el = frame.current as (HTMLElement & { scale: number }) | null
    if (!el) return
    const sync = () => {
      const scale = el.scale || 1
      setBox({ scale, width: window.innerWidth / scale, height: window.innerHeight / scale })
    }
    sync()
    el.addEventListener('framefit', sync)
    return () => el.removeEventListener('framefit', sync)
  }, [frame])

  return box
}

/** 우상단 붙임 여백 — 시안의 1440 기준 좌표에서 뽑은 값(아바타 18px, 알약 78px). */
const MENU_RIGHT = 18 + 48
const STUDIO_RIGHT = 78 + 171

/** 화면 키 → 엔트리.
 *  Record<ScreenKey, …> 라서 화면을 추가하면 여기를 채울 때까지 컴파일이 실패한다. */
const SCREENS: Record<ScreenKey, ReactElement> = {
  home: <Main />,
  login: <Login />,
  signup: <Signup />,
  register: <RegisterForm />,
  search: <Search />,
  agentDetail: <Detail />,
  editSearching: <EditSearching />,
  editNodes: <EditNodes />,
  runWaiting: <RunWaiting />,
  runRunning: <RunRunning />,
  myAgents: <MyAgents />,
  myEdit: <MyPageEdit />,
  myUsage: <MyPageUsage />,
  mySales: <MyPageSales />,
  myCredit: <MyPageCredit />,
}

const SCREEN_KEYS = Object.keys(routePatterns) as ScreenKey[]

export default function App() {
  useInternalLinks()
  useSessionRestore()
  useLogout()
  useWalletSessionGuard()

  // 프로필 메뉴의 아바타와 로그아웃 행만 세션을 본다 — 로그아웃한 사람에게
  // 다른 사람의 이니셜과 로그아웃을 보여줄 수는 없다. 나머지 껍데기는 그대로다.
  const signedIn = useAuthStore((s) => s.status === 'authenticated')
  const banner = useChainBanner()
  const nickname = useAuthStore((s) => s.profile?.nickname)

  const { pathname } = useLocation()
  const shell = shellFor(pathname)

  const frameRef = useRef<HTMLElement>(null)
  const shellBox = useShellBox(frameRef)

  return (
    <>
      {/* 배경은 캔버스가 아니라 창 전체를 채운다 — 캔버스 안에 두면 여백이 어두운 띠로 남는다(3절).
          라우트 밖에 한 번만 — key 를 바꾸면 리마운트되어 WebGL 컨텍스트가 늘어난다. */}
      <div className="fixed inset-0 overflow-hidden">
        <ocean-bg
          wave-intensity={shell.waveIntensity}
          sun-height={shell.sunHeight}
          motion-speed={shell.motionSpeed}
          dim={shell.dim}
          {...{ 'blur-px': shell.blur }}
        />

        {/* 화면별 어둡기도 창 전체에 — 캔버스 안에만 깔면 여백과의 경계가 선으로 보인다 */}
        {shell.vignette && (
          <div
            className="pointer-events-none absolute inset-0"
            style={{ background: shell.vignette }}
          />
        )}
      </div>

      {/* bg-abyss 를 얹지 않는다 — 배경을 가린다. `--edge` 는 캔버스와 창 사이 한쪽 여백.
          overflow 는 늘 명시한다: 빼면 React 가 값을 지워 이후 화면이 잘리지 않는다(3절). */}
      <screen-frame
        width={1440}
        height={1024}
        ref={frameRef}
        style={
          {
            overflow: shell.bleed ? 'visible' : 'hidden',
            '--edge': `${Math.max(0, (shellBox.width - 1440) / 2)}px`,
            '--edge-y': `${Math.max(0, (shellBox.height - 1024) / 2)}px`,
          } as CSSProperties
        }
      >
        {/* 띠가 뜨면 앞쪽 내용만 밀린다 — 배경까지 밀면 수평선이 튄다 */}
        <div
          className="absolute inset-x-0 bottom-0"
          style={{
            top: banner.visible ? BANNER_HEIGHT : 0,
            transition: 'top .34s cubic-bezier(.2,.8,.25,1)',
          }}
        >
          {/* 화면 내용만 갈아끼운다 — key 로 진입 모션을 매번 다시 태운다 */}
          <div key={pathname} className="animate-screen-in absolute inset-0">
            <ErrorBoundary resetKey={pathname}>
              {/* 가입을 마치지 않은 계정은 어느 화면으로도 새지 않는다 */}
              <OnboardingGate>
                <Routes>
                  {/* 인증이 필요한 화면은 ProtectedRoute 로 감싼다 — 어느 화면인지는 routes/access.ts */}
                  {SCREEN_KEYS.map((key) =>
                    routePatterns[key].map((path) => (
                      <Route
                        key={path}
                        path={path}
                        element={
                          REQUIRES_AUTH[key] ? (
                            <ProtectedRoute>{SCREENS[key]}</ProtectedRoute>
                          ) : (
                            SCREENS[key]
                          )
                        }
                      />
                    )),
                  )}

                  {/* 어느 것에도 걸리지 않은 경로 — 없으면 배경만 남은 빈 화면이 된다 */}
                  <Route path="*" element={<NotFound />} />
                </Routes>
              </OnboardingGate>
            </ErrorBoundary>
          </div>
        </div>
      </screen-frame>

      {/* 껍데기는 창 모서리에 붙인다 — 라우트가 바뀌어도 그대로 있고, 캔버스가 아니라
          창 크기를 따른다(useShellBox). 배율은 화면 내용과 같다.
          레이어 자체는 클릭을 통과시키고 껍데기 조각만 받는다. */}
      <div
        className="pointer-events-none fixed top-0 left-0"
        style={{
          width: `${shellBox.width}px`,
          height: `${shellBox.height}px`,
          transform: `scale(${shellBox.scale})`,
          transformOrigin: 'top left',
          zIndex: 40,
        }}
      >
        <ChainBanner {...banner} />

        {/* 띠가 뜨면 껍데기도 같이 내려간다 — 화면 내용과 같은 만큼 */}
        <div
          className="absolute inset-x-0"
          style={{
            top: banner.visible ? BANNER_HEIGHT : 0,
            transition: 'top .34s cubic-bezier(.2,.8,.25,1)',
          }}
        >
          {/* 껍데기 — 화면 내용과 달리 라우트가 바뀌어도 그대로 붙어 있습니다 */}
          {shell.header && (
            <app-header
              className="pointer-events-auto"
              home-href={paths.home}
              logo-src={logo}
            ></app-header>
          )}
          {/* My Agent Studio — 아바타(1374,26,48×48) 왼쪽 12px. 알약 크기는 와이어프레임 그대로,
        색·그림자·hover 는 이 앱의 primary 버튼(실행하기 등)과 같은 accent 계열로. */}
          {shell.studio && (
            <a
              href={paths.edit.nodes.withoutId}
              className="group bg-accent text-accent-ink pointer-events-auto absolute box-border flex cursor-pointer items-center justify-center text-[14px] font-semibold tracking-[-.01em] whitespace-nowrap transition-transform duration-[180ms] hover:-translate-y-[2px]"
              style={{
                left: `${shellBox.width - STUDIO_RIGHT}px`,
                top: '30px',
                width: '171px',
                height: '41px',
                padding: '0 20px',
                borderRadius: '100px',
                gap: '8px',
                boxShadow: '0 10px 26px rgba(140,100,130,.24)',
                zIndex: 40,
              }}
            >
              <span className="text-[17px] leading-none">+</span>
              <span>My Agent Studio</span>

              {/* 버튼 아래 10px, 버튼 중심 정렬. 색·유리효과는 앱의 기존 팝오버와 동일 */}
              <div
                className="glass-card absolute left-1/2 box-border hidden flex-col items-center text-center whitespace-normal group-hover:flex"
                style={{
                  top: 'calc(100% + 10px)',
                  transform: 'translateX(-50%)',
                  width: '268px',
                  padding: '12px 14px',
                  gap: '6px',
                  borderRadius: '10px',
                  background: 'rgba(20,11,36,.93)',
                  boxShadow: '0 10px 34px rgba(10,4,20,.34)',
                }}
              >
                <div className="text-[13px] leading-[1.4] font-semibold tracking-[-.01em] text-white">
                  나만의 에이전트를 만들고, 판매해보세요
                </div>
              </div>
            </a>
          )}
          {shell.menu && (
            <profile-menu
              className="pointer-events-auto absolute"
              style={{ left: `${shellBox.width - MENU_RIGHT}px`, top: '26px', zIndex: '40' }}
              initial={nickname?.slice(0, 1) ?? ''}
              search-href={paths.search}
              mypage-href={paths.my.agents}
              {...(signedIn ? { 'signed-in': '' } : {})}
            ></profile-menu>
          )}
        </div>
      </div>
    </>
  )
}
