import SearchIcon from '@/components/SearchIcon'
import {
  CARD_GUTTER,
  PANEL_BOTTOM,
  PANEL_CONTENT_WIDTH,
  PANEL_HEIGHT,
  PANEL_INSET,
  PANEL_SPLIT_Y,
} from '@/components/SidePanel'
import type { useNodePalette } from '@/hooks/useNodePalette'

/** `scrollbar-width: thin` 이 먹는 폭. 이만큼 넓혀야 카드가 위의 검색칸과 나란히 선다. */
const SCROLLBAR = 8

/** 가르는 선부터의 거리. 선을 옮기면 아래가 통째로 따라간다. */
const SEARCH_Y = PANEL_SPLIT_Y + 24
const GRID_Y = PANEL_SPLIT_Y + 78
/** 창이 커지면 위아래로 늘어난 만큼(`--edge-y`) 더 받는다. */
const GRID_HEIGHT = PANEL_HEIGHT - PANEL_BOTTOM - GRID_Y

type Palette = ReturnType<typeof useNodePalette>

type NodePalettePanelProps = {
  q: Palette['q']
  cards: Palette['cards']
  isEmpty: Palette['isEmpty']
  isError: Palette['isError']
  isFetchNextPageError: Palette['isFetchNextPageError']
  isFetchingNextPage: Palette['isFetchingNextPage']
  hasNextPage: Palette['hasNextPage']
  onSearch: Palette['handleSearch']
  onRetryNextPage: Palette['handleRetryNextPage']
  refs: Palette['refs']
}

/** 패널 아래쪽 — 캔버스로 끌어다 놓을 마켓 에이전트를 찾는 자리. */
export default function NodePalettePanel({
  q,
  cards,
  isEmpty,
  isError,
  isFetchNextPageError,
  isFetchingNextPage,
  hasNextPage,
  onSearch: handleSearch,
  onRetryNextPage: handleRetryNextPage,
  refs: { paletteRef, sentinelRef },
}: NodePalettePanelProps) {
  return (
    <>
      <div
        className="absolute"
        style={{
          left: `${PANEL_INSET}px`,
          top: `${PANEL_SPLIT_Y}px`,
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '1px',
          background: 'rgba(255,255,255,.14)',
        }}
      ></div>

      {/* 돋보기는 장식이라 클릭을 흘려보낸다. input 이 남는 자리를 전부 채워
            상자 어디를 눌러도 입력으로 들어간다 — 예전에는 왼쪽 250px 만 먹었다. */}
      <div
        className="absolute bg-[rgba(255,255,255,.10)]"
        style={{
          left: `${PANEL_INSET}px`,
          top: `${SEARCH_Y}px`,
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '40px',
          borderRadius: '10px',
        }}
      >
        <SearchIcon left={14} top={13} color="rgba(255,255,255,.7)" />
        <input
          className="absolute inset-y-0 border-0 bg-transparent font-[inherit] text-[15.5px] text-white outline-0"
          type="text"
          value={q}
          onChange={handleSearch}
          placeholder="마켓 에이전트 검색"
          aria-label="마켓 에이전트 검색"
          style={{ left: '42px', right: '16px', width: 'auto' }}
        />
      </div>

      <div
        ref={paletteRef}
        className="scroll-thin absolute grid"
        style={{
          left: `${PANEL_INSET}px`,
          top: `${GRID_Y}px`,
          width: `${PANEL_CONTENT_WIDTH + SCROLLBAR}px`,
          height: `calc(${GRID_HEIGHT}px + 2 * var(--edge-y, 0px))`,
          // 한 줄에 두 장. 남는 폭을 열이 나눠 갖게 한다.
          gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
          gap: '14px',
          alignContent: 'start',
        }}
      >
        {isError && (
          <div
            className="text-[14.5px]"
            style={{ gridColumn: '1 / -1', color: 'rgba(255,236,246,.78)' }}
          >
            목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.
          </div>
        )}
        {isEmpty && (
          <div
            className="text-[14.5px]"
            style={{ gridColumn: '1 / -1', color: 'rgba(255,236,246,.66)' }}
          >
            검색 결과가 없습니다.
          </div>
        )}
        {cards.map((item) => (
          <div
            key={item.itemId}
            draggable
            onDragStart={item.drag}
            title="캔버스로 드래그하세요"
            style={{
              // 폭은 주지 않고 그리드 열을 그대로 채운다.
              height: '134px',
              borderRadius: '12px',
              transition: 'background .18s,transform .18s',
              // 자신의 에이전트와 다른 사람 에이전트를 구분할 수 있게 테두리와 칩으로 표시한다.
              boxShadow: item.mine
                ? 'inset 0 0 0 1.5px rgba(255,190,220,.8)'
                : 'inset 0 0 0 1px rgba(255,255,255,.10)',
            }}
            className="relative cursor-grab bg-[rgba(255,255,255,.09)] hover:-translate-y-[2px] hover:bg-[rgba(255,255,255,.17)] active:cursor-grabbing"
          >
            <div
              className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
              style={{
                left: `${CARD_GUTTER}px`,
                top: '16px',
                width: '44px',
                height: '44px',
                borderRadius: '10px',
              }}
            >
              {item.iconUrl ? (
                <img
                  src={item.iconUrl}
                  alt=""
                  className="absolute inset-0 h-full w-full object-cover"
                />
              ) : (
                <div
                  className="absolute inset-0 flex items-center justify-center text-[16px] font-semibold"
                  style={{ color: 'rgba(255,226,238,.65)' }}
                >
                  {item.initial}
                </div>
              )}
            </div>
            <div
              className="absolute truncate text-[15.5px] font-semibold tracking-[-.005em] text-white"
              style={{ left: `${CARD_GUTTER}px`, right: `${CARD_GUTTER}px`, top: '72px' }}
            >
              {item.name}
            </div>
            <div
              className="absolute text-[13px]"
              style={{ left: `${CARD_GUTTER}px`, top: '99px', color: 'rgba(255,236,246,.7)' }}
            >
              {item.unit}
            </div>
            {/* 내 에이전트에만 MY 칩을 붙인다 */}
            {item.mine && (
              <div
                className="absolute text-[12px] font-bold tracking-[-.005em]"
                style={{
                  right: '12px',
                  top: '96px',
                  height: '20px',
                  padding: '0 8px',
                  lineHeight: '20px',
                  borderRadius: '10px',
                  background: 'rgba(255,190,220,.26)',
                  color: 'rgba(255,240,248,.95)',
                }}
              >
                MY
              </div>
            )}
            <div
              onClick={item.info}
              title="항목 정보"
              style={{
                right: `${CARD_GUTTER}px`,
                top: '12px',
                width: '24px',
                height: '24px',
                borderRadius: '12px',
                background: item.infoBg,
                zIndex: '2',
              }}
              className="absolute flex cursor-pointer items-center justify-center text-[13px] font-bold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.34)]"
            >
              i
            </div>
            {item.infoOpen && (
              <div
                className="absolute inset-0 box-border flex cursor-pointer flex-col"
                onClick={item.info}
                style={{
                  padding: '14px',
                  borderRadius: '12px',
                  background: 'rgba(20,11,36,.94)',
                  backdropFilter: 'blur(20px) saturate(120%)',
                  WebkitBackdropFilter: 'blur(20px) saturate(120%)',
                  gap: '6px',
                  animation: 'fadeIn .16s ease both',
                  zIndex: '3',
                }}
              >
                <div className="text-[14.5px] font-semibold tracking-[-.005em] text-white">
                  {item.name}
                </div>
                <div className="flex justify-between text-[13px]">
                  <span style={{ color: 'rgba(255,236,246,.72)' }}>총 실행 횟수</span>
                  <span className="font-semibold text-white">{item.uses}</span>
                </div>
                <div className="flex justify-between text-[13px]">
                  <span style={{ color: 'rgba(255,236,246,.72)' }}>만든 사람</span>
                  <span className="font-semibold text-white">{item.owner.nickname ?? '—'}</span>
                </div>
                <div
                  className="text-[12.5px]"
                  style={{
                    marginTop: '3px',
                    lineHeight: '1.45',
                    color: 'rgba(255,236,246,.78)',
                  }}
                >
                  {item.summary ?? '설명이 없습니다'}
                </div>
              </div>
            )}
          </div>
        ))}
        <div ref={sentinelRef} style={{ gridColumn: '1 / -1', height: '1px' }}></div>
        {!isFetchNextPageError && cards.length > 0 && (
          <div
            className="text-center text-[12.5px]"
            style={{
              gridColumn: '1 / -1',
              paddingTop: '6px',
              color: 'rgba(255,236,246,.45)',
            }}
          >
            {isFetchingNextPage ? '더 불러오는 중…' : hasNextPage ? '' : '목록의 끝입니다'}
          </div>
        )}
        {/* observer 가 꺼진 뒤라 손으로 눌러야 다음 장이 다시 온다. */}
        {isFetchNextPageError && (
          <div
            className="flex items-center justify-center text-[12.5px]"
            style={{
              gridColumn: '1 / -1',
              gap: '10px',
              paddingTop: '4px',
              animation: 'fadeIn .2s ease both',
            }}
          >
            <span style={{ color: 'rgba(255,196,214,.9)' }}>다음 목록을 불러오지 못했습니다</span>
            <span
              onClick={handleRetryNextPage}
              className="flex items-center font-semibold text-white hover:bg-[rgba(255,255,255,.16)]"
              style={{
                height: '28px',
                padding: '0 12px',
                borderRadius: '14px',
                background: 'rgba(255,255,255,.09)',
                opacity: isFetchingNextPage ? 0.6 : 1,
                cursor: isFetchingNextPage ? 'default' : 'pointer',
              }}
            >
              {isFetchingNextPage ? '불러오는 중…' : '다시 시도'}
            </span>
          </div>
        )}
      </div>
    </>
  )
}
