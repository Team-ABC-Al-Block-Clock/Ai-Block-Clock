import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'

import { convertPayout } from '@/api/credits'
import { cancelWithdrawal, createWithdrawal, type Withdrawal } from '@/api/withdrawals'
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

export function usePayoutWithdrawal() {
  const queryClient = useQueryClient()
  const withdrawalId = useRequestId()
  const conversionId = useRequestId()
  const [withdrawal, setWithdrawal] = useState<Withdrawal | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const refreshCredits = () => queryClient.invalidateQueries({ queryKey: queryKeys.credits.all })

  const create = useMutation({
    mutationFn: (amount: string) =>
      createWithdrawal({ requestId: withdrawalId.take(`w:${amount}`), amount }),
    onSuccess: (result) => {
      withdrawalId.clear()
      setWithdrawal(result)
      void refreshCredits()
    },
    onError: (e) => setError(withdrawalErrorText(e)),
  })

  const cancel = useMutation({
    mutationFn: (id: string) => cancelWithdrawal(id),
    onSuccess: (result) => {
      setWithdrawal(result)
      void refreshCredits()
    },
    onError: (e) => setError(withdrawalErrorText(e)),
  })

  const convert = useMutation({
    mutationFn: (amount: string) =>
      convertPayout({ requestId: conversionId.take(`c:${amount}`), amount }),
    onSuccess: (result) => {
      conversionId.clear()
      queryClient.setQueryData(queryKeys.credits.balance(), result.balance)
      setNotice(`${chargeAmountLabel(result.convertedAmount)} 를 쓸 수 있는 ABC 로 옮겼습니다.`)
      void queryClient.invalidateQueries({ queryKey: queryKeys.credits.transactions.lists() })
    },
    onError: (e) => setError(withdrawalErrorText(e)),
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
    cancel: () => withdrawal && start(() => cancel.mutate(withdrawal.withdrawalId)),
    dismiss: () => {
      setWithdrawal(null)
      setError(null)
    },
    clearMessages: () => {
      setError(null)
      setNotice(null)
    },
  }
}
