import type { RecommendedAgent } from '@/api/agents'
import { CARD_GUTTER } from '@/components/SidePanel'
import { priceLabel } from '@/lib/format'

import { MAX_PICKED } from './config'

interface CandidateCardProps {
  agent: RecommendedAgent
  chosen: boolean
  atLimit: boolean
  onToggle: () => void
}

/** 추천 목록의 카드 한 장. 누르면 고르고, 다시 누르면 뺀다. */
export default function CandidateCard({ agent, chosen, atLimit, onToggle }: CandidateCardProps) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={chosen}
      disabled={atLimit && !chosen}
      title={atLimit && !chosen ? `${MAX_PICKED}개까지 선택할 수 있습니다` : undefined}
      style={{
        // 폭은 주지 않고 그리드 열을 그대로 채운다.
        height: '176px',
        borderRadius: '12px',
        background: chosen ? 'rgba(255,255,255,.20)' : 'rgba(255,255,255,.09)',
        boxShadow: chosen ? 'inset 0 0 0 1.5px rgba(255,190,220,.75)' : 'none',
        transition: 'background .18s,transform .18s,box-shadow .18s',
      }}
      className="relative cursor-pointer border-0 p-0 text-left font-[inherit] hover:-translate-y-[2px] hover:bg-[rgba(255,255,255,.17)] disabled:cursor-default disabled:opacity-45 disabled:hover:translate-y-0 disabled:hover:bg-[rgba(255,255,255,.09)]"
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
        <div
          className="absolute inset-0 flex items-center justify-center text-[16px] font-semibold"
          style={{ color: 'rgba(255,226,238,.65)' }}
        >
          {agent.name.trim().charAt(0)}
        </div>
      </div>

      {chosen && (
        <div
          className="absolute flex items-center justify-center rounded-full"
          style={{
            right: `${CARD_GUTTER}px`,
            top: '16px',
            width: '22px',
            height: '22px',
            background: 'var(--color-accent)',
            animation: 'uiInPop .28s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <svg
            width="12"
            height="12"
            viewBox="0 0 12 12"
            fill="none"
            style={{ stroke: 'var(--color-accent-ink)' }}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="M2.5 6.2 5 8.7l4.5-5" />
          </svg>
        </div>
      )}

      <div
        className="absolute truncate text-[14px] font-semibold tracking-[-.01em] text-white"
        style={{ left: `${CARD_GUTTER}px`, right: `${CARD_GUTTER}px`, top: '76px' }}
      >
        {agent.name}
      </div>
      <div
        className="absolute overflow-hidden text-[12.5px] leading-[1.5]"
        style={{
          left: `${CARD_GUTTER}px`,
          right: `${CARD_GUTTER}px`,
          top: '100px',
          height: '38px',
          color: 'rgba(255,236,246,.66)',
          display: '-webkit-box',
          WebkitBoxOrient: 'vertical',
          WebkitLineClamp: 2,
        }}
      >
        {agent.summary}
      </div>
      <div
        className="absolute text-[12px]"
        style={{
          left: `${CARD_GUTTER}px`,
          top: '146px',
          color: 'rgba(255,214,234,.8)',
        }}
      >
        {priceLabel(agent.price)}
      </div>
    </button>
  )
}
