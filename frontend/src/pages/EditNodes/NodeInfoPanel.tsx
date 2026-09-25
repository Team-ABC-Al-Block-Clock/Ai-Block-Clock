import { useState } from 'react'

import { NODE_NAME_MAX, type AiModel } from '@/api/nodes'
import { priceLabel } from '@/lib/format'
import ModelSelect from '@/components/ModelSelect'
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

/** 설명 아래 한 줄짜리 값. 라벨은 왼쪽, 값은 오른쪽 끝에 세운다. */
function MetaRow({ label, value }: { label: string; value: string }) {
  return (
    <div
      className="flex items-center justify-between"
      style={{ height: '38px', borderTop: '1px solid rgba(255,255,255,.08)' }}
    >
      <span className="text-[15px]" style={{ color: 'rgba(255,224,238,.7)' }}>
        {label}
      </span>
      <span className="truncate text-[16px] font-semibold text-white">{value}</span>
    </div>
  )
}

/** 고칠 수 있는 자리임을 알린다. 워크플로우 이름 칸과 같은 그림이다. */
function PencilIcon() {
  return (
    <svg
      className="pointer-events-none flex-none"
      width="15"
      height="15"
      viewBox="0 0 14 14"
      fill="none"
      aria-hidden
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

/** 「노드 추가」 패널과 같은 차림 — 라벨 아래 칸 하나. 두 자리가 달라 보이면 같은 값이 아닌 줄 안다. */
function FieldLabel({ top, children }: { top: string; children: React.ReactNode }) {
  return (
    <div
      className="absolute text-[15px]"
      style={{ left: `${PANEL_INSET}px`, top, color: 'rgba(255,214,232,.85)' }}
    >
      {children}
    </div>
  )
}

function NodeForm({ initial, onApply }: { initial: FormValues; onApply: (v: FormValues) => void }) {
  const [name, setName] = useState(initial.name)
  const [prompt, setPrompt] = useState(initial.prompt)
  const [model, setModel] = useState<AiModel>(initial.model)

  const changed = name !== initial.name || prompt !== initial.prompt || model !== initial.model
  const canApply = changed && !!name.trim() && !!prompt.trim()

  return (
    <>
      <FieldLabel top="62px">노드 이름</FieldLabel>
      <div
        className="bg-surface-card absolute flex items-center"
        style={{ ...rowStyle('84px'), height: '42px', borderRadius: '11px', padding: '0 12px' }}
      >
        <input
          className="w-full border-0 bg-transparent font-[inherit] text-[16.5px] text-white outline-0"
          value={name}
          maxLength={NODE_NAME_MAX}
          onChange={(ev) => setName(ev.target.value)}
          placeholder="예: 내용 분석"
          aria-label="노드 이름"
        />
        <PencilIcon />
      </div>

      {/* 「노드 추가」 패널과 같은 자리·같은 차림. 참고 자료 붙이기는 아직 손대지 않았다. */}
      <FieldLabel top="134px">입력 파일</FieldLabel>
      <div
        className="bg-surface-card absolute flex items-center justify-between"
        style={{
          ...rowStyle('156px'),
          height: '42px',
          borderRadius: '11px',
          padding: '0 6px 0 12px',
        }}
      >
        <div className="truncate text-[16.5px]" style={{ color: 'rgba(255,255,255,.5)' }}>
          파일을 선택하세요
        </div>
        <div
          className="flex flex-none items-center text-[14px] font-semibold text-white"
          style={{
            height: '30px',
            padding: '0 14px',
            borderRadius: '8px',
            background: 'rgba(255,255,255,.16)',
          }}
        >
          찾기
        </div>
      </div>

      <FieldLabel top="206px">AI 모델</FieldLabel>
      <ModelSelect
        value={model}
        onChange={setModel}
        style={{ ...rowStyle('228px'), height: '42px', fontSize: '16.5px' }}
      />

      <FieldLabel top="278px">프롬프트</FieldLabel>
      <div
        className="bg-surface-card absolute"
        style={{ ...rowStyle('300px'), height: '68px', borderRadius: '11px', padding: '9px 12px' }}
      >
        <textarea
          className="scroll-thin w-full resize-none border-0 bg-transparent font-[inherit] text-[16.5px] leading-[1.55] text-white outline-0"
          value={prompt}
          onChange={(ev) => setPrompt(ev.target.value)}
          placeholder="이 노드가 할 일을 프롬프트로 적으세요"
          aria-label="프롬프트"
          style={{ height: '50px' }}
        />
      </div>

      <div className="absolute flex justify-end" style={rowStyle('378px')}>
        <button
          type="button"
          disabled={!canApply}
          onClick={() => onApply({ name: name.trim(), prompt: prompt.trim(), model })}
          title={canApply ? '고친 값을 캔버스에 반영합니다' : '바뀐 것이 없습니다'}
          className="border-0 font-[inherit] text-[14px] font-semibold disabled:cursor-default disabled:opacity-45"
          style={{
            height: '32px',
            padding: '0 18px',
            borderRadius: '16px',
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
  /** 에이전트의 단가·실행 횟수·만든 사람. 모르는 값은 비워 둔다. */
  meta?: { price: string | null; executionCount?: number | null; owner?: string | null }
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
  meta,
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

  // 기본 노드는 폼이 이름을 들고 있어 머리 카드도 종류 줄도 겹친다. 폼만 세운다.
  if (basic)
    return (
      <NodeForm
        key={basic.id}
        initial={{ name: basic.name, prompt: basic.prompt, model: basic.model }}
        onApply={(values) => onApply(basic.id, values)}
      />
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
          <div className="truncate text-[18px] font-semibold tracking-[-.005em] text-white">
            {name}
          </div>
          <div className="text-[14.5px]" style={{ color: 'rgba(255,224,238,.88)' }}>
            {typeLabel} 노드
          </div>
        </div>
      </div>

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('180px')}>
        설명
      </div>
      <div
        className="scroll-thin absolute text-[16.5px] leading-[1.75] tracking-[-.003em]"
        style={{
          ...rowStyle('206px'),
          maxHeight: '198px',
          color: summary ? 'rgba(255,246,251,.94)' : 'rgba(255,236,246,.5)',
        }}
      >
        {summary ?? '설명이 없습니다'}
      </div>

      {meta && (
        <div className="absolute flex flex-col" style={{ ...rowStyle('300px'), gap: '1px' }}>
          <MetaRow label="단가" value={meta.price ? priceLabel(meta.price) : '—'} />
          <MetaRow
            label="총 실행 횟수"
            value={
              typeof meta.executionCount === 'number'
                ? `${meta.executionCount.toLocaleString('ko-KR')}회`
                : '—'
            }
          />
          <MetaRow label="만든 사람" value={meta.owner ?? '—'} />
        </div>
      )}
    </>
  )
}
