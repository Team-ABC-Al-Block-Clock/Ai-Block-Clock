import { useEffect, useRef, useState } from 'react'

import StatusDot from '@/components/StatusDot'
import { useWalletStatus } from '@/hooks/wallet/useWalletStatus'

/** 연결된 지갑 카드. 보여 주고 주소를 복사할 뿐 바꾸지는 않는다. */
export default function WalletSection() {
  const wallet = useWalletStatus()
  const [copied, setCopied] = useState(false)
  const copiedTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(copiedTimer.current), [])
  const handleCopyAddress = () => {
    void navigator.clipboard
      ?.writeText(wallet.walletAddress)
      .then(() => {
        setCopied(true)
        // 연달아 누르면 앞 타이머가 새 '복사됨' 을 일찍 지우므로 끊고 다시 잰다
        clearTimeout(copiedTimer.current)
        copiedTimer.current = setTimeout(() => setCopied(false), 1500)
      })
      .catch(() => undefined)
  }

  return (
    <div
      className="bg-surface-card absolute box-border"
      style={{
        left: '442px',
        top: '464px',
        width: '900px',
        height: '214px',
        padding: '24px',
        borderRadius: '16px',
        animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      <div className="text-[16px] font-semibold tracking-[-.01em] text-white">지갑 연결</div>
      <div
        className="box-border flex items-center"
        style={{
          marginTop: '18px',
          height: '96px',
          padding: '0 22px',
          borderRadius: '14px',
          background: 'rgba(255,255,255,.06)',
          gap: '18px',
        }}
      >
        <div
          className="flex flex-none items-center justify-center rounded-full"
          style={{
            width: '56px',
            height: '56px',
            background: 'linear-gradient(135deg, rgba(233,174,196,.45), rgba(160,148,194,.45))',
          }}
        >
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect x="3" y="6" width="18" height="13" rx="3" stroke="#fff" strokeWidth="1.8" />
            <path d="M3 10h18" stroke="#fff" strokeWidth="1.8" />
            <circle cx="16.5" cy="14.5" r="1.4" fill="#fff" />
          </svg>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center" style={{ gap: '10px' }}>
            <div className="text-[17px] font-semibold text-white">{wallet.provider}</div>
            <div
              className="flex items-center text-[13px] font-semibold"
              style={{
                gap: '6px',
                height: '24px',
                padding: '0 10px',
                borderRadius: '12px',
                background: 'rgba(255,255,255,.08)',
                color: wallet.connection.color,
              }}
            >
              <StatusDot size={7} color={wallet.connection.dot} />
              {wallet.connection.text}
            </div>
          </div>
          {/* 대조해 보라고 두는 값이라 주소를 줄이지 않는다. */}
          <div
            className="truncate text-[14.5px]"
            style={{
              marginTop: '6px',
              color: 'rgba(255,255,255,.82)',
              fontVariantNumeric: 'tabular-nums',
              letterSpacing: '.01em',
            }}
          >
            {wallet.walletAddress || '—'}
          </div>
        </div>
        <div className="flex-none text-right" style={{ width: '120px' }}>
          <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.58)' }}>
            네트워크
          </div>
          <div className="text-[15px] font-semibold text-white" style={{ marginTop: '4px' }}>
            {wallet.network}
          </div>
        </div>
        <button
          type="button"
          onClick={handleCopyAddress}
          disabled={!wallet.walletAddress}
          className="flex flex-none cursor-pointer items-center bg-[rgba(255,255,255,.10)] text-[14px] font-semibold whitespace-nowrap transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)]"
          style={{ height: '38px', padding: '0 16px', borderRadius: '11px', color: '#fff' }}
        >
          {copied ? '복사됨' : '주소 복사'}
        </button>
      </div>
      <div className="text-[13px]" style={{ marginTop: '12px', color: 'rgba(255,236,246,.55)' }}>
        다른 지갑을 사용하시면 해당 지갑으로 다시 로그인해 주세요.
      </div>
    </div>
  )
}
