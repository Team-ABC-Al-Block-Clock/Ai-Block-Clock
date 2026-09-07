import type { CSSProperties } from 'react'

/** 'left:10px;top:4px' 같은 CSS 문자열을 React style 객체로.
 *  화면 로직이 스타일을 통째로 문자열로 만들어 넘기는 자리가 있어 필요합니다. */
export function cssToStyle(text: string): CSSProperties {
  const out: Record<string, string> = {}
  for (const part of (text || '').split(';')) {
    const at = part.indexOf(':')
    if (at < 0) continue
    const key = part.slice(0, at).trim()
    const val = part.slice(at + 1).trim()
    if (!key || !val) continue
    const camel = key.startsWith('-webkit-')
      ? 'Webkit' +
        key
          .slice(8)
          .replace(/-(\w)/g, (_, c) => c.toUpperCase())
          .replace(/^\w/, (c) => c.toUpperCase())
      : key.replace(/-(\w)/g, (_, c) => c.toUpperCase())
    out[camel] = val
  }
  return out as CSSProperties
}
