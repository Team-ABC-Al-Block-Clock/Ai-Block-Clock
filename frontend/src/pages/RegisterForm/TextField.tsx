import { useState } from 'react'

import LongTextDialog, { ExpandButton } from '@/components/LongTextDialog'

const INPUT_CLASS =
  'absolute border-0 bg-transparent font-[inherit] text-[15.5px] tracking-[-.005em] text-white outline-0'

interface TextFieldProps {
  label: string
  /** 제목 줄의 위치. 입력 상자는 그 38px 아래에 놓인다. */
  top: number
  /** 보낼 때 이 id 로 값을 읽는다(`FIELD`). */
  id: string
  maxLength: number
  placeholder: string
  /** 주면 이 높이의 여러 줄 입력이 되고 크게 쓰기 버튼이 붙는다. */
  boxHeight?: number
}

/** 제목 한 줄과 그 아래 입력 상자. 제출할 때는 여전히 id 로 값을 읽는다. */
export default function TextField({
  label,
  top,
  id,
  maxLength,
  placeholder,
  boxHeight,
}: TextFieldProps) {
  const [value, setValue] = useState('')
  const [expanded, setExpanded] = useState(false)
  const multiline = boxHeight !== undefined

  return (
    <>
      <div
        className="absolute flex items-baseline justify-between"
        style={{ left: '272px', top: `${top}px`, width: '896px' }}
      >
        <div
          className="text-[18px] font-semibold tracking-[-.01em]"
          style={{ color: 'rgba(255,255,255,.86)' }}
        >
          {label}
        </div>
        <div className="text-[14px]" style={{ color: 'rgba(255,236,246,.55)' }}>
          {value.length} / {maxLength}
        </div>
      </div>
      <div
        className="bg-surface-card absolute"
        style={{
          left: '272px',
          top: `${top + 38}px`,
          width: '896px',
          height: `${boxHeight ?? 52}px`,
          borderRadius: '12px',
        }}
      >
        {multiline ? (
          <>
            <textarea
              className={`scroll-thin ${INPUT_CLASS}`}
              id={id}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              maxLength={maxLength}
              placeholder={placeholder}
              style={{
                left: '18px',
                top: '16px',
                width: '824px',
                height: `${boxHeight - 32}px`,
                resize: 'none',
                lineHeight: '1.6',
              }}
            ></textarea>
            <ExpandButton
              onClick={() => setExpanded(true)}
              style={{ top: '10px', right: '10px' }}
            />
          </>
        ) : (
          <input
            className={INPUT_CLASS}
            type="text"
            id={id}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            maxLength={maxLength}
            placeholder={placeholder}
            style={{ left: '18px', top: '15px', width: '860px', height: '22px' }}
          />
        )}
      </div>

      {expanded && (
        <LongTextDialog
          title={label}
          value={value}
          onChange={setValue}
          onClose={() => setExpanded(false)}
          maxLength={maxLength}
          placeholder={placeholder}
        />
      )}
    </>
  )
}
