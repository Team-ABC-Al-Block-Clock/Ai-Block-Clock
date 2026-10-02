import type { CSSProperties } from 'react'

interface MyPageHeaderProps {
  title: string
  description: string
  /** 설명 줄의 폭. 오른쪽에 놓인 것이 화면마다 달라 각자 정한다. */
  descriptionWidth: number
  descriptionStyle?: CSSProperties
}

/** 마이페이지 다섯 화면의 제목과 한 줄 설명. */
export default function MyPageHeader({
  title,
  description,
  descriptionWidth,
  descriptionStyle,
}: MyPageHeaderProps) {
  return (
    <>
      <div
        className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '442px',
          top: '158px',
          textShadow: '0 2px 20px rgba(35,8,45,.6)',
          animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {title}
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: `${descriptionWidth}px`,
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
          ...descriptionStyle,
        }}
      >
        {description}
      </div>
    </>
  )
}
