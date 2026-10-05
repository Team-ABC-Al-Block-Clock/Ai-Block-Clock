import type { CreditBalance } from '@/api/credits'
import { WITHDRAWAL_LIMITS } from '@/api/withdrawals'
import { isSendableAmount, trimAbcAmount } from '@/lib/wallet/abcToken'
import { amountLabel, priceLabel, shortAddress } from '@/lib/format'

import type { Mode } from './config'

const { MIN_AMOUNT, MAX_AMOUNT, INTERVAL_HOURS } = WITHDRAWAL_LIMITS

/** 전환은 정산금 안이면 금액 · 주기 제한이 없다. */
function convertBlock(amount: string, payout: number) {
  if (!isSendableAmount(amount)) return '전환할 금액을 입력해 주세요.'
  if (Number(trimAbcAmount(amount)) > payout) return '정산금이 부족합니다.'
  return null
}

/** 출금은 정산금 안에서, 한 번에 1 ~ 1,000 ABC 다. 막혔으면 이유를 준다. */
function withdrawBlock(amount: string, payout: number) {
  if (!isSendableAmount(amount)) return '출금할 금액을 입력해 주세요.'
  const n = Number(trimAbcAmount(amount))
  if (n < MIN_AMOUNT || n > MAX_AMOUNT)
    return `한 번에 ${MIN_AMOUNT} ~ ${MAX_AMOUNT.toLocaleString()} ABC 까지 출금할 수 있습니다.`
  if (n > payout) return '정산금이 부족합니다.'
  return null
}

/** 탭마다 고를 수 있는 금액 칸. 적용 뒤 금액은 아래 요약이 보여 준다. 칸에는 금액만 둔다. */
function presetsOf(mode: Mode, payout: number, payoutText: string) {
  if (mode === 'charge')
    return ['500', '1000', '2500', '5000'].map((amount) => ({ amount, disabled: false }))
  // 전액은 서버 문자열을 그대로 보낸다 — 1e6 을 곱해 내리면 8.2 가 8.199999 가 된다.
  // 출금은 1,000 을 넘겨 보내면 PAYMENT_010 이다.
  const all = mode === 'withdraw' && payout > MAX_AMOUNT ? String(MAX_AMOUNT) : payoutText
  const tooSmall = (n: number) => (mode === 'withdraw' ? n < MIN_AMOUNT : n <= 0)
  return ['100', '200', '400', all].map((amount) => ({
    amount,
    disabled: Number(amount) > payout || tooSmall(Number(amount)),
  }))
}

interface CreditFormInput {
  mode: Mode
  /** 누른 칸의 순서 */
  pick: number
  /** 직접 입력한 값 */
  typed: string
  balance: CreditBalance | undefined
  balanceFailed: boolean
  wallet: string | null | undefined
  /** 지갑에 든 abcToken. 모르면 null */
  walletAmount: string | null
}

/** 고른 탭 · 칸 · 직접 입력으로 화면에 그릴 값과 보낼 금액을 정한다. */
export function creditFormView({
  mode,
  pick,
  typed,
  balance,
  balanceFailed,
  wallet,
  walletAmount,
}: CreditFormInput) {
  // 프리셋 계산에만 숫자를 쓴다. 표시는 서버 문자열 그대로다.
  const payout = Number(balance?.payoutAmount ?? 0)
  const available = Number(balance?.availableAmount ?? 0)
  const usesPayout = mode !== 'charge'

  const presets = presetsOf(mode, payout, trimAbcAmount(balance?.payoutAmount ?? '0'))
  // 고른 칸이 잔액 부족이면 고를 수 있는 마지막 칸으로 내린다. 없으면 그대로 둔다.
  const pickable = presets.findLastIndex((p) => !p.disabled)
  const at =
    presets[pick]?.disabled && pickable >= 0 ? pickable : Math.min(pick, presets.length - 1)

  // 직접 입력이 있으면 그 값이 이긴다. 프리셋은 비워 뒀을 때의 기본값이다.
  const amount = typed.trim() || presets[at].amount
  const picked = isSendableAmount(amount) ? Number(trimAbcAmount(amount)) : null
  // 정산금을 넘는 금액은 뺀 결과가 음수라 비워 둔다.
  const payoutAfter = picked !== null && picked <= payout ? priceLabel(payout - picked) : '—'
  const availableAfter = picked !== null ? priceLabel(available + picked) : '—'

  const reserved = balance?.reservedAmount
  const reservedNote =
    reserved && Number(reserved) > 0 ? `실행에 잠긴 금액 ${priceLabel(reserved)} · ` : ''

  return {
    presets,
    at,
    amount,
    usesPayout,
    // 불러오는 중에 0 을 띄우면 잔액이 비어 보였다가 튄다.
    bannerValue: balance
      ? priceLabel(usesPayout ? balance.payoutAmount : balance.availableAmount)
      : '—',
    bannerNote: balanceFailed
      ? '잔액을 불러오지 못했습니다'
      : mode === 'withdraw'
        ? '판매로 받은 정산금을 연결된 지갑에 abcToken 으로 보냅니다'
        : mode === 'convert'
          ? '정산금을 실행에 쓸 수 있는 ABC 로 바꿉니다'
          : reservedNote + '충전한 ABC 는 서비스 안에서만 쓰며 출금되지 않습니다',
    pickerNote:
      mode === 'withdraw'
        ? `한 번에 ${MIN_AMOUNT} ~ ${MAX_AMOUNT.toLocaleString()} ABC · ${INTERVAL_HOURS}시간에 한 번 신청할 수 있습니다`
        : mode === 'convert'
          ? '금액 · 횟수 제한 없음 · 전환한 ABC 는 정산금으로 되돌릴 수 없습니다'
          : `지갑의 abcToken ${walletAmount === null ? '—' : `${amountLabel(walletAmount)}개`} · ABC 는 양도할 수 없습니다`,
    // 요약은 실제로 보낼 값을 그대로 보여 준다 — 프리셋만 따르면 직접 입력한 금액이 화면에서 사라진다.
    sumCredit: picked !== null ? priceLabel(trimAbcAmount(amount)) : '—',
    details:
      mode === 'withdraw'
        ? [
            { label: '출금 후 정산금', value: payoutAfter },
            { label: '받을 지갑', value: wallet ? shortAddress(wallet) : '—' },
          ]
        : mode === 'convert'
          ? [
              { label: '전환 후 사용 가능', value: availableAfter },
              { label: '전환 후 정산금', value: payoutAfter },
            ]
          : [
              { label: '충전 후 사용 가능', value: availableAfter },
              // 가스비가 0 인 체인이다. 「≈」 를 붙이면 조금은 든다는 말이 된다.
              { label: '예상 가스비', value: '0 SSF' },
            ],
    blocked:
      mode === 'withdraw'
        ? withdrawBlock(amount, payout)
        : mode === 'convert'
          ? convertBlock(amount, payout)
          : null,
  }
}

export type CreditFormView = ReturnType<typeof creditFormView>
