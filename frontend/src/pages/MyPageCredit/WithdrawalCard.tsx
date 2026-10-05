import { IN_PROGRESS_WITHDRAWAL } from '@/api/withdrawals'
import type { WithdrawalView } from '@/hooks/wallet/usePayoutWithdrawal'
import { priceLabel, shortAddress } from '@/lib/format'
import { withdrawalStatusText } from '@/lib/wallet/withdrawalMessage'

import { ACCENT_BUTTON } from './config'

interface WithdrawalCardProps {
  withdrawal: WithdrawalView
  error: string | null
  busy: boolean
  onDismiss: () => void
  onCancel: () => void
}

/** 신청한 출금의 상태. 요약 자리를 차지한다. */
export default function WithdrawalCard({
  withdrawal,
  error,
  busy,
  onDismiss,
  onCancel,
}: WithdrawalCardProps) {
  const text = withdrawalStatusText(withdrawal.status)
  return (
    <>
      <div style={{ width: '530px' }}>
        <div className="text-[15.5px] font-semibold tracking-[-.01em] text-white">
          {text.title} · {priceLabel(withdrawal.amount)}
        </div>
        <div
          className="text-[15.5px]"
          style={{ marginTop: '10px', color: 'rgba(255,236,246,.72)' }}
        >
          {withdrawal.rejectReason ? `${text.note} 사유: ${withdrawal.rejectReason}` : text.note}
        </div>
        <div
          className="truncate text-[15.5px]"
          style={{ marginTop: '8px', color: 'rgba(255,196,214,.9)' }}
        >
          {error ?? (
            <span style={{ color: 'rgba(255,236,246,.5)' }}>
              받을 지갑 {shortAddress(withdrawal.walletAddress)} · 신청 번호 {withdrawal.id}
              {withdrawal.transactionHash &&
                ` · 거래 해시 ${shortAddress(withdrawal.transactionHash)}`}
            </span>
          )}
        </div>
      </div>
      {/* PAYING 부터는 체인으로 나가는 중이라 취소가 없다. */}
      {/* 끝나기 전에는 접지 않는다 — 접으면 새로 신청하려다 PAYMENT_012 에 막힌다. */}
      <div className="flex flex-col" style={{ gap: '8px' }}>
        {!IN_PROGRESS_WITHDRAWAL.includes(withdrawal.status) && (
          <div
            onClick={onDismiss}
            style={{ width: '300px', height: '60px', ...ACCENT_BUTTON }}
            className="flex cursor-pointer items-center justify-center text-[17px] font-semibold tracking-[-.01em] hover:brightness-[1.06]"
          >
            확인
          </div>
        )}
        {withdrawal.status === 'REQUESTED' && (
          <div
            onClick={busy ? undefined : onCancel}
            className="box-border flex cursor-pointer items-center justify-center text-[16px] font-semibold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.1)] hover:text-white"
            style={{
              width: '240px',
              height: '50px',
              borderRadius: '12px',
              border: '1px solid rgba(255,236,246,.32)',
              color: 'rgba(255,236,246,.86)',
            }}
          >
            {busy ? '취소하는 중' : '신청 취소'}
          </div>
        )}
      </div>
    </>
  )
}
