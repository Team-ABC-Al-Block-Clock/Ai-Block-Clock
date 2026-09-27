/** 결과 JSON(엔진 봉투)에서 사람이 읽을 본문. 글 출력은 `payload.text`, JSON 출력은 `payload` 그대로다. */
export function resultTextOf(envelope: unknown): string | null {
  if (!envelope || typeof envelope !== 'object') return null
  const payload = (envelope as { payload?: unknown }).payload
  if (!payload || typeof payload !== 'object') return null
  const text = (payload as { text?: unknown }).text
  if (typeof text === 'string' && Object.keys(payload).length === 1) return text
  return JSON.stringify(payload, null, 2)
}
