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
  type CanvasEdge,
  type CanvasNodeData,
} from './canvasWorkflow'

/** 선이 드나드는 쪽. 입력은 왼쪽, 출력은 오른쪽. */
export type PortSide = 'in' | 'out'

/** 선을 끄는 중인 출발점. 누를 때와 뗄 때만 바뀐다. */
interface LinkOrigin {
  id: string
  side: PortSide
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
  'id' | 'name' | 'nodeType' | 'sourceType' | 'sourceId' | 'price' | 'summary' | 'mine'
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

export type CanvasAction =
  /** 카드를 누름 — 선택은 누르는 즉시 옮겨간다. */
  | { type: 'pressNode'; id: string }
  /** 같은 카드를 다시 눌렀다 뗌 — 선택을 푼다. */
  | { type: 'releaseSelection' }
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
  /** 원본이 지워졌다. 같은 원본을 두 번 놓았을 수 있어 전부 내린다. */
  | { type: 'removeBySource'; sourceId: string }
  /** 원본 이름이 바뀌었다. 놓인 카드가 옛 이름을 붙들지 않게 맞춘다. */
  | { type: 'renameBySource'; sourceId: string; name: string }
  | { type: 'startLink'; from: LinkOrigin }
  /** 포트가 아닌 곳에서 손을 뗌. */
  | { type: 'cancelLink' }
  /** 포트 위에서 손을 뗌. 이을 수 없는 짝이면 `connect` 가 목록을 그대로 돌려준다. */
  | { type: 'endLink'; targetId: string; side: PortSide }

export function canvasReducer(state: CanvasState, action: CanvasAction): CanvasState {
  switch (action.type) {
    case 'pressNode':
      return { ...state, selId: action.id, selEdge: null, dragId: action.id }

    case 'releaseSelection':
      return { ...state, selId: null }

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

    case 'removeBySource': {
      const targets = state.nodes.filter((node) => node.sourceId === action.sourceId)
      if (!targets.length) return state
      // 한 장씩 내린다. 선까지 같이 걷어 내는 규칙이 `removeNode` 한 곳에만 있어야 한다.
      const after = targets.reduce((acc, node) => removeNode(acc.nodes, acc.edges, node.id), {
        nodes: state.nodes,
        edges: state.edges,
      })
      const gone = new Set(targets.map((node) => node.id))
      return {
        ...state,
        ...after,
        selId: state.selId && gone.has(state.selId) ? null : state.selId,
        hovId: state.hovId && gone.has(state.hovId) ? null : state.hovId,
        linkFrom: state.linkFrom && gone.has(state.linkFrom.id) ? null : state.linkFrom,
      }
    }

    case 'renameBySource': {
      if (!state.nodes.some((node) => node.sourceId === action.sourceId)) return state
      return {
        ...state,
        nodes: state.nodes.map((node) =>
          node.sourceId === action.sourceId ? { ...node, name: action.name } : node,
        ),
      }
    }

    case 'startLink':
      return { ...state, linkFrom: action.from }

    case 'cancelLink':
      return { ...state, linkFrom: null }

    case 'endLink': {
      const held = state.linkFrom
      if (!held || action.side === held.side) return { ...state, linkFrom: null }
      // 출발점이 사라졌으면 잇지 않는다. 한쪽이 없는 선은 그려지지 않아 지울 수도 없는데,
      // `canConnect` 의 입력 하나 규칙에는 잡혀 대상 노드가 영영 선을 못 받는다.
      if (!state.nodes.some((node) => node.id === held.id)) return { ...state, linkFrom: null }
      const [from, to] =
        held.side === 'out' ? [held.id, action.targetId] : [action.targetId, held.id]
      return { ...state, edges: connect(state.edges, from, to), linkFrom: null }
    }
  }
}
