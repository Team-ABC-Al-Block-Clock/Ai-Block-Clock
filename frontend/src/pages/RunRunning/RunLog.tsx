import type { CSSProperties } from 'react'

import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import StatusDot from '@/components/StatusDot'

interface RunLogProps {
  logs: { text: string; dot: string; color: string; caret: CSSProperties }[]
}

/** 도는 동안 지나간 단계를 한 줄씩 쌓는다. 마지막 줄에만 커서가 깜빡인다. */
export default function RunLog({ logs }: RunLogProps) {
  return (
    <>
      <div
        className="absolute text-[17.5px] font-semibold tracking-[-.005em]"
        style={{ left: `${PANEL_INSET}px`, top: '420px', color: 'rgba(255,255,255,.94)' }}
      >
        실시간 출력
      </div>
      <div
        className="scroll-thin absolute flex flex-col"
        style={{
          left: `${PANEL_INSET}px`,
          top: '452px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          // 패널이 길어진 아래까지 쓴다.
          height: 'calc(558px + 2 * var(--edge-y, 0px))',
          gap: '15px',
        }}
      >
        {logs.map((l, i) => (
          <div
            key={i}
            className="flex items-start"
            style={{ gap: '8px', animation: 'lineIn .34s ease both' }}
          >
            {/* 분기 줄은 조건과 판정까지 적어 길다. 자르면 판정이 사라져 줄을 바꾼다. */}
            <div className="flex flex-none" style={{ height: '27px', alignItems: 'center' }}>
              <StatusDot size={6} color={l.dot} />
            </div>
            <div
              className="min-w-0 text-[18px] tracking-[-.005em]"
              style={{ color: l.color, overflowWrap: 'anywhere' }}
            >
              {l.text}
            </div>
            <div style={l.caret}></div>
          </div>
        ))}
      </div>
    </>
  )
}
