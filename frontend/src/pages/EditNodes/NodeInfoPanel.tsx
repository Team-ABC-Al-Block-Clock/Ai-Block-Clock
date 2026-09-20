import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

type NodeInfoPanelProps = {
  /** 캔버스에서 고른 노드가 있는가. 없으면 안내 문구만 보여준다. */
  hasSelection: boolean
  name: string
  /** 이름 첫 글자. 아이콘이 아직 없어 이것으로 대신한다. */
  initial: string
  typeLabel: string
}

/** 패널 위쪽 — 고른 노드를 살펴보는 자리. */
export default function NodeInfoPanel({
  hasSelection,
  name,
  initial,
  typeLabel,
}: NodeInfoPanelProps) {
  return hasSelection ? (
    <>
      <div
        className="absolute box-border flex items-center"
        style={{
          left: `${PANEL_INSET}px`,
          top: '58px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '104px',
          borderRadius: '12px',
          background: 'rgba(255,255,255,.11)',
          padding: '0 16px',
          gap: '16px',
        }}
      >
        <div
          className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.10)]"
          style={{ width: '48px', height: '48px', borderRadius: '10px' }}
        >
          <div
            className="absolute inset-0 flex items-center justify-center text-[17px] font-semibold"
            style={{ color: 'rgba(255,226,238,.65)' }}
          >
            {initial}
          </div>
        </div>
        <div className="flex min-w-0 flex-col" style={{ gap: '4px' }}>
          <div className="truncate text-[18px] font-semibold tracking-[-.005em] text-white">
            {name}
          </div>
          <div className="text-[14.5px]" style={{ color: 'rgba(255,224,238,.88)' }}>
            {typeLabel} 노드
          </div>
        </div>
      </div>

      <div
        className="absolute text-[15.5px] font-semibold"
        style={{ left: `${PANEL_INSET}px`, top: '172px', color: 'rgba(255,255,255,.92)' }}
      >
        노드 개요
      </div>
      <div
        className="absolute"
        style={{
          left: `${PANEL_INSET}px`,
          top: '196px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '108px',
          borderRadius: '10px',
          background: 'var(--color-surface-inset)',
        }}
      >
        <div
          className="absolute"
          style={{
            left: '14px',
            top: '16px',
            width: '264px',
            height: '10px',
            borderRadius: '3px',
            background: 'rgba(255,255,255,.16)',
          }}
        ></div>
        <div
          className="absolute"
          style={{
            left: '14px',
            top: '38px',
            width: '240px',
            height: '10px',
            borderRadius: '3px',
            background: 'rgba(255,255,255,.13)',
          }}
        ></div>
        <div
          className="absolute"
          style={{
            left: '14px',
            top: '60px',
            width: '252px',
            height: '10px',
            borderRadius: '3px',
            background: 'rgba(255,255,255,.13)',
          }}
        ></div>
        <div
          className="absolute bg-[rgba(255,255,255,.10)]"
          style={{
            left: '14px',
            top: '82px',
            width: '176px',
            height: '10px',
            borderRadius: '3px',
          }}
        ></div>
      </div>

      <div
        className="absolute text-[15.5px] font-semibold"
        style={{ left: `${PANEL_INSET}px`, top: '326px', color: 'rgba(255,255,255,.92)' }}
      >
        모델
      </div>
      <div
        className="absolute flex items-center"
        style={{
          left: `${PANEL_INSET}px`,
          top: '350px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '44px',
          borderRadius: '10px',
          padding: '0 14px',
          background: 'var(--color-surface-inset)',
        }}
      >
        <div className="text-[15.5px]" style={{ color: 'rgba(255,255,255,.9)' }}>
          기본 모델
        </div>
      </div>
    </>
  ) : (
    <div
      className="absolute text-[15.5px]"
      style={{
        left: `${PANEL_INSET}px`,
        top: '64px',
        width: `${PANEL_CONTENT_WIDTH}px`,
        color: 'rgba(255,236,246,.66)',
      }}
    >
      캔버스에서 노드를 선택하면 여기서 살펴봅니다
    </div>
  )
}
