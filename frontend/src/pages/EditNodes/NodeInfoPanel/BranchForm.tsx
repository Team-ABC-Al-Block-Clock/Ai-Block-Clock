import { useState } from 'react'

import type { ConditionOperator } from '@/api/workflows'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import { formatConditionRight } from '@/lib/canvas/branchCondition'
import type { CanvasCondition } from '@/lib/canvas/canvasWorkflow'

import { branchFormState, type ConditionSource } from './branchFormState'
import FieldLabel from './FieldLabel'
import HeadCard from './HeadCard'
import { rowStyle } from './layout'
import PencilIcon from './PencilIcon'
import Picker from './Picker'

/**
 * 분기 조건을 고치는 자리.
 *
 * 읽을 노드는 고르게만 한다 — 손으로 적게 두면 앞에 없는 노드를 가리켜 `WORKFLOW_013` 을
 * 받는데, 코드 하나에 여러 원인이 몰려 있어 무엇이 틀렸는지 돌려주지 못한다.
 */
export default function BranchForm({
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

  const {
    picked,
    fields,
    field,
    pickedPath,
    operators,
    pickedOperator,
    parsed,
    canApply,
    drifted,
  } = branchFormState({ fromId, path, operator, right }, sources, initial)

  if (sources.length === 0)
    return (
      <>
        <HeadCard {...head} />
        <div
          className="absolute text-[15.5px] leading-[1.7]"
          style={{ ...rowStyle('144px'), color: 'rgba(255,236,246,.66)' }}
        >
          조건 분기 앞에 놓인 노드의 결과를 읽습니다. 앞에 있는 기본 노드의 출력 형식을 JSON 으로
          바꾸거나 에이전트를 연결해주세요.
        </div>
      </>
    )

  return (
    <>
      <HeadCard {...head} />

      <FieldLabel top="140px">읽을 노드</FieldLabel>
      <Picker
        top="162px"
        label="읽을 노드"
        value={picked}
        onChange={setFromId}
        options={sources.map((source) => ({ value: source.id, label: source.name }))}
      />

      <FieldLabel top="212px">값 경로</FieldLabel>
      {fields && fields.length > 0 ? (
        <Picker
          top="234px"
          label="값 경로"
          value={pickedPath}
          onChange={setPath}
          options={fields.map((it) => ({ value: it.path, label: it.path }))}
        />
      ) : fields ? (
        <div
          className="absolute text-[14.5px] leading-[1.5]"
          style={{ ...rowStyle('236px'), color: 'rgba(255,236,246,.66)' }}
        >
          출력 예시에 견줄 수 있는 칸(글자·숫자·참거짓)이 없습니다
        </div>
      ) : (
        <div
          className="bg-surface-card absolute flex items-center"
          style={{ ...rowStyle('234px'), height: '42px', borderRadius: '11px', padding: '0 12px' }}
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
      )}

      <FieldLabel top="284px">비교할 값</FieldLabel>
      <Picker
        top="306px"
        label="비교"
        value={pickedOperator}
        onChange={setOperator}
        options={operators}
        width={132}
      />
      <div
        className="bg-surface-card absolute flex items-center"
        style={{
          left: `${PANEL_INSET + 140}px`,
          top: '306px',
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
          placeholder={
            pickedOperator === 'IN'
              ? '쉼표로 나눠서'
              : field?.kind === 'boolean'
                ? 'true 또는 false'
                : field?.kind === 'number'
                  ? '예: 3'
                  : '예: high'
          }
          aria-label="비교할 값"
        />
        <PencilIcon />
      </div>

      <div
        className="absolute flex items-center justify-between"
        style={{ ...rowStyle('360px'), height: '32px' }}
      >
        <span
          className="truncate text-[14px]"
          style={{ color: drifted ? 'rgba(255,196,214,.9)' : 'rgba(255,236,246,.6)' }}
          title={drifted ? '저장된 조건이 지금 출력 예시와 맞지 않습니다' : undefined}
        >
          {/* 참이면 true 포트, 거짓이면 false 포트로 나간다. */}
          {drifted
            ? '저장된 조건과 달라요. 확인 후 적용해 주세요'
            : '조건이 참이면 위쪽 선으로 갑니다'}
        </span>
        <button
          type="button"
          disabled={!canApply}
          onClick={() =>
            canApply &&
            parsed !== null &&
            onApply({ fromId: picked, path: pickedPath, operator: pickedOperator, right: parsed })
          }
          title={canApply ? '조건을 캔버스에 반영합니다' : '빈 칸이나 값의 종류를 확인해 주세요'}
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
