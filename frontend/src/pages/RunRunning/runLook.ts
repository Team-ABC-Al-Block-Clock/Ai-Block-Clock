import type { CSSProperties } from 'react'

import type { StepStatus } from '@/api/executions'
import type { CanvasNodeData } from '@/lib/canvas/canvasWorkflow'

/**
 * 실행 엔진이 보낸 소문자가 그대로 온다 — 서버가 대조하지 않아 모르는 값이 올 수 있다.
 * 못 찾으면 대기로 둔다. 빈 칸이면 카드 아래 한 줄이 통째로 사라진다.
 */
const STEP_LABEL: Record<string, string> = {
  pending: '대기',
  running: '진행 중',
  succeeded: '완료',
  failed: '실패',
  skipped: '건너뜀',
}
export const labelOf = (status: StepStatus) => STEP_LABEL[status] ?? '대기'

/** 카드 한 장의 색과 아래 한 줄. 단계를 받았으면 상태로, 못 받았으면 차례로 짐작한다. */
export function cardLook({
  status,
  index,
  known,
  active,
  lost,
  finished,
}: {
  status: StepStatus | undefined
  /** 서버가 세는 단계 중 몇 번째인가. 제어·입출력은 단계가 아니라 없다. */
  index: number | undefined
  known: boolean
  active: number
  lost: boolean
  finished: boolean
}) {
  const behind = index !== undefined && (index < active || finished)
  const current = index !== undefined && index === active && !finished
  const stepDone = status ? status === 'succeeded' : known && !lost && behind
  const stepRunning = status ? status === 'running' : known && !lost && current
  const stepFailed = status === 'failed'
  const stepSkipped = status === 'skipped'
  return {
    background: stepRunning
      ? 'rgba(255,255,255,.17)'
      : stepDone || stepFailed
        ? 'rgba(255,255,255,.115)'
        : stepSkipped
          ? 'rgba(255,255,255,.03)'
          : 'rgba(255,255,255,.06)',
    numberColor: stepRunning ? '#fff' : stepDone ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.5)',
    nameColor: stepRunning
      ? '#fff'
      : stepDone
        ? 'rgba(255,255,255,.9)'
        : stepSkipped
          ? 'rgba(255,255,255,.3)'
          : 'rgba(255,255,255,.55)',
    sublabelColor: stepFailed
      ? 'rgba(255,170,194,.9)'
      : stepRunning
        ? 'rgba(230,196,212,.85)'
        : stepDone
          ? 'rgba(154,208,180,.85)'
          : 'rgba(255,236,246,.4)',
    stateLabel: status
      ? labelOf(status)
      : !known
        ? ''
        : stepRunning
          ? '진행 중'
          : stepDone
            ? '완료'
            : '대기',
  }
}

/** 도는 카드 뒤의 빛. 도는 카드가 없거나 끝났으면 감춘다. */
export function glowStyle(node: CanvasNodeData | undefined, finished: boolean): CSSProperties {
  return node && !finished
    ? {
        position: 'absolute',
        left: `${node.x - 10}px`,
        top: `${node.y - 10}px`,
        width: '188px',
        height: '188px',
        borderRadius: '50%',
        pointerEvents: 'none',
        background: 'radial-gradient(circle, rgba(255,160,200,.20) 0 48%, rgba(255,160,200,0) 66%)',
        animation: 'runGlow 2.4s ease-in-out infinite',
        transition: 'left .6s cubic-bezier(.2,.8,.25,1),top .6s cubic-bezier(.2,.8,.25,1)',
      }
    : { display: 'none' }
}

export const edgeStroke = (lit: boolean) =>
  lit ? 'rgba(255,190,214,.75)' : 'rgba(255,255,255,.38)'

/** 로그 한 줄. 끝나지 않았으면 마지막 줄에 커서를 붙인다. */
export function logRow(text: string, last: boolean) {
  return {
    text,
    dot: last ? 'rgba(255,194,216,.9)' : 'rgba(154,208,180,.7)',
    color: last ? 'rgba(255,255,255,.92)' : 'rgba(255,255,255,.6)',
    caret: (last
      ? {
          width: '7px',
          height: '14px',
          flex: 'none',
          borderRadius: '2px',
          background: 'rgba(255,194,216,.9)',
          animation: 'caret 1s steps(1,end) infinite',
        }
      : { display: 'none' }) satisfies CSSProperties,
  }
}

export const gaugeBackground = (pct: number) =>
  'conic-gradient(from 0deg, var(--color-accent) 0 ' +
  pct +
  '%, rgba(255,255,255,.10) ' +
  pct +
  '% 100%)'
