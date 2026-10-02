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
  /** 여러 줄 입력 — 상자가 124px 로 커진다. */
  multiline?: boolean
}

/** 제목 한 줄과 그 아래 입력 상자. 값은 제출할 때 id 로 한 번 읽어 제어하지 않는다. */
export default function TextField({
  label,
  top,
  id,
  maxLength,
  placeholder,
  multiline = false,
}: TextFieldProps) {
  return (
    <>
      <div
        className="absolute text-[18px] font-semibold tracking-[-.01em]"
        style={{ left: '272px', top: `${top}px`, color: 'rgba(255,255,255,.86)' }}
      >
        {label}
      </div>
      <div
        className="bg-surface-card absolute"
        style={{
          left: '272px',
          top: `${top + 38}px`,
          width: '896px',
          height: multiline ? '124px' : '52px',
          borderRadius: '12px',
        }}
      >
        {multiline ? (
          <textarea
            className={INPUT_CLASS}
            id={id}
            maxLength={maxLength}
            placeholder={placeholder}
            style={{
              left: '18px',
              top: '16px',
              width: '860px',
              height: '92px',
              resize: 'none',
              lineHeight: '1.6',
            }}
          ></textarea>
        ) : (
          <input
            className={INPUT_CLASS}
            type="text"
            id={id}
            maxLength={maxLength}
            placeholder={placeholder}
            style={{ left: '18px', top: '15px', width: '860px', height: '22px' }}
          />
        )}
      </div>
    </>
  )
}
