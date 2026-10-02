import { AGENT_LIMITS } from '@/api/agents'

import { FIELD } from './fields'

interface NameFieldProps {
  /** 워크플로우 이름. 도착하면 갈아 끼운다 — defaultValue 로 두면 첫 렌더의 빈 값이 굳는다. */
  value: string
  placeholder: string
}

/** 에이전트 이름. 편집 화면에서 정한 워크플로우 이름을 그대로 써서 여기서는 고칠 수 없다. */
export default function NameField({ value, placeholder }: NameFieldProps) {
  return (
    <>
      <div
        className="absolute text-[18px] font-semibold tracking-[-.01em]"
        style={{
          left: '272px',
          top: '542px',
          color: 'rgba(255,255,255,.86)',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        에이전트 이름
      </div>
      <div
        className="absolute text-[13.5px] tracking-[-.005em]"
        style={{
          left: '272px',
          top: '576px',
          color: 'rgba(255,236,246,.6)',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        편집 화면에서 정한 워크플로우 이름을 그대로 씁니다
      </div>
      <div
        className="absolute"
        style={{
          left: '272px',
          top: '608px',
          width: '896px',
          height: '52px',
          borderRadius: '12px',
          background: 'rgba(8,3,16,.70)',
          boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.07)',
          animation: 'rise .7s .16s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <input
          className="absolute border-0 bg-transparent font-[inherit] text-[15.5px] tracking-[-.005em] outline-0"
          type="text"
          id={FIELD.name}
          value={value}
          placeholder={placeholder}
          onChange={() => undefined}
          maxLength={AGENT_LIMITS.name.max}
          readOnly
          style={{
            left: '18px',
            top: '15px',
            width: '820px',
            height: '22px',
            color: 'rgba(255,255,255,.38)',
            cursor: 'not-allowed',
          }}
        />
        {/* 자물쇠 아이콘 — 이 칸은 워크플로우 이름을 따라가 고칠 수 없다는 표시 */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{
            position: 'absolute',
            right: '16px',
            top: '50%',
            transform: 'translateY(-50%)',
            width: '16px',
            height: '16px',
            color: 'rgba(255,255,255,.28)',
            pointerEvents: 'none',
          }}
        >
          <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
          <path d="M7 11V7a5 5 0 0 1 10 0v4" />
        </svg>
      </div>
    </>
  )
}
