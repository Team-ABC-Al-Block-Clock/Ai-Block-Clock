import { CHIP } from './config'

/** 다음 장을 못 불러왔을 때 목록 끝에 붙는 한 줄. observer 는 실패한 커서를 다시 밟지 않는다. */
export default function NextPageRetry({
  isFetching,
  onRetry,
}: {
  isFetching: boolean
  onRetry: () => void
}) {
  return (
    <div
      className="flex flex-none items-center justify-center"
      style={{
        width: '896px',
        gap: '12px',
        padding: '10px 0 2px',
        animation: 'fadeIn .2s ease both',
      }}
    >
      <div className="text-[14px] tracking-[-.01em]" style={{ color: 'rgba(255,196,214,.9)' }}>
        다음 목록을 불러오지 못했습니다
      </div>
      <div
        onClick={onRetry}
        className={`${CHIP} justify-center text-[14px] font-semibold text-white hover:bg-[rgba(255,255,255,.16)]`}
        style={{
          height: '30px',
          padding: '0 14px',
          borderRadius: '15px',
          background: 'rgba(255,255,255,.09)',
          opacity: isFetching ? 0.6 : 1,
          cursor: isFetching ? 'default' : 'pointer',
        }}
      >
        {isFetching ? '불러오는 중…' : '다시 시도'}
      </div>
    </div>
  )
}
