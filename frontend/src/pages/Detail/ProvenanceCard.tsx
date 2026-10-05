import type { AgentProvenance } from '@/api/agents'
import { blockLabel, dateTimeLabel, shortAddress } from '@/lib/format'
import { chainNameOf } from '@/lib/wallet/wagmi'

import { INK } from './ink'
import type { ProofRow } from './provenanceView'

interface ProvenanceCardProps {
  proof: AgentProvenance | undefined
  rows: ProofRow[]
  /** 대조할 상대가 없을 때의 말 — '대조 대기' · '대조 불가'. 있으면 일치·불일치 대신 적는다. */
  noCompare: string
  isPending: boolean
  /** 아직 온체인에 기록되지 않았다(`BLOCKCHAIN_003`). */
  missing: boolean
  isError: boolean
  onRetry: () => void
}

/** 원본 증명 — 원작자 · 네트워크 · 트랜잭션과 체인 대조 결과. */
export default function ProvenanceCard({
  proof,
  rows,
  noCompare,
  isPending,
  missing,
  isError,
  onRetry,
}: ProvenanceCardProps) {
  return (
    <div
      className="box-border text-[16px] tracking-[-.01em]"
      style={{
        padding: '24px',
        borderRadius: '14px',
        background: 'var(--color-surface-inset)',
        animation: 'unfold .8s .46s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      {isPending && <div style={{ color: INK.faint }}>불러오는 중…</div>}

      {/* 재조회가 실패해도 직전 응답은 캐시에 남는다 — 안내와 증명을 같이 띄우지 않는다. */}
      {missing && !proof && (
        <>
          <div className="font-medium text-white">아직 온체인에 기록되지 않았습니다.</div>
          <div style={{ marginTop: '8px', color: INK.label }}>
            등록이 끝나면 원작자 주소와 트랜잭션이 여기에 표시됩니다.
          </div>
        </>
      )}

      {isError && !missing && !proof && (
        <div className="flex items-center" style={{ gap: '12px' }}>
          <span className="font-medium" style={{ color: INK.bad }}>
            원본 증명을 불러오지 못했습니다.
          </span>
          <button
            className="cursor-pointer underline"
            onClick={onRetry}
            style={{ background: 'none', border: 0, padding: 0, color: INK.label }}
            type="button"
          >
            다시 시도
          </button>
        </div>
      )}

      {proof && (
        <>
          <div>
            {(
              [
                [
                  '원작자',
                  proof.owner.nickname
                    ? `${proof.owner.nickname}  ${shortAddress(proof.owner.walletAddress)}`
                    : shortAddress(proof.owner.walletAddress),
                ],
                // 네트워크 이름은 손으로 적지 않는다 — chainId 로 찾는다.
                [
                  '네트워크',
                  `${chainNameOf(proof.blockchain.chainId)} · ${proof.blockchain.chainId}`,
                ],
                [
                  '트랜잭션',
                  proof.blockchain.transactionHash
                    ? shortAddress(proof.blockchain.transactionHash)
                    : '—',
                ],
                ['블록', blockLabel(proof.blockchain.blockNumber)],
                ['등록 시각', dateTimeLabel(proof.blockchain.registeredAt)],
              ] as const
            ).map(([label, value], i) => (
              <div className="flex" key={label} style={{ marginTop: i === 0 ? 0 : '12px' }}>
                <div className="flex-none" style={{ width: '104px', color: INK.label }}>
                  {label}
                </div>
                <div className="font-semibold text-white">{value}</div>
              </div>
            ))}
          </div>

          <div style={{ marginTop: '20px' }}>
            {rows.map(([label, matched, value], i) => (
              <div
                className="flex items-center"
                key={label}
                style={{ gap: '10px', marginTop: i === 0 ? 0 : '10px' }}
              >
                <span
                  className="font-semibold"
                  style={{
                    color: noCompare ? INK.label : matched ? 'var(--color-accent-bright)' : INK.bad,
                  }}
                >
                  {noCompare
                    ? `${label} ${noCompare}`
                    : matched
                      ? `✓ ${label} 일치`
                      : `✗ ${label} 불일치`}
                </span>
                <span className="text-[14px]" style={{ color: INK.faint }}>
                  {value}
                </span>
              </div>
            ))}
          </div>

          {proof.verification.verifiedAt && (
            <div className="text-[14px]" style={{ marginTop: '12px', color: INK.faint }}>
              마지막 검증 {dateTimeLabel(proof.verification.verifiedAt)}
            </div>
          )}

          {/* 외부 주소라 paths 를 거치지 않는다. SSAFY 체인은 탐색기가 없어 기록 조회 주소다. */}
          {proof.blockchain.explorerUrl && (
            <a
              className="text-accent-bright inline-block font-semibold"
              href={proof.blockchain.explorerUrl}
              rel="noreferrer"
              style={{ marginTop: '18px' }}
              target="_blank"
            >
              체인 기록 보기 →
            </a>
          )}

          <div className="text-[14px]" style={{ marginTop: '16px', color: INK.faint }}>
            노드 구성·연결 관계·프롬프트는 블록체인에 기록하지 않습니다. 소유권과 해시만 기록합니다.
          </div>
        </>
      )}
    </div>
  )
}
