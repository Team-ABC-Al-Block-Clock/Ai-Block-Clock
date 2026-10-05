import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import { paths } from '@/routes/paths'

import { CHARGE_LINK, NOTICE_Y, RUN_BUTTON_Y } from './config'

interface RunActionProps {
  /** 지금 누를 수 있는가 */
  on: boolean
  pending: boolean
  /** 버튼 아래 한 줄. 비면 그리지 않는다. */
  notice: string
  /** 접수가 실패해 생긴 안내인가 */
  failed: boolean
  /** 잔액이 모자라 실패했는가. 충전으로 가는 길을 붙인다. */
  insufficient: boolean
  onRun: () => void
}

/** 실행 버튼과 그 아래 안내 한 줄. */
export default function RunAction({
  on,
  pending,
  notice,
  failed,
  insufficient,
  onRun,
}: RunActionProps) {
  return (
    <>
      <div
        id="runBtn"
        onClick={onRun}
        style={{
          left: `${PANEL_INSET}px`,
          top: `${RUN_BUTTON_Y}px`,
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '56px',
          borderRadius: '12px',
          background: on ? 'var(--color-accent)' : 'rgba(255,255,255,.09)',
          boxShadow: on
            ? '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)'
            : 'inset 0 1px 0 rgba(255,255,255,.12)',
          gap: '8px',
          color: on ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.42)',
          cursor: on ? 'pointer' : 'not-allowed',
          transition: 'background .25s,color .25s,box-shadow .25s,transform .18s',
        }}
        className="absolute flex items-center justify-center text-[19px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
      >
        <span>{pending ? '실행을 맡기는 중…' : '실행하기'}</span>
      </div>

      {notice && (
        <div
          className="absolute text-[14px] tracking-[-.005em]"
          style={{
            left: `${PANEL_INSET}px`,
            top: `${NOTICE_Y}px`,
            width: `${PANEL_CONTENT_WIDTH}px`,
            color: failed ? 'rgba(255,190,209,.9)' : 'rgba(255,236,246,.5)',
          }}
        >
          {notice}
          {insufficient && (
            <a href={paths.my.credit} style={{ ...CHARGE_LINK, marginLeft: '8px' }}>
              충전하러 가기
            </a>
          )}
        </div>
      )}
    </>
  )
}
