import { paths } from '@/routes/paths'

interface SaveActionsProps {
  canSave: boolean
  isPending: boolean
  onSave: () => void
}

/** 아래 구분선과 취소 · 저장 단추. */
export default function SaveActions({ canSave, isPending, onSave }: SaveActionsProps) {
  return (
    <>
      <div
        className="absolute bg-[rgba(255,255,255,.12)]"
        style={{ left: '442px', top: '929px', width: '900px', height: '1px' }}
      ></div>

      <div
        className="absolute flex"
        style={{
          left: '1104px',
          top: '951px',
          width: '238px',
          height: '44px',
          justifyContent: 'flex-end',
          gap: '12px',
          animation: 'rise .7s .26s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <a
          href={paths.my.agents}
          style={{
            width: '84px',
            height: '44px',
            borderRadius: '22px',
            color: 'rgba(255,255,255,.9)',
          }}
          className="flex items-center justify-center bg-[rgba(255,255,255,.09)] text-[15.5px] font-semibold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
        >
          취소
        </a>
        <button
          type="button"
          onClick={onSave}
          disabled={!canSave}
          style={{
            width: '142px',
            height: '44px',
            borderRadius: '22px',
            background: canSave ? 'var(--color-accent)' : 'rgba(255,255,255,.12)',
            boxShadow: canSave ? '0 8px 22px rgba(140,100,130,.24)' : 'none',
            color: canSave ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.45)',
            transition: 'transform .18s, background .18s, color .18s',
          }}
          className="flex items-center justify-center text-[15.5px] font-bold enabled:cursor-pointer enabled:hover:-translate-y-[2px]"
        >
          {isPending ? '저장 중…' : '변경사항 저장'}
        </button>
      </div>
    </>
  )
}
