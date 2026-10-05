import { useRef, type ChangeEvent } from 'react'

interface AvatarPickerProps {
  avatarUrl: string | null
  hasPicked: boolean
  onPick: (e: ChangeEvent<HTMLInputElement>) => void
  onCancelPick: () => void
  onRemove: () => void
}

/** 프로필 사진과 그 위의 삭제 · 선택 단추. */
export default function AvatarPicker({
  avatarUrl,
  hasPicked,
  onPick,
  onCancelPick,
  onRemove,
}: AvatarPickerProps) {
  const fileInput = useRef<HTMLInputElement>(null)

  return (
    <div className="relative flex-none" style={{ width: '104px', height: '104px' }}>
      <div
        className="relative overflow-hidden rounded-full bg-[rgba(255,255,255,.10)]"
        style={{ width: '104px', height: '104px' }}
      >
        {avatarUrl ? (
          <img src={avatarUrl} alt="" className="absolute inset-0 h-full w-full object-cover" />
        ) : (
          <div
            className="absolute inset-0"
            style={{
              background:
                'radial-gradient(circle at 52% 28%, rgba(255,218,224,.20), rgba(255,214,232,0) 62%)',
            }}
          ></div>
        )}
      </div>
      <input
        ref={fileInput}
        type="file"
        accept="image/png,image/jpeg"
        onChange={onPick}
        className="hidden"
      />

      {/* 지울 것이 있을 때만 낸다 — 기본 아바타에 ✕ 는 아무 일도 하지 않는다.
        고른 것이 있으면 그 선택만 되돌린다(`handleCancelPick`). */}
      {avatarUrl && (
        <button
          type="button"
          onClick={hasPicked ? onCancelPick : onRemove}
          aria-label={hasPicked ? '고른 사진 취소' : '프로필 사진 삭제'}
          style={{
            left: '74px',
            top: '0px',
            width: '30px',
            height: '30px',
            background: 'rgba(28,12,42,.9)',
            color: 'rgba(255,214,232,.9)',
          }}
          className="absolute flex cursor-pointer items-center justify-center rounded-full text-[14.5px] transition-colors duration-[160ms] hover:bg-[rgba(60,26,80,.95)]"
        >
          ✕
        </button>
      )}

      {/* role 만 붙인 div 는 포커스는 잡히지만 Enter·Space 로 눌리지 않는다 —
        옆의 삭제 버튼과 같은 <button> 으로 둔다 */}
      <button
        type="button"
        onClick={() => fileInput.current?.click()}
        aria-label="프로필 사진 선택"
        style={{
          left: '74px',
          top: '74px',
          width: '30px',
          height: '30px',
          background: 'rgba(28,12,42,.9)',
        }}
        className="absolute flex cursor-pointer items-center justify-center rounded-full transition-colors duration-[160ms] hover:bg-[rgba(60,26,80,.95)]"
      >
        <div
          className="relative"
          style={{
            width: '15px',
            height: '11px',
            borderRadius: '3px',
            background: 'rgba(255,214,232,.9)',
          }}
        >
          <div
            className="absolute"
            style={{
              left: '4px',
              top: '-2.5px',
              width: '7px',
              height: '3px',
              borderRadius: '1px',
              background: 'rgba(255,214,232,.9)',
            }}
          ></div>
          <div
            className="absolute rounded-full"
            style={{
              left: '5px',
              top: '3px',
              width: '5px',
              height: '5px',
              background: 'rgba(28,12,42,.9)',
            }}
          ></div>
        </div>
      </button>
    </div>
  )
}
