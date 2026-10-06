interface RegisterActionsProps {
  /** 구분선의 위치. 버튼과 오류 줄은 여기서 아래로 잰다. */
  top: number
  backTo: string
  isPending: boolean
  canSubmit: boolean
  onSubmit: () => void
  /** 보내기 전에 막힌 까닭이나 서버가 거절한 까닭. */
  errorMessage: string | null
}

/** 폼 맨 아래 — 취소 · 등록 완료와 그 위의 오류 한 줄. */
export default function RegisterActions({
  top,
  backTo,
  isPending,
  canSubmit,
  onSubmit,
  errorMessage,
}: RegisterActionsProps) {
  return (
    <>
      <div
        className="absolute bg-[rgba(255,255,255,.12)]"
        style={{ left: '272px', top: `${top}px`, width: '896px', height: '1px' }}
      ></div>

      <a
        href={backTo}
        style={{
          left: '832px',
          top: `${top + 40}px`,
          width: '160px',
          height: '52px',
          borderRadius: '26px',
          color: 'rgba(255,255,255,.9)',
        }}
        className="absolute flex items-center justify-center bg-[rgba(255,255,255,.10)] text-[16.5px] font-semibold tracking-[-.005em] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
      >
        취소하기
      </a>
      <button
        type="button"
        onClick={onSubmit}
        disabled={!canSubmit}
        style={{
          left: '1008px',
          top: `${top + 40}px`,
          width: '160px',
          height: '52px',
          borderRadius: '26px',
          background: 'var(--color-accent)',
          boxShadow: '0 10px 26px rgba(140,100,130,.24)',
          color: 'var(--color-accent-ink)',
          transition: 'transform .18s',
        }}
        className="absolute flex cursor-pointer items-center justify-center text-[16.5px] font-bold tracking-[-.005em] hover:-translate-y-[2px] disabled:cursor-default disabled:opacity-45"
      >
        {isPending ? '등록 중…' : '등록 완료'}
      </button>

      {/* 실패를 조용히 넘기지 않는다. 토스트가 없어 버튼 위에 한 줄로 둔다. */}
      {errorMessage && (
        <div
          className="absolute text-right text-[14px] tracking-[-.005em]"
          style={{
            left: '672px',
            top: `${top + 12}px`,
            width: '496px',
            color: 'rgba(255,196,214,.9)',
            animation: 'fadeIn .2s ease both',
          }}
        >
          {errorMessage}
        </div>
      )}
    </>
  )
}
