interface ListTabsProps<T extends string> {
  tabs: readonly { key: T; label: string }[]
  active: T
  onPick: (key: T) => void
}

/** 판매 · 사용 내역 목록 카드 왼쪽 위의 밑줄 탭. */
export default function ListTabs<T extends string>({ tabs, active, onPick }: ListTabsProps<T>) {
  return (
    <div className="flex" style={{ gap: '20px', height: '30px' }}>
      {tabs.map((t) => (
        <div
          key={t.key}
          className="relative flex cursor-pointer items-start text-[16px] font-semibold tracking-[-.01em]"
          onClick={() => onPick(t.key)}
          style={{
            height: '30px',
            color: active === t.key ? '#fff' : 'rgba(255,240,247,.6)',
            transition: 'color .16s',
          }}
        >
          {t.label}
          <div
            className="absolute left-0"
            style={{
              bottom: '0',
              width: '100%',
              height: '2px',
              borderRadius: '1px',
              background: active === t.key ? '#c9a9dd' : 'transparent',
            }}
          ></div>
        </div>
      ))}
    </div>
  )
}
