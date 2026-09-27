/** 결과 문서에 쓰는 마크다운 조각. LLM 이 흔히 내는 것만 다룬다 — 나머지는 문단으로 둔다. */
export type MarkdownBlock =
  | { type: 'heading'; level: number; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'table'; header: string[]; rows: string[][] }
  | { type: 'code'; text: string }
  | { type: 'rule' }

const HEADING = /^(#{1,6})\s+(.*)$/
const BULLET = /^\s*[-*+]\s+(.*)$/
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/
const RULE = /^\s*(-{3,}|\*{3,}|_{3,})\s*$/
const TABLE_DIVIDER = /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$/

const cells = (line: string) =>
  line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim())

export function parseMarkdown(source: string): MarkdownBlock[] {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const blocks: MarkdownBlock[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]

    if (line.trim() === '') {
      i++
      continue
    }

    if (line.trim().startsWith('```')) {
      const body: string[] = []
      i++
      while (i < lines.length && !lines[i].trim().startsWith('```')) body.push(lines[i++])
      i++
      blocks.push({ type: 'code', text: body.join('\n') })
      continue
    }

    const heading = line.match(HEADING)
    if (heading) {
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2].trim() })
      i++
      continue
    }

    if (RULE.test(line)) {
      blocks.push({ type: 'rule' })
      i++
      continue
    }

    if (line.includes('|') && TABLE_DIVIDER.test(lines[i + 1] ?? '')) {
      const header = cells(line)
      const rows: string[][] = []
      i += 2
      while (i < lines.length && lines[i].includes('|') && lines[i].trim() !== '')
        rows.push(cells(lines[i++]))
      blocks.push({ type: 'table', header, rows })
      continue
    }

    const ordered = NUMBERED.test(line)
    if (ordered || BULLET.test(line)) {
      const pattern = ordered ? NUMBERED : BULLET
      const items: string[] = []
      while (i < lines.length && pattern.test(lines[i])) items.push(lines[i++].match(pattern)![1])
      blocks.push({ type: 'list', ordered, items })
      continue
    }

    const body: string[] = []
    while (
      i < lines.length &&
      lines[i].trim() !== '' &&
      !HEADING.test(lines[i]) &&
      !lines[i].trim().startsWith('```') &&
      !BULLET.test(lines[i]) &&
      !NUMBERED.test(lines[i])
    )
      body.push(lines[i++].trim())
    blocks.push({ type: 'paragraph', text: body.join(' ') })
  }

  return blocks
}

export type InlinePart = { text: string; bold?: boolean; code?: boolean }

/** 문장 안의 `**굵게**` 와 `` `코드` `` 만 가른다. 한 줄에 든 ``` 묶음도 코드로 본다. */
export function parseInline(text: string): InlinePart[] {
  const parts: InlinePart[] = []
  const pattern = /\*\*(.+?)\*\*|```(?:\w+\s)?([^`]+?)```|`([^`]+)`/g
  let last = 0
  for (const match of text.matchAll(pattern)) {
    if (match.index > last) parts.push({ text: text.slice(last, match.index) })
    parts.push(
      match[1] !== undefined
        ? { text: match[1], bold: true }
        : { text: (match[2] ?? match[3]).trim(), code: true },
    )
    last = match.index + match[0].length
  }
  if (last < text.length) parts.push({ text: text.slice(last) })
  return parts
}

/** 한 줄에 눕히는 값. 엔진 `shape._inline` 과 같다. */
function inline(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map(inline).join(', ')
  if (typeof value === 'object')
    return Object.entries(value as Record<string, unknown>)
      .filter(([, sub]) => sub !== null)
      .map(([key, sub]) => `${key}: ${inline(sub)}`)
      .join(' · ')
  return String(value)
}

/**
 * JSON 결과를 문서 마크다운으로. 엔진 `shape.to_markdown` 과 같은 규칙이라 받은 PDF 와 같은 글이 된다.
 *
 * 키가 제목이고 맨 위에서 null 인 키는 뺀다. 한 겹만 펴고 그 아래는 한 줄로 눕힌다.
 */
export function jsonToMarkdown(payload: Record<string, unknown>): string {
  return Object.entries(payload)
    .filter(([, value]) => value !== null)
    .map(([key, value]) => {
      const body = Array.isArray(value)
        ? value.map((item) => `- ${inline(item)}`).join('\n')
        : typeof value === 'object'
          ? Object.entries(value as Record<string, unknown>)
              .map(([sub, item]) => `- **${sub}**: ${inline(item)}`)
              .join('\n')
          : String(value)
      return `## ${key}\n\n${body}`
    })
    .join('\n\n')
}
