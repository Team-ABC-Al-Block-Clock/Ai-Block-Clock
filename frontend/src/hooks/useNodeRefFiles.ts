import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import {
  attachRefFile,
  describeRefFileProblem,
  detachRefFile,
  getNode,
  type RefFile,
} from '@/api/nodes'
import { DRAFT_NODE_KEY, useRefFileDrafts } from '@/hooks/useRefFileDrafts'
import { queryClient } from '@/lib/queryClient'
import { queryKeys } from '@/lib/queryKeys'
import { refFileErrorText } from '@/lib/refFileMessage'

/**
 * 노드에 붙이는 참고 자료.
 *
 * 붙은 것은 서버가 원본이라 `GET /nodes/{id}` 로 읽는다 — 저장본을 다시 열어도 그대로 선다.
 * 원본이 아직 없는 노드는 파일을 들고 있다가, 저장으로 `sourceId` 가 생기면 그때 올린다.
 */

/** 한 노드의 자료 칸이 쓰는 값과 손잡이. 두 패널이 같은 것을 받는다. */
export interface RefFileSlot {
  /** 서버에 붙어 있는 것 */
  attached: RefFile[]
  /** 원본이 아직 없어 들고 있는 것 */
  pending: File[]
  busy: boolean
  /** 제출 전 검사나 서버가 거절한 사유. 없으면 null */
  problem: string | null
  onPick: (files: File[]) => void
  onDetach: (refFileId: string) => void
  onDropPending: (index: number) => void
}

const bytesOf = (files: { size: number }[]) => files.reduce((sum, file) => sum + file.size, 0)

export function useNodeRefFiles(selected: { canvasId: string; sourceId: string } | null) {
  const drafts = useRefFileDrafts()
  const [problems, setProblems] = useState<Record<string, string | null>>({})
  /** 올리는 중인 카드들. 한 칸으로 들면 A 가 끝날 때 B 의 잠금까지 풀린다 */
  const [busyAt, setBusyAt] = useState<readonly string[]>([])

  const sourceId = selected?.sourceId ?? ''
  const detail = useQuery({
    queryKey: queryKeys.nodes.detail(sourceId),
    queryFn: () => getNode(sourceId),
    enabled: !!sourceId,
  })

  const note = (nodeId: string, text: string | null) =>
    setProblems((all) => ({ ...all, [nodeId]: text }))

  /**
   * 한 건씩 차례로. 합계 상한을 서버가 누적으로 재서, 한꺼번에 던지면 무엇이 통과할지 순서에 달린다.
   *
   * `carry` 는 제출 전 검사가 남긴 사유다. 올린 것이 다 통과해도 지우지 않는다 — 고른 것 중
   * 하나가 빠졌다는 사실은 그대로 알려야 한다.
   */
  const sendAll = async (
    nodeId: string,
    targetId: string,
    files: File[],
    carry: string | null = null,
  ) => {
    const failed: File[] = []
    let rejected: string | null = null

    setBusyAt((all) => [...all, nodeId])
    try {
      for (const file of files) {
        try {
          await attachRefFile(targetId, file)
        } catch (error) {
          failed.push(file)
          rejected ??= refFileErrorText(error)
        }
      }
    } finally {
      setBusyAt((all) => all.filter((id) => id !== nodeId))
    }

    drafts.keep(nodeId, failed)
    note(nodeId, rejected ?? carry)
    void queryClient.invalidateQueries({ queryKey: queryKeys.nodes.detail(targetId) })
    return failed.length
  }

  /**
   * 고른 파일을 받는다. 서버가 거절할 것은 여기서 먼저 막는다 — 빈 파일과 형식 불가가
   * 같은 코드(`NODE_006`)로 와서, 사유를 가르는 곳은 여기뿐이다.
   */
  const pick = (nodeId: string, targetId: string, attached: RefFile[], picked: File[]) => {
    const taken: File[] = []
    const refusals: string[] = []

    for (const file of picked) {
      const waiting = drafts.filesOf(nodeId)
      const used = bytesOf(attached) + bytesOf(waiting) + bytesOf(taken)
      const names = [
        ...attached.map((file) => file.filename),
        ...waiting.map((file) => file.name),
        ...taken.map((file) => file.name),
      ]
      const problem = describeRefFileProblem(file, used, names)
      if (problem) refusals.push(problem)
      else taken.push(file)
    }

    // 파일 이름은 적지 않는다 — 한 줄이라 긴 이름이 사유를 밀어낸다. 빠진 것은 목록에 없어 보인다.
    const refused = refusals.length
      ? refusals.length > 1
        ? `${refusals[0]} (외 ${refusals.length - 1}개)`
        : refusals[0]
      : null

    note(nodeId, refused)
    if (!taken.length) return

    // 원본이 없으면 보낼 주소가 없다. 저장이 만들어 줄 때까지 들고 있는다.
    if (!targetId) {
      drafts.add(nodeId, taken)
      return
    }
    // 앞서 못 올린 것이 남아 있으면 함께 보낸다. 올린 것은 목록을 다시 읽어 세운다.
    void sendAll(nodeId, targetId, [...drafts.filesOf(nodeId), ...taken], refused)
  }

  const detach = (nodeId: string, targetId: string, refFileId: string) => {
    note(nodeId, null)
    setBusyAt((all) => [...all, nodeId])
    void detachRefFile(targetId, refFileId)
      .catch((error: unknown) => note(nodeId, refFileErrorText(error)))
      .finally(() => {
        setBusyAt((all) => all.filter((id) => id !== nodeId))
        void queryClient.invalidateQueries({ queryKey: queryKeys.nodes.detail(targetId) })
      })
  }

  const slotFor = (
    nodeId: string,
    targetId: string,
    attached: RefFile[],
    loading = false,
  ): RefFileSlot => ({
    attached,
    pending: drafts.filesOf(nodeId),
    // 붙은 목록을 아직 못 읽었으면 잠근다 — 빈 목록으로 합계와 중복을 재면 검사가 헛돈다.
    busy: busyAt.includes(nodeId) || loading,
    problem: problems[nodeId] ?? null,
    onPick: (files) => pick(nodeId, targetId, attached, files),
    onDetach: (refFileId) => detach(nodeId, targetId, refFileId),
    // 사유도 같이 지운다 — 거절당한 그 파일을 내렸는데 문구만 남으면 고친 줄을 모른다.
    onDropPending: (index) => {
      drafts.dropAt(nodeId, index)
      note(nodeId, null)
    },
  })

  return {
    /** 캔버스에서 고른 노드의 칸 */
    selected: slotFor(
      selected?.canvasId ?? '',
      sourceId,
      sourceId ? (detail.data?.refFiles ?? []) : [],
      !!sourceId && detail.isPending,
    ),
    /** 「노드 추가」 폼의 칸. 아직 카드가 없어 들고만 있는다 */
    draft: slotFor(DRAFT_NODE_KEY, '', []),
    /** 폼에 담긴 것을 방금 놓은 카드 앞으로 옮긴다 */
    handOff: (canvasId: string) => drafts.move(DRAFT_NODE_KEY, canvasId),
    /** 폼을 닫거나 카드로 옮긴 뒤 비운다. 사유도 같이 지운다 */
    clearDraft: () => {
      drafts.keep(DRAFT_NODE_KEY, [])
      note(DRAFT_NODE_KEY, null)
    },
    /** 아직 못 올려 남은 자료의 수 */
    unsentIn: (canvasIds: readonly string[]) => drafts.countIn(canvasIds),
    /** 저장으로 원본이 생긴 카드에 차례로 올린다. 실패한 것은 남겨 둔다 */
    flush: async (sourceIdByCanvasId: Record<string, string>) => {
      for (const [canvasId, targetId] of Object.entries(sourceIdByCanvasId)) {
        const files = drafts.filesOf(canvasId)
        if (files.length) await sendAll(canvasId, targetId, files)
      }
    },
  }
}
