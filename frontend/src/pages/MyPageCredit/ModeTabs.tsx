import { MODES, type Mode } from './config'

/** 충전 · 출금 · ABC 전환 탭. */
export default function ModeTabs({ mode, onPick }: { mode: Mode; onPick: (mode: Mode) => void }) {
  return (
    <div
      className="absolute flex"
      style={{
        left: '442px',
        top: '250px',
        height: '38px',
        gap: '10px',
        animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      {MODES.map((m) => (
        <div
          key={m.key}
          className="flex cursor-pointer items-center text-[14.5px] font-semibold transition-colors duration-[160ms]"
          onClick={() => onPick(m.key)}
          style={{
            height: '38px',
            padding: '0 20px',
            borderRadius: '11px',
            background: mode === m.key ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.09)',
            color: mode === m.key ? '#3a1030' : 'rgba(255,240,247,.78)',
          }}
        >
          {m.label}
        </div>
      ))}
    </div>
  )
}
