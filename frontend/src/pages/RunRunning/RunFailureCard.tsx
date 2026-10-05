import type { CSSProperties } from 'react'

import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

/** 안내 패널 안의 버튼. 실패와 조회 불가가 같은 모양을 쓴다. */
const PANEL_ACTION: CSSProperties = {
  marginTop: '6px',
  height: '38px',
  padding: '0 16px',
  borderRadius: '10px',
  background: 'rgba(255,255,255,.12)',
}

const ACTION_CLASS =
  'flex cursor-pointer items-center text-[14px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)]'

interface RunFailureCardProps {
  /** 없는 실행이거나 다른 계정의 실행이다. */
  missing: boolean
  /** 진행을 한 번도 받지 못했다. */
  lost: boolean
  notice: string
  /** 처음부터 다시 실행할 때 돌아갈 대기 화면 주소. */
  retryHref: string
}

/** 실행이 멎었거나 진행을 받지 못했을 때의 안내와 다음 행동. */
export default function RunFailureCard({ missing, lost, notice, retryHref }: RunFailureCardProps) {
  return (
    <div
      className="bg-surface-card absolute box-border flex flex-col items-center text-center"
      style={{
        left: `${PANEL_INSET}px`,
        top: '400px',
        width: `${PANEL_CONTENT_WIDTH}px`,
        padding: '22px 20px',
        borderRadius: '12px',
        gap: '10px',
        animation: 'uiIn .6s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      <div className="text-[16px] font-semibold" style={{ color: 'rgba(255,190,209,.95)' }}>
        {missing
          ? '잘못된 링크이거나 다른 계정의 실행입니다'
          : lost
            ? '진행 상황을 불러오지 못했습니다'
            : '실행이 실패했습니다'}
      </div>
      <div
        className="text-[14.5px]"
        style={{ color: 'rgba(255,236,246,.7)', lineHeight: '1.7', whiteSpace: 'pre-line' }}
      >
        {notice}
      </div>
      {/*
        멎었으면 다시 걸어 준다 — 까닭은 대개 모델이 잠깐 넘어진 것이라 고칠 게 없다.
        중간부터 이어 갈 수는 없다. 서버에 그런 자리가 없어 늘 첫 단계부터 새로 돈다.
        진행을 못 받은 것은 다르다. 실행은 멀쩡히 돌고 있을 수 있어 다시 걸면 두 번 빠진다.
      */}
      {lost && !missing ? (
        <button
          type="button"
          onClick={() => location.reload()}
          style={PANEL_ACTION}
          className={ACTION_CLASS}
        >
          새로고침
        </button>
      ) : (
        <a href={retryHref} style={PANEL_ACTION} className={ACTION_CLASS}>
          처음부터 다시 실행하기
        </a>
      )}
    </div>
  )
}
