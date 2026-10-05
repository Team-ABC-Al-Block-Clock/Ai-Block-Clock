import { PANE_TEXT_WIDTH } from './config'

/** 작업면 가운데의 「검색 중」 표시. 물결과 빛줄기가 지나간다. */
export default function SearchingPane({ query }: { query: string }) {
  return (
    <div
      className="absolute top-0 left-0"
      style={{ width: `${PANE_TEXT_WIDTH}px`, height: '1024px' }}
    >
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div
          className="absolute left-0"
          style={{
            top: '-20%',
            width: '38%',
            height: '150%',
            background:
              'linear-gradient(90deg,rgba(255,255,255,0),rgba(255,236,246,.16),rgba(255,255,255,0))',
            animation: 'streak 1.7s .25s cubic-bezier(.3,.7,.2,1) both',
          }}
        ></div>
      </div>

      <div
        className="pointer-events-none absolute"
        style={{ left: '350px', top: '392px', width: '280px', height: '192px' }}
      >
        <div
          className="absolute rounded-full"
          style={{
            left: '104px',
            top: '60px',
            width: '72px',
            height: '72px',
            background:
              'radial-gradient(circle at 50% 45%, rgba(255,225,240,.34), rgba(255,190,225,.06) 62%, transparent 70%)',
            animation: 'glow 3.2s ease-in-out infinite',
          }}
        ></div>
        {[0, 1.13, 2.26].map((delay, i) => (
          <div
            key={delay}
            className="absolute rounded-full"
            style={{
              left: '68px',
              top: '24px',
              width: '144px',
              height: '144px',
              border: `1.5px solid rgba(255,222,240,${[0.75, 0.6, 0.45][i]})`,
              animation: `ripple 3.4s ${delay}s cubic-bezier(.2,.6,.25,1) infinite`,
            }}
          ></div>
        ))}
      </div>

      <div
        className="absolute left-0 text-center text-[24px] font-semibold tracking-[-.01em] text-white"
        style={{
          top: '560px',
          width: `${PANE_TEXT_WIDTH}px`,
          textShadow: '0 2px 20px rgba(35,8,45,.8)',
          animation: 'uiInPop .8s .2s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <span className="relative inline-block">검색 중</span>
      </div>
      <div
        className="absolute left-0 truncate text-center text-[15px]"
        style={{
          top: '598px',
          width: `${PANE_TEXT_WIDTH}px`,
          padding: '0 60px',
          color: 'rgba(255,236,246,.8)',
          textShadow: '0 1px 10px rgba(35,8,45,.7)',
          animation: 'uiInPop .8s .28s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        ‘{query}’ 에 맞는 에이전트를 찾고 있습니다
      </div>
    </div>
  )
}
