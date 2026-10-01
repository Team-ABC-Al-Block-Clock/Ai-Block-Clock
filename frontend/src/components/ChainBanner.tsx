import { chainLabel, chainNameOf } from '@/lib/wallet/wagmi'
import {
  BANNER_HEIGHT,
  type ChainBannerState,
  type SwitchPhase,
} from '@/hooks/wallet/useChainBanner'
import StatusDot from '@/components/StatusDot'

const MESSAGE: Record<Exclude<SwitchPhase, 'idle'>, string> = {
  pending: '지갑에서 네트워크 전환을 승인해 주세요',
  rejected: `전환을 취소했습니다 — 실행하려면 ${chainLabel}가 필요합니다`,
  failed: '네트워크를 전환할 수 없었습니다. 지갑에서 직접 바꿔주세요',
}

/**
 * 네트워크가 다를 때 화면 맨 위에 뜨는 띠. 모달로 막지 않는다 —
 * 검색·상세는 체인과 무관하게 볼 수 있어야 한다.
 */
export default function ChainBanner({
  visible,
  phase,
  chainId,
  switchToChain,
  dismiss,
}: ChainBannerState) {
  if (!visible) return null

  const message =
    phase === 'idle'
      ? `${chainNameOf(chainId)}에 연결돼 있습니다 — 실행은 ${chainLabel}에서만 처리됩니다`
      : MESSAGE[phase]

  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-auto absolute inset-x-0 top-0 flex items-center justify-center"
      style={{
        height: `${BANNER_HEIGHT}px`,
        gap: '9px',
        zIndex: 60,
        // 위가 진하고 아래로 사라지는 띠 — 경계선을 긋지 않고 화면에 얹힌다
        background: 'linear-gradient(180deg, rgba(184,120,150,.42), rgba(184,120,150,.14))',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        animation: 'dropInDown .34s cubic-bezier(.2,.8,.25,1) both',
      }}
    >
      <StatusDot
        size={5}
        color="#ffd0e0"
        animation={phase === 'pending' ? 'pulseDot 1.4s ease-in-out infinite' : undefined}
      />

      <div className="truncate text-[12.5px] font-medium tracking-[-.01em] text-white">
        {message}
      </div>

      {phase !== 'pending' && (
        <button
          type="button"
          onClick={() => void switchToChain()}
          className="cursor-pointer text-[12.5px] font-bold tracking-[-.01em] text-white underline transition-colors duration-[160ms] hover:text-[#ffd7e6]"
          style={{ textUnderlineOffset: '3px' }}
        >
          {phase === 'idle' ? '전환' : '다시 시도'}
        </button>
      )}

      {/* 닫기는 전환이 실패했을 때만. 정상 경로에서 숨겨 버리면 실행 단계에서
          같은 문제를 다시 만나고, 그때는 이유를 모른다. */}
      {phase === 'failed' && (
        <button
          type="button"
          onClick={dismiss}
          aria-label="닫기"
          className="absolute cursor-pointer text-sm leading-none"
          style={{ right: '16px', top: '12px', color: 'rgba(255,255,255,.6)' }}
        >
          ×
        </button>
      )}
    </div>
  )
}
