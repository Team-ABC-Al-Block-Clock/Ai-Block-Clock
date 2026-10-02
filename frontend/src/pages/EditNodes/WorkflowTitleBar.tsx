import StatusDot from '@/components/StatusDot'

/** 이름 길이. 서버가 `@Size(max = 40)` 으로 막는다. */
const NAME_MAX = 40

interface WorkflowTitleBarProps {
  name: string
  onNameChange: (name: string) => void
  canSell: boolean
  /** 판매할 수 없는 까닭. 알약에 마우스를 올리면 보인다. */
  sellProblem: string | null
}

/**
 * 작업면 위 가운데의 워크플로우 이름과 판매 가능 여부.
 *
 * 둘을 한 줄로 묶어 작업면(0~980) 가운데에 맞춘다 — 각각 left 를 주면 알약 글자 수에 따라
 * 묶음이 좌우로 흔들린다.
 */
export default function WorkflowTitleBar({
  name,
  onNameChange,
  canSell,
  sellProblem,
}: WorkflowTitleBarProps) {
  return (
    <div
      className="absolute flex items-center justify-center"
      style={{
        left: '0px',
        top: 'calc(18px - var(--edge-y, 0px))',
        width: '980px',
        gap: '12px',
      }}
    >
      <div
        className="relative flex-none"
        style={{
          // 바탕을 진하게 하고 테두리를 둘러 입력창이라는 것이 드러나게 한다.
          width: '300px',
          height: '46px',
          borderRadius: '12px',
          background: 'rgba(30,14,46,.78)',
          boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.22), 0 6px 18px rgba(10,4,20,.3)',
          backdropFilter: 'blur(14px)',
          WebkitBackdropFilter: 'blur(14px)',
          animation: 'uiIn .7s .44s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <input
          className="absolute rounded-md border-0 bg-transparent font-[inherit] text-[17px] font-semibold tracking-[-.005em] text-white outline-0 transition-colors duration-[160ms] hover:bg-white/10 focus:bg-white/14"
          value={name}
          onChange={(ev) => onNameChange(ev.target.value)}
          maxLength={NAME_MAX}
          placeholder="워크플로우 이름"
          aria-label="워크플로우 이름"
          style={{ left: '14px', top: '9px', width: '245px', height: '28px', padding: '0 6px' }}
        />
        <svg
          className="pointer-events-none absolute"
          width="17"
          height="17"
          viewBox="0 0 14 14"
          fill="none"
          style={{ left: '269px', top: '15px' }}
        >
          <path
            d="M0 12.5L1.5 8L9.5 0L12.5 3L4.5 11L0 12.5ZM0 12.5L3.7 11.8"
            stroke="rgba(255,255,255,.6)"
            strokeWidth="1.4"
            strokeLinejoin="round"
          />
        </svg>
      </div>

      <div
        title={
          canSell
            ? '실행이 끝나면 완료 화면에서 마켓에 등록할 수 있어요'
            : (sellProblem ?? undefined)
        }
        style={{
          height: '28px',
          padding: '0 12px',
          borderRadius: '14px',
          gap: '7px',
          background: 'rgba(30,14,46,.78)',
          boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.22)',
          backdropFilter: 'blur(14px)',
          WebkitBackdropFilter: 'blur(14px)',
          animation: 'uiIn .7s .58s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="flex flex-none items-center whitespace-nowrap"
      >
        <StatusDot color={canSell ? '#a8ddc0' : '#e79a9a'} />
        <div
          className="text-[14.5px] font-bold tracking-[-.005em]"
          style={{ color: 'rgba(255,255,255,.9)' }}
        >
          {canSell ? '판매 등록 가능' : '판매 등록 불가'}
        </div>
      </div>
    </div>
  )
}
