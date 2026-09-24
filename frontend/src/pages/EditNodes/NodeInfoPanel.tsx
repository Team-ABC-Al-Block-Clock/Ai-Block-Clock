import { useState } from 'react'

import { ApiError } from '@/api/errors'
import { AI_MODELS, NODE_NAME_MAX, type AiModel, type NodeSaveBody } from '@/api/nodes'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import { useNodeDetail } from '@/hooks/useNodeDetail'

/**
 * 패널 위쪽 — 고른 노드를 살펴보고 고치는 자리.
 *
 * 세로는 424px 까지다. 그 아래는 팔레트가 쓴다(`NodePalettePanel`) — 같은 `SidePanel` 안에
 * 절대 배치라, 넘치면 팔레트가 위를 덮어 버튼이 눌리지 않는다.
 */

function messageOf(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback
  if (error.code === 'NODE_001') return '이미 지워진 노드입니다'
  if (error.code === 'NODE_002') return '이 노드를 고칠 권한이 없습니다'
  if (error.code === 'NODE_003') return '프롬프트를 입력해 주세요'
  if (error.code === 'NODE_004') return '워크플로우에 배치된 노드는 지울 수 없습니다'
  // 서버 빈 검증은 이름·프롬프트를 한 코드로 묶어 답한다. 한쪽을 지목하지 않는다.
  if (error.code === 'COMMON_001') return '입력한 값을 확인해 주세요'
  return fallback
}

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

type NodeFormProps = {
  initial: FormValues
  /** 만들 때는 비어 있어도 보낼 수 있다. 고칠 때는 달라진 것이 있어야 한다. */
  requireChange: boolean
  submitLabel: string
  busyLabel: string
  busy: boolean
  status: string
  isError: boolean
  onSubmit: (body: NodeSaveBody) => void
  /** 오른쪽에 붙는 버튼 — 고칠 때는 삭제, 만들 때는 취소. */
  trailing: React.ReactNode
}

/** 이름·프롬프트·모델을 담는 자리. 고치기와 만들기가 같은 모양을 쓴다. */
function NodeForm({
  initial,
  requireChange,
  submitLabel,
  busyLabel,
  busy,
  status,
  isError,
  onSubmit,
  trailing,
}: NodeFormProps) {
  const [name, setName] = useState(initial.name)
  const [prompt, setPrompt] = useState(initial.prompt)
  const [model, setModel] = useState<AiModel>(initial.model)

  const changed = name !== initial.name || prompt !== initial.prompt || model !== initial.model
  const filled = !!name.trim() && !!prompt.trim()
  const canSubmit = filled && !busy && (!requireChange || changed)

  return (
    <>
      <input
        className="absolute border-0 bg-transparent font-[inherit] text-[18px] font-semibold tracking-[-.005em] text-white outline-0"
        value={name}
        maxLength={NODE_NAME_MAX}
        onChange={(ev) => setName(ev.target.value)}
        placeholder="노드 이름"
        aria-label="노드 이름"
        // 머리 카드의 글자 칸과 같은 자리에 선다 — 30(inset) + 16(padding) + 48(아이콘) + 16(gap).
        style={{ left: '110px', top: '81px', width: '224px', height: '24px' }}
      />

      <textarea
        className="scroll-thin absolute resize-none border-0 font-[inherit] text-[14px] leading-[1.6] text-white outline-0"
        value={prompt}
        onChange={(ev) => setPrompt(ev.target.value)}
        placeholder="이 노드가 할 일을 적어 주세요"
        aria-label="프롬프트"
        style={{
          ...rowStyle('192px'),
          height: '80px',
          borderRadius: '10px',
          background: 'var(--color-surface-inset)',
          padding: '10px 14px',
        }}
      />

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('282px')}>
        모델
      </div>
      <select
        className="absolute cursor-pointer border-0 font-[inherit] text-[15.5px] text-white outline-0"
        value={model}
        onChange={(ev) => setModel(ev.target.value as AiModel)}
        aria-label="모델"
        style={{
          ...rowStyle('302px'),
          height: '36px',
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

      <div
        className="absolute truncate text-[12.5px]"
        style={{
          ...rowStyle('346px'),
          color: isError ? 'rgba(255,196,214,.95)' : 'rgba(255,236,246,.55)',
        }}
      >
        {status}
      </div>

      <div className="absolute flex items-center" style={{ ...rowStyle('370px'), gap: '8px' }}>
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => onSubmit({ name: name.trim(), prompt: prompt.trim(), model })}
          className="border-0 font-[inherit] text-[14px] font-semibold disabled:cursor-default disabled:opacity-45"
          style={{
            height: '34px',
            padding: '0 18px',
            borderRadius: '17px',
            background: 'var(--color-accent)',
            color: 'var(--color-accent-ink)',
            cursor: canSubmit ? 'pointer' : 'default',
          }}
        >
          {busy ? busyLabel : submitLabel}
        </button>
        {trailing}
      </div>
    </>
  )
}

type EditProps = {
  nodeId: string
  /** 저장된 워크플로우에 놓인 노드는 서버가 삭제를 막는다(`NODE_004`). */
  deletable: boolean
  onRenamed: (sourceId: string, name: string) => void
  onDeleted: (sourceId: string) => void
}

/**
 * 원본 하나를 읽고 고치는 자리.
 *
 * `key` 에 노드 id 를 받아 노드를 바꿀 때마다 새로 만든다 — 앞 노드의 프롬프트도,
 * 앞 노드에서 난 오류도 따라오지 않는다.
 */
function NodeSource({ nodeId, deletable, onRenamed, onDeleted }: EditProps) {
  const { detail, save, remove } = useNodeDetail(nodeId)
  const node = detail.data

  if (!node)
    return (
      <div
        className="absolute text-[14px] leading-[1.7]"
        style={{ ...rowStyle('192px'), color: 'rgba(255,236,246,.66)' }}
      >
        {detail.isPending ? '불러오는 중' : messageOf(detail.error, '노드를 불러오지 못했습니다')}
      </div>
    )

  const error = save.error ?? remove.error
  const busy = save.isPending || remove.isPending

  return (
    <NodeForm
      key={node.nodeId}
      initial={{ name: node.name, prompt: node.prompt, model: node.model }}
      requireChange
      submitLabel="저장"
      busyLabel="저장 중"
      busy={busy}
      isError={!!error}
      status={
        error
          ? messageOf(error, '저장하지 못했습니다')
          : deletable
            ? '삭제하면 다른 워크플로우에서도 사라집니다'
            : '저장된 워크플로우에 놓여 있어 원본을 지울 수 없습니다'
      }
      onSubmit={(body) =>
        save.mutate(body, { onSuccess: (saved) => onRenamed(node.nodeId, saved.name) })
      }
      trailing={
        <button
          type="button"
          disabled={busy || !deletable}
          onClick={() => remove.mutate(undefined, { onSuccess: () => onDeleted(node.nodeId) })}
          title={
            deletable
              ? '캔버스에서 내리는 것과 다릅니다 — 원본이 사라집니다'
              : '저장된 워크플로우에 놓인 노드는 지울 수 없습니다'
          }
          className="border-0 font-[inherit] text-[14px] disabled:cursor-default disabled:opacity-45"
          style={{
            height: '34px',
            padding: '0 16px',
            borderRadius: '17px',
            background: 'rgba(255,255,255,.12)',
            color: 'rgba(255,236,246,.9)',
            cursor: busy || !deletable ? 'default' : 'pointer',
          }}
        >
          원본 삭제
        </button>
      }
    />
  )
}

type NodeInfoPanelProps = {
  /** 캔버스에서 고른 노드가 있는가. */
  hasSelection: boolean
  name: string
  /** 이름 첫 글자. 아이콘이 아직 없어 이것으로 대신한다. */
  initial: string
  typeLabel: string
  /** 고른 것이 내 기본 노드일 때만 원본 id. 에이전트나 남의 것이면 undefined. */
  nodeId?: string
  /** 에이전트는 내부 구성을 열 수 없다. 대신 팔레트가 준 한 줄 설명을 보여준다. */
  summary?: string | null
  deletable: boolean
  onRenamed: (sourceId: string, name: string) => void
  onDeleted: (sourceId: string) => void
}

export default function NodeInfoPanel({
  hasSelection,
  name,
  initial,
  typeLabel,
  nodeId,
  summary,
  deletable,
  onRenamed,
  onDeleted,
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

  const editing = !!nodeId

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
            style={{ visibility: editing ? 'hidden' : 'visible' }}
          >
            {name}
          </div>
          <div className="text-[14.5px]" style={{ color: 'rgba(255,224,238,.88)' }}>
            {typeLabel} 노드
          </div>
        </div>
      </div>

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('168px')}>
        {nodeId ? '노드 개요' : '설명'}
      </div>

      {nodeId ? (
        <NodeSource
          key={nodeId}
          nodeId={nodeId}
          deletable={deletable}
          onRenamed={onRenamed}
          onDeleted={onDeleted}
        />
      ) : (
        <div
          className="scroll-thin absolute text-[14px] leading-[1.7]"
          style={{
            ...rowStyle('192px'),
            maxHeight: '212px',
            color: summary ? 'rgba(255,240,248,.82)' : 'rgba(255,236,246,.5)',
          }}
        >
          {summary ?? '설명이 없습니다'}
        </div>
      )}
    </>
  )
}
