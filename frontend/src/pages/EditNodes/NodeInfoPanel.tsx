import { useState } from 'react'

import { AI_MODELS, NODE_NAME_MAX, type AiModel } from '@/api/nodes'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

/**
 * 패널 위쪽 — 고른 노드를 살펴보고 고치는 자리.
 *
 * 세로는 424px 까지다. 그 아래는 팔레트가 쓴다(`NodePalettePanel`) — 같은 `SidePanel` 안에
 * 절대 배치라, 넘치면 팔레트가 위를 덮어 버튼이 눌리지 않는다.
 *
 * 고친 값은 캔버스에만 머문다. 기본 노드는 워크플로우를 저장할 때 배치와 함께
 * 서버에 만들어지기 때문이다(`NodePlacementRequest`).
 */

const labelStyle = (top: string) => ({
  left: `${PANEL_INSET}px`,
  top,
  color: 'rgba(255,255,255,.92)',
})

const rowStyle = (top: string) => ({
  left: `${PANEL_INSET}px`,
  top,
  width: `${PANEL_CONTENT_WIDTH}px`,
})

type FormValues = { name: string; prompt: string; model: AiModel }

/** 고칠 수 있는 자리임을 알린다. 워크플로우 이름 칸과 같은 그림이다. */
function PencilIcon({ left, top }: { left: number; top: number }) {
  return (
    <svg
      className="pointer-events-none absolute"
      width="15"
      height="15"
      viewBox="0 0 14 14"
      fill="none"
      style={{ left: `${left}px`, top: `${top}px` }}
    >
      <path
        d="M0 12.5L1.5 8L9.5 0L12.5 3L4.5 11L0 12.5ZM0 12.5L3.7 11.8"
        stroke="rgba(255,255,255,.6)"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** 이름·프롬프트·모델을 담는 자리. */
function NodeForm({ initial, onApply }: { initial: FormValues; onApply: (v: FormValues) => void }) {
  const [name, setName] = useState(initial.name)
  const [prompt, setPrompt] = useState(initial.prompt)
  const [model, setModel] = useState<AiModel>(initial.model)

  const changed = name !== initial.name || prompt !== initial.prompt || model !== initial.model
  const canApply = changed && !!name.trim() && !!prompt.trim()

  return (
    <>
      <input
        className="absolute rounded-md border-0 bg-transparent font-[inherit] text-[18px] font-semibold tracking-[-.005em] text-white outline-0 transition-colors duration-[160ms] hover:bg-white/10 focus:bg-white/14"
        value={name}
        maxLength={NODE_NAME_MAX}
        onChange={(ev) => setName(ev.target.value)}
        placeholder="노드 이름"
        aria-label="노드 이름"
        // 머리 카드의 글자 칸과 같은 자리 — 30(inset) + 16(padding) + 48(아이콘) + 16(gap).
        style={{ left: '104px', top: '78px', width: '214px', height: '30px', padding: '0 6px' }}
      />
      <PencilIcon left={322} top={85} />

      <textarea
        className="scroll-thin absolute resize-none border-0 font-[inherit] text-[14px] leading-[1.6] text-white outline-0"
        value={prompt}
        onChange={(ev) => setPrompt(ev.target.value)}
        placeholder="이 노드가 할 일을 프롬프트로 적으세요"
        aria-label="프롬프트"
        style={{
          ...rowStyle('192px'),
          height: '108px',
          borderRadius: '10px',
          background: 'var(--color-surface-inset)',
          padding: '10px 14px',
        }}
      />

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('312px')}>
        모델
      </div>
      <select
        className="absolute cursor-pointer border-0 font-[inherit] text-[15.5px] text-white outline-0"
        value={model}
        onChange={(ev) => setModel(ev.target.value as AiModel)}
        aria-label="모델"
        style={{
          ...rowStyle('334px'),
          height: '38px',
          borderRadius: '10px',
          padding: '0 14px',
          background: 'var(--color-surface-inset)',
        }}
      >
        {AI_MODELS.map((item) => (
          <option key={item.value} value={item.value} style={{ color: '#20121c' }}>
            {item.label}
          </option>
        ))}
      </select>

      <div className="absolute flex justify-end" style={rowStyle('382px')}>
        <button
          type="button"
          disabled={!canApply}
          onClick={() => onApply({ name: name.trim(), prompt: prompt.trim(), model })}
          title={canApply ? '고친 값을 캔버스에 반영합니다' : '바뀐 것이 없습니다'}
          className="border-0 font-[inherit] text-[14px] font-semibold disabled:cursor-default disabled:opacity-45"
          style={{
            height: '34px',
            padding: '0 18px',
            borderRadius: '17px',
            background: 'var(--color-accent)',
            color: 'var(--color-accent-ink)',
            cursor: canApply ? 'pointer' : 'default',
          }}
        >
          저장
        </button>
      </div>
    </>
  )
}

type NodeInfoPanelProps = {
  /** 캔버스에서 고른 노드가 있는가. */
  hasSelection: boolean
  name: string
  /** 이름 첫 글자. 아이콘이 아직 없어 이것으로 대신한다. */
  initial: string
  typeLabel: string
  /**
   * 고른 것이 캔버스에서 적은 기본 노드일 때의 값.
   *
   * 에이전트면 undefined — 남의 내부 구성이라 열지 않고 한 줄 설명만 보여준다.
   */
  basic?: { id: string; name: string; prompt: string; model: AiModel }
  /** 에이전트의 한 줄 설명. */
  summary?: string | null
  /** 고친 값을 캔버스에 돌려준다. 저장은 워크플로우를 저장할 때 서버로 함께 나간다. */
  onApply: (id: string, values: { name: string; prompt: string; model: AiModel }) => void
}

/** 패널 위쪽 — 고른 노드를 살펴보고 고치는 자리. */
export default function NodeInfoPanel({
  hasSelection,
  name,
  initial,
  typeLabel,
  basic,
  summary,
  onApply,
}: NodeInfoPanelProps) {
  if (!hasSelection)
    return (
      <div
        className="absolute text-[15.5px]"
        style={{ ...rowStyle('64px'), color: 'rgba(255,236,246,.66)' }}
      >
        캔버스에서 노드를 선택하면 상세 정보를 조회할 수 있습니다
      </div>
    )

  return (
    <>
      <div
        className="absolute box-border flex items-center"
        style={{
          ...rowStyle('58px'),
          height: '104px',
          borderRadius: '12px',
          background: 'rgba(255,255,255,.11)',
          padding: '0 16px',
          gap: '16px',
        }}
      >
        <div
          className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.10)]"
          style={{ width: '48px', height: '48px', borderRadius: '10px' }}
        >
          <div
            className="absolute inset-0 flex items-center justify-center text-[17px] font-semibold"
            style={{ color: 'rgba(255,226,238,.65)' }}
          >
            {initial}
          </div>
        </div>
        <div className="flex min-w-0 flex-col" style={{ gap: '4px' }}>
          {/* 고칠 수 있을 때는 이름 입력이 이 자리를 덮는다. */}
          <div
            className="truncate text-[18px] font-semibold tracking-[-.005em] text-white"
            style={{ visibility: basic ? 'hidden' : 'visible' }}
          >
            {name}
          </div>
          <div className="text-[14.5px]" style={{ color: 'rgba(255,224,238,.88)' }}>
            {typeLabel} 노드
          </div>
        </div>
      </div>

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('168px')}>
        {basic ? '프롬프트' : '설명'}
      </div>

      {basic ? (
        <NodeForm
          key={basic.id}
          initial={{ name: basic.name, prompt: basic.prompt, model: basic.model }}
          onApply={(values) => onApply(basic.id, values)}
        />
      ) : (
        <div
          className="scroll-thin absolute text-[15px] leading-[1.7]"
          style={{
            ...rowStyle('192px'),
            maxHeight: '212px',
            color: summary ? 'rgba(255,242,249,.9)' : 'rgba(255,236,246,.5)',
          }}
        >
          {summary ?? '설명이 없습니다'}
        </div>
      )}
    </>
  )
}
