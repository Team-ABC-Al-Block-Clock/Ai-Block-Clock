import type { AiModel, NodeOutput } from '@/api/nodes'
import {
  NO_BOUNDS,
  connect,
  disconnect,
  moveNodeTo,
  placeNode,
  removeNode,
  chainEdges,
  seedCanvasNodes,
  type CanvasBounds,
  type CanvasCondition,
  type CanvasEdge,
  type CanvasNodeData,
} from './canvasWorkflow'

/** 선이 드나드는 쪽. 입력은 왼쪽, 출력은 오른쪽. */
export type PortSide = 'in' | 'out'

/** 카드에 붙는 포트 하나. 분기만 오른쪽에 참·거짓 둘을 낸다. */
export interface NodePort {
  side: PortSide
  /** 분기에서 나가는 포트만. 참 갈래가 `true` */
  when?: boolean
}

/** 선을 끄는 중인 출발점. 누를 때와 뗄 때만 바뀐다. */
interface LinkOrigin extends NodePort {
  id: string
}

/**
 * 캔버스가 들고 있는 것 전부.
 *
 * 포인터가 움직이는 동안의 좌표는 여기 없다 — 그것은 리렌더가 필요 없어
 * 훅이 ref 와 DOM 으로 따로 다룬다. 여기에는 화면이 다시 그려져야 하는 것만 둔다.
 */
export interface CanvasState {
  nodes: CanvasNodeData[]
  edges: CanvasEdge[]
  /** 선택한 노드. 노드 선택과 선 선택은 한 번에 하나만 살아 있다. */
  selId: string | null
  selEdge: string | null
  hovId: string | null
  /** 끌고 있는 노드. 카드가 모션을 끄고 위로 올라온다. */
  dragId: string | null
  linkFrom: LinkOrigin | null
}

export const initialCanvasState: CanvasState = {
  // 빈 캔버스에서 시작한다. 자리표시자를 깔아 두면 사용자가 놓지 않은 것이 저장된다.
  nodes: [],
  edges: [],
  selId: null,
  selEdge: null,
  hovId: null,
  dragId: null,
  linkFrom: null,
}

/** 팔레트에서 끌어다 놓은 항목. 자리는 놓는 쪽이 정한다. */
export type PlacedSeed = Pick<
  CanvasNodeData,
  | 'id'
  | 'name'
  | 'nodeType'
  | 'sourceType'
  | 'sourceId'
  | 'price'
  | 'summary'
  | 'prompt'
  | 'model'
  | 'output'
  | 'executionCount'
  | 'owner'
  | 'mine'
  | 'controlType'
  | 'ioType'
  | 'condition'
  | 'resultShape'
  | 'resultFormat'
>

/**
 * 추천에서 선택한 것을 안고 들어왔을 때의 첫 상태.
 *
 * 자리표시자는 쓰지 않는다 — 선택한 적 없는 데모 노드가 섞이면 무엇을 내가 선택했는지 알 수 없다.
 * 선택한 순서대로 가로 한 줄로 펴고 그 순서대로 잇는다. 선택한 순서가 곧 실행 순서다.
 */
export function seededCanvasState(seeds?: PlacedSeed[]): CanvasState {
  if (!seeds?.length) return initialCanvasState
  const nodes = seedCanvasNodes(seeds, 'row')
  return { ...initialCanvasState, nodes, edges: chainEdges(nodes) }
}

/** 기본 노드에서 고칠 수 있는 값. */
export interface NodeValues {
  name: string
  prompt: string
  model: AiModel
  output: NodeOutput
}

export type CanvasAction =
  /** 카드를 누름 — 선택은 누르는 즉시 옮겨간다. */
  | { type: 'pressNode'; id: string }
  /** 같은 카드를 다시 눌렀다 뗌 — 선택을 푼다. */
  | { type: 'releaseSelection' }
  /** 끌지 않고 고르기만 한다. 노드 추가 패널로 만든 노드가 그렇다. */
  | { type: 'selectNode'; id: string }
  | { type: 'selectEdge'; id: string }
  | { type: 'clearSelection' }
  | { type: 'hover'; id: string | null }
  | { type: 'endDrag' }
  /** 미끄러짐까지 끝난 자리로 확정. 겹침은 부르는 쪽에서 이미 풀었다. */
  | { type: 'settleNode'; id: string; x: number; y: number }
  | { type: 'placeNode'; seed: PlacedSeed; x: number; y: number; bounds?: CanvasBounds }
  /** 저장된 워크플로우를 불러와 캔버스를 통째로 갈아 끼운다. */
  | { type: 'loadGraph'; nodes: CanvasNodeData[]; edges: CanvasEdge[] }
  /** 선택한 것을 지움. 노드면 거기 붙은 선도 함께. */
  | { type: 'removeSelected' }
  /** 고른 기본 노드의 값을 고친다. 저장은 워크플로우 저장이 맡는다. */
  | { type: 'editNode'; id: string; values: NodeValues }
  /** 분기 조건을 고침. 조건이 없으면 저장이 막힌다. */
  | { type: 'editCondition'; id: string; condition: CanvasCondition }
  /** 저장하며 만들어진 노드의 원본 id 를 되쓴다. 비워 두면 다음 저장에서 또 만들어진다. */
  | { type: 'attachSources'; sourceIdByNodeId: Record<string, string> }
  /** 원본을 고치면 서버가 원가를 다시 매긴다. 그 값을 카드에 되쓴다. */
  | { type: 'repriceBySource'; sourceId: string; price: string }
  | { type: 'startLink'; from: LinkOrigin }
  /** 포트가 아닌 곳에서 손을 뗌. */
  | { type: 'cancelLink' }
  /** 포트 위에서 손을 뗌. 이을 수 없는 짝이면 `connect` 가 목록을 그대로 돌려준다. */
  | { type: 'endLink'; targetId: string; port: NodePort }

/** 저장하며 서버가 만든 원본 id 를 카드에 되쓴다. 받은 것이 없는 카드는 그대로 둔다. */
export function withSources(
  nodes: CanvasNodeData[],
  sourceIdByNodeId: Record<string, string>,
): CanvasNodeData[] {
  return nodes.map((node) =>
    sourceIdByNodeId[node.id] ? { ...node, sourceId: sourceIdByNodeId[node.id] } : node,
  )
}

export function canvasReducer(state: CanvasState, action: CanvasAction): CanvasState {
  switch (action.type) {
    case 'pressNode':
      return { ...state, selId: action.id, selEdge: null, dragId: action.id }

    case 'releaseSelection':
      return { ...state, selId: null }

    case 'selectNode':
      if (!state.nodes.some((node) => node.id === action.id)) return state
      return { ...state, selId: action.id, selEdge: null }

    case 'selectEdge':
      // 같은 선을 다시 누르면 선택을 푼다.
      return { ...state, selId: null, selEdge: state.selEdge === action.id ? null : action.id }

    case 'clearSelection':
      return { ...state, selId: null, selEdge: null }

    case 'hover':
      return { ...state, hovId: action.id }

    case 'endDrag':
      return { ...state, dragId: null }

    case 'settleNode':
      return {
        ...state,
        nodes: moveNodeTo(state.nodes, action.id, action.x, action.y),
        dragId: null,
      }

    case 'placeNode':
      return {
        ...state,
        nodes: placeNode(state.nodes, action.seed, action.x, action.y, action.bounds ?? NO_BOUNDS),
      }

    // 고르던 것과 끌던 것은 새 그래프에 없는 id 일 수 있다. 같이 비운다.
    case 'loadGraph':
      return {
        ...state,
        nodes: action.nodes,
        edges: action.edges,
        selId: null,
        selEdge: null,
        hovId: null,
        dragId: null,
        linkFrom: null,
      }

    case 'removeSelected': {
      if (state.selEdge) {
        return { ...state, edges: disconnect(state.edges, state.selEdge), selEdge: null }
      }
      if (!state.selId) return state
      const after = removeNode(state.nodes, state.edges, state.selId)
      if (after.nodes === state.nodes) return state
      // 끄는 중인 선의 출발점이 방금 지운 노드일 수 있다. 남겨 두면 없는 노드에서 선이 나간다.
      return { ...state, ...after, selId: null, hovId: null, linkFrom: null }
    }

    case 'editNode': {
      if (!state.nodes.some((node) => node.id === action.id)) return state
      return {
        ...state,
        nodes: state.nodes.map((node) =>
          node.id === action.id ? { ...node, ...action.values } : node,
        ),
      }
    }

    case 'editCondition':
      return {
        ...state,
        nodes: state.nodes.map((node) =>
          node.id === action.id && node.controlType
            ? { ...node, condition: action.condition }
            : node,
        ),
      }

    case 'attachSources': {
      const given = action.sourceIdByNodeId
      if (!state.nodes.some((node) => given[node.id] && given[node.id] !== node.sourceId))
        return state
      return { ...state, nodes: withSources(state.nodes, given) }
    }

    case 'repriceBySource': {
      if (!state.nodes.some((node) => node.sourceId === action.sourceId)) return state
      return {
        ...state,
        nodes: state.nodes.map((node) =>
          node.sourceId === action.sourceId ? { ...node, price: action.price } : node,
        ),
      }
    }

    case 'startLink':
      return { ...state, linkFrom: action.from }

    case 'cancelLink':
      return { ...state, linkFrom: null }

    case 'endLink': {
      const held = state.linkFrom
      if (!held || action.port.side === held.side) return { ...state, linkFrom: null }
      // 출발점이 사라졌으면 잇지 않는다. 한쪽이 없는 선은 그려지지 않아 지울 수도 없는데,
      // `canConnect` 의 입력 하나 규칙에는 잡혀 대상 노드가 영영 선을 못 받는다.
      if (!state.nodes.some((node) => node.id === held.id)) return { ...state, linkFrom: null }
      // 갈래는 늘 나가는 쪽 포트가 들고 있다. 거꾸로 끌어와도 출발점은 그쪽이다.
      const [out, to] = held.side === 'out' ? [held, action.targetId] : [action.port, held.id]
      const from = held.side === 'out' ? held.id : action.targetId
      return {
        ...state,
        edges: connect(state.nodes, state.edges, from, to, out.when),
        linkFrom: null,
      }
    }
  }
}
