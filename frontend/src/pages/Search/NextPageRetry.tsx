/** 다음 장을 못 불러왔을 때 그리드 끝에 붙는 한 줄. observer 는 실패한 커서를 다시 밟지 않는다. */
export default function NextPageRetry({
  isFetching,
  onRetry,
}: {
  isFetching: boolean
  onRetry: () => void
}) {
  return (
    <div
      className="flex justify-center text-[14px]"
      style={{ width: '100%', gap: '12px', color: 'rgba(255,236,246,.62)' }}
    >
      <span>다음 목록을 불러오지 못했습니다.</span>
      <button
        className="cursor-pointer text-white underline"
        disabled={isFetching}
        onClick={onRetry}
        style={{ background: 'none', border: 0, padding: 0 }}
        type="button"
      >
        다시 시도
      </button>
    </div>
  )
}
