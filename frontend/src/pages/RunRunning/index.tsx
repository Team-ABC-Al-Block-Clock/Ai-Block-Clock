import { useParams, useSearchParams } from 'react-router-dom'

import SidePanel, { PANEL_INSET } from '@/components/SidePanel'
import WorkflowLoadNotice from '@/components/WorkflowLoadNotice'
import { sellProblem } from '@/lib/canvas/canvasWorkflow'
import { paths } from '@/routes/paths'

import CopyToast from './CopyToast'
import RunCanvas from './RunCanvas'
import RunDonePanel from './RunDonePanel'
import RunFailureCard from './RunFailureCard'
import RunGauge from './RunGauge'
import RunLog from './RunLog'
import { useResultActions } from './useResultActions'
import { useRunView } from './useRunView'

/**
 * 실행 진행 화면.
 *
 * 진행을 짝지어 그릴 값으로 바꾸는 일은 `useRunView`, 끝난 결과를 다루는 일은
 * `useResultActions` 가 한다. 여기는 그 값을 작업면과 오른쪽 패널에 나눠 그린다.
 */
export default function RunRunning() {
  const [searchParams] = useSearchParams()
  // 어떤 워크플로우를 돌리는지. 대기 화면이 넘겨준다. 없으면 자리표시자를 그린다.
  const workflowId = searchParams.get('workflowId') ?? undefined
  /** 경로가 접수 번호를 늘 싣는다 — 이 화면은 서버가 주는 진행만 그린다. */
  const { executionId } = useParams()

  const view = useRunView({ executionId, workflowId })
  const result = useResultActions(executionId, view.finished)

  const waitingHref = workflowId
    ? `${paths.run.waiting}?workflowId=${workflowId}`
    : paths.run.waiting

  // 없는 워크플로우는 실행할 수도 없다. 편집 화면과 같은 안내로 끊는다.
  const { loaded } = view
  if (loaded.error) return <WorkflowLoadNotice error={loaded.error} onRetry={loaded.refetch} />

  return (
    <>
      <RunCanvas {...view.canvas} />

      <SidePanel>
        <div
          className="absolute text-[23px] font-bold tracking-[-.02em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: '36px' }}
        >
          워크플로우 실행
        </div>

        {/* 이전 단계로 돌아가 프롬프트를 수정하여 재실행할 수 있다. */}
        <a
          href={waitingHref}
          title="다시 실행"
          aria-label="다시 실행"
          style={{
            right: '26px',
            top: '32px',
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            background: 'rgba(255,255,255,.12)',
            boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.16)',
          }}
          className="absolute flex cursor-pointer items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.24)]"
        >
          <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
            <path
              d="M17 11H6M11 6L6 11L11 16"
              stroke="rgba(255,255,255,.9)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </a>

        <RunGauge {...view.gauge} />

        {view.failure.shown && (
          <RunFailureCard
            missing={view.failure.missing}
            lost={view.failure.lost}
            notice={view.failure.notice}
            retryHref={waitingHref}
          />
        )}

        {view.running && <RunLog logs={view.logs} />}

        {view.done && (
          <RunDonePanel
            workflowId={workflowId}
            result={{
              blocks: result.blocks,
              notice: result.notice,
              copied: result.copied,
              onCopy: result.handleCopy,
              canDownload: result.canDownload,
              downloading: result.downloading,
              downloadProblem: result.downloadProblem,
              onDownload: (format) => void result.handleDownload(format),
            }}
            sell={{ ...view.sell, problem: sellProblem(view.defs) }}
          />
        )}
      </SidePanel>

      {result.copied && <CopyToast />}
    </>
  )
}
