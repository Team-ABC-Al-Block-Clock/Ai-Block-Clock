import type { AgentProvenance } from '@/api/agents'
import { mismatchReasonLabel } from '@/lib/format'

import { INK } from './ink'

/** 증명 카드의 대조 한 줄. */
export type ProofRow = [label: string, matched: boolean, value: string]

/** 아직 블록에 안 들어간 트랜잭션은 되돌려질 수 있다 — 검증됐다고 말하지 않는다. */
function proofBadgeOf(proof: AgentProvenance) {
  if (proof.blockchain.transactionStatus === 'PENDING')
    return { text: '블록 확정 대기 중', color: INK.label }
  if (!proof.verification.verified) return { text: '✗ 원본 검증 불일치', color: INK.bad }
  return { text: '✓ 원본 검증됨', color: 'var(--color-accent-bright)' }
}

/** 원본 증명을 화면에 옮길 값 — 이름 옆 배지와 불일치 사유, 증명 카드의 대조 줄. */
export function provenanceView(proof: AgentProvenance) {
  // 대조해 보라고 두는 값이라 주소는 줄이지 않는다.
  const onChainOwner = proof.verification.onChainOwnerAddress ?? ''
  const onChainHash = proof.verification.onChainMetadataHash
    ? `${proof.verification.onChainMetadataHash.slice(0, 18)}…`
    : ''
  const pending = proof.blockchain.transactionStatus === 'PENDING'
  const reason = proof.verification.mismatchReason ?? null
  // 체인에 없으면 대조할 값이 비어 있다. 빈칸 대신 이유를 적는다.
  const onChainMissing = reason === 'NOT_ON_CHAIN' ? '체인 기록 없음' : ''
  // 확정 전과 체인 기록 없음은 대조할 상대가 없다. 불일치로 적지 않는다.
  const noCompare = pending ? '대조 대기' : onChainMissing ? '대조 불가' : ''
  const rows: ProofRow[] = [
    ['원작자 주소', proof.verification.ownerMatched, onChainOwner || onChainMissing],
    ['메타데이터 해시', proof.verification.metadataMatched, onChainHash || onChainMissing],
    // 두 값은 맞는데 서버에 적어 둔 기록이 체인과 다르다. 어긋난 줄을 따로 보여 준다.
    // 이유는 배지 옆 줄이 이미 말하므로 값 칸은 비운다 — 대조할 단일 값도 없다.
    ...(reason === 'RECORD_MISMATCH' ? [['서버 기록', false, ''] as ProofRow] : []),
  ]
  return {
    badge: proofBadgeOf(proof),
    // 확정 대기는 배지가 이미 말한다.
    mismatchText: !proof.verification.verified && !pending ? mismatchReasonLabel(reason) : null,
    noCompare,
    rows,
  }
}
