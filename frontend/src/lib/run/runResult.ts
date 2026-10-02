import { ApiError, EXECUTION_ERROR } from '@/api/errors'
import { jsonToMarkdown, parseMarkdown, type MarkdownBlock } from '@/lib/run/markdown'

/** 화면에 그릴 조각과 복사할 원문. */
export interface RunResult {
  blocks: MarkdownBlock[]
  text: string
}

/** 결과 JSON(엔진 봉투)에서 본문. 글 출력은 `payload.text` 가 마크다운이고, JSON 출력은 PDF 와 같은 규칙으로 문서를 만든다. */
export function resultOf(envelope: unknown): RunResult | null {
  if (!envelope || typeof envelope !== 'object') return null
  const payload = (envelope as { payload?: unknown }).payload
  if (!payload || typeof payload !== 'object') return null
  const text = (payload as { text?: unknown }).text
  if (typeof text === 'string' && Object.keys(payload).length === 1)
    return { blocks: parseMarkdown(text), text }
  const document = jsonToMarkdown(payload as Record<string, unknown>)
  return { blocks: parseMarkdown(document), text: document }
}

/** 결과 상자에 글 대신 놓는 안내. 그릴 결과가 있으면 `null`. */
export function resultProblem(state: {
  isPending: boolean
  error: unknown
  data: RunResult | null | undefined
}): string | null {
  if (state.data) return null
  if (state.isPending) return '결과를 받는 중입니다…'
  const { error } = state
  if (error instanceof ApiError) {
    if (error.code === EXECUTION_ERROR.RESULT_UNAVAILABLE)
      return '실패로 끝난 실행이라 결과가 없습니다.'
    if (error.code === EXECUTION_ERROR.RESULT_NOT_READY && error.status === 409)
      return '결과가 아직 준비되지 않았습니다. 잠시 뒤 다시 열어 주세요.'
  }
  if (error) return '결과를 받지 못했습니다. PDF 나 JSON 으로 받아 확인해 주세요.'
  return '결과가 비어 있습니다.'
}
