import { useWalletStatus } from '@/hooks/useWalletStatus'
import { shortAddress } from '@/lib/format'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'
import StatusDot from '@/components/StatusDot'

const TABS = [
  { key: 'agents', label: '내 에이전트', href: paths.my.agents },
  { key: 'usage', label: '사용 내역', href: paths.my.usage },
  { key: 'sales', label: '판매 내역', href: paths.my.sales },
  { key: 'credit', label: '예치/출금', href: paths.my.credit },
] as const

const TAB =
  'flex items-center justify-center text-[14.5px] font-semibold whitespace-nowrap tracking-[-.01em]'
const BOX = { width: '126px', height: '38px', borderRadius: '11px' }

type Tab = (typeof TABS)[number]['key']

/**
 * 마이페이지 좌측 패널. 화면마다 다른 것은 선택 상태뿐이다.
 * 사용자 정보는 인증 스토어에서 읽는다 — 보호 라우트라 프로필은 이미 있다.
 */
interface SidebarProps {
  /** 채워질 탭 */
  active?: Tab
  /** 정보 수정 화면이면 탭 대신 수정 버튼이 채워집니다 */
  editing?: boolean
}

export default function Sidebar({ active, editing = false }: SidebarProps) {
  const profile = useAuthStore((s) => s.profile)
  const { walletAddress, network, connection } = useWalletStatus()
  // App 이 문서에서 받아 세션과 지갑 연결을 함께 끊는다. 프로필 메뉴와 같은 길이다.
  const handleLogout = () => document.dispatchEvent(new CustomEvent('logout'))

  return (
    <>
      <div
        className="absolute"
        style={{
          left: '62px',
          top: '160px',
          width: '338px',
          // 판매 내역 표 카드 바닥에 맞춘다. 다섯 화면이 같은 높이다.
          height: '835px',
          borderRadius: '20px',
          background: 'var(--color-surface-card)', // 오른쪽 카드와 같은 흰 유리 (원본은 rgba(18,9,32,.44) 어두운 패널)
          backdropFilter: 'blur(22px) saturate(118%)',
          WebkitBackdropFilter: 'blur(22px) saturate(118%)',
          animation: 'rise .8s cubic-bezier(.2,.8,.25,1) both',
        }}
      ></div>

      <div
        className="absolute overflow-hidden rounded-full bg-[rgba(255,255,255,.10)]"
        style={{
          left: '119px',
          top: '190px',
          width: '230px',
          height: '230px',
          boxShadow: '0 20px 48px rgba(8,4,18,.32)',
          animation:
            'floaty 7.4s ease-in-out infinite, rise .8s .08s cubic-bezier(.2,.8,.25,1) backwards',
        }}
      >
        <div
          className="absolute inset-0"
          style={{
            background:
              'radial-gradient(circle at 52% 28%, rgba(255,218,224,.18), rgba(255,214,232,0) 62%)',
          }}
        ></div>
        {profile?.profileImageUrl ? (
          // alt 는 비웁니다 — 바로 아래에 이름이 그대로 있어 읽어 주면 두 번 들립니다.
          <img
            src={profile.profileImageUrl}
            alt=""
            className="absolute inset-0 h-full w-full object-cover"
          />
        ) : (
          <div
            className="absolute inset-0 flex items-center justify-center text-[64px] font-semibold"
            style={{ color: 'rgba(255,222,238,.85)' }}
          >
            {profile?.nickname.slice(0, 1) ?? ''}
          </div>
        )}
      </div>

      <div
        className="absolute text-[36px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '102px',
          top: '452px',
          textShadow: '0 2px 18px rgba(35,8,45,.6)',
          animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {profile?.nickname}
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '102px',
          top: '502px',
          color: 'var(--color-muted)',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {profile?.introduction}
      </div>

      {editing ? (
        <div
          className="absolute flex items-center justify-center text-[15px] font-semibold tracking-[-.01em]"
          style={{
            left: '102px',
            top: '544px',
            width: '263px',
            height: '44px',
            borderRadius: '21px',
            background: 'rgba(255,255,255,.9)',
            color: '#3a1030',
            animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          내 정보 수정하기
        </div>
      ) : (
        <a
          href={paths.my.edit}
          style={{
            left: '102px',
            top: '544px',
            width: '263px',
            height: '44px',
            borderRadius: '21px',
            background: 'rgba(255,255,255,.11)',
            animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
          }}
          className="absolute flex items-center justify-center text-[15px] font-semibold tracking-[-.01em] text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)]"
        >
          내 정보 수정하기
        </a>
      )}

      <div
        className="absolute"
        style={{
          left: '102px',
          top: '618px',
          width: '263px',
          display: 'grid',
          gridTemplateColumns: '126px 126px',
          gap: '11px',
          animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {TABS.map((t) =>
          t.key === active ? (
            <div
              key={t.key}
              className={TAB}
              style={{ ...BOX, background: 'rgba(255,255,255,.9)', color: '#3a1030' }}
            >
              {t.label}
            </div>
          ) : (
            <a
              key={t.key}
              href={t.href}
              style={{ ...BOX, color: 'rgba(255,240,247,.78)' }}
              className={`bg-[rgba(255,255,255,.09)] transition-colors duration-[160ms] ${TAB} hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]`}
            >
              {t.label}
            </a>
          ),
        )}
      </div>

      <div
        className="absolute box-border bg-[rgba(255,255,255,.09)]"
        style={{
          left: '102px',
          top: '738px',
          width: '263px',
          height: '172px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .26s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[13px] font-semibold tracking-[-.01em] text-white">연결된 지갑</div>
        <div
          className="text-[16px] font-semibold text-white"
          style={{ marginTop: '12px', letterSpacing: '.01em' }}
        >
          {walletAddress ? shortAddress(walletAddress) : '—'}
        </div>
        <div className="text-[13.5px]" style={{ marginTop: '8px', color: 'rgba(255,236,246,.62)' }}>
          {network}
        </div>
        <div className="flex items-center" style={{ marginTop: '14px', gap: '8px' }}>
          <StatusDot size={8} color={connection.dot} />
          <div className="text-[13.5px] font-semibold" style={{ color: connection.color }}>
            {connection.text}
          </div>
        </div>
      </div>

      <button
        type="button"
        onClick={handleLogout}
        className="absolute flex cursor-pointer items-center text-[13.5px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(255,138,138,.12)]"
        style={{
          left: '90px',
          top: '937px',
          height: '36px',
          padding: '0 12px',
          gap: '8px',
          borderRadius: '10px',
          color: '#ff8a8a',
          animation: 'rise .7s .3s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {/* 프로필 메뉴와 같은 그림 — 열린 문에서 화살표가 나간다. */}
        <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
          <path d="M8 2H3a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h5" stroke="currentColor" strokeWidth="2" />
          <path d="M7 9h8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          <path
            d="M12.5 5.5 16 9l-3.5 3.5"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        로그아웃
      </button>
    </>
  )
}
