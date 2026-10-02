import type { ResultFormat } from '@/api/executions'
import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'
import type { MarkdownBlock } from '@/lib/run/markdown'
import { paths } from '@/routes/paths'

import ResultDocument from './ResultDocument'

/** PDF 옆 JSON 버튼 폭. */
const JSON_BUTTON_WIDTH = 104

interface RunDonePanelProps {
  workflowId: string | undefined
  result: {
    blocks: MarkdownBlock[] | undefined
    notice: string | null
    copied: boolean
    onCopy: () => void
    canDownload: boolean
    downloading: boolean
    downloadProblem: 'PDF_MISSING' | 'FAILED' | null
    onDownload: (format: ResultFormat) => void
  }
  sell: {
    canSell: boolean
    canSellNow: boolean
    registerHref: string
    /** 판매할 수 없는 까닭. 없으면 아직 워크플로우를 읽는 중이다. */
    problem: string | null
  }
}

/** 실행이 끝난 뒤의 오른쪽 패널 — 결과 받기 · 수정하기 · 판매 등록 · 출력 결과. */
export default function RunDonePanel({ workflowId, result, sell }: RunDonePanelProps) {
  const downloadLabel = result.downloading ? '받는 중…' : '결과 다운로드 (PDF)'
  const copyLabel = result.copied ? '복사됨' : '복사'
  const copyBg = result.copied ? 'rgba(154,208,180,.26)' : 'rgba(255,255,255,.1)'
  const copyFg = result.copied ? '#a8ddc0' : 'rgba(255,255,255,.82)'
  const { canSell } = sell

  return (
    <>
      <div
        className="bg-surface-card absolute"
        style={{
          left: `${PANEL_INSET}px`,
          top: '400px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: '88px',
          borderRadius: '12px',
          animation: 'uiIn .6s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div
          className="absolute left-0 text-center text-[17px] font-semibold tracking-[-.01em] text-white"
          style={{ top: '20px', width: `${PANEL_CONTENT_WIDTH}px` }}
        >
          완료되었습니다.
        </div>
        <div
          className="absolute left-0 text-center text-[17px]"
          style={{
            top: '48px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            color: 'rgba(255,236,246,.76)',
          }}
        >
          결과를 PDF 나 JSON 파일로 받을 수 있습니다.
        </div>
      </div>

      <button
        type="button"
        onClick={() => result.onDownload('PDF')}
        disabled={!result.canDownload}
        style={{
          left: `${PANEL_INSET}px`,
          top: '508px',
          width: `${PANEL_CONTENT_WIDTH - JSON_BUTTON_WIDTH - 8}px`,
          height: '56px',
          borderRadius: '12px',
          background: 'var(--color-accent)',
          boxShadow: '0 10px 26px rgba(140,100,130,.24),inset 0 1px 0 rgba(255,255,255,.5)',
          gap: '10px',
          transition: 'transform .18s',
          animation: 'uiIn .6s .08s cubic-bezier(.2,.8,.25,1) both',
          opacity: result.downloading ? 0.7 : 1,
        }}
        className="absolute flex cursor-pointer items-center justify-center hover:-translate-y-[2px] disabled:cursor-default disabled:hover:translate-y-0"
      >
        <svg className="block" width="16" height="17" viewBox="0 0 14 15" fill="none">
          <path
            d="M6.5 0V9.5M2 6L6.5 10.5L11 6M0 13.5H13"
            style={{ stroke: 'var(--color-accent-ink)' }}
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <div
          className="text-[17px] font-bold tracking-[-.01em]"
          style={{ color: 'var(--color-accent-ink)' }}
        >
          {downloadLabel}
        </div>
      </button>

      <button
        type="button"
        onClick={() => result.onDownload('JSON')}
        disabled={!result.canDownload}
        title="엔진이 낸 결과 JSON 을 그대로 받습니다"
        style={{
          left: `${PANEL_INSET + PANEL_CONTENT_WIDTH - JSON_BUTTON_WIDTH}px`,
          top: '508px',
          width: `${JSON_BUTTON_WIDTH}px`,
          height: '56px',
          borderRadius: '12px',
          boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
          animation: 'uiIn .6s .08s cubic-bezier(.2,.8,.25,1) both',
          opacity: result.downloading ? 0.7 : 1,
        }}
        className="bg-surface-card absolute flex cursor-pointer items-center justify-center border-0 font-[inherit] text-[16px] font-bold text-white hover:bg-[rgba(255,255,255,.16)] disabled:cursor-default"
      >
        JSON
      </button>

      {result.downloadProblem && (
        <div
          role="alert"
          className="absolute text-center text-[13px]"
          style={{
            left: `${PANEL_INSET}px`,
            top: '568px',
            width: `${PANEL_CONTENT_WIDTH}px`,
            color: 'rgba(255,214,232,.72)',
          }}
        >
          {result.downloadProblem === 'PDF_MISSING' ? (
            <>PDF 를 만들지 못했습니다. 옆의 JSON 으로 받아 주세요</>
          ) : (
            '결과를 받지 못했습니다. 잠시 후 다시 눌러 주세요'
          )}
        </div>
      )}

      <a
        href={workflowId ? paths.edit.nodes(workflowId) : paths.edit.nodes.withoutId}
        style={{
          left: `${PANEL_INSET}px`,
          top: '576px',
          width: '193px',
          height: '56px',
          borderRadius: '12px',
          boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.18)',
          gap: '8px',
          transition: 'background .18s',
          animation: 'uiIn .6s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="bg-surface-card absolute flex cursor-pointer items-center justify-center hover:bg-[rgba(255,255,255,.16)]"
      >
        <svg className="block" width="15" height="15" viewBox="0 0 14 14" fill="none">
          <path
            d="M0 12.5L1.5 8L9.5 0L12.5 3L4.5 11L0 12.5ZM0 12.5L3.7 11.8"
            stroke="rgba(255,255,255,.75)"
            strokeWidth="1.4"
            strokeLinejoin="round"
          />
        </svg>
        <div
          className="text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
          style={{ color: 'rgba(255,255,255,.9)' }}
        >
          수정하기
        </div>
      </a>

      {/* 워크플로우 전체 실행에 성공해야 마켓에 판매 등록 가능. */}
      {/* 등록 화면에서 취소하면 여기로 돌아온다 — 워크플로우를 실어 보내야 빈 화면이 되지 않는다. */}
      <a
        {...(sell.canSellNow
          ? {
              // 접수 번호도 함께 넘긴다. 돌아올 때 이 실행으로 되돌아와야 결과를 잃지 않는다.
              href: sell.registerHref,
            }
          : {})}
        title={sell.problem ?? (sell.canSellNow ? undefined : '워크플로우를 읽는 중입니다')}
        aria-disabled={!sell.canSellNow}
        style={{
          left: '237px',
          top: '576px',
          width: '193px',
          height: '56px',
          borderRadius: '12px',
          // 흐릿하게 하되 opacity 는 쓰지 않는다.
          boxShadow: `inset 0 0 0 1px rgba(255,255,255,${canSell ? '.18' : '.08'})`,
          gap: '8px',
          cursor: canSell ? 'pointer' : 'not-allowed',
          transition: 'background .18s',
          animation: 'uiIn .6s .2s cubic-bezier(.2,.8,.25,1) both',
        }}
        className={
          canSell
            ? 'bg-surface-card absolute flex items-center justify-center hover:bg-[rgba(255,255,255,.16)]'
            : 'absolute flex items-center justify-center bg-[rgba(255,255,255,.04)]'
        }
      >
        <svg className="block" width="15" height="15" viewBox="0 0 14 14" fill="none">
          <path
            d="M7.4 0.6H12.3A1.1 1.1 0 0 1 13.4 1.7V6.6L6.6 13.4L0.6 7.4L7.4 0.6Z"
            stroke={canSell ? 'rgba(255,255,255,.75)' : 'rgba(255,255,255,.3)'}
            strokeWidth="1.4"
            strokeLinejoin="round"
          />
          <circle
            cx="10.2"
            cy="3.8"
            r="1"
            fill={canSell ? 'rgba(255,255,255,.75)' : 'rgba(255,255,255,.3)'}
          />
        </svg>
        <div
          className="text-[15px] font-semibold tracking-[-.01em] whitespace-nowrap"
          style={{ color: canSell ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.38)' }}
        >
          판매 등록
        </div>
      </a>

      <div
        className="absolute text-[17.5px] font-semibold tracking-[-.005em]"
        style={{ left: `${PANEL_INSET}px`, top: '660px', color: 'rgba(255,255,255,.94)' }}
      >
        출력 결과
      </div>
      <div
        onClick={result.onCopy}
        style={{
          // 라벨이 '복사'/'복사됨' 으로 바뀌어 폭이 달라지므로 결과 상자의 오른쪽 끝에 맞춘다.
          right: `${PANEL_INSET}px`,
          top: '655px',
          height: '26px',
          padding: '0 11px',
          borderRadius: '8px',
          background: copyBg,
          gap: '6px',
        }}
        className="absolute flex cursor-pointer items-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.18)]"
      >
        <svg
          width="12"
          height="12"
          viewBox="0 0 12 12"
          fill="none"
          stroke={copyFg}
          strokeWidth="1.3"
          strokeLinejoin="round"
        >
          <rect x="3.6" y="3.6" width="7.4" height="7.4" rx="1.4" />
          <path d="M8.4 1H2.4A1.4 1.4 0 0 0 1 2.4v6" />
        </svg>
        <div
          className="text-[13.5px] font-semibold tracking-[-.005em] whitespace-nowrap"
          style={{ color: copyFg }}
        >
          {copyLabel}
        </div>
      </div>
      <div
        className="absolute box-border overflow-x-hidden"
        id="resultBox"
        style={{
          left: `${PANEL_INSET}px`,
          top: '692px',
          width: `${PANEL_CONTENT_WIDTH}px`,
          height: 'calc(318px + 2 * var(--edge-y, 0px))',
          padding: '16px',
          borderRadius: '12px',
          background: 'var(--color-surface-quiet)',
          overflowY: 'auto',
          userSelect: 'text',
          WebkitUserSelect: 'text',
          cursor: 'text',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,.28) transparent',
          animation: 'uiIn .6s .2s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        {result.blocks ? (
          <ResultDocument blocks={result.blocks} />
        ) : (
          <div className="text-[16px]" style={{ color: 'rgba(255,255,255,.6)' }}>
            {result.notice}
          </div>
        )}
      </div>
    </>
  )
}
