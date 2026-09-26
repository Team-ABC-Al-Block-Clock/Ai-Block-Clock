import { useQuery } from '@tanstack/react-query'
import { Fragment, useEffect, useRef, useState } from 'react'
import Sidebar from '../components/Sidebar'
import MyPageFrame from '@/components/MyPageFrame'
import { getCreditBalance } from '@/api/credits'
import { WITHDRAWAL_LIMITS } from '@/api/withdrawals'
import { useAbcCharge, type AbcCharge } from '@/hooks/useAbcCharge'
import { usePayoutWithdrawal } from '@/hooks/usePayoutWithdrawal'
import { useWalletAbcBalance } from '@/hooks/useWalletAbcBalance'
import { isSendableAmount, trimAbcAmount } from '@/lib/abcToken'
import { chargeFailureText } from '@/lib/chargeMessage'
import { chargeAmountLabel, priceLabel, shortAddress } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { withdrawalStatusText } from '@/lib/withdrawalMessage'
import { useAuthStore } from '@/stores/authStore'

const { MIN_AMOUNT, MAX_AMOUNT } = WITHDRAWAL_LIMITS

type Mode = 'deposit' | 'withdraw' | 'convert'
const MODES: { key: Mode; label: string }[] = [
  { key: 'deposit', label: '예치' },
  { key: 'withdraw', label: '출금' },
  { key: 'convert', label: 'ABC 전환' },
]

/** 전환은 정산금 안이면 금액 · 주기 제한이 없다. */
function convertBlock(amount: string, payout: number) {
  if (!isSendableAmount(amount)) return '전환할 금액을 입력해 주세요.'
  if (Number(trimAbcAmount(amount)) > payout) return '정산금이 부족합니다.'
  return null
}

/** 출금은 정산금 안에서, 한 번에 1 ~ 1,000 ABC 다. 막혔으면 이유를 준다. */
function withdrawBlock(amount: string, payout: number) {
  if (!isSendableAmount(amount)) return '출금할 금액을 입력해 주세요.'
  const n = Number(trimAbcAmount(amount))
  if (n < MIN_AMOUNT || n > MAX_AMOUNT)
    return `한 번에 ${MIN_AMOUNT} ~ ${MAX_AMOUNT.toLocaleString()} ABC 까지 출금할 수 있습니다.`
  if (n > payout) return '정산금이 부족합니다.'
  return null
}

/**
 * 충전 단계별 화면 문구와 버튼.
 *
 * 토큰이 나간 뒤로는 「다시 시도」가 두 번 보내기가 된다 — 접수만 다시 하는 것과
 * 확인하고 나가는 것을 갈라 둔다.
 */
function describeCharge(charge: AbcCharge, finish: () => void) {
  switch (charge.phase) {
    case 'signing':
      return { title: '지갑 승인 대기', note: 'MetaMask 에서 전송을 승인해 주세요.', action: null }
    case 'submitting':
      return { title: '접수 중', note: '거래 해시를 서버에 넘기고 있습니다.', action: null }
    case 'unsubmitted':
      return {
        title: '접수하지 못했습니다',
        note: charge.error ?? '토큰은 이미 보냈습니다. 접수만 다시 하면 됩니다.',
        action: { label: '다시 접수', run: charge.retrySubmit },
      }
    case 'confirming':
      // 더 묻지 않게 된 건은 화면이 스스로 못 푼다. 해시를 버리는 reset 이 아니라 다시 묻게 한다.
      return {
        title: '확인 중',
        note: charge.stalled
          ? '상태를 확인하지 못했습니다. 거래는 체인에 남아 있습니다.'
          : '거래가 블록에 적히면 잔액에 반영됩니다. 이 화면을 떠나도 됩니다.',
        action: charge.stalled ? { label: '다시 확인', run: charge.recheck } : null,
      }
    case 'confirmed':
      return {
        title: '충전 완료',
        note: `${chargeAmountLabel(charge.status?.creditedAmount ?? charge.requestedAmount ?? '0')} 를 적립했습니다.`,
        action: { label: '확인', run: finish },
      }
    case 'failed':
      return {
        title: '충전 실패',
        note: chargeFailureText(charge.status?.failureReason ?? null),
        action: { label: '확인', run: finish },
      }
    default:
      return { title: '', note: '', action: null }
  }
}

export default function MyPageCredit() {
  const [pick, setPick] = useState(2)
  const [mode, setMode] = useState<Mode>('deposit')
  const [asking, setAsking] = useState<string | null>(null)
  const [typed, setTyped] = useState('')
  const charge = useAbcCharge()
  const payoutFlow = usePayoutWithdrawal()
  const wallet = useAuthStore((s) => s.profile?.wallet.address ?? s.walletAddress)

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
  // 프리셋 계산에만 숫자를 쓴다. 표시는 서버 문자열 그대로다.
  const payout = Number(balance.data?.payoutAmount ?? 0)
  // 1,000 을 넘겨 보내면 PAYMENT_010 이다.
  const payoutMax = Math.min(Math.floor(payout * 1e6) / 1e6, MAX_AMOUNT)
  const withdraw = mode === 'withdraw'
  const convert = mode === 'convert'
  const available = Number(balance.data?.availableAmount ?? 0)
  const payoutFloor = Math.floor(payout * 1e6) / 1e6
  // 적용 뒤 금액은 아래 요약이 보여 준다. 칸에는 금액만 둔다.
  const presetAmounts = withdraw
    ? [100, 200, 400, payoutMax]
    : convert
      ? [100, 200, 400, payoutFloor]
      : [500, 1000, 2500, 5000]
  const raw = presetAmounts.map((amount) => ({
    credit: amount.toLocaleString() + ' ABC',
    amount,
    disabled: withdraw
      ? amount > payout || amount < MIN_AMOUNT
      : convert
        ? amount > payout || amount <= 0
        : false,
  }))
  // 고른 칸이 잔액 부족이면 고를 수 있는 마지막 칸으로 내린다. 없으면 그대로 둔다.
  const pickable = raw.findLastIndex((p) => !p.disabled)
  const at = raw[pick]?.disabled && pickable >= 0 ? pickable : Math.min(pick, raw.length - 1)
  const presets = raw.map((p, i) => ({
    ...p,
    bg: i === at ? 'rgba(185,141,169,.32)' : 'var(--color-surface-card)',
    ring: 'none',
    fg: p.disabled ? 'rgba(255,240,247,.38)' : i === at ? '#e6cbe0' : '#fff',
    anim: 'rise .7s ' + (0.2 + i * 0.04).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
    pick: () => {
      if (p.disabled) return
      setPick(i)
      // 프리셋을 누르면 직접 입력은 버린다. 남겨 두면 고른 칸과 보내는 값이 갈린다.
      setTyped('')
    },
  }))
  const cur = raw[at]
  // 직접 입력이 있으면 그 값이 이긴다. 프리셋은 비워 뒀을 때의 기본값이다.
  const amount = typed.trim() || String(cur.amount)
  // 요약은 실제로 보낼 값을 그대로 보여 준다 — 프리셋만 따르면 직접 입력한 금액이 화면에서 사라진다.
  const sumCredit = isSendableAmount(amount) ? chargeAmountLabel(trimAbcAmount(amount)) : '—'
  const handlePickMode = (next: Mode) => {
    setMode(next)
    setPick(2)
    setTyped('')
    payoutFlow.clearMessages()
  }
  const usesPayout = withdraw || convert
  const bannerLabel = withdraw
    ? '출금 가능 정산금'
    : convert
      ? '전환 가능 정산금'
      : '현재 사용 가능 ABC'
  // 불러오는 중에 0 을 띄우면 잔액이 비어 보였다가 튄다.
  const bannerValue = balance.data
    ? priceLabel(usesPayout ? balance.data.payoutAmount : balance.data.availableAmount)
    : '—'
  const reserved = balance.data?.reservedAmount
  const reservedNote = reserved && Number(reserved) > 0 ? `실행 중 ${priceLabel(reserved)} · ` : ''
  const bannerNote = balance.isError
    ? '잔액을 불러오지 못했습니다'
    : withdraw
      ? '판매로 받은 정산금을 연결된 지갑으로 보냅니다'
      : convert
        ? '정산금을 실행에 쓸 수 있는 ABC 로 바꿉니다'
        : reservedNote + '충전한 ABC 는 서비스 안에서만 쓰며 출금되지 않습니다'
  const pickerTitle = withdraw ? '출금 금액 선택' : convert ? '전환 금액 선택' : '예치 금액 선택'
  const pickerNote = withdraw
    ? `한 번에 ${MIN_AMOUNT} ~ ${MAX_AMOUNT.toLocaleString()} ABC · 24시간에 한 번 신청할 수 있습니다`
    : convert
      ? '금액 · 횟수 제한 없음 · 전환한 ABC 는 정산금으로 되돌릴 수 없습니다'
      : `지갑 잔액 ${walletBalance.amount === null ? '—' : priceLabel(walletBalance.amount)} · ABC 는 다른 사용자에게 양도할 수 없습니다`
  const inputPlaceholder = usesPayout
    ? `${withdraw ? '출금' : '전환'}할 정산금을 입력하세요`
    : '예치할 금액을 입력하세요'
  const summaryTitle = withdraw ? '출금 요약' : convert ? '전환 요약' : '예치 요약'
  const sumLabel = withdraw ? '출금 정산금' : convert ? '전환 정산금' : '예치 금액'
  const ctaLabel = withdraw ? '출금 신청' : convert ? 'ABC로 전환하기' : '예치하기'
  const picked = isSendableAmount(amount) ? Number(trimAbcAmount(amount)) : null
  // 정산금을 넘는 금액은 뺀 결과가 음수라 비워 둔다.
  const payoutAfter = picked !== null && picked <= payout ? priceLabel(payout - picked) : '—'
  const availableAfter = picked !== null ? priceLabel(available + picked) : '—'
  const details = withdraw
    ? [
        { label: '출금 후 정산금', value: payoutAfter },
        { label: '받을 지갑', value: wallet ? shortAddress(wallet) : '—' },
      ]
    : convert
      ? [
          { label: '전환 후 사용 가능', value: availableAfter },
          { label: '전환 후 정산금', value: payoutAfter },
        ]
      : [
          { label: '예치 후 사용 가능', value: availableAfter },
          // 가스비가 0 인 체인이다. 「≈」 를 붙이면 조금은 든다는 말이 된다.
          { label: '예상 가스비', value: '0 SSF' },
        ]

  const blocked = withdraw
    ? withdrawBlock(amount, payout)
    : convert
      ? convertBlock(amount, payout)
      : null
  const withdrawal = withdraw ? payoutFlow.withdrawal : null
  const withdrawalCard = withdrawal && withdrawalStatusText(withdrawal.status)

  const charging = mode === 'deposit' && charge.phase !== 'idle'
  const chargeCard = describeCharge(charge, () => {
    charge.reset()
    setTyped('')
  })
  const handleCta = () => {
    if (mode === 'deposit') return void charge.send(amount)
    if (blocked || payoutFlow.busy) return
    // 전환은 되돌릴 수 없어 한 번 더 묻는다.
    if (convert) return setAsking(trimAbcAmount(amount))
    payoutFlow.request(amount)
  }
  const handleDismissWithdrawal = () => {
    payoutFlow.dismiss()
    setTyped('')
  }

  const confirmRef = useRef<HTMLElement>(null)
  // 의존성 배열을 두지 않는다 — 처음 렌더의 asking 이 굳는다.
  useEffect(() => {
    const el = confirmRef.current
    if (!el) return
    const onConfirm = () => {
      if (asking) payoutFlow.convert(asking)
      setAsking(null)
      setTyped('')
    }
    const onCancel = () => setAsking(null)
    el.addEventListener('confirm', onConfirm)
    el.addEventListener('cancel', onCancel)
    return () => {
      el.removeEventListener('confirm', onConfirm)
      el.removeEventListener('cancel', onCancel)
    }
  })

  return (
    <>
      <MyPageFrame>
        <Sidebar active="credit" />

        <div
          className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
          style={{
            left: '442px',
            top: '158px',
            textShadow: '0 2px 20px rgba(35,8,45,.6)',
            animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          예치/출금
        </div>
        <div
          className="absolute text-[13px] tracking-[-.01em]"
          style={{
            left: '442px',
            top: '206px',
            width: '820px',
            color: 'rgba(255,236,246,.7)',
            animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          지갑의 ABC 를 서비스에 예치하거나, 판매로 받은 정산금을 연결된 지갑으로 보냅니다.
        </div>

        <div
          className="absolute flex"
          style={{
            left: '442px',
            top: '250px',
            height: '38px',
            gap: '10px',
            animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {MODES.map((m) => (
            <div
              key={m.key}
              className="flex cursor-pointer items-center text-[14.5px] font-semibold transition-colors duration-[160ms]"
              onClick={() => handlePickMode(m.key)}
              style={{
                height: '38px',
                padding: '0 20px',
                borderRadius: '11px',
                background: mode === m.key ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.09)',
                color: mode === m.key ? '#3a1030' : 'rgba(255,240,247,.78)',
              }}
            >
              {m.label}
            </div>
          ))}
        </div>

        <div
          className="bg-surface-card absolute box-border flex items-center justify-between"
          style={{
            left: '442px',
            top: '300px',
            width: '896px',
            height: '90px',
            padding: '0 24px',
            borderRadius: '12px',
            backdropFilter: 'blur(18px) saturate(115%)',
            WebkitBackdropFilter: 'blur(18px) saturate(115%)',
            animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div>
            <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
              {bannerLabel}
            </div>
            <div
              className="text-[26px] font-semibold tracking-[-.01em] text-white"
              style={{ marginTop: '4px' }}
            >
              {bannerValue}
            </div>
          </div>
          <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.58)' }}>
            {bannerNote}
          </div>
        </div>

        <div
          className="absolute flex"
          style={{
            left: '442px',
            top: '424px',
            alignItems: 'baseline',
            gap: '14px',
            animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div className="text-[18px] font-semibold tracking-[-.01em] text-white">
            {pickerTitle}
          </div>
          <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.58)' }}>
            {pickerNote}
          </div>
        </div>

        <div
          className="absolute flex"
          style={{ left: '442px', top: '460px', width: '896px', gap: '16px' }}
        >
          {presets.map((p, i) => (
            <Fragment key={i}>
              <div
                onClick={p.pick}
                style={{
                  width: '212px',
                  height: '96px',
                  padding: '0 22px',
                  borderRadius: '12px',
                  background: `${p.bg}`,
                  boxShadow: `${p.ring}`,
                  transition: 'background .16s,box-shadow .16s',
                  animation: `${p.anim}`,
                }}
                className={`box-border flex flex-none items-center ${p.disabled ? 'cursor-not-allowed' : 'cursor-pointer hover:bg-[rgba(255,255,255,.14)]'}`}
              >
                <div
                  className="text-[24px] font-semibold tracking-[-.01em]"
                  style={{ color: `${p.fg}` }}
                >
                  {p.credit}
                </div>
              </div>
            </Fragment>
          ))}
        </div>

        <div
          className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
          style={{ left: '442px', top: '590px' }}
        >
          직접 입력
        </div>
        <div
          className="absolute box-border flex items-center justify-between"
          style={{
            left: '442px',
            top: '620px',
            width: '896px',
            height: '64px',
            padding: '0 22px',
            borderRadius: '10px',
            background: 'rgba(255,255,255,.06)',
          }}
        >
          <input
            className="flex-1 border-0 bg-transparent font-[inherit] text-[16px] tracking-[-.01em] text-white outline-0"
            id="creditInput"
            type="text"
            onChange={(e) => setTyped(e.target.value)}
            placeholder={inputPlaceholder}
            style={{ height: '32px' }}
            value={typed}
          />
          <div className="text-[16px] font-semibold" style={{ color: 'rgba(255,236,246,.6)' }}>
            ABC
          </div>
        </div>

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
            <>
              <div style={{ width: '530px' }}>
                <div className="text-[15.5px] font-semibold tracking-[-.01em] text-white">
                  {chargeCard.title}
                </div>
                <div
                  className="text-[15.5px]"
                  style={{ marginTop: '10px', color: 'rgba(255,236,246,.72)' }}
                >
                  {chargeCard.note}
                </div>
                {/* 해시는 문의할 때 유일한 단서다. 끝날 때까지 지우지 않는다. */}
                {charge.transactionHash && (
                  <div
                    className="truncate text-[15.5px]"
                    style={{ marginTop: '8px', color: 'rgba(255,236,246,.5)' }}
                  >
                    거래 해시 {charge.transactionHash}
                  </div>
                )}
              </div>
              {chargeCard.action && (
                <div
                  onClick={chargeCard.action.run}
                  style={{
                    width: '300px',
                    height: '60px',
                    borderRadius: '10px',
                    background: 'var(--color-accent)',
                    color: 'var(--color-accent-ink)',
                    transition: 'filter .16s',
                  }}
                  className="flex cursor-pointer items-center justify-center text-[17px] font-semibold tracking-[-.01em] hover:brightness-[1.06]"
                >
                  {chargeCard.action.label}
                </div>
              )}
            </>
          ) : withdrawal && withdrawalCard ? (
            <>
              <div style={{ width: '530px' }}>
                <div className="text-[15.5px] font-semibold tracking-[-.01em] text-white">
                  {withdrawalCard.title} · {chargeAmountLabel(withdrawal.amount)}
                </div>
                <div
                  className="text-[15.5px]"
                  style={{ marginTop: '10px', color: 'rgba(255,236,246,.72)' }}
                >
                  {withdrawal.rejectReason
                    ? `${withdrawalCard.note} 사유: ${withdrawal.rejectReason}`
                    : withdrawalCard.note}
                </div>
                <div
                  className="truncate text-[15.5px]"
                  style={{ marginTop: '8px', color: 'rgba(255,196,214,.9)' }}
                >
                  {payoutFlow.error ?? (
                    <span style={{ color: 'rgba(255,236,246,.5)' }}>
                      받을 지갑 {shortAddress(withdrawal.walletAddress)} · 신청 번호 {withdrawal.id}
                      {withdrawal.transactionHash &&
                        ` · 거래 해시 ${shortAddress(withdrawal.transactionHash)}`}
                    </span>
                  )}
                </div>
              </div>
              {/* PAYING 부터는 체인으로 나가는 중이라 취소가 없다. */}
              <div className="flex flex-col" style={{ gap: '8px' }}>
                <div
                  onClick={handleDismissWithdrawal}
                  style={{
                    width: '300px',
                    height: withdrawal.status === 'REQUESTED' ? '50px' : '60px',
                    borderRadius: '10px',
                    background: 'var(--color-accent)',
                    color: 'var(--color-accent-ink)',
                    transition: 'filter .16s',
                  }}
                  className="flex cursor-pointer items-center justify-center text-[17px] font-semibold tracking-[-.01em] hover:brightness-[1.06]"
                >
                  확인
                </div>
                {withdrawal.status === 'REQUESTED' && (
                  <div
                    onClick={payoutFlow.busy ? undefined : payoutFlow.cancel}
                    className="flex cursor-pointer items-center justify-center text-[15.5px] font-semibold hover:text-white"
                    style={{ height: '24px', color: 'rgba(255,236,246,.72)' }}
                  >
                    {payoutFlow.busy ? '취소하는 중' : '신청 취소'}
                  </div>
                )}
              </div>
            </>
          ) : (
            <>
              <div style={{ width: '500px' }}>
                <div
                  className="text-[14px] font-semibold"
                  style={{ color: 'rgba(255,236,246,.62)' }}
                >
                  {summaryTitle}
                </div>
                <div
                  className="flex items-baseline justify-between"
                  style={{ marginTop: '10px', paddingBottom: '14px' }}
                >
                  <span className="text-[15px]" style={{ color: 'rgba(255,236,246,.72)' }}>
                    {sumLabel}
                  </span>
                  <span className="text-[26px] font-semibold tracking-[-.02em] text-white">
                    {sumCredit}
                  </span>
                </div>
                <div style={{ paddingTop: '12px', borderTop: '1px solid rgba(255,255,255,.1)' }}>
                  {details.map((d, i) => (
                    <div
                      key={d.label}
                      className="flex items-baseline justify-between"
                      style={{ marginTop: i === 0 ? 0 : '8px' }}
                    >
                      <span className="text-[15px]" style={{ color: 'rgba(255,236,246,.72)' }}>
                        {d.label}
                      </span>
                      <span
                        className="text-[15.5px] font-semibold"
                        style={{ color: 'rgba(255,255,255,.86)' }}
                      >
                        {d.value}
                      </span>
                    </div>
                  ))}
                </div>
                {/* 지갑을 열기 전에 걸린 것들 — 금액이 틀렸거나 지갑이 다르거나 거절했다. */}
                {!withdraw && charge.error && (
                  <div
                    className="text-[15px]"
                    style={{ marginTop: '10px', color: 'rgba(255,196,214,.9)' }}
                  >
                    {charge.error}
                  </div>
                )}
                {usesPayout &&
                  (payoutFlow.error || payoutFlow.notice || (typed.trim() && blocked)) && (
                    <div
                      className="text-[15px]"
                      style={{
                        marginTop: '10px',
                        color:
                          payoutFlow.error || !payoutFlow.notice
                            ? 'rgba(255,196,214,.9)'
                            : '#9ad0b4',
                      }}
                    >
                      {payoutFlow.error ?? payoutFlow.notice ?? blocked}
                    </div>
                  )}
              </div>
              <div
                className="flex flex-col justify-center self-stretch"
                style={{
                  gap: '12px',
                  paddingLeft: '30px',
                  borderLeft: '1px solid rgba(255,255,255,.1)',
                }}
              >
                <div
                  onClick={handleCta}
                  style={{
                    width: '280px',
                    height: '58px',
                    borderRadius: '14px',
                    background: 'var(--color-accent)',
                    color: 'var(--color-accent-ink)',
                    opacity: blocked ? 0.5 : 1,
                    transition: 'filter .16s',
                  }}
                  className={`flex items-center justify-center text-[17px] font-semibold tracking-[-.01em] ${blocked ? 'cursor-not-allowed' : 'cursor-pointer hover:brightness-[1.06]'}`}
                >
                  {usesPayout && payoutFlow.busy ? '처리 중' : ctaLabel}
                </div>
              </div>
            </>
          )}
        </div>
      </MyPageFrame>

      {/* 프레임 밖에 둔다 — 안에 두면 가운데로 민 만큼 덮개가 비껴 난다. */}
      <confirm-dialog
        ref={confirmRef}
        open={asking ? '' : undefined}
        heading="정산금을 ABC 로 전환할까요?"
        body-text={'전환한 ABC 는 실행에만 쓸 수 있고\n정산금으로 되돌릴 수 없습니다.'}
        note-text={asking ? `${chargeAmountLabel(asking)} 전환` : undefined}
        confirm-label="전환하기"
        cancel-label="취소"
      />
    </>
  )
}
