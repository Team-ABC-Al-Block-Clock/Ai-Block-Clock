import type { ChargeFailureReason } from '@/api/credits'
import { ApiError, CHARGE_ERROR } from '@/api/errors'
import { chainLabel, describeWalletError } from '@/lib/wagmi'

/**
 * 충전이 어긋났을 때의 화면 문구.
 *
 * 어느 경우든 토큰은 이미 지갑에서 나간 뒤다 — 「다시 시도해 주세요」로 끝내면 두 번 보낸다.
 * 사유마다 다음에 할 일을 적는다.
 */

const FAILURE_TEXT: Record<ChargeFailureReason, string> = {
  ONCHAIN_TRANSACTION_FAILED: '거래가 체인에서 되돌려졌습니다. 토큰은 지갑에 그대로 있습니다.',
  VERIFICATION_TIMEOUT:
    '30분 안에 거래가 확인되지 않아 닫혔습니다. 지갑에서 거래가 처리됐다면 문의해 주세요.',
  SENDER_WALLET_MISMATCH: '로그인한 지갑에서 보낸 거래가 아닙니다. 보낸 지갑으로 로그인해 주세요.',
  CUSTODY_ADDRESS_MISMATCH: '입금 주소가 아닌 곳으로 보냈습니다. 문의해 주세요.',
  DEPOSIT_TOKEN_CONTRACT_MISMATCH: 'ABC 토큰이 아닌 자산을 보냈습니다. 문의해 주세요.',
  AMOUNT_MISMATCH: '요청한 금액과 실제로 보낸 금액이 다릅니다. 문의해 주세요.',
  CHAIN_ID_MISMATCH: `${chainLabel} 체인의 거래가 아닙니다. 네트워크를 바꾼 뒤 다시 보내 주세요.`,
}

/** 서버가 실패로 닫은 이유. 사유 없이 닫히는 경우는 없지만 와도 화면은 서야 한다. */
export function chargeFailureText(reason: ChargeFailureReason | null): string {
  if (reason === null) return '충전이 실패했습니다. 문의해 주세요.'
  return FAILURE_TEXT[reason] ?? '충전이 실패했습니다. 문의해 주세요.'
}

/**
 * 접수 요청이 거절됐을 때. 해시는 이미 손에 있으므로 「보내기」가 아니라 「접수」만 다시 한다.
 * 지갑이 거절한 것과 서버가 거절한 것은 다음 행동이 달라 갈라 둔다.
 */
export function chargeSubmitText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isNetworkError) return '서버에 닿지 못했습니다. 연결을 확인하고 다시 접수해 주세요.'
    switch (error.code) {
      case CHARGE_ERROR.TRANSACTION_ALREADY_USED:
        return '이미 다른 사용자가 이 거래로 충전을 요청했습니다. 문의해 주세요.'
      case CHARGE_ERROR.INVALID_AMOUNT:
        return '충전 금액은 0보다 크고 소수 6자리까지입니다.'
      case CHARGE_ERROR.SENDER_IS_CUSTODY:
        return '입금 보관 지갑으로는 충전할 수 없습니다.'
      case CHARGE_ERROR.FORBIDDEN:
      case CHARGE_ERROR.NOT_FOUND:
        return '충전 요청을 찾을 수 없습니다. 문의해 주세요.'
      default:
        return error.message
    }
  }
  return describeWalletError(error).message
}
