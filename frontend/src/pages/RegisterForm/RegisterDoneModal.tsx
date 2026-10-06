import WindowLayer from '@/components/WindowLayer'
import { paths } from '@/routes/paths'

interface RegisterDoneModalProps {
  agentId: string
  /** 서버가 산정한 판매 단가. 등록해야 나와서 확인 팝업에는 띄울 수 없다. */
  price: string | null
}

/** 등록을 마친 뒤 화면을 덮는 안내. 마이페이지나 새 에이전트의 상세로 보낸다. */
export default function RegisterDoneModal({ agentId, price }: RegisterDoneModalProps) {
  return (
    <WindowLayer>
      <div
        className="absolute inset-0"
        style={{
          background: 'rgba(6,3,14,.55)',
          backdropFilter: 'blur(4px)',
          WebkitBackdropFilter: 'blur(4px)',
          animation: 'fadeIn .24s ease both',
        }}
      ></div>
      <div className="absolute inset-0 flex items-center justify-center">
        <div
          className="relative box-border text-center"
          style={{
            width: '440px',
            padding: '34px',
            borderRadius: '20px',
            background: 'rgba(26,13,42,.94)',
            backdropFilter: 'blur(28px)',
            WebkitBackdropFilter: 'blur(28px)',
            boxShadow: '0 30px 70px rgba(6,3,14,.6)',
            animation: 'rise .32s cubic-bezier(.2,.9,.3,1) both',
          }}
        >
          <div
            className="flex items-center justify-center rounded-full text-[24px]"
            style={{
              width: '56px',
              height: '56px',
              margin: '0 auto 18px',
              background: 'rgba(255,179,205,.2)',
              color: '#ffd0e0',
            }}
          >
            ✓
          </div>
          <div className="text-[21px] font-semibold tracking-[-.005em] text-white">
            등록이 완료되었습니다
          </div>
          <div
            className="text-[14.5px]"
            style={{ marginTop: '10px', lineHeight: '1.6', color: 'rgba(255,236,246,.68)' }}
          >
            에이전트 올려줘서 ㄱㅅㄱㅅ!!
            <br />
          </div>
          {price && (
            <div
              className="text-[14.5px] font-semibold"
              style={{
                marginTop: '16px',
                padding: '12px 14px',
                borderRadius: '11px',
                background: 'rgba(255,255,255,.06)',
                color: '#fff',
              }}
            >
              실행 1회당 판매가 {price} ABC
            </div>
          )}
          <div className="flex" style={{ marginTop: '26px', gap: '12px' }}>
            <a
              href={paths.my.agents}
              style={{ height: '50px', borderRadius: '25px', color: 'rgba(255,255,255,.9)' }}
              className="flex flex-1 items-center justify-center bg-[rgba(255,255,255,.09)] text-[15.5px] font-semibold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.18)] hover:text-[#fff]"
            >
              마이페이지 가기
            </a>
            <a
              className="flex flex-1 items-center justify-center text-[15.5px] font-bold"
              href={paths.agentDetail(agentId)}
              style={{
                height: '50px',
                borderRadius: '25px',
                background: 'var(--color-accent)',
                color: 'var(--color-accent-ink)',
              }}
            >
              상세 페이지 보기
            </a>
          </div>
        </div>
      </div>
    </WindowLayer>
  )
}
