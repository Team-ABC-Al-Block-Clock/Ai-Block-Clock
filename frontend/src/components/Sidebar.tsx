import { paths } from '@/routes/paths'

const TABS = [
  { key: 'agents', label: '내 에이전트', href: paths.my.agents },
  { key: 'usage', label: '사용 내역', href: paths.my.usage },
  { key: 'sales', label: '판매 내역', href: paths.my.sales },
  { key: 'credit', label: '코인 예치/출금', href: paths.my.credit },
] as const

const TAB =
  'flex items-center justify-center text-[12px] font-semibold whitespace-nowrap tracking-[-.01em]'
const BOX = { width: '126px', height: '38px', borderRadius: '11px' }

type Tab = (typeof TABS)[number]['key']

/** 마이페이지 좌측 패널 — 아바타·이름·수정 버튼·탭·지갑 카드.
 *  마이페이지 5개 화면에 똑같이 들어갑니다. 화면마다 다른 건 선택 상태뿐 —
 *  탭 하나(active)가 채워지거나, 정보 수정 화면이면 수정 버튼(editing)이 채워집니다.
 *
 *  ponytail: 사용자 정보가 아직 하드코딩입니다. `GET /users/me` 는 mock 핸들러가
 *  있으니(8절) 쿼리로 바꿔 주세요.
 */
interface SidebarProps {
  /** 채워질 탭 */
  active?: Tab
  /** 정보 수정 화면이면 탭 대신 수정 버튼이 채워집니다 */
  editing?: boolean
}

export default function Sidebar({ active, editing = false }: SidebarProps) {
  return (
    <>
      <div
        className="absolute"
        style={{
          left: '62px',
          top: '160px',
          width: '338px',
          height: '776px',
          borderRadius: '20px',
          background: 'rgba(255,255,255,.08)', // 오른쪽 카드와 같은 흰 유리 (원본은 rgba(18,9,32,.44) 어두운 판)
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
            'floaty 7.4s ease-in-out infinite, rise .8s .08s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div
          className="absolute inset-0"
          style={{
            background:
              'radial-gradient(circle at 52% 28%, rgba(255,218,224,.18), rgba(255,214,232,0) 62%)',
          }}
        ></div>
        <div
          className="absolute inset-0 flex items-center justify-center text-[64px] font-semibold"
          style={{ color: 'rgba(255,222,238,.85)' }}
        >
          김
        </div>
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
        이싸피
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '102px',
          top: '502px',
          color: 'rgba(255,236,246,.66)',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        나는척척박사입니다
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
          0xA1B2...7F3e
        </div>
        <div className="text-[12px]" style={{ marginTop: '8px', color: 'rgba(255,236,246,.62)' }}>
          Polygon Mainnet
        </div>
        <div className="flex items-center" style={{ marginTop: '14px', gap: '8px' }}>
          <div
            className="rounded-full"
            style={{ width: '8px', height: '8px', background: '#84bf9f' }}
          ></div>
          <div className="text-[12px] font-semibold" style={{ color: '#9ad0b4' }}>
            정상 연결
          </div>
        </div>
      </div>
    </>
  )
}
