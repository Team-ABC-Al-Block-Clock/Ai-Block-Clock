import type { ReactNode } from 'react'

import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

/** 목록 자리에 대신 서는 안내. 실패와 결과 없음이 같은 자리·모양을 쓴다. */
function NoticeBox({ children }: { children: ReactNode }) {
  return (
    <div
      className="absolute text-[14px] leading-[1.7]"
      style={{
        left: `${PANEL_INSET}px`,
        top: '140px',
        width: `${PANEL_CONTENT_WIDTH}px`,
        color: 'rgba(255,236,246,.72)',
        animation: 'fadeIn .3s ease both',
      }}
    >
      {children}
    </div>
  )
}

export function FailedNotice({ retrying, onRetry }: { retrying: boolean; onRetry: () => void }) {
  return (
    <NoticeBox>
      추천을 불러오지 못했습니다.
      <button
        type="button"
        onClick={onRetry}
        disabled={retrying}
        className="mt-[14px] block border-0 font-[inherit] text-[13px] text-white disabled:cursor-default disabled:opacity-60"
        style={{
          height: '34px',
          padding: '0 16px',
          borderRadius: '17px',
          background: 'rgba(255,255,255,.14)',
          cursor: retrying ? 'default' : 'pointer',
        }}
      >
        다시 시도
      </button>
    </NoticeBox>
  )
}

export function EmptyNotice() {
  return (
    <NoticeBox>
      맞는 에이전트를 찾지 못했습니다.
      <br />
      다른 말로 다시 적어 보거나, 빈 캔버스에서 직접 만들 수 있습니다.
    </NoticeBox>
  )
}
