import type { ReactNode } from 'react'

interface ProfileInputProps {
  label: string
  value: string
  onChange: (value: string) => void
  /** 상자 아래 한 줄. 오류나 글자 수가 들어간다. */
  children?: ReactNode
}

/** 제목과 한 줄 입력 상자. 프로필 정보 칸 둘이 같은 모양을 쓴다. */
export default function ProfileInput({ label, value, onChange, children }: ProfileInputProps) {
  return (
    <div className="flex-1">
      <div className="text-[15.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
        {label}
      </div>
      <div
        className="bg-surface-card flex items-center"
        style={{
          marginTop: '8px',
          height: '42px',
          borderRadius: '11px',
          padding: '0 14px',
        }}
      >
        <input
          className="border-0 bg-transparent font-[inherit] text-[15px] text-white outline-0"
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          style={{ width: '100%' }}
        />
      </div>
      {children}
    </div>
  )
}
