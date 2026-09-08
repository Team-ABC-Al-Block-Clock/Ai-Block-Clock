import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { ApiError } from '@/api/errors'
import { shortAddress } from '@/lib/format'
import { redirectTargetFrom } from '@/routes/access'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'

/** 연결한 지갑 대신 쓰는 값. 181(wagmi 도입)·182(서명 요청)에서 실제 계정의
 *  주소와 서명으로 바뀝니다 — 지금은 보호 라우트 복귀를 확인할 만큼만 태웁니다. */
const PLACEHOLDER_WALLET = '0x1f9c4b2e7a0d5c83916fe2b47c0a91d3e5b8072a'
const PLACEHOLDER_SIGNATURE = `0x${'ab'.repeat(65)}`

/** 지갑 연결로 시작하는 로그인 화면.
 *
 *  좌표는 1440×1024 설계 그대로 인라인 style 에 둡니다 — 좌표마다 값이 달라서
 *  Tailwind 임의값(left-[504px])으로 옮겨봐야 길어지기만 합니다.
 *  반복되는 색·유리판·hover 만 Tailwind 클래스로 가져갑니다.
 */
export default function Login() {
  const [walletOpen, setWalletOpen] = useState(false)
  const [isSigningIn, setIsSigningIn] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const navigate = useNavigate()
  const { state } = useLocation()
  const signIn = useAuthStore((s) => s.signIn)

  const handleStart = async () => {
    setIsSigningIn(true)
    setError(null)
    try {
      await signIn({ walletAddress: PLACEHOLDER_WALLET, signature: PLACEHOLDER_SIGNATURE })
      // 보호 라우트에 막혀 왔으면 그 경로로, 직접 들어왔으면 홈으로.
      // replace — 로그인 화면을 히스토리에 남기면 뒤로 가기가 다시 여기로 온다.
      navigate(redirectTargetFrom(state), { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '지갑 연결에 실패했습니다. 다시 시도해 주세요.')
    } finally {
      setIsSigningIn(false)
    }
  }

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(20,9,34,.3) 0%, rgba(20,9,34,.12) 34%, rgba(20,9,34,.28) 100%)',
        }}
      />

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
          연결한 주소로 코인 정산과 사용 내역이 관리됩니다.
        </div>

        <div className="mt-[22px] flex flex-col gap-[9px]">
          {['Polygon Mainnet 지원', '서명만 요청하며 자산 이동 권한은 요구하지 않습니다'].map(
            (t) => (
              <div key={t} className="flex items-center gap-[9px]">
                <div className="bg-lilac size-1.5 flex-none rounded-full" />
                <div className="text-body text-[12.5px] tracking-[-.01em]">{t}</div>
              </div>
            ),
          )}
        </div>

        <div className="mt-[26px] h-px bg-white/12" />

        <button
          type="button"
          onClick={() => setWalletOpen(true)}
          className="bg-accent text-accent-ink mt-6 flex h-14 w-full cursor-pointer items-center justify-center gap-2.5 rounded-[14px] transition-transform duration-200 hover:-translate-y-0.5"
          style={{ boxShadow: '0 10px 24px rgba(140,100,130,.24)' }}
        >
          <div className="bg-accent-ink/20 flex size-[22px] flex-none items-center justify-center rounded-md text-[11px] font-bold">
            M
          </div>
          <div className="text-base font-bold tracking-[-.01em]">MetaMask 지갑 연결</div>
        </button>

        <div className="text-muted mt-6 text-center text-[12.5px] tracking-[-.01em]">
          처음이신가요? 지갑을 연결하면{' '}
          <a href={paths.signup} className="font-semibold text-[#e9b7d2]">
            프로필 등록
          </a>
          으로 이어집니다
        </div>
      </div>

      {walletOpen && (
        <>
          <div
            onClick={() => setWalletOpen(false)}
            className="animate-fade absolute inset-0 z-20 bg-[rgb(12_5_22/0.5)] backdrop-blur-[6px] [animation-duration:.2s]"
          />
          <div
            className="animate-rise absolute z-21 box-border w-[400px] rounded-[20px] bg-[rgb(22_12_38/0.96)] p-7 text-center backdrop-blur-[26px] [animation-duration:.3s]"
            style={{ left: 520, top: 392, boxShadow: '0 20px 52px rgba(4,2,12,.55)' }}
          >
            <div className="mx-auto flex size-13 items-center justify-center rounded-[15px] bg-[rgb(233_174_196/0.2)] text-[19px] font-bold text-[#ffd7e6]">
              M
            </div>
            <div className="mt-4 text-[17px] font-semibold tracking-[-.01em]">지갑 연결 요청</div>
            <div className="text-muted mt-2 text-[13px] leading-relaxed tracking-[-.01em]">
              MetaMask 확장에서 연결을 승인해 주세요.
              <br />
              승인하면 지갑 주소로 로그인됩니다.
            </div>
            <div className="bg-surface-inset mt-[18px] flex h-11 items-center justify-center rounded-[11px] text-[13.5px] font-semibold tracking-[.01em]">
              {shortAddress(PLACEHOLDER_WALLET)}
            </div>
            <div className="mt-5 flex gap-2.5">
              <button
                type="button"
                onClick={() => setWalletOpen(false)}
                className="flex h-[46px] flex-1 cursor-pointer items-center justify-center rounded-xl bg-white/8 text-sm font-semibold transition-colors hover:bg-white/16"
              >
                취소
              </button>
              {/* 링크가 아니라 버튼입니다 — 서명 결과를 받아야 이동할 곳이 정해집니다 */}
              <button
                type="button"
                onClick={handleStart}
                disabled={isSigningIn}
                className="bg-accent text-accent-ink flex h-[46px] flex-1 cursor-pointer items-center justify-center rounded-xl text-sm font-bold transition-transform hover:-translate-y-px disabled:cursor-default disabled:opacity-60 disabled:hover:translate-y-0"
              >
                {isSigningIn ? '연결 중…' : '연결하고 시작'}
              </button>
            </div>

            {error && (
              <div className="mt-3 text-[12.5px] leading-[1.5] tracking-[-.01em] text-[#ffb4c8]">
                {error}
              </div>
            )}
          </div>
        </>
      )}
    </>
  )
}
