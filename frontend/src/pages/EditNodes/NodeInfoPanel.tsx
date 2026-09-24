import { useState } from 'react'

import { ApiError } from '@/api/errors'
import { NODE_NAME_MAX, type AiModel, type NodeDetail } from '@/api/nodes'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import { useNodeDetail } from '@/hooks/useNodeDetail'

/** 서버가 아는 모델. 늘어나면 여기만 고친다. */
const MODELS: { value: AiModel; label: string }[] = [
  { value: 'GEMINI_2_5_FLASH', label: 'Gemini 2.5 Flash' },
]

function messageOf(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback
  if (error.code === 'NODE_001') return '이미 지워진 노드입니다'
  if (error.code === 'NODE_002') return '이 노드를 고칠 권한이 없습니다'
  if (error.code === 'NODE_003') return '프롬프트를 입력해 주세요'
  if (error.code === 'NODE_004') return '워크플로우에 배치된 노드는 지울 수 없습니다'
  if (error.code === 'COMMON_001') return '이름을 확인해 주세요'
  return fallback
}

const labelStyle = (top: string) => ({
  left: `${PANEL_INSET}px`,
  top,
  color: 'rgba(255,255,255,.92)',
})

type EditorProps = {
  node: NodeDetail
  save: ReturnType<typeof useNodeDetail>['save']
  remove: ReturnType<typeof useNodeDetail>['remove']
  onDeleted?: () => void
}

/**
 * 고친 내용을 담는 자리. 부르는 쪽이 `key` 에 노드 id 를 주어 노드를 바꿀 때마다 새로 만든다 —
 * 그래야 옆 노드를 눌렀을 때 앞 노드의 프롬프트가 남지 않는다.
 */
function NodeEditor({ node, save, remove, onDeleted }: EditorProps) {
  const [name, setName] = useState(node.name)
  const [prompt, setPrompt] = useState(node.prompt)
  const [model, setModel] = useState<AiModel>(node.model)

  const dirty = name !== node.name || prompt !== node.prompt || model !== node.model
  const busy = save.isPending || remove.isPending
  const canSave = dirty && !!name.trim() && !!prompt.trim() && !busy
  const error = save.error ?? remove.error

  return (
    <>
      <input
        className="absolute border-0 bg-transparent font-[inherit] text-[18px] font-semibold tracking-[-.005em] text-white outline-0"
        value={name}
        maxLength={NODE_NAME_MAX}
        onChange={(ev) => setName(ev.target.value)}
        placeholder="노드 이름"
        style={{ left: '96px', top: '76px', width: '236px', height: '26px' }}
      />

      <textarea
        className="scroll-thin absolute resize-none border-0 font-[inherit] text-[14px] leading-[1.6] text-white outline-0"
        value={prompt}
        onChange={(ev) => setPrompt(ev.target.value)}
        placeholder="이 노드가 할 일을 적어 주세요"
        style={{
          left: `${PANEL_INSET}px`,
          top: '196px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '108px',
          borderRadius: '10px',
          background: 'var(--color-surface-inset)',
          padding: '12px 14px',
        }}
      />

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('326px')}>
        모델
      </div>
      <select
        className="absolute cursor-pointer border-0 font-[inherit] text-[15.5px] text-white outline-0"
        value={model}
        onChange={(ev) => setModel(ev.target.value as AiModel)}
        style={{
          left: `${PANEL_INSET}px`,
          top: '350px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '44px',
          borderRadius: '10px',
          padding: '0 14px',
          background: 'var(--color-surface-inset)',
        }}
      >
        {MODELS.map((item) => (
          <option key={item.value} value={item.value} style={{ color: '#20121c' }}>
            {item.label}
          </option>
        ))}
      </select>

      <div
        className="absolute text-[13px] leading-[1.6]"
        style={{
          left: `${PANEL_INSET}px`,
          top: '404px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          minHeight: '20px',
          color: error ? 'rgba(255,196,214,.95)' : 'rgba(255,236,246,.6)',
        }}
      >
        {error
          ? messageOf(error, '저장하지 못했습니다')
          : `1회 실행 원가 ${node.baseCost} ${node.currency}`}
      </div>

      <div
        className="absolute flex items-center"
        style={{ left: `${PANEL_INSET}px`, top: '430px', gap: '8px' }}
      >
        <button
          type="button"
          disabled={!canSave}
          onClick={() => save.mutate({ name: name.trim(), prompt: prompt.trim(), model })}
          className="border-0 font-[inherit] text-[14px] font-semibold disabled:cursor-default disabled:opacity-45"
          style={{
            height: '38px',
            padding: '0 18px',
            borderRadius: '19px',
            background: 'var(--color-accent)',
            color: 'var(--color-accent-ink)',
            cursor: canSave ? 'pointer' : 'default',
          }}
        >
          {save.isPending ? '저장 중' : '저장'}
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => remove.mutate(undefined, { onSuccess: () => onDeleted?.() })}
          className="border-0 font-[inherit] text-[14px] disabled:cursor-default disabled:opacity-45"
          style={{
            height: '38px',
            padding: '0 16px',
            borderRadius: '19px',
            background: 'rgba(255,255,255,.12)',
            color: 'rgba(255,236,246,.9)',
            cursor: busy ? 'default' : 'pointer',
          }}
        >
          원본 삭제
        </button>
      </div>
      <div
        className="absolute text-[12.5px] leading-[1.6]"
        style={{
          left: `${PANEL_INSET}px`,
          top: '476px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          color: 'rgba(255,236,246,.5)',
        }}
      >
        캔버스에서 내리는 것과 다릅니다 — 원본을 지우면 다른 워크플로우에서도 사라집니다
      </div>
    </>
  )
}

type NodeInfoPanelProps = {
  /** 캔버스에서 고른 노드가 있는가. 없으면 안내 문구만 보여준다. */
  hasSelection: boolean
  name: string
  /** 이름 첫 글자. 아이콘이 아직 없어 이것으로 대신한다. */
  initial: string
  typeLabel: string
  /**
   * 고른 것이 내 기본 노드일 때만 원본 id. 에이전트나 남의 것이면 undefined —
   * 그때는 예전처럼 읽기만 한다.
   */
  nodeId?: string
  /** 원본이 지워졌다. 캔버스에서도 내려야 한다. */
  onDeleted?: () => void
}

/** 패널 위쪽 — 고른 노드를 살펴보고 고치는 자리. */
export default function NodeInfoPanel({
  hasSelection,
  name,
  initial,
  typeLabel,
  nodeId,
  onDeleted,
}: NodeInfoPanelProps) {
  const { detail, save, remove } = useNodeDetail(nodeId)
  const loaded = detail.data

  if (!hasSelection)
    return (
      <div
        className="absolute text-[15.5px]"
        style={{
          left: `${PANEL_INSET}px`,
          top: '64px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          color: 'rgba(255,236,246,.66)',
        }}
      >
        캔버스에서 노드를 선택하면 상세 정보를 조회할 수 있습니다
      </div>
    )

  return (
    <>
      <div
        className="absolute box-border flex items-center"
        style={{
          left: `${PANEL_INSET}px`,
          top: '58px',
          width: `${PANEL_CONTENT_WIDTH}px`,
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
            style={{ visibility: nodeId && loaded ? 'hidden' : 'visible' }}
          >
            {name}
          </div>
          <div className="text-[14.5px]" style={{ color: 'rgba(255,224,238,.88)' }}>
            {typeLabel} 노드
          </div>
        </div>
      </div>

      <div className="absolute text-[15.5px] font-semibold" style={labelStyle('172px')}>
        노드 개요
      </div>

      {nodeId && loaded ? (
        <NodeEditor
          key={loaded.nodeId}
          node={loaded}
          save={save}
          remove={remove}
          onDeleted={onDeleted}
        />
      ) : (
        <div
          className="absolute text-[14px] leading-[1.7]"
          style={{
            left: `${PANEL_INSET}px`,
            top: '196px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            color: 'rgba(255,236,246,.66)',
          }}
        >
          {nodeId
            ? detail.isPending
              ? '불러오는 중'
              : messageOf(detail.error, '노드를 불러오지 못했습니다')
            : '에이전트는 내부 구성을 열 수 없습니다'}
        </div>
      )}
    </>
  )
}
