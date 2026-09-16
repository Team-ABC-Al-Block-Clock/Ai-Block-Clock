import type { NodeType } from '@/api/workflows'

/**
 * 편집·대기·실행 화면이 함께 그리는 워크플로우.
 *
 * 워크플로우 저장·조회 API 가 아직 없어 자리표시자를 한 곳에 두고 세 화면이 같이 읽는다.
 */
export interface CanvasNodeData {
  name: string
  nodeType: NodeType
  x: number
  y: number
  mine: boolean
  animation: string
}

export const CANVAS_NODES: CanvasNodeData[] = [
  {
    name: '파일 수집',
    nodeType: 'TRIGGER',
    x: 12,
    y: 400,
    mine: true,
    animation: 'floaty 6.4s ease-in-out infinite, uiIn .7s .18s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    name: '내용 분석',
    nodeType: 'LLM',
    x: 209,
    y: 320,
    mine: true,
    animation: 'floaty 6.9s .3s ease-in-out infinite, uiIn .7s .26s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    name: '분류 및 태깅',
    nodeType: 'TOOL',
    x: 406,
    y: 440,
    mine: true,
    animation: 'floaty 7.1s .8s ease-in-out infinite, uiIn .7s .34s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    name: '요약 생성',
    nodeType: 'LLM',
    x: 603,
    y: 330,
    mine: true,
    animation: 'floaty 6.8s .4s ease-in-out infinite, uiIn .7s .42s cubic-bezier(.2,.8,.25,1) both',
  },
  {
    name: '결과 전달',
    nodeType: 'TOOL',
    x: 800,
    y: 430,
    mine: true,
    animation: 'floaty 7.6s 1.2s ease-in-out infinite, uiIn .7s .5s cubic-bezier(.2,.8,.25,1) both',
  },
]

/** 다른 사람의 에이전트가 하나라도 섞이면 팔 수 없다. 빈 캔버스도 등록할 것이 없다. */
export function canSellWorkflow(nodes: CanvasNodeData[]): boolean {
  return nodes.length > 0 && nodes.every((node) => node.mine)
}
