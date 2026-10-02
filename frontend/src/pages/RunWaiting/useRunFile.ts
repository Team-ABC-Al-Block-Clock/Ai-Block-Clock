import { useState } from 'react'

import { describeRunFileProblem } from '@/api/executions'
import type { RefFileSlot } from '@/hooks/canvas/useNodeRefFiles'

/**
 * 첫 단계가 읽을 파일. 고르지 않아도 실행할 수 있다 — 파일 없이 도는 워크플로우가 있다.
 *
 * 편집 화면의 참고 자료 칸과 같은 모양으로 넘긴다. 명세가 한 건이라 목록은 길어야 한 줄이고,
 * 서버에 미리 올리지 않아 삭제는 화면에서 끝난다.
 */
export function useRunFile() {
  const [file, setFile] = useState<File | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  const slot: RefFileSlot = {
    attached: [],
    pending: file ? [file] : [],
    busy: false,
    loading: false,
    problem,
    onPick: (picked) => {
      const [first] = picked
      if (!first) return
      const found = describeRunFileProblem(first)
      setProblem(found)
      setFile(found ? null : first)
    },
    onDetach: () => undefined,
    onDropPending: () => {
      setFile(null)
      setProblem(null)
    },
  }

  return { file, slot }
}
