import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'

import { convertPayout, getCreditHistory } from '@/api/credits'
import {
  cancelWithdrawal,
  createWithdrawal,
  IN_PROGRESS_WITHDRAWAL,
  type WithdrawalStatus,
} from '@/api/withdrawals'
import { trimAbcAmount } from '@/lib/abcToken'
import { chargeAmountLabel } from '@/lib/format'
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

const isInProgress = (status: string) =>
  (IN_PROGRESS_WITHDRAWAL as readonly string[]).includes(status)

export function usePayoutWithdrawal() {
  const queryClient = useQueryClient()
  const withdrawalId = useRequestId()
  const conversionId = useRequestId()
  // 끝난 출금은 이 화면에서 건드린 것만 카드로 보여 주고, 확인을 누르면 접는다.
  const [touchedId, setTouchedId] = useState<string | null>(null)
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
  const withdrawal: WithdrawalView | null =
    item && item.id !== dismissedId && (isInProgress(item.status) || item.id === touchedId)
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

  const refreshCredits = () => queryClient.invalidateQueries({ queryKey: queryKeys.credits.all })

  const create = useMutation({
    mutationFn: (amount: string) =>
      createWithdrawal({ requestId: withdrawalId.take(`w:${amount}`), amount }),
    onSuccess: (result) => {
      withdrawalId.clear()
      setTouchedId(result.withdrawalId)
      void refreshCredits()
    },
    onError: (e) => setError(withdrawalErrorText(e, 'request')),
  })

  const cancel = useMutation({
    mutationFn: (id: string) => cancelWithdrawal(id),
    onSuccess: (result) => {
      setTouchedId(result.withdrawalId)
      void refreshCredits()
    },
    onError: (e) => setError(withdrawalErrorText(e, 'cancel')),
  })

  const convert = useMutation({
    mutationFn: (amount: string) =>
      convertPayout({ requestId: conversionId.take(`c:${amount}`), amount }),
    onSuccess: (result) => {
      conversionId.clear()
      queryClient.setQueryData(queryKeys.credits.balance(), result.balance)
      setNotice(`${chargeAmountLabel(result.convertedAmount)} 를 ABC 로 전환했습니다.`)
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
