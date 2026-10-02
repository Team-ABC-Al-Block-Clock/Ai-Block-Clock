import { rowStyle } from './layout'

/** 패널 안의 드롭다운. 네이티브 `select` 로 둔다 — 값이 몇 개 없고 키보드가 그냥 먹는다. */
export default function Picker<T extends string>({
  value,
  onChange,
  options,
  label,
  top,
  width,
}: {
  value: T
  onChange: (next: T) => void
  options: { value: T; label: string }[]
  label: string
  top: string
  /** 비우면 패널 폭 전체 */
  width?: number
}) {
  return (
    <div
      className="bg-surface-card absolute flex items-center"
      style={{
        ...rowStyle(top),
        ...(width === undefined ? {} : { width: `${width}px` }),
        height: '42px',
        borderRadius: '11px',
        padding: '0 8px 0 12px',
      }}
    >
      <select
        className="w-full cursor-pointer border-0 bg-transparent font-[inherit] text-[16.5px] text-white outline-0"
        value={value}
        onChange={(ev) => onChange(ev.target.value as T)}
        aria-label={label}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value} style={{ color: '#1d1430' }}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  )
}
