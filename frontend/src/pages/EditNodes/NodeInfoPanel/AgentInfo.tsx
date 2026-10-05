import { priceLabel } from '@/lib/format'

import HeadCard from './HeadCard'
import { labelStyle, rowStyle } from './layout'

/** 에이전트 쪽 세로 좌표. 설명은 길이에 따라 늘고, 메타 세 줄은 그 아래 고정이다. */
const SUMMARY_Y = 170
const META_Y = 330

/** 설명 아래 한 줄짜리 값. 라벨은 왼쪽, 값은 오른쪽 끝에 세운다. */
function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div
      className="flex items-center justify-between"
      style={{ height: '38px', borderTop: '1px solid rgba(255,255,255,.08)' }}
    >
      <span className="text-[15px]" style={{ color: 'rgba(255,224,238,.7)' }}>
        {label}
      </span>
      <span className="truncate text-[16px] font-semibold text-white">{value}</span>
    </div>
  )
}

interface AgentInfoProps {
  head: { initial: string; name: string; typeLabel: string }
  summary?: string | null
  /** 단가 · 실행 횟수 · 만든 사람. 모르는 값은 비워 둔다. */
  meta?: { price: string | null; executionCount?: number | null; owner?: string | null }
}

/** 에이전트와 제어 · 입출력 노드. 남의 내부 구성이라 열지 않고 설명과 메타만 보여 준다. */
export default function AgentInfo({ head, summary, meta }: AgentInfoProps) {
  return (
    <>
      <HeadCard {...head} />

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('144px')}>
        설명
      </div>
      <div
        className="scroll-thin absolute text-[16.5px] leading-[1.75] tracking-[-.003em]"
        style={{
          ...rowStyle(`${SUMMARY_Y}px`),
          // 아래 메타 행까지가 한계다. 서버 상한(80자)이면 이 폭에서 넉 줄이라 86px 로는 잘렸다.
          maxHeight: `${META_Y - SUMMARY_Y - 14}px`,
          color: summary ? 'rgba(255,246,251,.94)' : 'rgba(255,236,246,.5)',
        }}
      >
        {summary ?? '설명이 없습니다'}
      </div>

      {meta && (
        <div className="absolute flex flex-col" style={{ ...rowStyle(`${META_Y}px`), gap: '1px' }}>
          <MetaRow label="단가" value={meta.price ? priceLabel(meta.price) : '—'} />
          <MetaRow
            label="총 실행 횟수"
            value={
              typeof meta.executionCount === 'number'
                ? `${meta.executionCount.toLocaleString('ko-KR')}회`
                : '—'
            }
          />
          <MetaRow label="만든 사람" value={meta.owner ?? '—'} />
        </div>
      )}
    </>
  )
}
