interface SaveRunBarProps {
  isSaving: boolean
  /** 한 번이라도 저장했는가. 다음 저장은 덮어쓰기다. */
  hasSaved: boolean
  dirty: boolean
  canSave: boolean
  saveBlocked: string | null
  onSave: () => void
  canRun: boolean
  runBlocked: string | null
  onRun: () => void
  /** 버튼 아래 안내. 막힌 까닭이나 실패한 까닭 하나. */
  notice: string | null
  /** 실패는 분홍, 막힌 까닭은 회색. */
  noticeIsError: boolean
  /** 붙지 못한 참고 자료가 있을 때만 — 저장 버튼은 이미 「저장됨」이라 다시 눌리지 않는다. */
  onResendFiles?: () => void
}

/** 작업면 오른쪽 아래의 저장 · 실행하기 단추와 그 아래 안내 한 줄. */
export default function SaveRunBar({
  isSaving,
  hasSaved,
  dirty,
  canSave,
  saveBlocked,
  onSave,
  canRun,
  runBlocked,
  onRun,
  notice,
  noticeIsError,
  onResendFiles,
}: SaveRunBarProps) {
  return (
    <>
      {/* 실행하기 왼쪽. 주 버튼과 경쟁하지 않게 유리 패널 차림으로 둔다. */}
      <button
        type="button"
        onClick={onSave}
        disabled={!canSave}
        title={
          saveBlocked ??
          (!dirty
            ? '마지막으로 저장한 뒤로 바뀐 것이 없습니다'
            : hasSaved
              ? '바뀐 구성으로 덮어씁니다'
              : '워크플로우를 저장합니다')
        }
        style={{
          left: 'calc(624px + var(--edge, 0px))',
          top: 'calc(915px + var(--edge-y, 0px))',
          width: '160px',
          height: '52px',
          borderRadius: '26px',
          background: 'rgba(30,14,46,.78)',
          boxShadow: `inset 0 0 0 1px rgba(255,255,255,${canSave ? '.22' : '.08'})`,
          backdropFilter: 'blur(14px)',
          WebkitBackdropFilter: 'blur(14px)',
          color: canSave ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.38)',
          cursor: canSave ? 'pointer' : 'not-allowed',
          transition: 'box-shadow .18s',
          animation: 'uiIn .7s .62s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="absolute flex items-center justify-center border-0 text-[17px] font-bold tracking-[-.01em]"
      >
        {isSaving ? '저장 중…' : hasSaved && !dirty ? '저장됨' : '저장'}
      </button>

      {notice && (
        <div
          className="absolute text-center text-[13px] tracking-[-.005em]"
          style={{
            // 실행하기 버튼(800~960) 바로 아래 가운데.
            left: 'calc(760px + var(--edge, 0px))',
            top: 'calc(974px + var(--edge-y, 0px))',
            width: '240px',
            color: noticeIsError ? 'rgba(255,196,214,.9)' : 'rgba(255,236,246,.6)',
          }}
        >
          {notice}
          {onResendFiles && (
            <button
              type="button"
              onClick={onResendFiles}
              className="cursor-pointer border-0 bg-transparent font-[inherit] text-[13px] font-semibold underline"
              style={{ marginLeft: '6px', color: 'rgba(255,214,232,.95)' }}
            >
              다시 보내기
            </button>
          )}
        </div>
      )}

      {/*
        저장하지 않고 눌러도 넘어간다 — 먼저 저장하고 그 워크플로우로 실행 화면을 연다.
        저장이 막힌 상태면 여기도 막히고, 시작·종료가 이어지지 않아도 막힌다.
      */}
      <button
        type="button"
        onClick={onRun}
        disabled={!canRun}
        title={runBlocked ?? (dirty ? '저장하고 실행 화면으로 갑니다' : '실행 화면으로 갑니다')}
        style={{
          left: 'calc(800px + var(--edge, 0px))',
          top: 'calc(915px + var(--edge-y, 0px))',
          width: '160px',
          height: '52px',
          borderRadius: '26px',
          background: canRun ? 'var(--color-accent)' : 'rgba(255,255,255,.09)',
          boxShadow: canRun
            ? '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.6)'
            : 'inset 0 1px 0 rgba(255,255,255,.12)',
          color: canRun ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.42)',
          cursor: canRun ? 'pointer' : 'not-allowed',
          transition: 'transform .18s,box-shadow .18s',
          animation: 'uiIn .7s .62s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="absolute flex items-center justify-center border-0 text-[17px] font-bold tracking-[-.01em]"
      >
        {isSaving ? '저장 중…' : '실행하기'}
      </button>
    </>
  )
}
