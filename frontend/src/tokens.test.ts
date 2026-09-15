import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * 스타일 토큰이 조용히 어긋나는 두 경로를 막는다.
 *
 * 1. 변수명 오타. `var(--surface-menu)` 처럼 틀린 이름을 써도 CSS 는 오류를 내지 않고
 *    그 선언만 버린다. 예비값까지 붙어 있으면 화면이 멀쩡해 아무도 눈치채지 못한다.
 * 2. 예비값. `var(--x, 값)` 의 예비값은 토큰 값의 두 번째 사본이라 시간이 지나면 갈라진다.
 *    `@theme static` 이 토큰을 항상 `:root` 에 내보내므로 예비값은 이제 필요 없다.
 */

/** jsdom 환경에서는 import.meta.url 이 file: 이 아니다 — vitest 루트(frontend/)를 기준으로 잡는다 */
const SRC = join(process.cwd(), 'src')
if (!existsSync(join(SRC, 'index.css'))) throw new Error(`src 를 찾지 못했다: ${SRC}`)

/** 런타임에 주입돼 토큰 목록에 없는 것이 정상인 변수 */
const RUNTIME_VARS = new Set([
  // App.tsx 가 캔버스와 창 사이 여백을 인라인 style 로 내려준다 — 주입 전에는 없다
  '--edge',
])

const isTailwindInternal = (name: string) => name.startsWith('--tw-')

/** `@theme` 블록을 중괄호 균형으로 잘라내 정의된 토큰 이름을 모은다 */
function themeTokens(css: string): Set<string> {
  const at = css.indexOf('@theme')
  if (at < 0) throw new Error('index.css 에서 @theme 블록을 찾지 못했다')

  let depth = 0
  let end = css.indexOf('{', at)
  for (let i = end; i < css.length; i++) {
    if (css[i] === '{') depth++
    else if (css[i] === '}') {
      depth--
      if (depth === 0) {
        end = i
        break
      }
    }
  }

  const block = css.slice(at, end)
  return new Set(Array.from(block.matchAll(/^\s*(--[a-zA-Z0-9-]+)\s*:/gm), (m) => m[1]))
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) return sourceFiles(path)
    if (!/\.(ts|tsx|css)$/.test(entry.name)) return []
    // 이 파일 자체가 var(--...) 예시를 품고 있어 스스로를 검사하면 안 된다
    if (/\.test\.tsx?$/.test(entry.name)) return []
    return [path]
  })
}

type Ref = { name: string; hasFallback: boolean; where: string }

function varRefs(): Ref[] {
  return sourceFiles(SRC).flatMap((path) => {
    const text = readFileSync(path, 'utf8')
    const rel = path.slice(SRC.length).replace(/\\/g, '/')

    return Array.from(text.matchAll(/var\(\s*(--[a-zA-Z0-9-]+)\s*(,?)/g), (m) => ({
      name: m[1],
      hasFallback: m[2] === ',',
      where: `${rel}:${text.slice(0, m.index).split('\n').length}`,
    }))
  })
}

describe('스타일 토큰', () => {
  const defined = themeTokens(readFileSync(join(SRC, 'index.css'), 'utf8'))
  const refs = varRefs()

  it('검사할 토큰과 참조가 실제로 모인다', () => {
    // 수집이 조용히 0건이 되면 아래 두 검사가 통과해도 아무것도 지키지 못한다
    expect(defined.size).toBeGreaterThan(10)
    expect(refs.length).toBeGreaterThan(10)
  })

  it('참조하는 변수가 모두 @theme 에 정의돼 있다', () => {
    const unknown = refs
      .filter(
        (r) => !defined.has(r.name) && !RUNTIME_VARS.has(r.name) && !isTailwindInternal(r.name),
      )
      .map((r) => `${r.where} ${r.name}`)

    expect(unknown).toEqual([])
  })

  it('토큰 var() 에 예비값을 쓰지 않는다', () => {
    const withFallback = refs
      .filter((r) => r.hasFallback && !RUNTIME_VARS.has(r.name))
      .map((r) => `${r.where} ${r.name}`)

    expect(withFallback).toEqual([])
  })
})
