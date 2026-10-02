import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

import type { ExecutionStep, ExecutionStatus } from '@/api/executions'
import type { CanvasNodeData } from '@/lib/canvas/canvasWorkflow'
import type { CanvasNodeProps } from '@/components/canvas/CanvasNode'
import type { ControlNodeProps } from '@/components/canvas/ControlNode'

const data = vi.hoisted(() => ({
  nodes: [] as CanvasNodeData[],
  steps: [] as ExecutionStep[],
  status: 'SUCCEEDED' as ExecutionStatus,
}))

vi.mock('@/hooks/canvas/useSavedWorkflow', () => ({
  useSavedWorkflow: () => ({
    canvas: {
      nodes: data.nodes,
      edges: [
        { id: 's-a', from: 's', to: 'a' },
        { id: 'a-b', from: 'a', to: 'b' },
        { id: 'b-br', from: 'b', to: 'br' },
        { id: 'br-t', from: 'br', to: 't', when: true },
        { id: 'br-f', from: 'br', to: 'f', when: false },
        { id: 't-e', from: 't', to: 'e' },
        { id: 'f-e', from: 'f', to: 'e' },
      ],
    },
  }),
}))
vi.mock('@/hooks/run/useRunProgress', () => ({
  useRunProgress: () => ({
    progress: {
      status: data.status,
      percent: 100,
      done: 4,
      total: 4,
      steps: data.steps,
    },
  }),
}))
vi.mock('@/hooks/useEasedPercent', () => ({ useEasedPercent: () => 100 }))
vi.mock('react-router-dom', () => ({
  useSearchParams: () => [new URLSearchParams('workflowId=test')],
  useParams: () => ({ executionId: 'test-run' }),
}))
vi.mock('@tanstack/react-query', () => ({ useQuery: () => ({ data: { text: '', blocks: [] } }) }))
vi.mock('@/components/canvas/ControlNode', () => ({
  default: (props: ControlNodeProps) => <span data-kind={props.kind} data-verdict={props.note} />,
}))
vi.mock('@/components/canvas/CanvasNode', () => ({
  default: (props: CanvasNodeProps) => <span data-name={props.name} data-status={props.sublabel} />,
}))

import RunRunning from './index'

function prepare(legacy = false) {
  const node = (id: string, extra: Partial<CanvasNodeData> = {}): CanvasNodeData => ({
    id,
    name: id,
    sourceType: 'NODE',
    sourceId: id,
    nodeType: 'LLM',
    mine: true,
    prompt: null,
    model: null,
    price: '1',
    summary: null,
    x: 12,
    y: 300,
    floaty: '',
    ...extra,
  })
  data.nodes = [
    node('s', { sourceType: 'IO', ioType: 'START', mine: null }),
    node('a', { sourceType: 'AGENT', nodeType: 'AGENT' }),
    node('b', { sourceType: 'AGENT', nodeType: 'AGENT' }),
    node('br', {
      sourceType: 'CONTROL',
      controlType: 'BRANCH',
      mine: null,
      condition: { fromId: 'b', path: 'score', operator: 'GT', right: 0 },
    }),
    node('t'),
    node('f'),
    node('e', { sourceType: 'IO', ioType: 'END', mine: null }),
  ].map((node, i) => ({ ...node, x: 12 + i * 197 }))
  const branch: ExecutionStep = { name: 'b1', status: 'succeeded' }
  const tail: ExecutionStep[] = [
    { name: 't', status: 'succeeded' },
    { name: 'f', status: 'skipped' },
  ]
  data.steps = [
    { name: 'inner-a1', status: 'succeeded' },
    { name: 'inner-a2', status: 'succeeded' },
    { name: 'inner-b1', status: 'succeeded' },
    ...(legacy ? [...tail, branch] : [branch, ...tail]),
  ]
  data.status = 'SUCCEEDED'
}

const render = () =>
  new DOMParser().parseFromString(renderToStaticMarkup(<RunRunning />), 'text/html')

describe('RunRunning', () => {
  it.each([false, true])(
    '연속 에이전트가 있어도 분기 판정과 확정된 상태를 그린다 (legacy=%s)',
    (legacy) => {
      prepare(legacy)
      const document = render()
      expect(document.querySelector('[data-kind="BRANCH"]')?.getAttribute('data-verdict')).toBe(
        '→ true',
      )
      expect(document.querySelector('[data-name="t"]')?.getAttribute('data-status')).toBe('완료')
      expect(document.querySelector('[data-name="f"]')?.getAttribute('data-status')).toBe('건너뜀')
      expect(document.querySelector('[data-name="a"]')?.getAttribute('data-status')).toBe('')
      expect(document.querySelector('[data-name="b"]')?.getAttribute('data-status')).toBe('')
    },
  )

  it('부분 매핑에서도 에이전트 내부 진행 로그를 빠뜨리지 않는다', () => {
    prepare()
    data.status = 'RUNNING'
    const document = render()
    for (const name of ['inner-a1', 'inner-a2', 'inner-b1'])
      expect(document.body.textContent).toContain(name)
  })

  it('화면 아래 노드도 축소된 카메라 안에 그린다', () => {
    prepare()
    data.nodes = data.nodes.map((node) => ({
      ...node,
      y: node.id === 't' ? 953.5 : 185.5,
    }))
    const document = render()
    const camera = document.querySelector('[data-name="t"]')!.parentElement!
    const transform = camera.style.transform.match(
      /translate\(([-\d.]+)px, ([-\d.]+)px\) scale\(([-\d.]+)\)/,
    )!
    const [, tx, ty, k] = transform.map(Number)
    expect(k).toBeLessThan(1)
    expect((953.5 + 168) * k + ty).toBeLessThanOrEqual(1024)
    const focus = data.nodes.find((node) => node.id === 't')!
    expect((focus.x + 84) * k + tx).toBeCloseTo(980 / 2)
  })
})
