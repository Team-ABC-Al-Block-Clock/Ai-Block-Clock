import type { AgentDetail } from '@/api/agents'

import { INK } from './ink'

interface AgentHeaderProps {
  agent: AgentDetail
  /** 이름 옆 원본 증명 배지. 증명을 아직 못 받았으면 없다. */
  badge: { text: string; color: string } | null
  mismatchText: string | null
  favoritePending: boolean
  /** 즐겨찾기 실패. 토스트가 없어 버튼 아래에 한 줄로 둔다. */
  favoriteMessage: string | null
  onToggleFavorite: () => void
  /** 실행용 워크플로우를 저장하는 중 */
  runPending: boolean
  /** 실행 준비 실패. 즐겨찾기 실패와 같은 자리에 둔다. */
  runMessage: string | null
  onRun: () => void
}

/** 아이콘 · 이름 · 한 줄 설명 · 만든 사람과 즐겨찾기 · 실행하기. */
export default function AgentHeader({
  agent,
  badge,
  mismatchText,
  favoritePending,
  favoriteMessage,
  onToggleFavorite,
  runPending,
  runMessage,
  onRun,
}: AgentHeaderProps) {
  const favBg = agent.favorited ? 'rgba(255,179,205,.26)' : 'rgba(255,255,255,.11)'
  const favStarColor = agent.favorited ? '#e3c8a3' : 'rgba(255,255,255,.5)'

  return (
    <div className="flex items-start" style={{ marginTop: '14px', gap: '32px' }}>
      <div
        className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.10)]"
        style={{
          width: '128px',
          height: '128px',
          borderRadius: '18px',
          animation: 'unfold .7s .04s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div
          className="absolute inset-0"
          style={{
            background:
              'radial-gradient(circle at 52% 28%, rgba(255,218,224,.16), rgba(255,214,232,0) 66%)',
          }}
        ></div>
        {agent.iconUrl ? (
          <img className="relative h-full w-full object-cover" src={agent.iconUrl} alt="" />
        ) : (
          <div
            className="absolute inset-0 flex items-center justify-center text-[48px] font-bold"
            style={{ color: 'rgba(255,226,238,.85)' }}
          >
            {agent.name.trim().charAt(0).toUpperCase()}
          </div>
        )}
      </div>

      <div
        className="flex min-w-0 flex-1 flex-col"
        style={{ gap: '10px', animation: 'rise .7s .08s cubic-bezier(.2,.8,.25,1) both' }}
      >
        <div className="flex flex-wrap items-center" style={{ gap: '12px' }}>
          <div
            className="text-[36px] leading-[1.2] font-bold tracking-[-.02em] text-white"
            style={{ textShadow: '0 2px 20px rgba(35,8,45,.6)' }}
          >
            {agent.name}
          </div>
          {badge && (
            <div
              className="flex flex-none items-center text-[14.5px] font-bold tracking-[-.01em]"
              style={{
                height: '30px',
                padding: '0 14px',
                borderRadius: '15px',
                background: 'rgba(255,255,255,.1)',
                color: badge.color,
              }}
            >
              {badge.text}
            </div>
          )}
          {mismatchText && (
            <div className="flex-none text-[14.5px] font-medium" style={{ color: INK.bad }}>
              {mismatchText}
            </div>
          )}
        </div>
        <div
          className="text-[18px] font-medium tracking-[-.01em]"
          style={{ lineHeight: '1.5', color: INK.body }}
        >
          {agent.summary}
        </div>
        <div className="flex items-center" style={{ marginTop: '4px', gap: '12px' }}>
          <div
            className="flex-none overflow-hidden rounded-full"
            style={{ width: '40px', height: '40px', background: 'rgba(255,255,255,.14)' }}
          >
            {agent.owner.profileImageUrl && (
              <img
                className="h-full w-full object-cover"
                src={agent.owner.profileImageUrl}
                alt=""
              />
            )}
          </div>
          <div className="flex flex-col" style={{ gap: '2px' }}>
            <div className="text-[16px] font-bold tracking-[-.01em] text-white">
              {agent.owner.nickname ?? '—'}
            </div>
            <div className="text-[14px] font-medium" style={{ color: INK.faint }}>
              만든 사람
            </div>
          </div>
        </div>
      </div>

      <div
        className="flex flex-none flex-col items-end"
        style={{
          marginTop: '30px',
          gap: '8px',
          animation: 'rise .7s .2s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="flex" style={{ gap: '12px' }}>
          <div
            onClick={onToggleFavorite}
            style={{
              width: '136px',
              height: '54px',
              borderRadius: '27px',
              background: `${favBg}`,
              backdropFilter: 'blur(18px)',
              WebkitBackdropFilter: 'blur(18px)',
              gap: '8px',
              opacity: favoritePending ? 0.6 : 1,
              transition: 'background .18s,opacity .18s',
            }}
            className="flex cursor-pointer items-center justify-center hover:bg-[rgba(255,255,255,.2)]"
          >
            <div className="text-[17px]" style={{ color: `${favStarColor}` }}>
              ★
            </div>
            <div className="text-[17px] font-bold tracking-[-.01em] text-white">즐겨찾기</div>
          </div>
          <button
            type="button"
            onClick={onRun}
            disabled={runPending}
            style={{
              width: '164px',
              height: '54px',
              borderRadius: '27px',
              background: 'var(--color-accent)',
              boxShadow: '0 10px 26px rgba(140,100,130,.24)',
              color: 'var(--color-accent-ink)',
              transition: 'transform .18s,opacity .18s',
            }}
            className="flex cursor-pointer items-center justify-center border-0 font-[inherit] text-[18px] font-bold tracking-[-.01em] hover:-translate-y-[2px] disabled:cursor-default disabled:opacity-60"
          >
            {runPending ? '준비 중…' : '실행하기'}
          </button>
        </div>
        {(favoriteMessage ?? runMessage) && (
          <div
            className="text-[14px] font-medium tracking-[-.01em]"
            style={{ color: INK.bad, animation: 'fadeIn .2s ease both' }}
          >
            {favoriteMessage ?? runMessage}
          </div>
        )}
      </div>
    </div>
  )
}
