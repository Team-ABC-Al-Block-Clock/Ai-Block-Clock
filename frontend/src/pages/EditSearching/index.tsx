import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'

import CanvasPane from '@/components/canvas/CanvasPane'
import SidePanel, { PANEL_INSET } from '@/components/SidePanel'
import { queryFrom } from '@/routes/handoff'
import { paths } from '@/routes/paths'

import CandidateList from './CandidateList'
import PreviewPane from './PreviewPane'
import { EmptyNotice, FailedNotice } from './RecommendNotice'
import ResearchField from './ResearchField'
import SearchingPane from './SearchingPane'
import StartBar from './StartBar'
import { usePickedAgents } from './usePickedAgents'
import { useRecommendations } from './useRecommendations'

/**
 * 추천 에이전트를 찾아 선택하는 화면.
 *
 * 왼쪽은 편집 화면과 같은 작업면이다 — 선택한 카드가 곧바로 여기 올라가므로 "완료를 누르면
 * 무엇이 생기는지" 를 따로 설명하지 않는다. 선택한 것은 편집 화면으로 그대로 건너간다.
 */
export default function EditSearching() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()

  // 검색어는 주소가 들고 있다. 화면이 따로 쥐면 뒤로 가기와 새로고침에서 둘이 갈린다.
  const query = queryFrom(params)
  const { picked, atLimit, preview, previewView, toggle, unpick } = usePickedAgents()
  const { items, searching, isFetching, refetch, failed, isEmpty, ready } =
    useRecommendations(query)

  // 되돌아갈 곳은 랜딩이다 — 다시 찾은 검색어를 히스토리에 쌓지 않는다.
  const handleResearch = (next: string) => setParams({ q: next }, { replace: true })

  const handleStart = () =>
    navigate(paths.edit.nodes.withoutId, { state: picked.length > 0 ? { agents: picked } : null })

  // 랜딩에서 적은 문장 없이는 찾을 것이 없다. 주소를 직접 친 경우다.
  if (!query) return <Navigate to={paths.home} replace />

  return (
    <>
      <CanvasPane view={previewView}>
        {searching ? (
          <SearchingPane query={query} />
        ) : (
          <PreviewPane
            preview={preview}
            view={previewView}
            hasPicked={picked.length > 0}
            onUnpick={unpick}
          />
        )}
      </CanvasPane>

      <SidePanel animation="sideInDeep 1.05s .12s cubic-bezier(.18,.86,.24,1) both">
        <ResearchField query={query} onSearch={handleResearch} />

        <div
          className="absolute text-[15px] font-semibold tracking-[-.01em] text-white"
          style={{ left: `${PANEL_INSET}px`, top: '88px' }}
        >
          추천 결과
        </div>
        <div
          className="absolute text-right text-[13px]"
          style={{ right: `${PANEL_INSET}px`, top: '90px', color: 'rgba(255,236,246,.6)' }}
        >
          {searching || isFetching ? '찾는 중' : failed ? '' : `${items.length}개`}
        </div>

        {failed && <FailedNotice retrying={isFetching} onRetry={() => void refetch()} />}

        {isEmpty && <EmptyNotice />}

        {ready && (
          <CandidateList items={items} picked={picked} atLimit={atLimit} onToggle={toggle} />
        )}

        <StartBar
          pickedCount={picked.length}
          atLimit={atLimit}
          searching={searching}
          onStart={handleStart}
        />
      </SidePanel>
    </>
  )
}
