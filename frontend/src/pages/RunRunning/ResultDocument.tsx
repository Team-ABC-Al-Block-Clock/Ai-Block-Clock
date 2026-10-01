import type { CSSProperties } from 'react'

import { parseInline, type MarkdownBlock } from '@/lib/run/markdown'

const TEXT = 'rgba(255,255,255,.8)'
const LINE = 'rgba(255,255,255,.14)'

const HEADING_SIZE: Record<number, string> = { 1: '21px', 2: '19px', 3: '17.5px' }

const CELL: CSSProperties = {
  padding: '6px 9px',
  borderBottom: `1px solid ${LINE}`,
  textAlign: 'left',
  verticalAlign: 'top',
  wordBreak: 'break-word',
}

function Inline({ text }: { text: string }) {
  return parseInline(text).map((part, i) =>
    part.code ? (
      <code
        key={i}
        className="font-mono text-[.9em]"
        style={{ padding: '1px 5px', borderRadius: '5px', background: 'rgba(255,255,255,.1)' }}
      >
        {part.text}
      </code>
    ) : part.bold ? (
      <strong key={i} className="font-bold text-white">
        {part.text}
      </strong>
    ) : (
      <span key={i}>{part.text}</span>
    ),
  )
}

function Block({ block }: { block: MarkdownBlock }) {
  switch (block.type) {
    case 'heading':
      return (
        <div
          className="font-bold tracking-[-.01em] text-white"
          style={{ fontSize: HEADING_SIZE[block.level] ?? '16px', marginTop: '6px' }}
        >
          <Inline text={block.text} />
        </div>
      )
    case 'paragraph':
      return (
        <p className="m-0">
          <Inline text={block.text} />
        </p>
      )
    case 'list': {
      const List = block.ordered ? 'ol' : 'ul'
      return (
        <List
          className="m-0 flex flex-col"
          start={block.ordered ? block.start : undefined}
          style={{
            paddingLeft: '20px',
            gap: '4px',
            listStyle: block.ordered ? 'decimal' : 'disc',
          }}
        >
          {block.items.map((item, i) => (
            <li key={i}>
              <Inline text={item.text} />
              {item.children.length > 0 && (
                <ul
                  className="m-0 flex flex-col"
                  style={{ paddingLeft: '18px', gap: '2px', listStyle: 'circle', marginTop: '2px' }}
                >
                  {item.children.map((child, c) => (
                    <li key={c}>
                      <Inline text={child} />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </List>
      )
    }
    case 'table':
      return (
        <div style={{ overflowX: 'auto' }}>
          <table
            className="w-full text-[14.5px]"
            style={{ borderCollapse: 'collapse', border: `1px solid ${LINE}` }}
          >
            <thead>
              <tr style={{ background: 'rgba(255,255,255,.07)' }}>
                {block.header.map((cell, i) => (
                  <th key={i} className="font-semibold text-white" style={CELL}>
                    <Inline text={cell} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, r) => (
                <tr key={r}>
                  {block.header.map((_, c) => (
                    <td key={c} style={CELL}>
                      <Inline text={row[c] ?? ''} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )
    case 'code':
      return (
        <pre
          className="scroll-thin m-0 font-mono text-[13.5px]"
          style={{
            padding: '10px 12px',
            borderRadius: '9px',
            background: 'rgba(0,0,0,.22)',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            lineHeight: 1.5,
          }}
        >
          {block.text}
        </pre>
      )
    case 'rule':
      return <hr className="m-0 border-0" style={{ borderTop: `1px solid ${LINE}` }} />
  }
}

/** 실행 결과 문서. 글 결과는 마크다운으로, JSON 결과는 키를 제목으로 편 조각으로 받는다. */
export default function ResultDocument({ blocks }: { blocks: MarkdownBlock[] }) {
  return (
    <div
      className="flex flex-col text-[16px] tracking-[-.005em]"
      style={{ gap: '12px', color: TEXT, lineHeight: 1.65 }}
    >
      {blocks.map((block, i) => (
        <Block key={i} block={block} />
      ))}
    </div>
  )
}
