import { useState, type Dispatch, type SetStateAction } from 'react'

import { NODE_NAME_MAX, type NodeOutput } from '@/api/nodes'
import type { ControlKind } from '@/api/workflows'
import LongTextDialog, { ExpandButton } from '@/components/LongTextDialog'
import RefFileField from '@/components/RefFileField'
import type { RefFileSlot } from '@/hooks/canvas/useNodeRefFiles'
import { outputDraftProblem, readOutputDraft } from '@/lib/canvas/nodeOutput'

import ModelSelect from './ModelSelect'
import type { NodeDraft } from './nodeDraft'
import { OutputExampleBox, OutputFieldsBox, OutputKindToggle, OutputModeLink } from './OutputField'

const UNSUPPORTED = '아직 지원하지 않습니다'

/** 「제어 흐름 노드」 줄의 칩. 누르면 캔버스 가운데에 하나 놓인다. */
function ControlChip({
  label,
  disabled = false,
  title,
  onClick,
}: {
  label: string
  disabled?: boolean
  title?: string
  onClick: () => void
}) {
  return (
    <div
      onClick={disabled ? undefined : onClick}
      title={title}
      style={{
        height: '36px',
        padding: '0 15px',
        borderRadius: '10px',
        opacity: disabled ? 0.45 : 1,
      }}
      className={`bg-surface-card flex items-center text-[12.5px] font-semibold whitespace-nowrap text-white ${
        disabled ? 'cursor-default' : 'cursor-pointer hover:bg-[rgba(255,255,255,.2)]'
      }`}
    >
      {label}
    </div>
  )
}

const LABEL_STYLE = { color: 'rgba(255,214,232,.85)', marginBottom: '8px' }

interface AddNodePanelProps {
  /**
   * 적던 값. 패널은 닫히면 내려가므로 값은 화면이 든다 — × 로 닫았다 다시 열어도 남아 있어야
   * 한다. 버리는 것은 「취소」뿐이다.
   */
  draft: NodeDraft
  onDraftChange: Dispatch<SetStateAction<NodeDraft>>
  /** 아직 원본이 없는 노드의 참고 자료 칸. 고른 파일은 워크플로우를 저장할 때 함께 올라간다. */
  fileSlot: RefFileSlot
  /** 시작 · 종료는 저마다 하나뿐이다. 둘을 놓으면 서버가 `WORKFLOW_013` 으로 물린다. */
  placed: { start: boolean; end: boolean }
  /** × — 적던 값은 남긴다. */
  onClose: () => void
  /** 취소 — 적던 값까지 버린다. */
  onCancel: () => void
  onCreate: (output: NodeOutput) => void
  onPlaceControl: (kind: ControlKind) => void
}

/** 왼쪽 아래 「노드 추가」 패널. 기본 노드를 적어 올리거나 제어 · 입출력 노드를 놓는다. */
export default function AddNodePanel({
  draft,
  onDraftChange,
  fileSlot,
  placed,
  onClose,
  onCancel,
  onCreate,
  onPlaceControl,
}: AddNodePanelProps) {
  const [promptExpanded, setPromptExpanded] = useState(false)
  const draftOutput = readOutputDraft(draft.output)
  const draftFilled = !!draft.name.trim() && !!draft.prompt.trim() && !!draftOutput

  return (
    <div
      className="absolute box-border flex flex-col"
      style={{
        left: 'calc(32px - var(--edge, 0px))',
        // 패널을 여는 바가 `--edge-y` 만큼 내려간다. 따라가지 않으면 창이 길어질수록 떨어진다.
        top: 'calc(262px + var(--edge-y, 0px))',
        width: '500px',
        height: '700px',
        borderRadius: '14px',
        background: 'rgba(20,11,36,.88)',
        backdropFilter: 'blur(28px) saturate(125%)',
        WebkitBackdropFilter: 'blur(28px) saturate(125%)',
        boxShadow: 'inset 0 1px 0 rgba(255,255,255,.18),0 26px 60px rgba(4,2,12,.6)',
        padding: '20px',
        animation: 'panelPop .34s cubic-bezier(.2,.9,.3,1) both',
        transformOrigin: '20% 100%',
      }}
    >
      <div className="flex flex-none items-center justify-between" style={{ marginBottom: '16px' }}>
        <div className="text-[20px] font-semibold tracking-[-.01em] text-white">노드 추가</div>
        <div
          onClick={onClose}
          style={{
            width: '22px',
            height: '22px',
            borderRadius: '6px',
            color: 'rgba(255,255,255,.6)',
          }}
          className="flex cursor-pointer items-center justify-center text-[16px] hover:bg-[rgba(255,255,255,.14)] hover:text-[#fff]"
        >
          ×
        </div>
      </div>

      {/* 시작·종료는 워크플로우마다 꼭 필요해 폼 아래에 두지 않고 스크롤 밖에 고정한다 */}
      <div className="flex-none">
        <div className="text-[17px]" style={{ ...LABEL_STYLE, marginBottom: '10px' }}>
          제어 흐름 노드
        </div>
        <div className="flex flex-wrap" style={{ gap: '8px', marginBottom: '16px' }}>
          <ControlChip
            label="시작"
            disabled={placed.start}
            title={placed.start ? '시작 노드는 하나만 놓을 수 있습니다' : undefined}
            onClick={() => onPlaceControl('START')}
          />
          <ControlChip
            label="종료"
            disabled={placed.end}
            title={placed.end ? '종료 노드는 하나만 놓을 수 있습니다' : undefined}
            onClick={() => onPlaceControl('END')}
          />
          <ControlChip label="조건 분기" onClick={() => onPlaceControl('BRANCH')} />
          {/* 서버가 받는 제어 노드는 분기뿐이다(`ControlType`). 병합은 노드 없이 두 갈래를 같은 노드로 잇는다. */}
          <ControlChip label="반복" disabled title={UNSUPPORTED} onClick={() => {}} />
          <ControlChip
            label="병합"
            disabled
            title="두 갈래에서 같은 노드로 선을 이으면 합쳐집니다"
            onClick={() => {}}
          />
        </div>
      </div>
      <div
        className="flex-none"
        style={{ height: '1px', background: 'rgba(255,255,255,.12)', marginBottom: '16px' }}
      />

      <div
        className="scroll-thin flex-1 overflow-x-hidden"
        style={{ minHeight: '0', paddingRight: '4px' }}
      >
        <div className="text-[17px] font-semibold text-white" style={{ marginBottom: '14px' }}>
          새 노드 만들기
        </div>
        <div className="text-[17px]" style={LABEL_STYLE}>
          노드 이름
        </div>
        <div
          className="bg-surface-card flex items-center"
          style={{
            height: '44px',
            borderRadius: '11px',
            padding: '0 12px',
            marginBottom: '14px',
          }}
        >
          <input
            className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
            type="text"
            value={draft.name}
            maxLength={NODE_NAME_MAX}
            onChange={(ev) => onDraftChange((it) => ({ ...it, name: ev.target.value }))}
            placeholder="예: 내용 분석"
            aria-label="노드 이름"
            style={{ width: '100%' }}
          />
        </div>

        <div className="text-[17px]" style={LABEL_STYLE}>
          입력 파일
        </div>
        <RefFileField slot={fileSlot} style={{ marginBottom: '14px' }} />

        <div className="text-[17px]" style={LABEL_STYLE}>
          AI 모델
        </div>
        <div style={{ position: 'relative', height: '44px', marginBottom: '14px' }}>
          <ModelSelect
            value={draft.model}
            onChange={(model) => onDraftChange((it) => ({ ...it, model }))}
            style={{ inset: 0, fontSize: '17px' }}
          />
        </div>

        <div className="text-[17px] font-semibold" style={LABEL_STYLE}>
          프롬프트
        </div>
        <div
          className="bg-surface-card relative"
          style={{
            borderRadius: '9px',
            padding: '11px 40px 6px 12px',
            marginBottom: '14px',
          }}
        >
          <ExpandButton
            onClick={() => setPromptExpanded(true)}
            style={{ top: '8px', right: '8px' }}
          />
          <textarea
            className="border-0 bg-transparent font-[inherit] text-[17px] text-white outline-0"
            value={draft.prompt}
            onChange={(ev) => onDraftChange((it) => ({ ...it, prompt: ev.target.value }))}
            placeholder="이 노드가 할 일을 프롬프트로 적으세요&#10;예: 이 문서를 분석하여 핵심 내용을 요약해 주세요."
            aria-label="프롬프트"
            style={{
              width: '100%',
              height: '180px',
              minHeight: '90px',
              resize: 'vertical',
              lineHeight: '1.5',
            }}
          ></textarea>
        </div>

        <div className="text-[17px]" style={LABEL_STYLE}>
          출력 형식
        </div>
        <OutputKindToggle
          value={draft.output.kind}
          onChange={(kind) => onDraftChange((it) => ({ ...it, output: { ...it.output, kind } }))}
          style={{ height: '44px', fontSize: '16px', marginBottom: '14px' }}
        />
        {draft.output.kind === 'JSON' && (
          <>
            <div className="flex items-baseline justify-between text-[17px]" style={LABEL_STYLE}>
              {draft.output.form ? '출력 필드' : '출력 예시'}
              <OutputModeLink
                draft={draft.output}
                onChange={(output) => onDraftChange((it) => ({ ...it, output }))}
                style={{ fontSize: '14px' }}
              />
            </div>
            {draft.output.form ? (
              <OutputFieldsBox
                fields={draft.output.fields}
                onChange={(fields) =>
                  onDraftChange((it) => ({ ...it, output: { ...it.output, fields } }))
                }
                style={{ marginBottom: '14px' }}
              />
            ) : (
              <OutputExampleBox
                value={draft.output.text}
                onChange={(text) =>
                  onDraftChange((it) => ({ ...it, output: { ...it.output, text } }))
                }
                invalid={!!draft.output.text.trim() && !draftOutput}
                height={130}
                fontSize="14.5px"
                style={{ marginBottom: '14px' }}
              />
            )}
          </>
        )}
      </div>

      <div
        className="flex flex-none items-center"
        style={{ justifyContent: 'flex-end', gap: '8px', paddingTop: '14px' }}
      >
        <button
          type="button"
          onClick={onCancel}
          style={{
            height: '40px',
            padding: '0 22px',
            borderRadius: '11px',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
            color: 'rgba(255,255,255,.9)',
          }}
          className="bg-surface-card flex cursor-pointer items-center border-0 font-[inherit] text-[17px] font-semibold hover:bg-[rgba(255,255,255,.18)]"
        >
          취소
        </button>
        <button
          type="button"
          onClick={() => {
            if (draftFilled && draftOutput) onCreate(draftOutput)
          }}
          disabled={!draftFilled}
          title={
            draftFilled
              ? '노드를 만들어 팔레트에 넣습니다'
              : !draftOutput
                ? (outputDraftProblem(draft.output) ?? undefined)
                : '이름과 프롬프트를 적어 주세요'
          }
          style={{
            height: '40px',
            padding: '0 24px',
            borderRadius: '11px',
            background: 'var(--color-accent)',
            boxShadow: '0 8px 20px rgba(140,100,130,.24)',
            color: 'var(--color-accent-ink)',
            cursor: draftFilled ? 'pointer' : 'default',
          }}
          className="flex items-center border-0 font-[inherit] text-[17px] font-bold hover:brightness-[1.05] disabled:opacity-45 disabled:hover:brightness-100"
        >
          추가
        </button>
      </div>

      {promptExpanded && (
        <LongTextDialog
          title="프롬프트"
          value={draft.prompt}
          onChange={(prompt) => onDraftChange((it) => ({ ...it, prompt }))}
          onClose={() => setPromptExpanded(false)}
          placeholder="이 노드가 할 일을 프롬프트로 적으세요"
        />
      )}
    </div>
  )
}
