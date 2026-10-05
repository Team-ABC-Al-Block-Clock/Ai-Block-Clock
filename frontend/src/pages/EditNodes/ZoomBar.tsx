interface ZoomBarProps {
  zoomLabel: string
  onZoomIn: () => void
  onZoomOut: () => void
  onToggleAddPanel: () => void
}

const STEP_STYLE = { width: '41px', height: '36px', color: 'rgba(255,255,255,.82)' }
const STEP_CLASS =
  'flex cursor-pointer items-center justify-center text-[17px] font-semibold hover:bg-[rgba(255,255,255,.12)]'

/** 왼쪽 아래 줌 단추와 「노드 추가」 여닫이. */
export default function ZoomBar({
  zoomLabel,
  onZoomIn,
  onZoomOut,
  onToggleAddPanel,
}: ZoomBarProps) {
  return (
    <div
      className="absolute flex items-center overflow-hidden"
      style={{
        left: 'calc(32px - var(--edge, 0px))',
        top: 'calc(952px + var(--edge-y, 0px))',
        height: '36px',
        borderRadius: '18px',
        background: 'rgba(14,7,28,.55)',
        backdropFilter: 'blur(18px)',
        WebkitBackdropFilter: 'blur(18px)',
        animation: 'uiIn .7s .56s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      <div onClick={onZoomOut} title="축소" style={STEP_STYLE} className={STEP_CLASS}>
        −
      </div>
      <div
        className="flex items-center justify-center text-[15px] font-semibold"
        style={{ width: '67px', height: '36px', color: 'rgba(255,255,255,.82)' }}
      >
        {zoomLabel}
      </div>
      <div onClick={onZoomIn} title="확대" style={STEP_STYLE} className={STEP_CLASS}>
        +
      </div>
      <div style={{ width: '1px', height: '22px', background: 'rgba(255,255,255,.20)' }}></div>
      <div
        onClick={onToggleAddPanel}
        style={{ height: '36px', padding: '0 16px', gap: '7px' }}
        className="flex cursor-pointer items-center text-[15px] font-semibold text-white hover:bg-[rgba(255,255,255,.12)]"
      >
        <span>+</span>
        <span>노드 추가</span>
      </div>
    </div>
  )
}
