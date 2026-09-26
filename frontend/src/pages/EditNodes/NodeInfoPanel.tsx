import { useState } from 'react'

import { NODE_NAME_MAX, type AiModel } from '@/api/nodes'
import type { ConditionOperator } from '@/api/workflows'
import {
  CONDITION_OPERATORS,
  formatConditionRight,
  parseConditionRight,
} from '@/lib/branchCondition'
import type { CanvasCondition } from '@/lib/canvasWorkflow'
import { priceLabel } from '@/lib/format'
import ModelSelect from '@/components/ModelSelect'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import type { RefFileSlot } from '@/hooks/useNodeRefFiles'
import RefFileField, { REF_FIELD_HEIGHT } from '@/components/RefFileField'

/**
 * 패널 위쪽 — 고른 노드를 살펴보고 고치는 자리.
 *
 * 세로는 `PANEL_SPLIT_Y` 까지다. 그 아래는 팔레트가 쓴다(`NodePalettePanel`) — 같은 `SidePanel`
 * 안에 절대 배치라, 넘치면 팔레트가 위를 덮어 버튼이 눌리지 않는다.
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

/**
 * 폼의 세로 좌표. 라벨은 칸에서 22px 위다.
 *
 * 자료 칸이 늘어난 만큼 아래가 통째로 내려간다 — 마지막 줄이 `PANEL_SPLIT_Y` 를 넘으면
 * 팔레트가 덮는다.
 */
const FILES_Y = 156
const MODEL_Y = FILES_Y + REF_FIELD_HEIGHT + 30
const PROMPT_Y = MODEL_Y + 72
/** 안쪽 여백을 빼면 다섯 줄이 선다. 두 줄로는 프롬프트를 적다가 바로 스크롤이 생긴다. */
const PROMPT_HEIGHT = 150
const FOOT_Y = PROMPT_Y + PROMPT_HEIGHT + 10

/** 에이전트 쪽 세로 좌표. 설명은 길이에 따라 늘고, 메타 세 줄은 그 아래 고정이다. */
const SUMMARY_Y = 206
const META_Y = 366

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

/** 조건이 읽을 수 있는 앞 노드. 이름만 있으면 고르게 할 수 있다. */
export interface ConditionSource {
  id: string
  name: string
}

/** 패널 안의 드롭다운. 네이티브 `select` 로 둔다 — 값이 몇 개 없고 키보드가 그냥 먹는다. */
function Picker<T extends string>({
  value,
  onChange,
  options,
  label,
  top,
  width,
}: {
  value: T
  onChange: (next: T) => void
  options: { value: T; label: string }[]
  label: string
  top: string
  /** 비우면 패널 폭 전체 */
  width?: number
}) {
  return (
    <div
      className="bg-surface-card absolute flex items-center"
      style={{
        ...rowStyle(top),
        ...(width === undefined ? {} : { width: `${width}px` }),
        height: '42px',
        borderRadius: '11px',
        padding: '0 8px 0 12px',
      }}
    >
      <select
        className="w-full cursor-pointer border-0 bg-transparent font-[inherit] text-[16.5px] text-white outline-0"
        value={value}
        onChange={(ev) => onChange(ev.target.value as T)}
        aria-label={label}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value} style={{ color: '#1d1430' }}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  )
}

/**
 * 분기 조건을 고치는 자리.
 *
 * 읽을 노드는 고르게만 한다 — 손으로 적게 두면 앞에 없는 노드를 가리켜 `WORKFLOW_013` 을
 * 받는데, 코드 하나에 여러 원인이 몰려 있어 무엇이 틀렸는지 돌려주지 못한다.
 */
function BranchForm({
  id,
  head,
  initial,
  sources,
  onApply,
}: {
  id: string
  head: { initial: string; name: string; typeLabel: string }
  initial: CanvasCondition | null
  sources: ConditionSource[]
  onApply: (condition: CanvasCondition) => void
}) {
  const [fromId, setFromId] = useState(initial?.fromId ?? sources[0]?.id ?? '')
  const [path, setPath] = useState(initial?.path ?? '')
  const [operator, setOperator] = useState<ConditionOperator>(initial?.operator ?? 'EQ')
  const [right, setRight] = useState(initial ? formatConditionRight(initial.right) : '')

  const parsed = parseConditionRight(right, operator)
  /**
   * 고른 값이 후보에 없으면 첫 후보로 본다.
   *
   * 폼은 분기 id 로만 다시 서서, 분기를 고른 채 앞에 노드를 이으면 후보만 늘고 `fromId` 는
   * 빈 채로 남는다. 그대로 두면 `select` 는 첫 후보를 보여주는데 값은 비어 있어 「적용」이
   * 영영 죽는다 — 후보가 하나뿐이면 골라서 빠져나올 수도 없다.
   */
  const picked = sources.some((source) => source.id === fromId) ? fromId : (sources[0]?.id ?? '')
  const canApply = !!picked && !!path.trim() && parsed !== null

  if (sources.length === 0)
    return (
      <>
        <HeadCard {...head} />
        <div
          className="absolute text-[15.5px] leading-[1.7]"
          style={{ ...rowStyle('180px'), color: 'rgba(255,236,246,.66)' }}
        >
          조건은 분기 앞에 놓인 노드나 에이전트의 결과를 읽습니다. 읽을 것을 분기 앞에 이어 주세요.
        </div>
      </>
    )

  return (
    <>
      <HeadCard {...head} />

      <FieldLabel top="176px">읽을 노드</FieldLabel>
      <Picker
        top="198px"
        label="읽을 노드"
        value={picked}
        onChange={setFromId}
        options={sources.map((source) => ({ value: source.id, label: source.name }))}
      />

      <FieldLabel top="248px">값 경로</FieldLabel>
      <div
        className="bg-surface-card absolute flex items-center"
        style={{ ...rowStyle('270px'), height: '42px', borderRadius: '11px', padding: '0 12px' }}
      >
        <input
          className="w-full border-0 bg-transparent font-[inherit] text-[16.5px] text-white outline-0"
          value={path}
          onChange={(ev) => setPath(ev.target.value)}
          placeholder="예: competition_level"
          aria-label="값 경로"
        />
        <PencilIcon />
      </div>

      {/* 비교와 값은 한 줄에 둔다. 따로 쌓으면 패널 높이(424px)를 넘어 팔레트가 덮는다. */}
      <FieldLabel top="320px">비교할 값</FieldLabel>
      <Picker
        top="342px"
        label="비교"
        value={operator}
        onChange={setOperator}
        options={CONDITION_OPERATORS}
        width={132}
      />
      <div
        className="bg-surface-card absolute flex items-center"
        style={{
          left: `${PANEL_INSET + 140}px`,
          top: '342px',
          width: `${PANEL_CONTENT_WIDTH - 140}px`,
          height: '42px',
          borderRadius: '11px',
          padding: '0 12px',
        }}
      >
        <input
          className="w-full border-0 bg-transparent font-[inherit] text-[16.5px] text-white outline-0"
          value={right}
          onChange={(ev) => setRight(ev.target.value)}
          placeholder={operator === 'IN' ? '쉼표로 나눠서' : '예: high'}
          aria-label="비교할 값"
        />
        <PencilIcon />
      </div>

      <div
        className="absolute flex items-center justify-between"
        style={{ ...rowStyle('396px'), height: '32px' }}
      >
        <span className="truncate text-[14px]" style={{ color: 'rgba(255,236,246,.6)' }}>
          {/* 참이면 true 포트, 거짓이면 false 포트로 나간다. */}
          조건이 참이면 위쪽 선으로 갑니다
        </span>
        <button
          type="button"
          disabled={!canApply}
          onClick={() =>
            canApply && onApply({ fromId: picked, path: path.trim(), operator, right: parsed })
          }
          title={canApply ? '조건을 캔버스에 반영합니다' : '빈 칸을 채워 주세요'}
          className="border-0 font-[inherit] text-[14px] font-semibold disabled:cursor-default disabled:opacity-45"
          style={{
            height: '32px',
            padding: '0 18px',
            borderRadius: '9px',
            background: 'var(--color-accent)',
            color: 'var(--color-accent-ink)',
            cursor: canApply ? 'pointer' : 'default',
          }}
          key={id}
        >
          적용
        </button>
      </div>
    </>
  )
}

/** 패널 맨 위의 머리 카드. 고른 것이 무엇인지 한 줄로 세운다 — 종류를 가리지 않는다. */
function HeadCard({
  initial,
  name,
  typeLabel,
}: {
  initial: string
  name: string
  typeLabel: string
}) {
  return (
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
  )
}

function NodeForm({
  initial,
  price,
  files,
  onApply,
}: {
  initial: FormValues
  /** 서버가 매긴 1회 실행 원가. 저장하기 전에는 없다 */
  price: string | null
  files: RefFileSlot
  onApply: (v: FormValues) => void
}) {
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

      {/* 「노드 추가」 패널과 같은 자리·같은 차림. 붙이고 떼는 것은 여기서 바로 서버로 나간다. */}
      <FieldLabel top={`${FILES_Y - 22}px`}>입력 파일</FieldLabel>
      <RefFileField slot={files} style={{ position: 'absolute', ...rowStyle(`${FILES_Y}px`) }} />

      <FieldLabel top={`${MODEL_Y - 22}px`}>AI 모델</FieldLabel>
      <ModelSelect
        value={model}
        onChange={setModel}
        style={{ ...rowStyle(`${MODEL_Y}px`), height: '42px', fontSize: '16.5px' }}
      />

      <FieldLabel top={`${PROMPT_Y - 22}px`}>프롬프트</FieldLabel>
      <div
        className="bg-surface-card absolute"
        style={{
          ...rowStyle(`${PROMPT_Y}px`),
          height: `${PROMPT_HEIGHT}px`,
          borderRadius: '11px',
          padding: '9px 12px',
        }}
      >
        <textarea
          className="scroll-thin w-full resize-none border-0 bg-transparent font-[inherit] text-[16.5px] leading-[1.55] text-white outline-0"
          value={prompt}
          onChange={(ev) => setPrompt(ev.target.value)}
          placeholder="이 노드가 할 일을 프롬프트로 적으세요"
          aria-label="프롬프트"
          style={{ height: `${PROMPT_HEIGHT - 18}px` }}
        />
      </div>

      <div
        className="absolute flex items-center justify-between"
        style={{ ...rowStyle(`${FOOT_Y}px`), height: '32px' }}
      >
        <span className="truncate text-[14px]" style={{ color: 'rgba(255,236,246,.6)' }}>
          {/* 원가는 서버가 프롬프트와 모델로 매긴다. 저장해 봐야 안다. */}
          {price ? `1회 실행 원가 ${priceLabel(price)}` : '실행 단계에서 원가가 측정됩니다'}
        </span>
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
  basic?: { id: string; name: string; prompt: string; model: AiModel; price: string | null }
  /** 고른 기본 노드에 붙은 참고 자료. 에이전트면 쓰지 않는다 */
  files: RefFileSlot
  /** 에이전트의 한 줄 설명. */
  summary?: string | null
  /** 에이전트의 단가·실행 횟수·만든 사람. 모르는 값은 비워 둔다. */
  meta?: { price: string | null; executionCount?: number | null; owner?: string | null }
  /** 고른 것이 분기 노드일 때. `sources` 는 어느 길로 와도 앞에 있는 노드·에이전트다. */
  branch?: { id: string; condition: CanvasCondition | null; sources: ConditionSource[] }
  /** 고친 조건을 캔버스에 돌려준다. */
  onApplyCondition: (id: string, condition: CanvasCondition) => void
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
  files,
  summary,
  meta,
  branch,
  onApply,
  onApplyCondition,
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

  // 분기는 이름이 종류로 정해져 있어 고칠 것이 조건뿐이다.
  if (branch)
    return (
      <BranchForm
        key={branch.id}
        id={branch.id}
        head={{ initial, name, typeLabel }}
        initial={branch.condition}
        sources={branch.sources}
        onApply={(condition) => onApplyCondition(branch.id, condition)}
      />
    )

  // 기본 노드는 폼이 이름을 들고 있어 머리 카드도 종류 줄도 겹친다. 폼만 세운다.
  if (basic)
    return (
      <NodeForm
        key={basic.id}
        initial={{ name: basic.name, prompt: basic.prompt, model: basic.model }}
        price={basic.price}
        files={files}
        onApply={(values) => onApply(basic.id, values)}
      />
    )

  return (
    <>
      <HeadCard initial={initial} name={name} typeLabel={typeLabel} />

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('180px')}>
        설명
      </div>
      <div
        className="scroll-thin absolute text-[16.5px] leading-[1.75] tracking-[-.003em]"
        style={{
          ...rowStyle(`${SUMMARY_Y}px`),
          // 아래 메타 행까지가 한계다. 서버 상한(80자)이면 이 폭에서 넉 줄이라 86px 로는 잘렸다.
          maxHeight: `${META_Y - SUMMARY_Y - 14}px`,
          color: summary ? 'rgba(255,246,251,.94)' : 'rgba(255,236,246,.5)',
        }}
      >
        {summary ?? '설명이 없습니다'}
      </div>

      {meta && (
        <div className="absolute flex flex-col" style={{ ...rowStyle(`${META_Y}px`), gap: '1px' }}>
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
