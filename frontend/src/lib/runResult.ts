import { jsonToBlocks, parseMarkdown, type MarkdownBlock } from '@/lib/markdown'

/** 화면에 그릴 조각과 복사할 원문. */
export interface RunResult {
  blocks: MarkdownBlock[]
  text: string
}

/** 결과 JSON(엔진 봉투)에서 본문. 글 출력은 `payload.text` 가 마크다운이고, JSON 출력은 `payload` 가 곧 결과다. */
export function resultOf(envelope: unknown): RunResult | null {
  if (!envelope || typeof envelope !== 'object') return null
  const payload = (envelope as { payload?: unknown }).payload
  if (!payload || typeof payload !== 'object') return null
  const text = (payload as { text?: unknown }).text
  if (typeof text === 'string' && Object.keys(payload).length === 1)
    return { blocks: parseMarkdown(text), text }
  return { blocks: jsonToBlocks(payload), text: JSON.stringify(payload, null, 2) }
}
