import { useRef, type ChangeEvent } from 'react'

import ImageProblemLine from './ImageProblemLine'
import type { Picked } from './useImagePicks'

interface IconFieldProps {
  icon: Picked | null
  onPick: (ev: ChangeEvent<HTMLInputElement>) => void
  problem: string | null
}

/** 에이전트 아이콘 한 장. 누르면 파일을 고르고, 고른 것을 미리 보여 준다. */
export default function IconField({ icon, onPick, problem }: IconFieldProps) {
  const fileRef = useRef<HTMLInputElement>(null)

  return (
    <>
      <div
        className="absolute text-[18px] font-semibold tracking-[-.01em]"
        style={{
          left: '272px',
          top: '254px',
          color: 'rgba(255,255,255,.86)',
          animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        에이전트 아이콘
      </div>
      {/* 숨긴 칸은 여는 버튼 밖에 둔다 — 안에 두면 click() 이 버블링해 핸들러가 두 번 돈다. */}
      <input ref={fileRef} type="file" accept="image/png,image/jpeg" hidden onChange={onPick} />
      {/* div 가 아니라 button 이다 — 키보드로도 닿아야 한다. */}
      <button
        type="button"
        aria-label={icon ? '에이전트 아이콘 바꾸기' : '에이전트 아이콘 고르기'}
        style={{
          left: '272px',
          top: '292px',
          width: '100px',
          height: '100px',
          borderRadius: '20px',
          border: '0',
          padding: '0',
          background: 'var(--color-surface-inset)',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
        onClick={() => fileRef.current?.click()}
        className="absolute flex cursor-pointer items-center justify-center overflow-hidden transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.13)]"
      >
        {icon ? (
          <img src={icon.url} alt="" className="h-full w-full object-cover" />
        ) : (
          <div
            className="text-[26px]"
            style={{ fontWeight: '300', color: 'rgba(255,255,255,.62)', lineHeight: '1' }}
          >
            +
          </div>
        )}
      </button>
      <div
        className="absolute text-[13px] tracking-[-.005em]"
        style={{ left: '272px', top: '400px', color: 'rgba(255,236,246,.68)' }}
      >
        {/* 서버에 파일을 받는 자리가 없어 지금은 보내지 못한다 — 고른 것이 사라지는 것처럼
            보이지 않게 미리 알린다. `POST /agents` 가 multipart 를 받게 되면 지운다. */}
        권장 크기: 256×256px · 아이콘과 스크린샷은 아직 저장되지 않습니다
      </div>
      {problem && <ImageProblemLine text={problem} top={400} />}
    </>
  )
}
