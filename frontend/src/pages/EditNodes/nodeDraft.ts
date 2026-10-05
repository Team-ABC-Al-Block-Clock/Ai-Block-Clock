import { DEFAULT_AI_MODEL, type AiModel } from '@/api/nodes'
import { toOutputDraft } from '@/lib/canvas/nodeOutput'

/** 「노드 추가」 패널에 적는 값. 저장하기 전까지 서버에는 없다. */
export interface NodeDraft {
  name: string
  prompt: string
  model: AiModel
  output: ReturnType<typeof toOutputDraft>
}

export const EMPTY_DRAFT: NodeDraft = {
  name: '',
  prompt: '',
  model: DEFAULT_AI_MODEL,
  output: toOutputDraft(),
}
