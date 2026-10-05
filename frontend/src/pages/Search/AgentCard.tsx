import type { MouseEvent } from 'react'

import type { AgentSummary } from '@/api/agents'
import { priceLabel, ratingLabel, runLabel } from '@/lib/format'
import { paths } from '@/routes/paths'

import { CARD_HEIGHT, PAGE_SIZE } from './config'

interface AgentCardProps {
  agent: AgentSummary
  index: number
  bright: boolean
  infoOpen: boolean
  onToggleInfo: (agentId: string) => void
}

/** 마켓 카드 한 장. 'i' 를 누르면 같은 자리에 정보 면이 덮인다. */
export default function AgentCard({
  agent,
  index,
  bright,
  infoOpen,
  onToggleInfo,
}: AgentCardProps) {
  const cardBg = bright ? 'rgba(24,13,40,.42)' : 'rgba(255,255,255,.10)'
  const descColor = bright ? 'rgba(255,240,248,.82)' : 'rgba(255,236,246,.7)'
  const infoBg = infoOpen ? 'rgba(255,255,255,.34)' : 'rgba(255,255,255,.18)'
  // 새로 불러온 페이지 안에서만 시차를 준다 — 누적 index 를 쓰면 전부 같은 지연이 된다.
  const anim = `rise .6s ${(0.16 + (index % PAGE_SIZE) * 0.04).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`

  const handleInfo = (e: MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    onToggleInfo(agent.agentId)
  }

  return (
    <a
      href={paths.agentDetail(agent.agentId)}
      style={{
        width: '280px',
        height: `${CARD_HEIGHT}px`,
        borderRadius: '14px',
        background: `${cardBg}`,
        boxShadow: '0 10px 34px rgba(10,4,20,.14)',
        transition: 'transform .2s,box-shadow .2s,background .2s',
        animation: `${anim}`,
      }}
      className="relative overflow-hidden hover:-translate-y-[4px] hover:shadow-[0_16px_46px_rgba(10,4,20,.26)] hover:brightness-[1.14]"
    >
      <div
        className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
        style={{
          left: '24px',
          top: '24px',
          width: '56px',
          height: '56px',
          borderRadius: '12px',
        }}
      >
        {agent.iconUrl ? (
          <img className="h-full w-full object-cover" src={agent.iconUrl} alt="" />
        ) : (
          <div
            className="absolute inset-0 flex items-center justify-center text-[18px] font-semibold"
            style={{ color: 'rgba(255,226,238,.72)' }}
          >
            {agent.name.trim().charAt(0).toUpperCase()}
          </div>
        )}
      </div>
      <div
        className="absolute flex items-center"
        style={{ right: '22px', top: '26px', height: '24px', gap: '8px' }}
      >
        <div
          className="flex items-center"
          style={{
            height: '24px',
            padding: '0 11px',
            borderRadius: '12px',
            background: 'rgba(255,255,255,.13)',
            gap: '5px',
          }}
        >
          <div className="text-[10px]" style={{ color: '#e3c8a3' }}>
            ★
          </div>
          <div className="text-[11.5px] font-semibold tracking-[-.01em] text-white">
            {ratingLabel(agent.averageRating, agent.reviewCount)}
          </div>
        </div>
        <div
          onClick={handleInfo}
          title="에이전트 정보"
          style={{
            width: '24px',
            height: '24px',
            borderRadius: '12px',
            background: `${infoBg}`,
            letterSpacing: '0',
          }}
          className="flex flex-none cursor-pointer items-center justify-center text-[12px] font-bold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.34)]"
        >
          i
        </div>
      </div>
      {infoOpen && (
        <div
          className="absolute top-0 left-0 box-border flex cursor-pointer flex-col"
          onClick={handleInfo}
          style={{
            width: '280px',
            height: `${CARD_HEIGHT}px`,
            padding: '22px 24px',
            borderRadius: '14px',
            background: 'rgba(20,11,36,.93)',
            backdropFilter: 'blur(22px) saturate(120%)',
            WebkitBackdropFilter: 'blur(22px) saturate(120%)',
            gap: '9px',
            animation: 'fadeIn .18s ease both',
            zIndex: '3',
          }}
        >
          <div className="text-[13.5px] font-semibold tracking-[-.01em] text-white">
            {agent.name}
          </div>
          <div className="flex justify-between text-[12px]">
            <span style={{ color: 'rgba(255,236,246,.6)' }}>사용 횟수</span>
            <span className="font-semibold text-white">{runLabel(agent.executionCount)}</span>
          </div>
          <div className="flex justify-between text-[12px]">
            <span style={{ color: 'rgba(255,236,246,.6)' }}>실행 단가</span>
            <span className="font-semibold text-white">{priceLabel(agent.price)}</span>
          </div>
          <div className="flex justify-between text-[12px]">
            <span style={{ color: 'rgba(255,236,246,.6)' }}>만든 사람</span>
            <span className="font-semibold text-white">{agent.owner.nickname ?? '—'}</span>
          </div>
          <div
            className="text-center text-[11px] tracking-[-.01em]"
            style={{ marginTop: 'auto', color: 'rgba(255,236,246,.5)' }}
          >
            다시 누르면 카드로 돌아갑니다
          </div>
        </div>
      )}
      <div
        className="absolute truncate text-[18px] font-semibold tracking-[-.01em] text-white"
        style={{ left: '24px', top: '94px', width: '232px' }}
      >
        {agent.name}
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '24px',
          top: '124px',
          width: '232px',
          lineHeight: '1.4',
          color: `${descColor}`,
        }}
      >
        {agent.summary}
      </div>
    </a>
  )
}
