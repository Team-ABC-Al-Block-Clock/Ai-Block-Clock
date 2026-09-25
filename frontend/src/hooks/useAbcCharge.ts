import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useAccount, useSwitchChain, useWriteContract } from 'wagmi'

import { createCharge, getCharge, type ChargeStatus } from '@/api/credits'
import {
  ABC_TOKEN_ABI,
  ABC_TOKEN_ADDRESS,
  CUSTODY_ADDRESS,
  isSendableAmount,
  toTokenValue,
  trimAbcAmount,
  ZERO_GAS,
} from '@/lib/abcToken'
import { chargeSubmitText } from '@/lib/chargeMessage'
import { queryKeys } from '@/lib/queryKeys'
import { chain } from '@/lib/wagmi'
import { useAuthStore } from '@/stores/authStore'

/**
 * ABC 충전 한 건의 흐름.
 *
 * 지갑이 해시를 주는 즉시 서버에 넘긴다. 영수증을 기다리는 동안 새로고침하면 해시가
 * 어디에도 안 남아 토큰만 나간 상태가 되기 때문이다. 그래서 해시를 받은 순간
 * `localStorage` 에도 적고, 접수가 실패하면 보내기가 아니라 접수만 다시 한다.
 *
 * 서버는 10초마다 체인을 다시 보고 30분이 지나면 실패로 닫는다. 화면이 따로 시간을
 * 세지 않고 서버가 준 상태만 따른다 — 두 쪽이 각자 세면 어긋난다.
 */

/** 확인 중 · 완료 · 실패는 서버가 정한다. 나머지는 이 화면 안에서만 사는 단계다. */
export type ChargePhase =
  | 'idle'
  | 'signing'
  | 'submitting'
  /** 해시는 받았는데 접수가 실패했다 — 토큰은 이미 나갔다 */
  | 'unsubmitted'
  | 'confirming'
  | 'confirmed'
  | 'failed'

/** 서버가 답한 뒤로는 응답이 단계를 정한다 — 화면이 따로 세면 두 값이 어긋난다. */
const STATUS_PHASE: Record<ChargeStatus, ChargePhase> = {
  PENDING: 'confirming',
  CONFIRMED: 'confirmed',
  FAILED: 'failed',
}

/** 서버가 아직 아무 말도 하지 않은 구간. 지갑을 기다리는 동안이 여기다. */
type LocalPhase = 'idle' | 'signing' | 'submitting' | 'unsubmitted' | 'confirming'

/** 블록이 10초쯤에 하나씩 생긴다. 더 자주 물어도 새 답이 없다. */
const POLL_MS = 5000

/** 서버가 30분에 닫는다. 그래도 안 닫히면 화면이 먼저 멈춘다 — 배경에서 영원히 돌지 않게. */
const POLL_LIMIT_MS = 32 * 60 * 1000

const STORAGE_KEY = 'abc:pending-charge'

interface PendingCharge {
  transactionHash: string
  /** ABC 단위. 접수를 다시 할 때 그대로 쓴다 */
  amount: string
  /** 접수 전이면 null */
  chargeId: string | null
}

// localStorage 는 시크릿 창·차단 설정에서 접근 자체가 던진다.
function readPending(): PendingCharge | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as PendingCharge) : null
  } catch {
    return null
  }
}

function writePending(next: PendingCharge | null) {
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* 못 적어도 이번 세션 값은 살아 있다 */
  }
}

export function useAbcCharge() {
  const queryClient = useQueryClient()
  const { address, chainId } = useAccount()
  const { switchChainAsync } = useSwitchChain()
  const { writeContractAsync } = useWriteContract()
  const loginWallet = useAuthStore((s) => s.profile?.wallet.address ?? s.walletAddress)

  const [pending, setPending] = useState<PendingCharge | null>(readPending)
  const [localPhase, setLocalPhase] = useState<LocalPhase>(() => {
    const saved = readPending()
    if (!saved) return 'idle'
    return saved.chargeId ? 'confirming' : 'unsubmitted'
  })
  const [error, setError] = useState<string | null>(null)

  const chargeId = pending?.chargeId ?? null

  /** 해시는 손에 있고 접수만 남은 상태. 실패해도 해시를 잃지 않는다. */
  const submit = useMutation({
    mutationFn: (params: { transactionHash: string; amount: string }) => createCharge(params),
    onSuccess: (status, params) => {
      const next = { ...params, chargeId: status.chargeId }
      setPending(next)
      writePending(next)
    },
    onError: (e) => {
      setLocalPhase('unsubmitted')
      setError(chargeSubmitText(e))
    },
  })

  const charge = useQuery({
    queryKey: queryKeys.credits.charge(chargeId ?? ''),
    queryFn: () => getCharge(chargeId!),
    enabled: chargeId !== null,
    refetchInterval: (query) => {
      const data = query.state.data
      if (!data || data.status !== 'PENDING') return false
      // 서버가 닫지 못한 건을 배경에서 계속 물고 있지 않는다.
      if (Date.now() - Date.parse(data.requestedAt) > POLL_LIMIT_MS) return false
      return POLL_MS
    },
    // 탭을 옮겨도 계속 묻는다. 기본값은 배경에서 쉬는 것이라 「떠나도 된다」는 안내가 거짓말이 된다.
    refetchIntervalInBackground: true,
  })

  // 접수 응답과 조회 응답이 같은 모양이라, 먼저 온 쪽을 그대로 쓴다.
  const status = charge.data ?? submit.data ?? null
  const phase: ChargePhase = status ? STATUS_PHASE[status.status] : localPhase

  // 끝난 건은 저장소에서 지운다. 완료면 잔액·내역도 같이 움직여야 화면이 말이 된다.
  const settled = status && status.status !== 'PENDING' ? status.status : null
  useEffect(() => {
    if (!settled) return
    writePending(null)
    if (settled === 'CONFIRMED') {
      void queryClient.invalidateQueries({ queryKey: queryKeys.credits.all })
    }
  }, [settled, queryClient])

  // 창을 닫았다 와도 접수를 이어 한다 — 사용자가 누르지 않아도 한 번은 스스로 시도한다.
  const resumed = useRef(false)
  const resumable = phase === 'unsubmitted' && pending && !pending.chargeId ? pending : null
  useEffect(() => {
    if (resumed.current || !resumable) return
    resumed.current = true
    submit.mutate({ transactionHash: resumable.transactionHash, amount: resumable.amount })
  }, [resumable, submit])

  /**
   * 지갑으로 토큰을 보내고 해시를 접수한다.
   *
   * 보내기 전에 막을 수 있는 것은 전부 여기서 막는다 — 나간 뒤에 알면 되돌릴 수 없다.
   */
  const send = async (amount: string) => {
    setError(null)

    if (!isSendableAmount(amount)) {
      return setError('충전 금액은 0보다 크고 소수 6자리까지입니다.')
    }
    if (!address) {
      return setError('MetaMask 를 먼저 연결해 주세요.')
    }
    // 다른 지갑에서 보내면 서버가 SENDER_WALLET_MISMATCH 로 닫는다. 토큰은 이미 나간 뒤다.
    if (loginWallet && address.toLowerCase() !== loginWallet.toLowerCase()) {
      return setError('로그인한 지갑에서만 보낼 수 있습니다. MetaMask 에서 계정을 바꿔 주세요.')
    }

    const requested = trimAbcAmount(amount)
    try {
      setLocalPhase('signing')
      if (chainId !== chain.id) await switchChainAsync({ chainId: chain.id })

      const transactionHash = await writeContractAsync({
        abi: ABC_TOKEN_ABI,
        address: ABC_TOKEN_ADDRESS,
        functionName: 'transfer',
        args: [CUSTODY_ADDRESS, toTokenValue(requested)],
        ...ZERO_GAS,
      })

      // 접수보다 먼저 적는다. 여기서 창이 닫혀도 해시는 남는다.
      const next = { transactionHash, amount: requested, chargeId: null }
      setPending(next)
      writePending(next)
      setLocalPhase('submitting')
      resumed.current = true

      submit.mutate({ transactionHash, amount: requested })
    } catch (e) {
      setLocalPhase('idle')
      setError(chargeSubmitText(e))
    }
  }

  /** 완료·실패를 확인하고 입력 화면으로 돌아간다. 진행 중인 건을 버리는 버튼이 아니다. */
  const reset = () => {
    setPending(null)
    writePending(null)
    setLocalPhase('idle')
    setError(null)
    submit.reset()
    resumed.current = false
  }

  return {
    phase,
    error,
    /** 확인 중 · 완료 · 실패 화면이 그리는 값. 접수 전이면 없다 */
    status,
    transactionHash: pending?.transactionHash ?? null,
    requestedAmount: pending?.amount ?? null,
    /** 접수가 실패했을 때 다시 누르는 것 — 토큰을 또 보내지 않는다 */
    retrySubmit: () => {
      if (!pending || pending.chargeId) return
      setError(null)
      setLocalPhase('submitting')
      submit.mutate({ transactionHash: pending.transactionHash, amount: pending.amount })
    },
    send,
    reset,
  }
}

export type AbcCharge = ReturnType<typeof useAbcCharge>
