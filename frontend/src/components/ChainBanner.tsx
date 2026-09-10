import { useAccount, useSwitchChain } from 'wagmi'

import { chain, chainLabel } from '@/lib/wagmi'

/** 지갑이 다른 네트워크에 있을 때 띄우는 알림. 연결할 때 한 번 전환을 권하지만
 *  그 뒤에 사용자가 지갑에서 바꿀 수 있다(wagmi 가 `chainChanged` 구독).
 *  검색·상세는 체인과 무관하게 볼 수 있어야 하므로 모달로 막지 않는다.
 */
export default function ChainBanner() {
  const { isConnected, chainId } = useAccount()
  const { switchChain, isPending } = useSwitchChain()

  // 지갑을 붙이지 않은 사람에게 네트워크 얘기는 소음이다.
  if (!isConnected || chainId === chain.id) return null

  return (
    // 가운데 정렬은 transform 이 아니라 flex 로 — 유리판에 transform 을 걸면
    // backdrop 루트가 되어 뒤 배경을 못 가져온다(10절).
    <div
      className="pointer-events-none absolute left-0 flex justify-center"
      style={{ top: '26px', width: '1440px', zIndex: 50 }}
    >
      <div
        className="pointer-events-auto flex items-center"
        style={{
          height: '44px',
          padding: '0 8px 0 20px',
          borderRadius: '22px',
          background: 'rgba(20,10,32,.9)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
          boxShadow: '0 16px 40px rgba(6,3,14,.45)',
          gap: '14px',
        }}
      >
        <div
          className="rounded-full"
          style={{ width: '7px', height: '7px', flex: 'none', background: '#f0b6c8' }}
        />
        <div className="text-[13.5px] font-semibold tracking-[-.01em] text-white">
          {`${chainLabel} 네트워크가 아닙니다`}
        </div>
        <button
          type="button"
          onClick={() => switchChain({ chainId: chain.id })}
          disabled={isPending}
          className="bg-accent text-accent-ink flex cursor-pointer items-center justify-center text-[12.5px] font-bold whitespace-nowrap transition-transform duration-[160ms] hover:-translate-y-[1px] disabled:cursor-default disabled:opacity-60 disabled:hover:translate-y-0"
          style={{ height: '30px', padding: '0 14px', borderRadius: '15px' }}
        >
          {isPending ? '전환 중…' : '네트워크 전환'}
        </button>
      </div>
    </div>
  )
}
