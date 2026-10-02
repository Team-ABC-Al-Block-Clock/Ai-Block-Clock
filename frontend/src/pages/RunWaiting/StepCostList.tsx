import type { CostItem } from '@/api/executions'
import { CARD_GUTTER, PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import { priceLabel } from '@/lib/format'

import { STEPS_HEIGHT, STEPS_Y } from './config'

interface StepCostListProps {
  items: CostItem[]
  /** 목록 대신 적을 안내. 있으면 줄을 그리지 않는다. */
  notice: string | null
}

/** 단계별 비용. 노드마다 이름과 단가를 한 줄씩 보여 준다. */
export default function StepCostList({ items, notice }: StepCostListProps) {
  return (
    <>
      <div
        className="absolute text-[22px] font-semibold tracking-[-.01em]"
        style={{
          left: `${PANEL_INSET}px`,
          top: `${STEPS_Y - 36}px`,
          color: 'rgba(255,255,255,.96)',
        }}
      >
        단계별 비용
      </div>
      {/* 노드가 스무 개까지 늘어나 창 밖으로 나간다. 패널 안에서만 굴린다. */}
      <div
        className="absolute flex flex-col overflow-x-hidden"
        style={{
          left: `${PANEL_INSET}px`,
          top: `${STEPS_Y}px`,
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: `calc(${STEPS_HEIGHT}px + 2 * var(--edge-y, 0px))`,
          gap: '8px',
          overflowY: 'auto',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,.28) transparent',
        }}
      >
        {notice ? (
          <div
            className="text-[15px] tracking-[-.005em]"
            style={{ color: 'rgba(255,236,246,.44)' }}
          >
            {notice}
          </div>
        ) : (
          items.map((item, i) => (
            <div
              key={item.nodeKey}
              style={{
                width: `${PANEL_CONTENT_WIDTH}px`,
                height: '46px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.06)',
                flex: 'none',
              }}
              className="relative"
            >
              <div
                className="absolute text-[13px] font-semibold tabular-nums"
                style={{ left: `${CARD_GUTTER}px`, top: '15px', color: 'rgba(255,236,246,.5)' }}
              >
                {i + 1}
              </div>
              <div
                className="absolute truncate text-[16px] font-semibold tracking-[-.005em]"
                style={{
                  left: `${CARD_GUTTER + 24}px`,
                  top: '14px',
                  maxWidth: '236px',
                  color: 'rgba(255,255,255,.9)',
                }}
              >
                {item.name}
              </div>
              <div
                className="absolute text-[14px] font-semibold tabular-nums"
                style={{ right: `${CARD_GUTTER}px`, top: '14px', color: '#d7bce8' }}
              >
                {priceLabel(item.price)}
              </div>
            </div>
          ))
        )}
      </div>
    </>
  )
}
