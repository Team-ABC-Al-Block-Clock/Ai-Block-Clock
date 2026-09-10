import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAccount, useConnect, useSwitchChain } from 'wagmi'

import { ApiError } from '@/api/errors'
import type { WalletPhase } from '@/components/wallet-dialog'
import { shortAddress } from '@/lib/format'
import { chain, chainLabel, describeWalletError } from '@/lib/wagmi'
import { expiredFrom, redirectTargetFrom } from '@/routes/access'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'

/** 서명은 182 몫입니다. 지금은 자리표시자로 세션까지 열어 흐름을 확인합니다. */
const PLACEHOLDER_SIGNATURE = `0x${'ab'.repeat(65)}`

/** 지갑 연결로 시작하는 로그인 화면. 좌표는 1440×1024 설계 그대로 인라인 style 에 둡니다(9절). */
export default function Login() {
  const [phase, setPhase] = useState<WalletPhase>('idle')
  const [wallet, setWallet] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const navigate = useNavigate()
  const { state } = useLocation()
  const signIn = useAuthStore((s) => s.signIn)
  // 쓰던 중 밀려온 것인지. 이유를 알려주지 않으면 로그아웃한 것과 구분되지 않는다.
  const expired = expiredFrom(state)

  const { address, chainId } = useAccount()
  const { connectAsync, connectors, isPending } = useConnect()
  const { switchChainAsync } = useSwitchChain()

  const handleConnect = async () => {
    setError(null)
    try {
      let connected = address
      let onChain = chainId

      if (!connected) {
        const result = await connectAsync({ connector: connectors[0] })
        connected = result.accounts[0]
        onChain = result.chainId
      }

      // 거절해도 로그인은 막지 않는다. 그 뒤로는 ChainBanner 가 계속 안내한다.
      if (onChain !== chain.id) {
        await switchChainAsync({ chainId: chain.id }).catch(() => undefined)
      }

      setWallet(connected)
      setPhase('request')
    } catch (e) {
      setError(describeWalletError(e).message)
    }
  }

  const handleSign = useCallback(async () => {
    if (!wallet) return
    setPhase('signing')
    try {
      await signIn({ walletAddress: wallet, signature: PLACEHOLDER_SIGNATURE })
      // 막혀 왔으면 그 경로로, 직접 들어왔으면 홈으로.
      // replace — 로그인 화면이 히스토리에 남으면 뒤로 가기가 다시 여기로 온다.
      navigate(redirectTargetFrom(state), { replace: true })
    } catch (e) {
      // 거절 화면 문구는 사용자가 서명을 거부한 경우다(182). 서버 실패는 카드에 알린다.
      setPhase('idle')
      setError(e instanceof ApiError ? e.message : describeWalletError(e).message)
    }
  }, [wallet, signIn, navigate, state])

  // 커스텀 엘리먼트의 이벤트는 props 로 못 받는다 — ref 로 직접 듣는다.
  const dialogRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const el = dialogRef.current
    if (!el) return
    const onSign = () => void handleSign()
    const onCancel = () => setPhase('idle')
    el.addEventListener('sign', onSign)
    el.addEventListener('cancel', onCancel)
    return () => {
      el.removeEventListener('sign', onSign)
      el.removeEventListener('cancel', onCancel)
    }
  }, [handleSign])

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(20,9,34,.3) 0%, rgba(20,9,34,.12) 34%, rgba(20,9,34,.28) 100%)',
        }}
      />

      {expired && (
        <div
          className="animate-rise absolute box-border flex w-[432px] items-center gap-[11px] rounded-xl"
          style={{ left: 504, top: 140, padding: '14px 16px', background: 'rgba(233,174,196,.16)' }}
        >
          <div
            className="flex size-[22px] flex-none items-center justify-center rounded-full text-xs font-bold"
            style={{ background: 'rgba(233,174,196,.28)', color: '#ffd7e6' }}
          >
            !
          </div>
          <div
            className="text-[12.5px] leading-[1.55] tracking-[-.01em]"
            style={{ color: 'rgba(255,240,247,.88)' }}
          >
            로그인이 만료되었습니다. 지갑을 다시 연결해 주세요.
          </div>
        </div>
      )}

      <div
        className="animate-rise absolute w-[432px] text-center text-[34px] font-bold tracking-[-.02em]"
        style={{ left: 504, top: 206, textShadow: '0 2px 22px rgba(35,8,45,.6)' }}
      >
        다시 오셨네요
      </div>
      <div
        className="text-muted animate-rise absolute w-[432px] text-center text-sm tracking-[-.01em] [animation-delay:.1s]"
        style={{ left: 504, top: 258 }}
      >
        MetaMask 지갑을 연결해 시작하세요
      </div>

      <div
        className="glass-panel animate-rise absolute box-border w-[432px] rounded-[22px] bg-[rgb(20_11_36/0.5)] p-8 [animation-delay:.14s]"
        style={{ left: 504, top: 322 }}
      >
        <div className="text-[15px] font-semibold tracking-[-.01em]">지갑으로 시작하기</div>
        <div className="text-muted mt-[9px] text-[13px] leading-[1.65] tracking-[-.01em]">
          별도의 아이디·비밀번호가 없습니다. 지갑 서명이 곧 인증이며,
          <br />
          연결한 주소로 정산금과 사용 내역이 관리됩니다.
        </div>

        <div className="mt-[22px] flex flex-col gap-[9px]">
          {[`${chainLabel} 지원`, '서명만 요청하며 자산 이동 권한은 요구하지 않습니다'].map((t) => (
            <div key={t} className="flex items-center gap-[9px]">
              <div className="bg-lilac size-1.5 flex-none rounded-full" />
              <div className="text-body text-[12.5px] tracking-[-.01em]">{t}</div>
            </div>
          ))}
        </div>

        <div className="mt-[26px] h-px bg-white/12" />

        <button
          type="button"
          onClick={() => void handleConnect()}
          disabled={isPending}
          className="bg-accent text-accent-ink mt-6 flex h-14 w-full cursor-pointer items-center justify-center gap-2.5 rounded-[14px] transition-transform duration-200 hover:-translate-y-0.5 disabled:cursor-default disabled:opacity-60 disabled:hover:translate-y-0"
          style={{ boxShadow: '0 10px 24px rgba(140,100,130,.24)' }}
        >
          <div className="bg-accent-ink/20 flex size-[22px] flex-none items-center justify-center rounded-md text-[11px] font-bold">
            M
          </div>
          <div className="text-base font-bold tracking-[-.01em]">
            {isPending ? '지갑 확인 중…' : 'MetaMask 지갑 연결'}
          </div>
        </button>

        {error && (
          <div className="mt-3.5 text-[12.5px] leading-[1.5] tracking-[-.01em] text-[#ffb4c8]">
            {error}
          </div>
        )}

        <div className="text-muted mt-6 text-center text-[12.5px] tracking-[-.01em]">
          처음이신가요? 지갑을 연결하면{' '}
          <a href={paths.signup} className="font-semibold text-[#e9b7d2]">
            프로필 등록
          </a>
          으로 이어집니다
        </div>
      </div>

      <wallet-dialog ref={dialogRef} phase={phase} address={wallet ? shortAddress(wallet) : ''} />
    </>
  )
}
