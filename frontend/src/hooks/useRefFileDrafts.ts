import { useState } from 'react'

/**
 * 아직 원본이 없는 노드에 붙일 참고 자료.
 *
 * 기본 노드는 워크플로우를 저장할 때 배치와 함께 만들어져서(`NodePlacementRequest`), 그전에는
 * 자료를 보낼 주소가 없다. 저장이 끝나 `sourceId` 가 채워지면 그때 차례로 올린다.
 *
 * `CanvasNodeData` 에 싣지 않는다 — 저장 여부를 `JSON.stringify` 로 가리는데(`shapeOf`)
 * `File` 은 `{}` 로 찍혀 서로 다른 파일이 같은 것으로 보인다.
 */

/** 캔버스에 놓기 전의 「노드 추가」 폼. 놓는 순간 새 카드 id 로 옮긴다. */
export const DRAFT_NODE_KEY = 'draft'

export function useRefFileDrafts() {
  const [byNode, setByNode] = useState<Record<string, File[]>>({})

  const filesOf = (nodeId: string) => byNode[nodeId] ?? []

  const add = (nodeId: string, files: File[]) =>
    setByNode((all) => ({ ...all, [nodeId]: [...(all[nodeId] ?? []), ...files] }))

  const dropAt = (nodeId: string, index: number) =>
    setByNode((all) => ({ ...all, [nodeId]: (all[nodeId] ?? []).filter((_, at) => at !== index) }))

  /** 올리지 못한 것만 남긴다. 빈 배열이면 칸 자체를 지운다. */
  const keep = (nodeId: string, files: File[]) =>
    setByNode((all) => {
      const next = { ...all }
      if (files.length) next[nodeId] = files
      else delete next[nodeId]
      return next
    })

  const move = (fromId: string, toId: string) =>
    setByNode((all) => {
      if (!all[fromId]?.length) return all
      const next = { ...all, [toId]: [...(all[toId] ?? []), ...all[fromId]] }
      delete next[fromId]
      return next
    })

  /** 주어진 카드들이 들고 있는 대기 파일 수. 지우면 곧바로 줄어든다 */
  const countIn = (nodeIds: readonly string[]) =>
    nodeIds.reduce((sum, id) => sum + (byNode[id]?.length ?? 0), 0)

  return { filesOf, add, dropAt, keep, move, countIn }
}

export type RefFileDrafts = ReturnType<typeof useRefFileDrafts>
