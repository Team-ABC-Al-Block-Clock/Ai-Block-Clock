import { useRef } from 'react'

import FieldLabel from './FieldLabel'

interface ProfileImageFieldProps {
  picked: { url: string } | null
  error: string | null
  onPick: (e: React.ChangeEvent<HTMLInputElement>) => void
  onClear: () => void
}

/** 프로필 이미지 고르기 — 동그란 미리보기와 추가·지우기 버튼, 안내 또는 오류 한 줄. */
export default function ProfileImageField({
  picked,
  error,
  onPick,
  onClear,
}: ProfileImageFieldProps) {
  const fileInput = useRef<HTMLInputElement>(null)

  return (
    <>
      <FieldLabel text="프로필 이미지" />
      <div className="flex items-center" style={{ marginTop: '12px', gap: '16px' }}>
        <div
          className="bg-surface-card flex-none overflow-hidden"
          style={{ width: '72px', height: '72px', borderRadius: '36px' }}
        >
          {picked && <img src={picked.url} alt="" className="h-full w-full object-cover" />}
        </div>
        <div className="flex-1">
          <input
            ref={fileInput}
            type="file"
            accept="image/png,image/jpeg"
            onChange={onPick}
            className="hidden"
          />
          <div className="flex items-center" style={{ gap: '8px' }}>
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              className="bg-surface-card box-border cursor-pointer text-[13px] font-semibold tracking-[-.01em] text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.16)]"
              style={{ height: '40px', padding: '0 18px', borderRadius: '11px' }}
            >
              {picked ? '사진 변경' : '+ 사진 추가'}
            </button>
            {picked && (
              <button
                type="button"
                onClick={onClear}
                className="cursor-pointer text-[12.5px] tracking-[-.01em] transition-colors duration-[160ms] hover:text-[#fff]"
                style={{ color: 'rgba(255,236,246,.56)' }}
              >
                지우기
              </button>
            )}
          </div>
          <div
            className="text-[11.5px] tracking-[-.01em]"
            style={{
              marginTop: '8px',
              color: error ? '#ffb4c8' : 'rgba(255,236,246,.56)',
            }}
          >
            {error ?? 'PNG · JPG, 5MB 이하 · 나중에 변경할 수 있어요'}
          </div>
        </div>
      </div>
    </>
  )
}
