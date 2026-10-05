import { describe, expect, it } from 'vitest'

import type { AgentProvenance } from '@/api/agents'
import { mismatchReasonLabel } from '@/lib/format'

import { provenanceView } from './provenanceView'

const ONCHAIN_HASH = '0x1234567890abcdef1234567890abcdef'

const verified: AgentProvenance = {
  agentId: 'agent-1',
  owner: { nickname: '원작자', walletAddress: '0xowner' },
  blockchain: {
    network: 'ssafy',
    chainId: 31221,
    contractAddress: '0xcontract',
    transactionHash: '0xtx',
    transactionStatus: 'CONFIRMED',
    blockNumber: 10,
    registeredAt: '2026-09-27T00:00:00Z',
    explorerUrl: null,
  },
  verification: {
    offChainOwnerAddress: '0xowner',
    onChainOwnerAddress: '0xowner',
    offChainMetadataHash: ONCHAIN_HASH,
    onChainMetadataHash: ONCHAIN_HASH,
    ownerMatched: true,
    metadataMatched: true,
    verified: true,
    mismatchReason: null,
    verifiedAt: '2026-09-27T00:00:00Z',
  },
}

const withVerification = (v: Partial<AgentProvenance['verification']>): AgentProvenance => ({
  ...verified,
  verification: { ...verified.verification, ...v },
})

describe('provenanceView', () => {
  it('검증된 원본은 두 줄 모두 일치이고 해시는 앞부분만 보인다', () => {
    const view = provenanceView(verified)
    expect(view.badge.text).toBe('✓ 원본 검증됨')
    expect(view.mismatchText).toBeNull()
    expect(view.noCompare).toBe('')
    expect(view.rows).toEqual([
      ['원작자 주소', true, '0xowner'],
      ['메타데이터 해시', true, `${ONCHAIN_HASH.slice(0, 18)}…`],
    ])
  })

  it('블록 확정 전에는 검증됐다고 말하지 않고, 대조를 미루며, 불일치 사유도 적지 않는다', () => {
    const view = provenanceView({
      ...withVerification({ verified: false, mismatchReason: 'NOT_CONFIRMED' }),
      blockchain: { ...verified.blockchain, transactionStatus: 'PENDING' },
    })
    expect(view.badge.text).toBe('블록 확정 대기 중')
    expect(view.noCompare).toBe('대조 대기')
    expect(view.mismatchText).toBeNull()
  })

  it('체인에 기록이 없으면 불일치가 아니라 대조 불가로 적고 빈칸 대신 이유를 둔다', () => {
    const view = provenanceView(
      withVerification({
        verified: false,
        ownerMatched: false,
        metadataMatched: false,
        onChainOwnerAddress: null,
        onChainMetadataHash: null,
        mismatchReason: 'NOT_ON_CHAIN',
      }),
    )
    expect(view.badge.text).toBe('✗ 원본 검증 불일치')
    expect(view.noCompare).toBe('대조 불가')
    expect(view.rows.map(([, , value]) => value)).toEqual(['체인 기록 없음', '체인 기록 없음'])
    expect(view.mismatchText).toBe(mismatchReasonLabel('NOT_ON_CHAIN'))
  })

  it('서버 기록만 어긋나면 그 줄을 따로 하나 더 보여 준다', () => {
    const view = provenanceView(
      withVerification({ verified: false, mismatchReason: 'RECORD_MISMATCH' }),
    )
    expect(view.rows).toHaveLength(3)
    expect(view.rows[2]).toEqual(['서버 기록', false, ''])
    expect(view.noCompare).toBe('')
  })
})
