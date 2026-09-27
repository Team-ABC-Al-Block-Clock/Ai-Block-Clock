/** 결과 문서에 쓰는 마크다운 조각. LLM 이 흔히 내는 것만 다룬다 — 나머지는 문단으로 둔다. */
export type MarkdownBlock =
  | { type: 'heading'; level: number; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; start: number; items: ListItem[] }
  | { type: 'table'; header: string[]; rows: string[][] }
  | { type: 'code'; text: string }
  | { type: 'rule' }

/** 목록 한 줄과 그 아래 들여 쓴 하위 항목. */
export interface ListItem {
  text: string
  children: string[]
}

const HEADING = /^(#{1,6})\s+(.*)$/
const BULLET = /^ {0,1}[-*+]\s+(.*)$/
// 번호는 세 자리까지만 본다. `2026. 9. 27. 기준` 같은 날짜 줄이 목록이 되지 않게 한다.
const NUMBERED = /^ {0,1}(\d{1,3})[.)]\s+(.*)$/
const SUB_ITEM = /^\s{2,}(?:[-*+]|\d{1,3}[.)])\s+(.*)$/
/** 밑줄 제목. 문장 바로 아래 `===` 는 1단, `---` 는 2단 제목이다. */
const SETEXT = /^\s*(=+|-+)\s*$/
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

    const trimmed = line.trim()
    if (trimmed.startsWith('```')) {
      // 한 줄에 여닫힌 묶음은 그 줄만 코드다.
      const inline = trimmed.length > 6 && trimmed.endsWith('```')
      if (inline) {
        blocks.push({
          type: 'code',
          text: trimmed
            .slice(3, -3)
            .replace(/^[\w-]+\s/, '')
            .trim(),
        })
        i++
        continue
      }
      const close = lines.findIndex((it, at) => at > i && it.trim().startsWith('```'))
      // 닫는 줄이 없으면 뒤를 다 삼키지 않고 이 줄만 문단으로 둔다.
      if (close < 0) {
        blocks.push({ type: 'paragraph', text: trimmed })
        i++
        continue
      }
      blocks.push({ type: 'code', text: lines.slice(i + 1, close).join('\n') })
      i = close + 1
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

    const numbered = line.match(NUMBERED)
    if (numbered || BULLET.test(line) || SUB_ITEM.test(line)) {
      const ordered = !!numbered
      const top = ordered ? NUMBERED : BULLET
      const text = (it: string) => (ordered ? it.match(NUMBERED)![2] : it.match(top)?.[1])
      const items: ListItem[] = []
      while (i < lines.length) {
        const here = lines[i]
        if (top.test(here) || (items.length === 0 && SUB_ITEM.test(here))) {
          items.push({ text: text(here) ?? here.match(SUB_ITEM)![1], children: [] })
          i++
        } else if (items.length > 0 && SUB_ITEM.test(here)) {
          items[items.length - 1].children.push(here.match(SUB_ITEM)![1])
          i++
        } else if (here.trim() === '') {
          // 빈 줄 뒤에 같은 목록이 이어지면 한 목록이다. 번호가 1. 1. 1. 로 끊기지 않게 한다.
          let next = i
          while (next < lines.length && lines[next].trim() === '') next++
          if (next < lines.length && top.test(lines[next])) i = next
          else break
        } else break
      }
      blocks.push({ type: 'list', ordered, start: numbered ? Number(numbered[1]) : 1, items })
      continue
    }

    const underline = lines[i + 1]?.match(SETEXT)
    if (underline) {
      blocks.push({ type: 'heading', level: underline[1][0] === '=' ? 1 : 2, text: line.trim() })
      i += 2
      continue
    }

    const body: string[] = []
    while (
      i < lines.length &&
      !(body.length > 0 && SETEXT.test(lines[i + 1] ?? '')) &&
      lines[i].trim() !== '' &&
      !HEADING.test(lines[i]) &&
      !lines[i].trim().startsWith('```') &&
      !RULE.test(lines[i]) &&
      !BULLET.test(lines[i]) &&
      !NUMBERED.test(lines[i]) &&
      !SUB_ITEM.test(lines[i])
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

/** 파이썬 `str` 과 같게 적는다. 엔진이 PDF 를 그 글자로 만든다. */
const pyStr = (value: unknown) =>
  value === true ? 'True' : value === false ? 'False' : String(value)

/** 한 줄에 눕히는 값. 엔진 `shape._inline` 과 같다. */
function inline(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) return value.map(inline).join(', ')
  if (typeof value === 'object')
    return Object.entries(value as Record<string, unknown>)
      .filter(([, sub]) => sub !== null)
      .map(([key, sub]) => `${key}: ${inline(sub)}`)
      .join(' · ')
  return pyStr(value)
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
          : pyStr(value)
      return `## ${key}\n\n${body}`
    })
    .join('\n\n')
}
