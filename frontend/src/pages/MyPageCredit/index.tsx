import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { getCreditBalance } from '@/api/credits'
import MyPageFrame from '@/components/mypage/MyPageFrame'
import MyPageHeader from '@/components/mypage/MyPageHeader'
import Sidebar from '@/components/mypage/Sidebar'
import { useAbcCharge } from '@/hooks/wallet/useAbcCharge'
import { usePayoutWithdrawal } from '@/hooks/wallet/usePayoutWithdrawal'
import { useWalletAbcBalance } from '@/hooks/wallet/useWalletAbcBalance'
import { useWalletStatus } from '@/hooks/wallet/useWalletStatus'
import { queryKeys } from '@/lib/queryKeys'
import { trimAbcAmount } from '@/lib/wallet/abcToken'

import AmountPicker from './AmountPicker'
import BalanceBanner from './BalanceBanner'
import { chargeCard } from './chargeCard'
import ChargeProgress from './ChargeProgress'
import { DEFAULT_PICK, MODE_TEXT, type Mode } from './config'
import ConvertConfirmDialog from './ConvertConfirmDialog'
import { creditFormView } from './creditForm'
import CreditSummary from './CreditSummary'
import ModeTabs from './ModeTabs'
import WithdrawalCard from './WithdrawalCard'

export default function MyPageCredit() {
  const [pick, setPick] = useState(DEFAULT_PICK)
  const [mode, setMode] = useState<Mode>('charge')
  const [asking, setAsking] = useState<string | null>(null)
  const [typed, setTyped] = useState('')
  const charge = useAbcCharge()
  const payoutFlow = usePayoutWithdrawal()
  const wallet = useWalletStatus().walletAddress

  const balance = useQuery({
    queryKey: queryKeys.credits.balance(),
    queryFn: getCreditBalance,
  })

  const walletBalance = useWalletAbcBalance()
  // 충전이 끝나면 지갑에서 토큰이 빠졌으니 다시 읽는다.
  const { refetch: refetchWallet } = walletBalance
  useEffect(() => {
    if (charge.phase === 'confirmed') void refetchWallet()
  }, [charge.phase, refetchWallet])

  const text = MODE_TEXT[mode]
  const view = creditFormView({
    mode,
    pick,
    typed,
    balance: balance.data,
    balanceFailed: balance.isError,
    wallet,
    walletAmount: walletBalance.amount,
  })
  const { amount, blocked, usesPayout } = view

  const handlePickMode = (next: Mode) => {
    setMode(next)
    setPick(DEFAULT_PICK)
    setTyped('')
    payoutFlow.clearMessages()
  }
  const handlePickPreset = (index: number) => {
    setPick(index)
    // 프리셋을 누르면 직접 입력은 버린다. 남겨 두면 고른 칸과 보내는 값이 갈린다.
    setTyped('')
  }
  const handleCta = () => {
    if (mode === 'charge') return void charge.send(amount)
    if (blocked || payoutFlow.busy) return
    // 전환은 되돌릴 수 없어 한 번 더 묻는다.
    if (mode === 'convert') return setAsking(trimAbcAmount(amount))
    payoutFlow.request(amount)
  }
  const handleDismissWithdrawal = () => {
    payoutFlow.dismiss()
    setTyped('')
  }
  const handleConvert = (asked: string) => {
    payoutFlow.convert(asked)
    setAsking(null)
    setTyped('')
  }

  const charging = mode === 'charge' && charge.phase !== 'idle'
  const withdrawal = mode === 'withdraw' ? payoutFlow.withdrawal : null

  // 지갑을 열기 전에 걸린 것들 — 금액이 틀렸거나 지갑이 다르거나 거절했다.
  const summaryMessage =
    mode === 'charge'
      ? charge.error
        ? { text: charge.error, good: false }
        : null
      : payoutFlow.error || payoutFlow.notice || (typed.trim() && blocked)
        ? {
            text: payoutFlow.error ?? payoutFlow.notice ?? blocked ?? '',
            good: !payoutFlow.error && !!payoutFlow.notice,
          }
        : null

  return (
    <>
      <MyPageFrame>
        <Sidebar active="credit" />

        <MyPageHeader
          title="충전/출금"
          description="지갑의 abcToken 으로 ABC 를 충전하거나, 판매로 받은 정산금을 지갑으로 출금합니다."
          descriptionWidth={820}
        />

        <ModeTabs mode={mode} onPick={handlePickMode} />

        <BalanceBanner label={text.bannerLabel} value={view.bannerValue} note={view.bannerNote} />

        <AmountPicker
          title={text.pickerTitle}
          note={view.pickerNote}
          presets={view.presets}
          at={view.at}
          onPick={handlePickPreset}
          typed={typed}
          placeholder={text.placeholder}
          onType={setTyped}
        />

        <div
          className="bg-surface-card absolute box-border flex items-center justify-between"
          style={{
            left: '442px',
            top: '716px',
            width: '896px',
            height: '224px',
            padding: '26px 30px',
            borderRadius: '16px',
            backdropFilter: 'blur(18px) saturate(115%)',
            WebkitBackdropFilter: 'blur(18px) saturate(115%)',
          }}
        >
          {/* 충전이 시작되면 요약 자리를 진행 상태가 가져간다 — 보내는 버튼을 남겨 두면 두 번 보낸다. */}
          {charging ? (
            <ChargeProgress
              card={chargeCard(charge, () => {
                charge.reset()
                setTyped('')
              })}
              transactionHash={charge.transactionHash}
            />
          ) : withdrawal ? (
            <WithdrawalCard
              withdrawal={withdrawal}
              error={payoutFlow.error}
              busy={payoutFlow.busy}
              onDismiss={handleDismissWithdrawal}
              onCancel={payoutFlow.cancel}
            />
          ) : (
            <CreditSummary
              title={text.summaryTitle}
              sumLabel={text.sumLabel}
              sumCredit={view.sumCredit}
              details={view.details}
              message={summaryMessage}
              ctaLabel={usesPayout && payoutFlow.busy ? '처리 중' : text.ctaLabel}
              blocked={!!blocked}
              onCta={handleCta}
            />
          )}
        </div>
      </MyPageFrame>

      {/* 프레임 밖에 둔다 — 안에 두면 가운데로 민 만큼 덮개가 비껴 난다. */}
      <ConvertConfirmDialog
        asking={asking}
        onConfirm={handleConvert}
        onCancel={() => setAsking(null)}
      />
    </>
  )
}
