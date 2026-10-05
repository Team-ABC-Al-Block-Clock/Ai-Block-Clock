import { priceLabel } from '@/lib/format'

import { AMOUNT_AT, type Column } from './config'

type AgentStat = { agentId: string | null; amount: string }

export const shareOf = (amount: string, total: number) =>
  total > 0 ? Math.round((Number(amount) / total) * 1000) / 10 : 0

export function growthLabel(growth: number | null) {
  return growth === null
    ? '지난달 매출 없음'
    : `${growth >= 0 ? '+' : ''}${growth.toLocaleString()}%`
}

/** 2 ~ 4위 칸은 늘 셋이다. 순위가 모자라면 빈 칸을 0 으로 채운다. */
export function shareSlots(
  soldAgents: AgentStat[],
  total: number,
  agentName: (agentId: string | null) => string,
) {
  return [1, 2, 3].map((rank) => {
    const a = soldAgents[rank]
    return {
      label: a ? agentName(a.agentId) : '—',
      value: `${priceLabel(a?.amount ?? 0)} (${a ? shareOf(a.amount, total) : 0}%)`,
      width: `${a ? shareOf(a.amount, total) : 0}%`,
    }
  })
}

/** 칸마다 폭 · 색 · 굵기를 붙인다. 건별 표의 마지막 칸(구분)은 판매 / 직접 사용으로 색이 갈린다. */
export function tableRows(data: string[][], columns: Column[], byAgentTab: boolean) {
  return data.map((cells) => ({
    cells: cells.map((text, i) => ({
      text,
      w: columns[i].w,
      color:
        !byAgentTab && i === cells.length - 1
          ? text === '판매'
            ? '#9ad0b4'
            : '#ff8a8a'
          : i === 0 && byAgentTab
            ? 'var(--color-body)'
            : i === AMOUNT_AT
              ? '#fff'
              : 'rgba(255,255,255,.84)',
      weight: i === AMOUNT_AT || (!byAgentTab && i === cells.length - 1) ? '600' : '400',
    })),
  }))
}
