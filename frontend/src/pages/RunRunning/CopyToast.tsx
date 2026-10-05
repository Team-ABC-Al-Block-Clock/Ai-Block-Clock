/**
 * 결과를 복사했다는 토스트. 오른쪽 패널 밖, 창 좌표에 그린다 — 결과 상자 위에 겹쳐 띄우므로
 * 상자와 같은 기준을 써야 한다.
 */
export default function CopyToast() {
  return (
    <div
      className="pointer-events-none absolute flex items-center justify-center"
      style={{
        right: 'calc(44px - var(--edge, 0px))',
        top: '706px',
        width: '372px',
        height: '46px',
        justifyContent: 'flex-end',
        zIndex: '6',
      }}
    >
      <div
        className="flex items-center"
        style={{
          height: '46px',
          padding: '0 22px',
          borderRadius: '23px',
          background: 'rgba(24,11,38,.82)',
          backdropFilter: 'blur(20px) saturate(120%)',
          WebkitBackdropFilter: 'blur(20px) saturate(120%)',
          boxShadow: '0 16px 44px rgba(10,4,20,.42)',
          gap: '11px',
          animation: 'toastIn 1.8s ease both',
        }}
      >
        <div
          className="flex flex-none items-center justify-center rounded-full"
          style={{ width: '22px', height: '22px', background: 'rgba(154,208,180,.24)' }}
        >
          <svg
            width="12"
            height="10"
            viewBox="0 0 12 10"
            fill="none"
            stroke="#a8ddc0"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M1 5.2 4.3 8.5 11 1.5" />
          </svg>
        </div>
        <div className="text-[16px] font-semibold tracking-[-.01em] whitespace-nowrap text-white">
          복사 완료
        </div>
      </div>
    </div>
  )
}
