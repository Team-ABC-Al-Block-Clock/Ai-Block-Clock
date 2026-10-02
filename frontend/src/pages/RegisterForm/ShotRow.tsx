import { Fragment, useRef, type ChangeEvent } from 'react'

import ImageProblemLine from './ImageProblemLine'
import { SHOT_LIMIT, type Picked } from './useImagePicks'

const SHOT_SLOTS = Array.from({ length: SHOT_LIMIT }, (_, i) => i)

interface ShotRowProps {
  /** 줄 제목의 위치. 스크린샷 칸은 그 32px 아래에 놓인다. */
  top: number
  title: string
  shots: Picked[]
  captionPlaceholder: string
  onPick: (ev: ChangeEvent<HTMLInputElement>) => void
  onDrop: (index: number) => void
  label: string
  problem: string | null
}

/** 스크린샷 다섯 칸과 「사진 추가」. 입력·출력이 같은 모양이라 한 곳에 둔다. */
export default function ShotRow({
  top,
  title,
  shots,
  captionPlaceholder,
  onPick,
  onDrop,
  label,
  problem,
}: ShotRowProps) {
  const fileRef = useRef<HTMLInputElement>(null)

  return (
    <>
      <div
        className="absolute text-[13.5px] tracking-[-.005em]"
        style={{ left: '272px', top: `${top}px`, color: 'rgba(255,236,246,.74)' }}
      >
        {title}
      </div>
      {problem && <ImageProblemLine text={problem} top={top} />}
      <div
        className="absolute flex items-start"
        style={{ left: '272px', top: `${top + 32}px`, width: '896px', gap: '12px' }}
      >
        {SHOT_SLOTS.map((i) => (
          <Fragment key={i}>
            <div className="flex flex-none flex-col" style={{ width: '128px', gap: '7px' }}>
              <div
                style={{ width: '128px', height: '84px', borderRadius: '10px' }}
                className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.09)]"
              >
                {shots[i] ? (
                  <>
                    <img
                      src={shots[i].url}
                      alt=""
                      className="absolute inset-0 h-full w-full object-cover"
                    />
                    {/* 잘못 고른 것을 무르는 길 */}
                    <button
                      type="button"
                      aria-label={`${label} ${i + 1}번째 사진 지우기`}
                      onClick={() => onDrop(i)}
                      style={{
                        position: 'absolute',
                        right: '6px',
                        top: '6px',
                        width: '22px',
                        height: '22px',
                        border: '0',
                        borderRadius: '11px',
                        background: 'rgba(12,5,22,.62)',
                        color: '#fff',
                        lineHeight: '1',
                      }}
                      className="cursor-pointer text-[13px] transition-colors duration-[160ms] hover:bg-[rgba(12,5,22,.86)]"
                    >
                      ×
                    </button>
                  </>
                ) : (
                  <>
                    <div
                      className="absolute"
                      style={{
                        left: '-20px',
                        top: '41px',
                        width: '168px',
                        height: '1px',
                        background: 'rgba(255,255,255,.16)',
                        transform: 'rotate(33deg)',
                      }}
                    ></div>
                    <div
                      className="absolute"
                      style={{
                        left: '-20px',
                        top: '41px',
                        width: '168px',
                        height: '1px',
                        background: 'rgba(255,255,255,.16)',
                        transform: 'rotate(-33deg)',
                      }}
                    ></div>
                  </>
                )}
              </div>
              <input
                className="box-border border-0 text-center font-[inherit] text-[13px] tracking-[-.005em] text-white outline-0"
                type="text"
                placeholder={captionPlaceholder}
                style={{
                  width: '128px',
                  height: '32px',
                  padding: '0 8px',
                  borderRadius: '9px',
                  background: 'rgba(255,255,255,.06)',
                }}
              />
            </div>
          </Fragment>
        ))}
        {/* 숨긴 칸은 여는 버튼 밖에 둔다 — 안에 두면 click() 이 버블링해 핸들러가 두 번 돈다. */}
        <input
          ref={fileRef}
          type="file"
          accept="image/png,image/jpeg"
          multiple
          hidden
          onChange={onPick}
        />
        <button
          type="button"
          onClick={() => fileRef.current?.click()}
          style={{
            width: '128px',
            height: '123px',
            border: '0',
            borderRadius: '10px',
            background: 'rgba(255,255,255,.05)',
            gap: '5px',
          }}
          className="flex flex-none cursor-pointer flex-col items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.14)]"
        >
          <div
            className="text-[18px] font-semibold"
            style={{ color: 'rgba(255,255,255,.72)', lineHeight: '1' }}
          >
            +
          </div>
          <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.76)' }}>
            사진 추가
          </div>
        </button>
      </div>
    </>
  )
}
