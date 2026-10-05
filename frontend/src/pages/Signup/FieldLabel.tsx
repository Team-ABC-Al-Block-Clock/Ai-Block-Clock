interface FieldLabelProps {
  text: string
  suffix?: string
  required?: boolean
}

/** 입력칸 위 제목 한 줄. 필수면 별표, 선택이면 회색 덧말을 붙인다. */
export default function FieldLabel({ text, suffix, required = false }: FieldLabelProps) {
  return (
    <div
      className="text-[13px] font-semibold tracking-[-.01em] text-white"
      style={{ marginTop: '22px' }}
    >
      {text}
      {required && <span style={{ marginLeft: '3px', color: '#ffb4c8' }}>*</span>}
      {suffix && (
        <span
          className="text-[11.5px] font-normal"
          style={{ marginLeft: '5px', color: 'rgba(255,236,246,.56)' }}
        >
          {suffix}
        </span>
      )}
    </div>
  )
}
