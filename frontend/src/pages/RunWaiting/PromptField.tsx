import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

import { COMMENT_MAX, DEFAULT_ONBOARDING_MESSAGE, DEFAULT_PROMPT_EXAMPLE } from './config'

interface PromptFieldProps {
  /** 맨 앞 에이전트의 인사말. 비면 기본 문구를 쓴다. */
  greeting: string
  /** 맨 앞 에이전트의 입력 예시. 비면 기본 예시를 쓴다. */
  example: string
  value: string
  onChange: (value: string) => void
}

/** 인사말과 실행에 넘길 프롬프트 입력칸. */
export default function PromptField({ greeting, example, value, onChange }: PromptFieldProps) {
  return (
    <>
      <div
        className="absolute text-[22px] font-semibold tracking-[-.01em]"
        style={{ left: `${PANEL_INSET}px`, top: '96px', color: 'rgba(255,255,255,.96)' }}
      >
        {greeting || DEFAULT_ONBOARDING_MESSAGE}
      </div>
      <div
        className="absolute"
        style={{
          left: `${PANEL_INSET}px`,
          top: '140px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '136px',
          borderRadius: '12px',
          background: 'var(--color-surface-inset)',
        }}
      >
        <textarea
          className="absolute border-0 bg-transparent font-[inherit] text-[16.5px] tracking-[-.005em] text-white outline-0"
          id="runPrompt"
          value={value}
          onChange={(ev) => onChange(ev.target.value)}
          maxLength={COMMENT_MAX}
          placeholder={example || DEFAULT_PROMPT_EXAMPLE}
          style={{
            left: '18px',
            top: '16px',
            width: '364px',
            height: '104px',
            resize: 'none',
            lineHeight: '1.55',
          }}
        ></textarea>
      </div>
    </>
  )
}
