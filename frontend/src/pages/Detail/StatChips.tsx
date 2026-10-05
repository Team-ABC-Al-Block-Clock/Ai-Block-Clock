import type { AgentDetail } from '@/api/agents'
import { compactCount, priceLabel, ratingLabel, runLabel, successLabel } from '@/lib/format'

import { INK } from './ink'

/** 사용 · 성공률 · 평점 · 즐겨찾기와 실행 1회당 가격. */
export default function StatChips({ agent }: { agent: AgentDetail }) {
  // 노드 개수·등급·평균 실행 시간·공개 범위는 명세가 반환하지 않는다
  // (내부 구조 보호 / 내부 자료 / 미집계 / 등록이 곧 공개).
  const chips = [
    `사용 ${runLabel(agent.executionCount)}`,
    `성공률 ${successLabel(agent.successRate, agent.executionCount)}`,
    `★ ${ratingLabel(agent.averageRating, agent.reviewCount)}`,
    `즐겨찾기 ${compactCount(agent.favoriteCount)}`,
  ]

  return (
    <div
      className="flex flex-wrap"
      style={{
        marginTop: '24px',
        gap: '10px',
        animation: 'rise .7s .28s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      {chips.map((label) => (
        <div
          key={label}
          className="flex items-center text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
          style={{
            height: '36px',
            padding: '0 16px',
            borderRadius: '100px',
            background: 'rgba(255,255,255,.12)',
            color: INK.body,
          }}
        >
          {label}
        </div>
      ))}
      {/* 명세 「가격은 한 줄이다」 — 실행 1회당 가격만 보여준다. 토큰 단위 단가는 없다. */}
      <div
        className="flex flex-none items-center text-[15px] font-bold tracking-[-.01em] whitespace-nowrap"
        style={{
          height: '36px',
          padding: '0 16px',
          borderRadius: '100px',
          background: 'rgba(185,141,169,.3)',
          color: '#ffe4f0',
        }}
      >
        {priceLabel(agent.price)}
      </div>
    </div>
  )
}
