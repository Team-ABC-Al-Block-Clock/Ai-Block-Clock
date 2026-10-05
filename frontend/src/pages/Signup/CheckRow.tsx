interface CheckRowProps {
  on: boolean
  toggle: () => void
  main: string
  sub?: string
}

/** 행 전체를 눌러 켜고 끄는 확인 한 줄. */
export default function CheckRow({ on, toggle, main, sub }: CheckRowProps) {
  return (
    <div
      onClick={toggle}
      style={{ padding: '8px 10px', borderRadius: '10px', gap: '10px' }}
      className="flex cursor-pointer items-start transition-colors duration-[140ms] hover:bg-[rgba(255,255,255,.06)]"
    >
      <div
        className="flex flex-none items-center justify-center text-[10px] font-bold transition-colors duration-[160ms]"
        style={{
          width: '17px',
          height: '17px',
          marginTop: '1px',
          borderRadius: '5px',
          background: on ? 'var(--color-accent-bright)' : 'rgba(255,255,255,.12)',
          color: 'var(--color-accent-ink)',
        }}
      >
        {on ? '✓' : ''}
      </div>
      <div className="flex-1">
        {/* keep-all — 없으면 열 폭에서 `확인했습니 / 다` 로 단어 중간이 끊긴다 */}
        <div
          className="text-[13px] leading-[1.45] tracking-[-.01em]"
          style={{ color: 'rgba(255,240,247,.84)', wordBreak: 'keep-all' }}
        >
          {main}
        </div>
        {sub && (
          <div
            className="text-[11.5px] tracking-[-.01em]"
            style={{ marginTop: '4px', color: 'rgba(255,236,246,.56)' }}
          >
            {sub}
          </div>
        )}
      </div>
    </div>
  )
}
