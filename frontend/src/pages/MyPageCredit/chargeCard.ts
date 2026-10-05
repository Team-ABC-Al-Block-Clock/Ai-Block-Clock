import type { AbcCharge } from '@/hooks/wallet/useAbcCharge'
import { priceLabel } from '@/lib/format'
import { chargeFailureText } from '@/lib/wallet/chargeMessage'

/**
 * 충전 단계별 화면 문구와 버튼.
 *
 * 토큰이 나간 뒤로는 「다시 시도」가 두 번 보내기가 된다 — 접수만 다시 하는 것과
 * 확인하고 나가는 것을 갈라 둔다.
 */
export function chargeCard(
  charge: Pick<
    AbcCharge,
    'phase' | 'error' | 'stalled' | 'status' | 'requestedAmount' | 'retrySubmit' | 'recheck'
  >,
  finish: () => void,
) {
  switch (charge.phase) {
    case 'signing':
      return { title: '지갑 승인 대기', note: 'MetaMask 에서 전송을 승인해 주세요.', action: null }
    case 'submitting':
      return { title: '접수 중', note: '거래 해시를 서버에 넘기고 있습니다.', action: null }
    case 'unsubmitted':
      return {
        title: '접수하지 못했습니다',
        note: charge.error ?? '토큰은 이미 보냈습니다. 접수만 다시 하면 됩니다.',
        action: { label: '다시 접수', run: charge.retrySubmit },
      }
    case 'confirming':
      // 더 묻지 않게 된 건은 화면이 스스로 못 푼다. 해시를 버리는 reset 이 아니라 다시 묻게 한다.
      return {
        title: '확인 중',
        note: charge.stalled
          ? '상태를 확인하지 못했습니다. 거래는 체인에 남아 있습니다.'
          : '거래가 블록에 적히면 잔액에 반영됩니다. 이 화면을 떠나도 됩니다.',
        action: charge.stalled ? { label: '다시 확인', run: charge.recheck } : null,
      }
    case 'confirmed':
      return {
        title: '충전 완료',
        note: `${priceLabel(charge.status?.creditedAmount ?? charge.requestedAmount ?? '0')} 를 적립했습니다.`,
        action: { label: '확인', run: finish },
      }
    case 'failed':
      return {
        title: '충전 실패',
        note: chargeFailureText(charge.status?.failureReason ?? null),
        action: { label: '확인', run: finish },
      }
    default:
      return { title: '', note: '', action: null }
  }
}
