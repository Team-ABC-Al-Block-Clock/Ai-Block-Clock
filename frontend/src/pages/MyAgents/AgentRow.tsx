import { sparkPath } from '@/lib/stats/spark'
import { paths } from '@/routes/paths'

import type { Row } from './config'

const ROW =
  'bg-surface-card box-border flex flex-none items-center transition-colors duration-200 hover:bg-[rgba(255,255,255,.14)]'

/** 지표 문자열을 밖에서 받는다 — 안에서 탭으로 가르면 같은 숫자에 다른 이름이 붙는다. */
export default function AgentRow({
  row,
  delay,
  onToggleFav,
}: {
  row: Row
  delay: number
  onToggleFav: () => void
}) {
  const gradId = `spk-${row.agentId}`
  const path = sparkPath(row.trend)

  return (
    <div
      className={`${ROW} rowCard`}
      style={{
        width: '896px',
        height: '112px',
        padding: '0 24px 0 26px',
        borderRadius: '18px',
        gap: '18px',
        // 새로 불러온 장 안에서만 시차를 준다 — 누적 index 를 쓰면 전부 같은 지연이 된다.
        animation: `rise .6s ${(0.24 + delay * 0.05).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`,
      }}
    >
      {row.iconUrl ? (
        <img
          src={row.iconUrl}
          alt=""
          className="flex-none rounded-full object-cover"
          style={{ width: '50px', height: '50px', background: 'rgba(255,220,235,.10)' }}
        />
      ) : (
        <div
          className="flex flex-none items-center justify-center rounded-full text-[16px] font-semibold"
          style={{
            width: '50px',
            height: '50px',
            background: 'rgba(255,220,235,.10)',
            color: 'rgba(255,226,238,.8)',
          }}
        >
          {row.name.trim().charAt(0).toUpperCase()}
        </div>
      )}

      <div className="flex-1" style={{ minWidth: '0' }}>
        <div className="flex items-center" style={{ gap: '10px' }}>
          <div
            className="overflow-hidden text-[16px] font-semibold tracking-[-.01em] whitespace-nowrap text-white"
            style={{ maxWidth: '270px', textOverflow: 'ellipsis' }}
          >
            {row.name}
          </div>
          {row.mine && (
            <div
              className="flex flex-none items-center text-[12px] font-bold text-white"
              style={{
                height: '18px',
                padding: '0 7px',
                borderRadius: '5px',
                background: '#7052e6',
                letterSpacing: '.04em',
              }}
            >
              MY
            </div>
          )}
          <div
            className="flex-none cursor-pointer text-[13px]"
            onClick={onToggleFav}
            role="button"
            aria-pressed={row.favorited}
            aria-label="즐겨찾기"
            style={{ color: row.favorited ? '#e3c8a3' : 'rgba(255,255,255,.34)' }}
          >
            ★
          </div>
        </div>
        <div
          className="overflow-hidden text-[13.5px] tracking-[-.01em] whitespace-nowrap"
          style={{
            marginTop: '7px',
            maxWidth: '340px',
            textOverflow: 'ellipsis',
            color: 'rgba(255,236,246,.6)',
          }}
        >
          {row.desc}
        </div>
        <div
          className="flex text-[13px]"
          style={{
            marginTop: '8px',
            gap: '18px',
            color: 'rgba(255,240,247,.55)',
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          <div>{row.metrics[0]}</div>
          <div>{row.metrics[1]}</div>
        </div>
      </div>

      {/* 그래프와 그 아래 보조 줄. 보조 줄이 88px 보다 길면 왼쪽으로 넘치는데, 그쪽은
          비어 있어 겹칠 것이 없다 — 오른쪽 끝(그래프 끝)에 맞추는 것이 중요하다. */}
      <div
        className="spark flex flex-none flex-col items-end"
        style={{ width: '88px', gap: '4px' }}
      >
        <svg width="90" height="28" viewBox="0 0 90 28" fill="none" style={{ overflow: 'visible' }}>
          <defs>
            <linearGradient id={gradId} x1="0" y1="1" x2="1" y2="0">
              <stop offset="0%" stopColor="#dba18b" />
              <stop offset="100%" stopColor="#e3b7c8" />
            </linearGradient>
          </defs>
          {/* trend 를 주지 않는 탭(즐겨찾기·사용한 에이전트)은 자리만 지킨다 */}
          {path && (
            <path
              d={path}
              stroke={`url(#${gradId})`}
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray="120"
            />
          )}
        </svg>
        {row.note && (
          <div
            className="text-[12.5px] whitespace-nowrap"
            style={{ color: 'rgba(255,240,247,.55)', fontVariantNumeric: 'tabular-nums' }}
          >
            {row.note}
          </div>
        )}
      </div>

      <a
        href={paths.agentDetail(row.agentId)}
        style={{
          width: '96px',
          height: '34px',
          marginLeft: '26px',
          borderRadius: '17px',
          color: 'rgba(255,255,255,.85)',
        }}
        className="flex flex-none items-center justify-center bg-[rgba(255,255,255,.10)] text-[13.5px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)] hover:text-[#fff]"
      >
        상세 보기 →
      </a>
    </div>
  )
}
