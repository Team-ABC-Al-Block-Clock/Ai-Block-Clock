import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'

import { convertPayout, getCreditHistory } from '@/api/credits'
import {
  cancelWithdrawal,
  createWithdrawal,
  isWithdrawalInProgress as isInProgress,
  type WithdrawalStatus,
} from '@/api/withdrawals'
import { trimAbcAmount } from '@/lib/abcToken'
import { priceLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { withdrawalErrorText } from '@/lib/withdrawalMessage'

/** 같은 금액을 다시 누르면 같은 requestId 로 보낸다. 성공하면 버린다. */
function useRequestId() {
  const last = useRef<{ key: string; id: string } | null>(null)
  return {
    take: (key: string) => {
      if (last.current?.key !== key) last.current = { key, id: crypto.randomUUID() }
      return last.current.id
    },
    clear: () => {
      last.current = null
    },
  }
}

export interface WithdrawalView {
  id: string
  status: WithdrawalStatus
  amount: string
  walletAddress: string
  transactionHash: string | null
  rejectReason: string | null
}

const LATEST = { type: 'WITHDRAWAL', size: 1 } as const
/** 서버가 10 ~ 30초 간격이면 충분하다고 했다. */
const POLL_MS = 15_000

export function usePayoutWithdrawal() {
  const queryClient = useQueryClient()
  const withdrawalId = useRequestId()
  const conversionId = useRequestId()
  // 끝난 출금은 이 화면에서 건드렸거나 진행 중으로 지켜본 것만 카드로 보여 주고, 확인을 누르면 접는다.
  const [touchedId, setTouchedId] = useState<string | null>(null)
  const [watchedId, setWatchedId] = useState<string | null>(null)
  const [dismissedId, setDismissedId] = useState<string | null>(null)

  const latest = useQuery({
    queryKey: queryKeys.credits.history.list(LATEST),
    queryFn: () => getCreditHistory(LATEST),
    select: (page) => page.content[0] ?? null,
    refetchInterval: (query) => {
      const item = query.state.data?.content[0]
      return item && isInProgress(item.status) ? POLL_MS : false
    },
  })
  const item = latest.data
  // 렌더 중에 맞춘다 — effect 에서 set 하면 한 번 더 그린다.
  if (item && isInProgress(item.status) && watchedId !== item.id) setWatchedId(item.id)
  const withdrawal: WithdrawalView | null =
    item &&
    item.id !== dismissedId &&
    (isInProgress(item.status) || item.id === touchedId || item.id === watchedId)
      ? {
          id: item.id,
          status: item.status as WithdrawalStatus,
          amount: item.amount,
          walletAddress: item.walletAddress,
          transactionHash: item.transactionHash,
          rejectReason: item.rejectReason,
        }
      : null
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  // 출금은 정산금에서 나가 ABC 사용 내역 · 사용량에는 남지 않는다. 잔액과 충전 · 출금 내역만 다시 읽는다.
  const refreshCredits = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.credits.balance() }),
      queryClient.invalidateQueries({ queryKey: queryKeys.credits.history.lists() }),
    ])

  // 반려 · 실패면 묶였던 금액이 정산금으로 돌아온다. 끝나는 순간 잔액을 다시 읽는다.
  const lastSeen = useRef<{ id: string; status: string } | null>(null)
  useEffect(() => {
    if (!item) return
    const prev = lastSeen.current
    lastSeen.current = { id: item.id, status: item.status }
    if (!isInProgress(item.status) && prev?.id === item.id && isInProgress(prev.status)) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.credits.balance() })
    }
  }, [item, queryClient])

  // 실패는 대개 상태가 그새 바뀐 것이다(PAYMENT_012 · 016). 카드가 옛 상태로 남지 않게 다시 묻는다.
  const failWith = (action: 'request' | 'cancel') => (e: unknown) => {
    setError(withdrawalErrorText(e, action))
    void queryClient.invalidateQueries({ queryKey: queryKeys.credits.history.lists() })
  }

  const create = useMutation({
    mutationFn: (amount: string) =>
      createWithdrawal({ requestId: withdrawalId.take(`w:${amount}`), amount }),
    onSuccess: (result) => {
      withdrawalId.clear()
      setTouchedId(result.withdrawalId)
      void refreshCredits()
    },
    onError: failWith('request'),
  })

  const cancel = useMutation({
    mutationFn: (id: string) => cancelWithdrawal(id),
    onSuccess: (result) => {
      setTouchedId(result.withdrawalId)
      void refreshCredits()
    },
    onError: failWith('cancel'),
  })

  const convert = useMutation({
    mutationFn: (amount: string) =>
      convertPayout({ requestId: conversionId.take(`c:${amount}`), amount }),
    onSuccess: (result) => {
      conversionId.clear()
      queryClient.setQueryData(queryKeys.credits.balance(), result.balance)
      setNotice(`${priceLabel(result.convertedAmount)} 를 ABC 로 전환했습니다.`)
      void queryClient.invalidateQueries({ queryKey: queryKeys.credits.transactions.lists() })
    },
    onError: (e) => setError(withdrawalErrorText(e, 'convert')),
  })

  const start = (run: () => void) => {
    setError(null)
    setNotice(null)
    run()
  }

  return {
    withdrawal,
    error,
    notice,
    busy: create.isPending || cancel.isPending || convert.isPending,
    request: (amount: string) => start(() => create.mutate(trimAbcAmount(amount))),
    convert: (amount: string) => start(() => convert.mutate(trimAbcAmount(amount))),
    cancel: () => withdrawal && start(() => cancel.mutate(withdrawal.id)),
    dismiss: () => {
      if (withdrawal) setDismissedId(withdrawal.id)
      setError(null)
    },
    clearMessages: () => {
      setError(null)
      setNotice(null)
    },
  }
}
