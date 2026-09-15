import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAccount, useConnect, useSignMessage, useSwitchChain } from 'wagmi'

import { requestWalletMessage } from '@/api/auth'
import { ApiError } from '@/api/errors'
import type { WalletPhase } from '@/components/wallet-dialog'
import { shortAddress } from '@/lib/format'
import { chain, describeWalletError } from '@/lib/wagmi'
import { endReasonFrom, redirectTargetFrom } from '@/routes/access'
import { paths } from '@/routes/paths'
import { useAuthStore, type SessionEndReason } from '@/stores/authStore'
import logo from '../assets/logo.png'

/** 밀려온 이유별 안내. 스스로 로그아웃한 사람에게는 아무것도 띄우지 않는다. */
const ENDED_BY: Record<SessionEndReason, string> = {
  expired: '세션이 만료되었습니다. 다시 로그인해 주세요.',
  revoked: '보안을 위해 세션이 종료되었습니다. 다시 로그인해 주세요.',
  invalid: '인증 정보를 확인할 수 없습니다. 다시 로그인해 주세요.',
  disconnected: '지갑 연결이 해제되어 로그아웃되었습니다. 다시 연결해 주세요.',
  account: '지갑 계정이 바뀌어 로그아웃되었습니다. 새 계정으로 다시 로그인해 주세요.',
}

/** 지갑 연결로 시작하는 로그인 화면. 좌표는 1440×1024 설계 그대로 인라인 style 에 둔다(9절). */
export default function Login() {
  const [phase, setPhase] = useState<WalletPhase>('idle')
  const [wallet, setWallet] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** 서명 대기 화면이 보여 주는 원문과, 거절 화면이 보여 주는 코드 */
  const [message, setMessage] = useState<string | null>(null)
  const [errorCode, setErrorCode] = useState<string | null>(null)

  const navigate = useNavigate()
  const { state } = useLocation()
  const signIn = useAuthStore((s) => s.signIn)
  // 이미 세션이 있는 사람에게 지갑 연결 버튼을 다시 보여 줄 이유가 없다.
  const status = useAuthStore((s) => s.status)
  // 쓰던 중 밀려온 것인지. 이유를 알려주지 않으면 로그아웃한 것과 구분되지 않는다.
  const endedBy = endReasonFrom(state)

  const { address, chainId } = useAccount()
  const { connectAsync, connectors, isPending } = useConnect()
  const { switchChainAsync } = useSwitchChain()
  const { signMessageAsync } = useSignMessage()

  /** 연결된 주소로 서명을 받아 세션을 연다. 연결 직후와 「다시 시도」가 같이 쓴다. */
  const signInWith = useCallback(
    async (walletAddress: string) => {
      setPhase('signing')
      setError(null)
      setErrorCode(null)
      try {
        // nonce 는 서버가 발급한다. 클라이언트가 만들면 서명 재사용을 막을 수 없다.
        const challenge = await requestWalletMessage(walletAddress)
        setMessage(challenge.message)

        const signature = await signMessageAsync({ message: challenge.message })
        const profile = await signIn({ walletAddress, signature })

        // 그 사이 세션이 갈려 이 로그인이 버려졌다는 뜻이다 — 성공으로 읽고
        // 넘어가면 로그인된 척 홈으로 들어간다.
        if (!profile) {
          setPhase('idle')
          setError('로그인이 완료되지 않았습니다. 다시 시도해 주세요.')
          return
        }

        // 프로필을 아직 채우지 않은 지갑은 가입 화면부터. 막혀 왔으면 그 경로로,
        // 직접 들어왔으면 홈으로.
        // replace — 로그인 화면이 히스토리에 남으면 뒤로 가기가 다시 여기로 온다.
        navigate(profile.onboardingCompleted ? redirectTargetFrom(state) : paths.signup, {
          replace: true,
        })
      } catch (e) {
        // 사용자가 지갑에서 거절한 것만 거절 화면으로 보낸다. 서버 실패는 문구가
        // 맞지 않으므로 카드에 알린다.
        const { message, code } = describeWalletError(e)
        if (!(e instanceof ApiError) && code) {
          setPhase('rejected')
          setErrorCode(code)
          return
        }
        setPhase('idle')
        setError(e instanceof ApiError ? e.message : message)
      }
    },
    [signIn, signMessageAsync, navigate, state],
  )

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
      // 연결이 끝나면 곧바로 서명으로 간다. 중간에 확인 화면을 한 번 더 두면
      // 이미 승인한 연결을 다시 승인하라는 것처럼 보인다.
      await signInWith(connected)
    } catch (e) {
      setError(describeWalletError(e).message)
    }
  }

  // 커스텀 엘리먼트의 이벤트는 props 로 못 받는다 — ref 로 직접 듣는다.
  const dialogRef = useRef<HTMLElement>(null)
  useEffect(() => {
    const el = dialogRef.current
    if (!el) return
    const onSign = () => {
      if (wallet) void signInWith(wallet)
    }
    const onCancel = () => setPhase('idle')
    el.addEventListener('sign', onSign)
    el.addEventListener('cancel', onCancel)
    return () => {
      el.removeEventListener('sign', onSign)
      el.removeEventListener('cancel', onCancel)
    }
  }, [signInWith, wallet])

  // 훅을 모두 부른 뒤에 돌려보낸다. 헤더에서 잘못 눌러 들어온 사람이
  // 지갑 연결부터 다시 하게 두지 않는다. 온보딩이 남았으면 `OnboardingGate` 가
  // 홈에서 다시 가입 화면으로 붙잡는다.
  if (status === 'authenticated') return <Navigate to={redirectTargetFrom(state)} replace />

  return (
    <>
      {/* 시안 5 — 밀려왔을 때만. 문구를 안 주면 로그아웃한 것과 구분되지 않는다. */}
      {endedBy && (
        <div
          className="animate-rise absolute box-border flex w-[432px] items-center justify-center rounded-xl"
          style={{ left: 504, top: 150, padding: '14px 16px', background: 'rgba(233,174,196,.16)' }}
        >
          <div
            className="text-[12.5px] leading-[1.55] tracking-[-.01em]"
            style={{ color: '#ffb4c8' }}
          >
            {ENDED_BY[endedBy]}
          </div>
        </div>
      )}

      {/* 카드 없이 로고·제목·버튼만. 배경이 주인공인 화면이라 유리판을 얹지 않는다. */}
      {/* 로고를 누르면 홈으로. 화면 안 링크는 전부 <a href="/..."> 이고
          App 의 전역 클릭 인터셉터가 클라이언트 라우팅으로 넘긴다(4절). */}
      <a
        href={paths.home}
        aria-label="홈으로"
        className="animate-rise absolute transition-transform duration-200 hover:-translate-y-0.5"
        style={{ left: 688, top: 330, width: '64px', height: '64px' }}
      >
        <img src={logo} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
      </a>

      <div
        className="animate-rise absolute w-[432px] text-center text-[34px] font-bold tracking-[-.02em] [animation-delay:.06s]"
        style={{ left: 504, top: 420, textShadow: '0 2px 22px rgba(35,8,45,.6)' }}
      >
        Welcome ABC
      </div>
      <div
        className="text-muted animate-rise absolute w-[432px] text-center text-sm tracking-[-.01em] [animation-delay:.1s]"
        style={{ left: 504, top: 472 }}
      >
        AI Block Clock에 오신 것을 환영합니다
      </div>

      <button
        type="button"
        onClick={() => void handleConnect()}
        disabled={isPending}
        className="bg-accent text-accent-ink animate-rise rounded-card absolute flex h-14 cursor-pointer items-center justify-center gap-2.5 transition-transform duration-200 [animation-delay:.14s] hover:-translate-y-0.5 disabled:cursor-default disabled:opacity-60 disabled:hover:translate-y-0"
        style={{
          left: 504,
          top: 528,
          width: '432px',
          boxShadow: '0 10px 24px rgba(140,100,130,.24)',
        }}
      >
        <div className="bg-accent-ink/20 flex size-[22px] flex-none items-center justify-center rounded-md text-[11px] font-bold">
          M
        </div>
        <div className="text-base font-bold tracking-[-.01em]">
          {isPending ? '지갑 확인 중…' : 'MetaMask 지갑 연결'}
        </div>
      </button>

      {error && (
        <div
          className="absolute w-[432px] text-center text-[12.5px] leading-[1.5] tracking-[-.01em] text-[#ffb4c8]"
          style={{ left: 504, top: 600 }}
        >
          {error}
        </div>
      )}

      <wallet-dialog
        ref={dialogRef}
        phase={phase}
        address={wallet ? shortAddress(wallet) : ''}
        message-text={message ?? undefined}
        error-code={errorCode ?? undefined}
      />
    </>
  )
}
