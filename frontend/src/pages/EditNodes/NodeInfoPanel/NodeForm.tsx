import { useState } from 'react'

import { NODE_NAME_MAX, type AiModel, type NodeOutput } from '@/api/nodes'
import RefFileField, { REF_FIELD_HEIGHT } from '@/components/RefFileField'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import type { RefFileSlot } from '@/hooks/canvas/useNodeRefFiles'
import type { NodeValues } from '@/lib/canvas/canvasReducer'
import {
  outputDraftProblem,
  readOutputDraft,
  sameOutput,
  toOutputDraft,
} from '@/lib/canvas/nodeOutput'
import { priceLabel } from '@/lib/format'

import ModelSelect from '../ModelSelect'
import { OutputExampleBox, OutputFieldsBox, OutputKindToggle, OutputModeLink } from '../OutputField'
import FieldLabel from './FieldLabel'
import { rowStyle } from './layout'
import PencilIcon from './PencilIcon'

/** 폼의 세로 좌표. 라벨은 칸에서 22px 위다. 프롬프트부터는 세로로 쌓는다. */
const FILES_Y = 120
const MODEL_Y = FILES_Y + REF_FIELD_HEIGHT + 30
const PROMPT_Y = MODEL_Y + 72
/** 안쪽 여백을 빼면 다섯 줄이 선다. 두 줄로는 프롬프트를 적다가 바로 스크롤이 생긴다. */
const PROMPT_HEIGHT = 150
const EXAMPLE_HEIGHT = 130
/** 모델과 출력 형식은 한 줄에 반씩. */
const HALF_WIDTH = (PANEL_CONTENT_WIDTH - 8) / 2

/** 캔버스에서 적은 기본 노드를 고치는 폼. 고친 값은 「저장」으로 캔버스에 돌려준다. */
export default function NodeForm({
  initial,
  price,
  files,
  onApply,
}: {
  initial: { name: string; prompt: string; model: AiModel; output: NodeOutput | null }
  /** 서버가 매긴 1회 실행 원가. 저장하기 전에는 없다 */
  price: string | null
  files: RefFileSlot
  onApply: (v: NodeValues) => void
}) {
  const [name, setName] = useState(initial.name)
  const [prompt, setPrompt] = useState(initial.prompt)
  const [model, setModel] = useState<AiModel>(initial.model)
  const [outputDraft, setOutputDraft] = useState(() => toOutputDraft(initial.output))

  const output = readOutputDraft(outputDraft)
  const json = outputDraft.kind === 'JSON'
  const changed =
    name !== initial.name ||
    prompt !== initial.prompt ||
    model !== initial.model ||
    !sameOutput(output, initial.output ?? readOutputDraft(toOutputDraft()))
  const canApply = changed && !!name.trim() && !!prompt.trim() && !!output

  return (
    <>
      <FieldLabel top="26px">노드 이름</FieldLabel>
      <div
        className="bg-surface-card absolute flex items-center"
        style={{ ...rowStyle('48px'), height: '42px', borderRadius: '11px', padding: '0 12px' }}
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
        style={{
          ...rowStyle(`${MODEL_Y}px`),
          width: `${HALF_WIDTH}px`,
          height: '42px',
          fontSize: '16.5px',
        }}
      />
      <div
        className="absolute text-[15px]"
        style={{
          left: `${PANEL_INSET + HALF_WIDTH + 8}px`,
          top: `${MODEL_Y - 22}px`,
          color: 'rgba(255,214,232,.85)',
        }}
      >
        출력 형식
      </div>
      <OutputKindToggle
        value={outputDraft.kind}
        onChange={(kind) => setOutputDraft((it) => ({ ...it, kind }))}
        style={{
          position: 'absolute',
          left: `${PANEL_INSET + HALF_WIDTH + 8}px`,
          top: `${MODEL_Y}px`,
          width: `${HALF_WIDTH}px`,
          height: '42px',
          fontSize: '15.5px',
        }}
      />

      {/* 아래는 출력 필드 줄 수만큼 늘어난다. 탭 안에서 스크롤된다. */}
      <div
        className="absolute flex flex-col"
        style={{ ...rowStyle(`${PROMPT_Y - 22}px`), paddingBottom: '24px' }}
      >
        <div className="text-[15px]" style={{ height: '22px', color: 'rgba(255,214,232,.85)' }}>
          프롬프트
        </div>
        <div
          className="bg-surface-card"
          style={{ height: `${PROMPT_HEIGHT}px`, borderRadius: '11px', padding: '9px 12px' }}
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

        {json && (
          <>
            <div
              className="flex items-baseline justify-between text-[15px]"
              style={{ height: '22px', marginTop: '8px', color: 'rgba(255,214,232,.85)' }}
            >
              {outputDraft.form ? '출력 필드' : '출력 예시'}
              <OutputModeLink
                draft={outputDraft}
                onChange={setOutputDraft}
                style={{ fontSize: '13px' }}
              />
            </div>
            {outputDraft.form ? (
              <OutputFieldsBox
                fields={outputDraft.fields}
                onChange={(fields) => setOutputDraft((it) => ({ ...it, fields }))}
              />
            ) : (
              <OutputExampleBox
                value={outputDraft.text}
                onChange={(text) => setOutputDraft((it) => ({ ...it, text }))}
                invalid={!!outputDraft.text.trim() && !output}
                height={EXAMPLE_HEIGHT}
                fontSize="14px"
              />
            )}
          </>
        )}

        <div
          className="flex flex-none items-center justify-between"
          style={{ height: '32px', marginTop: '10px' }}
        >
          <span className="truncate text-[14px]" style={{ color: 'rgba(255,236,246,.6)' }}>
            {/* 원가는 서버가 프롬프트와 모델로 매긴다. 저장해 봐야 안다. */}
            {!output
              ? outputDraftProblem(outputDraft)
              : price
                ? `1회 실행 원가 ${priceLabel(price)}`
                : '실행 단계에서 원가가 측정됩니다'}
          </span>
          <button
            type="button"
            disabled={!canApply}
            onClick={() =>
              output && onApply({ name: name.trim(), prompt: prompt.trim(), model, output })
            }
            title={canApply ? '고친 값을 캔버스에 반영합니다' : '바뀐 것이 없습니다'}
            className="flex-none border-0 font-[inherit] text-[14px] font-semibold disabled:cursor-default disabled:opacity-45"
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
      </div>
    </>
  )
}
