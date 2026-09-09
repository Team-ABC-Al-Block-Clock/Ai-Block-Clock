import { useEffect, type ReactElement } from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import logo from './assets/logo.png'
import { ErrorBoundary } from './components/ErrorBoundary'
import ProtectedRoute from './components/ProtectedRoute'
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
 *  ponytail: 문서 전역 클릭 리스너 하나. 링크마다 <Link>가 필요해지면 그때 바꾸세요.
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

/** `<profile-menu>` 의 로그아웃 행이 던지는 이벤트를 받는다.
 *
 *  커스텀 엘리먼트는 shadow DOM 안에 있어 스토어를 읽을 수 없다. 메뉴는 이벤트만
 *  던지고, 세션을 끊고 어디로 보낼지는 라우터를 아는 이쪽이 정한다.
 */
function useLogout() {
  const navigate = useNavigate()
  const { disconnectAsync } = useDisconnect()

  useEffect(() => {
    const onLogout = () => {
      // 스스로 누른 로그아웃은 지갑 연결까지 끊는다. 세션 만료(401)로 끊길 때는
      // 지갑을 그대로 두어 다시 연결할 필요 없이 로그인만 하면 되게 한다.
      void disconnectAsync()
        .catch(() => undefined)
        .then(() => useAuthStore.getState().signOut())
        .catch(() => undefined)
        .finally(() => navigate(paths.home, { replace: true }))
    }
    document.addEventListener('logout', onLogout)
    return () => document.removeEventListener('logout', onLogout)
  }, [navigate, disconnectAsync])
}

/** 지갑 계정이 바뀌면 열려 있던 세션은 남의 것이 된다 — 끊는다.
 *
 *  `connected` 일 때만 본다. 부팅 직후에는 wagmi 가 아직 재연결 중이라
 *  주소가 없는데, 그때 끊으면 쿠키로 되살린 세션이 매번 날아간다.
 *  지갑을 사용자가 끊은 것(`disconnected`)만으로는 세션을 끝내지 않는다 —
 *  서버 세션은 살아 있고, 끝내려면 로그아웃을 누르면 된다.
 */
function useWalletAccountGuard() {
  const { address, status } = useAccount()

  useEffect(() => {
    if (status !== 'connected' || !address) return
    const session = useAuthStore.getState()
    if (session.status !== 'authenticated' || !session.walletAddress) return
    if (session.walletAddress.toLowerCase() === address.toLowerCase()) return
    void session.signOut().catch(() => undefined)
  }, [address, status])
}

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
  editNodesSeller: <EditNodes seller />,
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
  useWalletAccountGuard()

  // 프로필 메뉴의 아바타와 로그아웃 행만 세션을 본다 — 로그아웃한 사람에게
  // 남의 이니셜과 로그아웃을 보여줄 수는 없다. 나머지 껍데기는 그대로다.
  const signedIn = useAuthStore((s) => s.status === 'authenticated')
  const nickname = useAuthStore((s) => s.profile?.nickname)

  const { pathname } = useLocation()
  const shell = shellFor(pathname)

  return (
    <screen-frame width={1440} height={1024} className="bg-abyss">
      {/* 배경은 라우트 밖에 한 번만 — key 를 바꾸면 리마운트되어 WebGL 컨텍스트가 늘어난다.
          화면을 옮겨도 같은 캔버스를 유지하고 dim·blur 값만 갈아끼운다. */}
      <div className="absolute inset-0 overflow-hidden">
        <ocean-bg
          wave-intensity={shell.waveIntensity}
          sun-height={shell.sunHeight}
          motion-speed={shell.motionSpeed}
          dim={shell.dim}
          {...{ 'blur-px': shell.blur }}
        />
      </div>

      {/* 화면 내용만 갈아끼운다 — key 로 진입 모션을 매번 다시 태운다 */}
      <div key={pathname} className="animate-screen-in absolute inset-0">
        <ErrorBoundary resetKey={pathname}>
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
        </ErrorBoundary>
      </div>

      {/* 껍데기 — 화면 내용과 달리 라우트가 바뀌어도 그대로 붙어 있습니다 */}
      {shell.header && <app-header home-href={paths.home} logo-src={logo}></app-header>}
      {/* My Agent Studio — 아바타(1374,26,48×48) 왼쪽 12px. 알약 크기는 와이어프레임 그대로,
          색·그림자·hover 는 이 앱의 primary 버튼(실행하기 등)과 같은 accent 계열로. */}
      {shell.studio && (
        <a
          href={paths.edit.nodesSeller.withoutId}
          className="group bg-accent text-accent-ink absolute box-border flex cursor-pointer items-center justify-center text-[14px] font-semibold tracking-[-.01em] whitespace-nowrap transition-transform duration-[180ms] hover:-translate-y-[2px]"
          style={{
            left: '1191px',
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
              나만의 에이전트를 만들어 팔아보세요
            </div>
            <div className="text-[12px] leading-[18px]" style={{ color: 'rgba(255,236,246,.6)' }}>
              내가 등록한 에이전트만 조합해 판매용 에이전트를 만들 수 있어요
            </div>
          </div>
        </a>
      )}
      {shell.menu && (
        <profile-menu
          className="absolute"
          style={{ left: '1374px', top: '26px', zIndex: '40' }}
          initial={nickname?.slice(0, 1) ?? ''}
          search-href={paths.search}
          mypage-href={paths.my.agents}
          {...(signedIn ? { 'signed-in': '' } : {})}
        ></profile-menu>
      )}
    </screen-frame>
  )
}
