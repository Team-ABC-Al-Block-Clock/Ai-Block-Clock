import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

import { MAX_PICKED } from './config'

interface StartBarProps {
  pickedCount: number
  atLimit: boolean
  searching: boolean
  onStart: () => void
}

/** 패널 맨 아래 줄. 고른 개수와 편집 화면으로 넘어가는 버튼. */
export default function StartBar({ pickedCount, atLimit, searching, onStart }: StartBarProps) {
  return (
    <div
      className="absolute flex items-center"
      style={{
        left: `${PANEL_INSET}px`,
        top: 'calc(928px + var(--edge-y, 0px))',
        width: `${PANEL_CONTENT_WIDTH}px`,
        height: '46px',
        animation: 'uiIn .6s .5s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      {pickedCount > 0 && (
        <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.7)' }}>
          {atLimit ? `${MAX_PICKED}개까지 선택됨` : `${pickedCount}개 선택됨`}
        </div>
      )}
      <button
        type="button"
        onClick={onStart}
        // 검색 중에는 고른 것이 없다. 그때 눌리면 빈 편집 화면으로 가고,
        // 뒤로 오면 선택은 이 화면과 함께 사라진 뒤다.
        disabled={searching}
        className="ml-auto border-0 font-[inherit] text-[14px] font-semibold hover:brightness-[1.05] disabled:cursor-default disabled:opacity-50 disabled:hover:brightness-100"
        style={{
          height: '46px',
          padding: '0 22px',
          borderRadius: '23px',
          background: 'var(--color-accent)',
          color: 'var(--color-accent-ink)',
          boxShadow: '0 6px 18px rgba(140,100,130,.26)',
          cursor: searching ? 'default' : 'pointer',
        }}
      >
        시작
      </button>
    </div>
  )
}
