import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

export type PanelTab = 'info' | 'search'

/** 탭 막대의 아랫변. 두 탭의 내용은 여기서부터 시작한다. */
export const TABS_HEIGHT = 68

export default function PanelTabs({
  tab,
  onChange,
  selectedName,
}: {
  tab: PanelTab
  onChange: (next: PanelTab) => void
  /** 고른 노드가 있으면 첫 탭 옆에 이름을 붙인다 */
  selectedName: string | null
}) {
  const items: { value: PanelTab; label: string }[] = [
    { value: 'info', label: '노드 정보' },
    { value: 'search', label: '에이전트 검색' },
  ]
  return (
    <div
      role="tablist"
      aria-label="오른쪽 패널"
      className="absolute flex"
      style={{
        left: `${PANEL_INSET}px`,
        top: 0,
        width: `${PANEL_CONTENT_WIDTH}px`,
        height: `${TABS_HEIGHT}px`,
        boxShadow: 'inset 0 -1px 0 rgba(255,255,255,.14)',
        gap: '4px',
      }}
    >
      {items.map((item) => {
        const on = tab === item.value
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            id={`panel-tab-${item.value}`}
            aria-selected={on}
            aria-controls={`panel-${item.value}`}
            onClick={() => onChange(item.value)}
            className="relative flex min-w-0 flex-1 cursor-pointer items-end justify-center border-0 bg-transparent font-[inherit] text-[17px] font-semibold tracking-[-.005em]"
            style={{
              gap: '8px',
              paddingBottom: '14px',
              color: on ? '#fff' : 'rgba(255,255,255,.55)',
            }}
          >
            {item.label}
            {item.value === 'info' && selectedName && (
              <span
                className="truncate text-[12.5px] font-medium"
                style={{
                  maxWidth: '110px',
                  marginBottom: '2px',
                  padding: '1px 8px',
                  borderRadius: '10px',
                  background: 'rgba(233,183,210,.14)',
                  color: 'var(--color-accent-bright)',
                }}
              >
                {selectedName}
              </span>
            )}
            {on && (
              <span
                className="absolute"
                style={{
                  left: '10%',
                  right: '10%',
                  bottom: 0,
                  height: '2px',
                  borderRadius: '2px',
                  background: 'var(--color-accent-bright)',
                }}
              />
            )}
          </button>
        )
      })}
    </div>
  )
}
