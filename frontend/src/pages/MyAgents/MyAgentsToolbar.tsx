import type { ChangeEvent } from 'react'

import SearchIcon from '@/components/SearchIcon'

import { CHIP, TABS, type Tab } from './config'

/** 오른쪽 열의 가로 격자. 컨트롤과 카드의 오른쪽 끝을 1338 에 맞춘다. */
const LEFT = 442
const SORT_W = 148
/** 즐겨찾기 토글 필이 없으므로 검색바가 그 자리까지 넓어진다. 896 - 148 - 12 */
const SEARCH_W = 736

interface MyAgentsToolbarProps {
  tab: Tab
  onTab: (next: Tab) => () => void
  countLabel: string
  q: string
  onChangeQuery: (e: ChangeEvent<HTMLInputElement>) => void
  /** 정렬이 없는 탭(즐겨찾기)은 필을 감춘다. */
  hasSort: boolean
  sortLabel: string
  onCycleSort: () => void
}

/** 목록 위의 탭 · 개수 · 검색 · 정렬. */
export default function MyAgentsToolbar({
  tab,
  onTab,
  countLabel,
  q,
  onChangeQuery,
  hasSort,
  sortLabel,
  onCycleSort,
}: MyAgentsToolbarProps) {
  return (
    <>
      {/* 탭 — 하나가 API 하나다 */}
      <div
        className="absolute flex"
        style={{
          left: `${LEFT}px`,
          top: '250px',
          height: '36px',
          gap: '8px',
          zIndex: 5,
          animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {TABS.map((t) => {
          const on = tab === t.key
          return (
            <div
              key={t.key}
              className={`${CHIP} text-[13px] font-semibold whitespace-nowrap ${on ? '' : 'hover:bg-[rgba(255,255,255,.16)]'}`}
              onClick={onTab(t.key)}
              style={{
                height: '36px',
                padding: '0 18px',
                borderRadius: '12px',
                background: on ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.09)',
                color: on ? '#3a1030' : 'rgba(255,240,247,.78)',
              }}
            >
              {t.label}
            </div>
          )
        })}
      </div>

      {/* 개수 — 커서 목록은 전체 개수를 주지 않는다. 불러온 만큼만 세고 더 있으면 '+' */}
      <div
        className="absolute text-right text-[13.5px] tracking-[-.01em]"
        style={{
          left: '1178px',
          top: '260px',
          width: '160px',
          color: 'rgba(255,240,247,.55)',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {countLabel}
      </div>

      {/* 검색 */}
      <div
        className="absolute bg-[rgba(255,255,255,.09)]"
        style={{
          left: `${LEFT}px`,
          top: '302px',
          width: `${SEARCH_W}px`,
          height: '44px',
          borderRadius: '22px',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <SearchIcon left={18} top={15} color="rgba(255,255,255,.62)" handleLength={8} />
        <input
          className="absolute border-0 bg-transparent font-[inherit] text-[13px] tracking-[-.01em] text-white outline-0 placeholder:text-white/50 disabled:cursor-not-allowed"
          type="text"
          value={q}
          onChange={onChangeQuery}
          // 명세에 keyword 가 없는 탭이다. 열어 두면 입력은 되는데 아무 효과가 없다.
          disabled={tab === 'favorites'}
          placeholder={
            tab === 'favorites'
              ? '즐겨찾기 탭은 이름 검색을 지원하지 않습니다'
              : tab === 'flows'
                ? '워크플로우 이름 검색'
                : '에이전트 이름 또는 설명 검색'
          }
          style={{ left: '46px', top: '11px', width: `${SEARCH_W - 66}px`, height: '22px' }}
        />
      </div>

      {/* 정렬 — 눌러서 다음 값으로. 즐겨찾기 탭은 명세에 sort 가 없어 감춘다 */}
      {hasSort && (
        <div
          onClick={onCycleSort}
          style={{
            left: '1190px',
            top: '305px',
            width: `${SORT_W}px`,
            height: '38px',
            borderRadius: '19px',
            padding: '0 14px',
            animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
          }}
          className={`${CHIP} absolute box-border justify-between bg-[rgba(255,255,255,.09)] hover:bg-[rgba(255,255,255,.16)]`}
        >
          <div
            className="text-[13.5px] whitespace-nowrap"
            style={{ color: 'rgba(255,240,247,.82)' }}
          >
            {sortLabel}
          </div>
          <div
            style={{
              width: '0',
              height: '0',
              borderLeft: '4px solid transparent',
              borderRight: '4px solid transparent',
              borderTop: '5px solid rgba(255,255,255,.6)',
            }}
          ></div>
        </div>
      )}
    </>
  )
}
