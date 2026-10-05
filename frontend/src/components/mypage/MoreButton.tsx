interface MoreButtonProps {
  /** 다음 장이 있을 때만 버튼을 띄운다. 자리는 늘 차지한다. */
  show: boolean
  isFetching: boolean
  onMore: () => void
}

/** 목록 카드 오른쪽 아래의 '더 보기'. */
export default function MoreButton({ show, isFetching, onMore }: MoreButtonProps) {
  return (
    <div className="flex" style={{ marginTop: '12px', justifyContent: 'flex-end' }}>
      {show && (
        <button
          type="button"
          onClick={onMore}
          disabled={isFetching}
          style={{ gap: '6px', color: 'rgba(255,240,247,.78)', transition: 'color .16s' }}
          className="flex cursor-pointer items-center text-[13px] font-semibold hover:text-[#fff]"
        >
          {isFetching ? '불러오는 중' : '더 보기'}
          <span className="text-[13.5px]">→</span>
        </button>
      )}
    </div>
  )
}
