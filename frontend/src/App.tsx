import { useEffect, type ReactElement } from 'react'
import { Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import logo from './assets/logo.png'
import { ErrorBoundary } from './components/ErrorBoundary'
import { shellFor } from './lib/shell'
import { paths, routePatterns, type ScreenKey } from './routes/paths'
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
            {SCREEN_KEYS.map((key) =>
              routePatterns[key].map((path) => (
                <Route key={path} path={path} element={SCREENS[key]} />
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
          initial="김"
          search-href={paths.search}
          mypage-href={paths.my.agents}
        ></profile-menu>
      )}
    </screen-frame>
  )
}
