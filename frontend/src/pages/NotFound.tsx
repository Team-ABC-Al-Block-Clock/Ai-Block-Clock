import { useLocation } from 'react-router-dom'

import { paths } from '@/routes/paths'

/** 어느 라우트에도 걸리지 않은 경로. 카드 모양은 ErrorBoundary 와 같게 둔다. */
export default function NotFound() {
  const { pathname } = useLocation()

  return (
    <div className="absolute inset-0 flex items-center justify-center p-10">
      <div className="glass-card bg-surface-deep shadow-card rounded-panel flex w-[420px] flex-col items-center gap-3 px-8 py-9 text-center">
        <div className="text-faint font-brand text-[44px] leading-none font-semibold tracking-[-0.02em]">
          404
        </div>
        <h1 className="text-body text-[18px] font-semibold tracking-[-0.01em]">
          페이지를 찾을 수 없어요
        </h1>
        <p className="text-muted text-[13px] leading-[1.6]">
          주소가 바뀌었거나 삭제된 페이지입니다. 주소를 다시 확인해 주세요.
        </p>

        <pre className="bg-surface-inset text-faint scroll-thin mt-1 w-full rounded-[10px] px-3 py-2 text-left text-[11px] leading-[1.5] break-all whitespace-pre-wrap">
          {pathname}
        </pre>

        <div className="mt-2 flex w-full gap-2">
          <a
            href={paths.home}
            className="bg-accent text-accent-ink flex h-11 flex-1 cursor-pointer items-center justify-center rounded-xl text-[13px] font-bold transition-transform hover:-translate-y-px"
          >
            홈으로
          </a>
          <a
            href={paths.search}
            className="bg-surface-quiet text-body flex h-11 flex-1 items-center justify-center rounded-xl text-[13px] font-semibold transition-transform hover:-translate-y-px"
          >
            에이전트 둘러보기
          </a>
        </div>
      </div>
    </div>
  )
}
