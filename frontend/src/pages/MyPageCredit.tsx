import { useQuery } from '@tanstack/react-query'
import { Fragment, useState } from 'react'
import Sidebar from '../components/Sidebar'
import { getCreditBalance } from '@/api/credits'
import { useAbcCharge, type AbcCharge } from '@/hooks/useAbcCharge'
import { isSendableAmount, trimAbcAmount } from '@/lib/abcToken'
import { chargeFailureText } from '@/lib/chargeMessage'
import { chargeAmountLabel, priceLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'

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
  const [mode, setMode] = useState('deposit')
  const [typed, setTyped] = useState('')
  const charge = useAbcCharge()

  const balance = useQuery({
    queryKey: queryKeys.credits.balance(),
    queryFn: getCreditBalance,
  })

  // 예치금·지갑 잔액은 충전 API 가 열리면 붙인다.
  const DEPOSITED = 2500,
    WALLET = 3120
  // 프리셋 계산에만 숫자를 쓴다. 표시는 서버 문자열 그대로다.
  const available = Number(balance.data?.availableAmount ?? 0)
  const withdraw = mode === 'withdraw'
  const raw = withdraw
    ? [
        { amount: 100 },
        { amount: 200 },
        { amount: 400 },
        { amount: available, note: '전액 출금' },
      ].map((p) => ({
        credit: p.amount.toLocaleString() + ' ABC',
        // 잔액을 넘는 금액은 뺀 결과가 음수라 「출금 후 -260 ABC」 가 되어 버린다.
        price:
          p.amount > available
            ? '잔액 부족'
            : p.note || '출금 후 ' + (available - p.amount).toLocaleString() + ' ABC',
        amount: p.amount,
        disabled: p.amount > available || p.amount <= 0,
      }))
    : [500, 1000, 2500, 5000].map((amount) => ({
        credit: amount.toLocaleString() + ' ABC',
        price: '예치 후 ' + (DEPOSITED + amount).toLocaleString() + ' ABC',
        amount,
        disabled: false,
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
  const handlePickDeposit = () => {
    setMode('deposit')
    setPick(2)
  }
  const handlePickWithdraw = () => {
    setMode('withdraw')
    setPick(2)
  }
  const bgDeposit = withdraw ? 'rgba(255,255,255,.09)' : 'rgba(255,255,255,.9)'
  const fgDeposit = withdraw ? 'rgba(255,240,247,.78)' : '#3a1030'
  const bgWithdraw = withdraw ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.09)'
  const fgWithdraw = withdraw ? '#3a1030' : 'rgba(255,240,247,.78)'
  const bannerLabel = withdraw ? '출금 가능 코인' : '현재 사용 가능 코인'
  // 불러오는 중에 0 을 띄우면 잔액이 비어 보였다가 튄다.
  const bannerValue = balance.data ? priceLabel(balance.data.availableAmount) : '—'
  const reserved = balance.data?.reservedAmount
  const reservedNote =
    reserved && Number(reserved) > 0 ? `실행에 잠긴 코인 ${priceLabel(reserved)} · ` : ''
  const bannerNote = balance.isError
    ? '잔액을 불러오지 못했습니다'
    : withdraw
      ? reservedNote + '출금은 연결된 MetaMask 지갑으로 전송됩니다'
      : reservedNote + '예치한 코인은 언제든 출금할 수 있습니다'
  const pickerTitle = withdraw ? '출금 금액 선택' : '예치 금액 선택'
  const pickerNote = withdraw
    ? '예치금 ' + DEPOSITED.toLocaleString() + ' ABC · 사용 중인 코인은 출금할 수 없습니다'
    : '지갑 잔액 ' + WALLET.toLocaleString() + ' ABC · ABC 는 다른 사용자에게 양도할 수 없습니다'
  const inputPlaceholder = withdraw ? '출금할 코인을 입력하세요' : '예치할 코인을 입력하세요'
  const summaryTitle = withdraw ? '출금 요약' : '예치 요약'
  const sumLabel = withdraw ? '출금 코인' : '예치 코인'
  const ctaLabel = withdraw ? '출금하기' : '예치하기'

  // 출금은 서버에 기능이 없다 — 예치일 때만 지갑을 연다.
  const charging = !withdraw && charge.phase !== 'idle'
  const chargeCard = describeCharge(charge, () => {
    charge.reset()
    setTyped('')
  })

  return (
    <>
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
        코인 예치/출금
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
        지갑의 ABC 코인을 서비스에 예치하거나, 사용하지 않은 코인을 지갑으로 되돌립니다.
      </div>

      <div
        className="absolute flex"
        style={{
          left: '442px',
          top: '250px',
          height: '34px',
          gap: '10px',
          animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div
          className="flex cursor-pointer items-center text-[13px] font-semibold transition-colors duration-[160ms]"
          onClick={handlePickDeposit}
          style={{
            height: '34px',
            padding: '0 18px',
            borderRadius: '11px',
            background: `${bgDeposit}`,
            color: `${fgDeposit}`,
          }}
        >
          예치
        </div>
        <div
          className="flex cursor-pointer items-center text-[13px] font-semibold transition-colors duration-[160ms]"
          onClick={handlePickWithdraw}
          style={{
            height: '34px',
            padding: '0 18px',
            borderRadius: '11px',
            background: `${bgWithdraw}`,
            color: `${fgWithdraw}`,
          }}
        >
          출금
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border flex items-center justify-between"
        style={{
          left: '442px',
          top: '294px',
          width: '896px',
          height: '64px',
          padding: '0 24px',
          borderRadius: '12px',
          backdropFilter: 'blur(18px) saturate(115%)',
          WebkitBackdropFilter: 'blur(18px) saturate(115%)',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div>
          <div className="text-[12px]" style={{ color: 'rgba(255,236,246,.62)' }}>
            {bannerLabel}
          </div>
          <div
            className="text-[20px] font-semibold tracking-[-.01em] text-white"
            style={{ marginTop: '3px' }}
          >
            {bannerValue}
          </div>
        </div>
        <div className="text-[12px]" style={{ color: 'rgba(255,236,246,.58)' }}>
          {bannerNote}
        </div>
      </div>

      <div
        className="absolute flex"
        style={{
          left: '442px',
          top: '392px',
          alignItems: 'baseline',
          gap: '14px',
          animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[16px] font-semibold tracking-[-.01em] text-white">{pickerTitle}</div>
        <div className="text-[12px]" style={{ color: 'rgba(255,236,246,.58)' }}>
          {pickerNote}
        </div>
      </div>

      <div
        className="absolute flex"
        style={{ left: '442px', top: '424px', width: '896px', gap: '16px' }}
      >
        {presets.map((p, i) => (
          <Fragment key={i}>
            <div
              onClick={p.pick}
              style={{
                width: '212px',
                height: '88px',
                padding: '16px 18px',
                borderRadius: '12px',
                background: `${p.bg}`,
                boxShadow: `${p.ring}`,
                transition: 'background .16s,box-shadow .16s',
                animation: `${p.anim}`,
              }}
              className={`box-border flex-none ${p.disabled ? 'cursor-not-allowed' : 'cursor-pointer hover:bg-[rgba(255,255,255,.14)]'}`}
            >
              <div
                className="text-[18px] font-semibold tracking-[-.01em]"
                style={{ color: `${p.fg}` }}
              >
                {p.credit}
              </div>
              <div
                className="text-[12.5px]"
                style={{ marginTop: '8px', color: 'rgba(255,236,246,.62)' }}
              >
                {p.price}
              </div>
            </div>
          </Fragment>
        ))}
      </div>

      <div
        className="absolute text-[13px] font-semibold tracking-[-.01em] text-white"
        style={{ left: '442px', top: '540px' }}
      >
        직접 입력
      </div>
      <div
        className="absolute box-border flex items-center justify-between"
        style={{
          left: '442px',
          top: '564px',
          width: '896px',
          height: '52px',
          padding: '0 20px',
          borderRadius: '10px',
          background: 'rgba(255,255,255,.06)',
        }}
      >
        <input
          className="flex-1 border-0 bg-transparent font-[inherit] text-[14px] tracking-[-.01em] text-white outline-0"
          id="creditInput"
          type="text"
          onChange={(e) => setTyped(e.target.value)}
          placeholder={inputPlaceholder}
          style={{ height: '32px' }}
          value={typed}
        />
        <div className="text-[14px] font-semibold" style={{ color: 'rgba(255,236,246,.6)' }}>
          ABC
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border flex items-center justify-between"
        style={{
          left: '442px',
          top: '660px',
          width: '896px',
          height: '120px',
          padding: '20px 24px',
          borderRadius: '14px',
          backdropFilter: 'blur(18px) saturate(115%)',
          WebkitBackdropFilter: 'blur(18px) saturate(115%)',
        }}
      >
        {/* 충전이 시작되면 요약 자리를 진행 상태가 가져간다 — 보내는 버튼을 남겨 두면 두 번 보낸다. */}
        {charging ? (
          <>
            <div style={{ width: '530px' }}>
              <div className="text-[13px] font-semibold tracking-[-.01em] text-white">
                {chargeCard.title}
              </div>
              <div
                className="text-[12.5px]"
                style={{ marginTop: '10px', color: 'rgba(255,236,246,.72)' }}
              >
                {chargeCard.note}
              </div>
              {/* 해시는 문의할 때 유일한 단서다. 끝날 때까지 지우지 않는다. */}
              {charge.transactionHash && (
                <div
                  className="truncate text-[11.5px]"
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
                  width: '262px',
                  height: '52px',
                  borderRadius: '10px',
                  background: 'var(--color-accent)',
                  color: 'var(--color-accent-ink)',
                  transition: 'filter .16s',
                }}
                className="flex cursor-pointer items-center justify-center text-[16px] font-semibold tracking-[-.01em] hover:brightness-[1.06]"
              >
                {chargeCard.action.label}
              </div>
            )}
          </>
        ) : (
          <>
            <div style={{ width: '530px' }}>
              <div className="text-[13px] font-semibold tracking-[-.01em] text-white">
                {summaryTitle}
              </div>
              <div
                className="flex justify-between"
                style={{ marginTop: '14px', alignItems: 'baseline' }}
              >
                <span className="text-[12px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                  {sumLabel}
                </span>
                <span className="text-[13px] font-semibold text-white">{sumCredit}</span>
              </div>
              <div
                className="flex justify-between"
                style={{ marginTop: '8px', alignItems: 'baseline' }}
              >
                <span className="text-[12px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                  예상 가스비
                </span>
                {/* 가스비가 0 인 체인이다. 「≈」 를 붙이면 조금은 든다는 말이 된다. */}
                <span
                  className="text-[13px] font-semibold"
                  style={{ color: 'rgba(255,255,255,.86)' }}
                >
                  0 SSF
                </span>
              </div>
              {/* 지갑을 열기 전에 걸린 것들 — 금액이 틀렸거나 지갑이 다르거나 거절했다. */}
              {!withdraw && charge.error && (
                <div
                  className="text-[12px]"
                  style={{ marginTop: '10px', color: 'rgba(255,196,214,.9)' }}
                >
                  {charge.error}
                </div>
              )}
            </div>
            <div
              onClick={withdraw ? undefined : () => void charge.send(amount)}
              style={{
                width: '262px',
                height: '52px',
                borderRadius: '10px',
                background: 'var(--color-accent)',
                color: 'var(--color-accent-ink)',
                transition: 'filter .16s',
              }}
              className="flex cursor-pointer items-center justify-center text-[16px] font-semibold tracking-[-.01em] hover:brightness-[1.06]"
            >
              {ctaLabel}
            </div>
          </>
        )}
      </div>
    </>
  )
}
