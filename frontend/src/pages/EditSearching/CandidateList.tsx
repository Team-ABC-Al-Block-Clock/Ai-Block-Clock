import type { RecommendedAgent } from '@/api/agents'
import { PANEL_INSET } from '@/components/SidePanel'

import CandidateCard from './CandidateCard'

interface CandidateListProps {
  items: RecommendedAgent[]
  picked: RecommendedAgent[]
  atLimit: boolean
  onToggle: (agent: RecommendedAgent) => void
}

/** 추천 결과. 한 줄에 두 장씩 세로로 넘긴다. */
export default function CandidateList({ items, picked, atLimit, onToggle }: CandidateListProps) {
  return (
    <div
      className="scroll-thin absolute grid"
      style={{
        left: `${PANEL_INSET}px`,
        top: '116px',
        width: '408px',
        // 패널은 위아래로 `--edge-y` 만큼 자라지만 아래쪽 버튼 줄은 제자리에 선다.
        // 2 배로 늘리면 창이 높을 때 목록 끝이 버튼 줄 밑으로 들어가 눌리지 않는다.
        height: 'calc(786px + var(--edge-y, 0px))',
        // 한 줄에 두 장. 남는 폭을 열이 나눠 갖게 한다.
        gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
        gap: '14px',
        alignContent: 'start',
      }}
    >
      {items.map((agent) => (
        <CandidateCard
          key={agent.agentId}
          agent={agent}
          chosen={picked.some((it) => it.agentId === agent.agentId)}
          atLimit={atLimit}
          onToggle={() => onToggle(agent)}
        />
      ))}
    </div>
  )
}
