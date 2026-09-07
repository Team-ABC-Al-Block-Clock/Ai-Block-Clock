import { Fragment, useState } from 'react'
import Sidebar from '../components/Sidebar'

export default function MyPageCredit() {
  const [pick, setPick] = useState(2)
  const [mode, setMode] = useState('deposit')

  const DEPOSITED = 2500,
    AVAILABLE = 640,
    WALLET = 3120
  const withdraw = mode === 'withdraw'
  const raw = withdraw
    ? [
        { amount: 100 },
        { amount: 200 },
        { amount: 400 },
        { amount: AVAILABLE, note: '전액 출금' },
      ].map((p) => ({
        credit: p.amount.toLocaleString() + ' ABC',
        price: p.note || '출금 후 ' + (AVAILABLE - p.amount).toLocaleString() + ' ABC',
        amount: p.amount,
      }))
    : [500, 1000, 2500, 5000].map((amount) => ({
        credit: amount.toLocaleString() + ' ABC',
        price: '예치 후 ' + (DEPOSITED + amount).toLocaleString() + ' ABC',
        amount,
      }))
  const presets = raw.map((p, i) => ({
    ...p,
    bg: i === pick ? 'rgba(185,141,169,.32)' : 'rgba(255,255,255,.08)',
    ring: 'none',
    fg: i === pick ? '#e6cbe0' : '#fff',
    anim: 'rise .7s ' + (0.2 + i * 0.04).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
    pick: () => setPick(i),
  }))
  const cur = raw[Math.min(pick, raw.length - 1)]
  const sumCredit = cur.amount.toLocaleString() + ' ABC'
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
  const bannerValue = AVAILABLE.toLocaleString() + ' ABC'
  const bannerNote = withdraw
    ? '출금은 연결된 MetaMask 지갑으로 전송됩니다'
    : '예치한 코인은 언제든 출금할 수 있습니다'
  const pickerTitle = withdraw ? '출금 금액 선택' : '예치 금액 선택'
  const pickerNote = withdraw
    ? '예치금 ' + DEPOSITED.toLocaleString() + ' ABC · 사용 중인 코인은 출금할 수 없습니다'
    : '지갑 잔액 ' + WALLET.toLocaleString() + ' ABC · 예치한 코인은 언제든 출금할 수 있습니다'
  const inputPlaceholder = withdraw
    ? '출금할 코인을 입력하세요 (최소 10 ABC)'
    : '예치할 코인을 입력하세요 (최소 100 ABC)'
  const summaryTitle = withdraw ? '출금 요약' : '예치 요약'
  const sumLabel = withdraw ? '출금 코인' : '예치 코인'
  const ctaLabel = withdraw ? '출금하기' : '예치하기'

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(20,9,34,.42) 0%, rgba(20,9,34,.22) 30%, rgba(20,9,34,.30) 100%)',
        }}
      ></div>
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
              className="box-border flex-none cursor-pointer hover:bg-[rgba(255,255,255,.14)]"
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
          placeholder={inputPlaceholder}
          style={{ height: '32px' }}
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
            <span className="text-[13px] font-semibold" style={{ color: 'rgba(255,255,255,.86)' }}>
              ≈ 0.01 MATIC
            </span>
          </div>
        </div>
        <div
          style={{
            width: '262px',
            height: '52px',
            borderRadius: '10px',
            background: '#b98da9',
            color: '#2f1527',
            transition: 'filter .16s',
          }}
          className="flex cursor-pointer items-center justify-center text-[16px] font-semibold tracking-[-.01em] hover:brightness-[1.06]"
        >
          {ctaLabel}
        </div>
      </div>
    </>
  )
}
