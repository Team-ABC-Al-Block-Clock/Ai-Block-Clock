import type { NodeOutput, OutputKind } from '@/api/nodes'

/** 폼이 들고 있는 출력 형식. 예시는 고치는 동안 JSON 이 아닐 수 있어 글자로 둔다. */
export interface OutputDraft {
  kind: OutputKind
  text: string
}

export const toOutputDraft = (output?: NodeOutput | null): OutputDraft => ({
  kind: output?.kind ?? 'TEXT',
  text: output?.example ? JSON.stringify(output.example, null, 2) : '',
})

export const EXAMPLE_INVALID = '출력 예시를 키가 하나 이상인 JSON 객체로 적어 주세요'

/** 서버가 받는 값으로. 예시가 틀렸으면 `null`. */
export function readOutputDraft(draft: OutputDraft): NodeOutput | null {
  if (draft.kind === 'TEXT') return { kind: 'TEXT', example: null }
  try {
    const example: unknown = JSON.parse(draft.text)
    const isObject = typeof example === 'object' && example !== null && !Array.isArray(example)
    return isObject && Object.keys(example).length > 0
      ? { kind: 'JSON', example: example as Record<string, unknown> }
      : null
  } catch {
    return null
  }
}

/** 같은 값인지. 예시의 들여쓰기만 바뀐 것은 고친 것으로 보지 않는다. */
export const sameOutput = (a: NodeOutput | null, b: NodeOutput | null) =>
  JSON.stringify(a) === JSON.stringify(b)
