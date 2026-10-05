import MyPageFrame from '@/components/mypage/MyPageFrame'
import MyPageHeader from '@/components/mypage/MyPageHeader'
import Sidebar from '@/components/mypage/Sidebar'
import NextPageRetry from '@/components/NextPageRetry'
import { paths } from '@/routes/paths'

import AgentRow from './AgentRow'
import { PAGE_SIZE } from './config'
import DeleteWorkflowModal from './DeleteWorkflowModal'
import ListNotice from './ListNotice'
import MyAgentsToolbar from './MyAgentsToolbar'
import { useMyAgentLists } from './useMyAgentLists'
import { useWorkflowDelete } from './useWorkflowDelete'
import WorkflowCard from './WorkflowCard'

/**
 * 나의 에이전트 — 판매 중 · 즐겨찾기 · 워크플로우 세 탭.
 *
 * 목록은 `useMyAgentLists`, 워크플로우 삭제는 `useWorkflowDelete` 가 든다.
 */
export default function MyAgents() {
  const remove = useWorkflowDelete()
  const lists = useMyAgentLists({ onTabChange: remove.reset })
  const { tab, sentinelRef } = lists

  return (
    <>
      <MyPageFrame>
        <Sidebar active="agents" />

        <MyPageHeader
          title="나의 에이전트"
          description="내가 만든 에이전트와 저장한 에이전트를 사용하고 수정할 수 있는 공간입니다."
          descriptionWidth={560}
          descriptionStyle={{ lineHeight: '1.5' }}
        />

        <MyAgentsToolbar
          tab={tab}
          onTab={lists.handleTab}
          countLabel={lists.countLabel}
          q={lists.q}
          onChangeQuery={lists.handleChangeQuery}
          hasSort={lists.hasSort}
          sortLabel={lists.sortLabel}
          onCycleSort={lists.cycleSort}
        />

        <div
          className="absolute box-border overflow-x-hidden"
          id="myRows"
          style={{
            left: '430px',
            top: '366px',
            width: '946px',
            height: '629px',
            paddingRight: '26px',
            overflowY: 'auto',
            scrollbarWidth: 'thin',
            scrollbarColor: 'rgba(255,255,255,.28) transparent',
          }}
        >
          <div
            className="flex flex-col"
            style={{ width: '896px', margin: '8px 12px', gap: tab === 'flows' ? '16px' : '20px' }}
          >
            {lists.rows.map((row, i) => (
              <AgentRow
                key={row.agentId}
                row={row}
                delay={i % PAGE_SIZE}
                onToggleFav={lists.toggleFav(row.agentId, !row.favorited)}
              />
            ))}
            {lists.flowRows.map((flow, i) => (
              <WorkflowCard
                key={flow.workflowId}
                name={flow.name}
                description={flow.description}
                lastExecutedAt={flow.lastExecutedAt}
                href={paths.edit.nodes(flow.workflowId)}
                delay={i % PAGE_SIZE}
                onDelete={() => remove.ask(flow.workflowId, flow.name)}
              />
            ))}
            {/* 이 칸이 보이면 다음 장을 불러온다. 마지막 장이면 걸지 않는다. */}
            <div ref={sentinelRef} style={{ width: '100%', height: '1px' }} />
            {lists.isFetchNextPageError && (
              <NextPageRetry
                isFetching={lists.isFetchingNextPage}
                onRetry={lists.handleRetryNextPage}
              />
            )}
          </div>
        </div>

        {lists.notice && (
          <ListNotice
            tab={tab}
            keyword={lists.keyword}
            isError={lists.isError}
            error={lists.error}
            onClear={lists.clearQuery}
            onRetry={lists.refetch}
          />
        )}
      </MyPageFrame>

      {remove.target !== null && (
        <DeleteWorkflowModal
          name={remove.target.name}
          isPending={remove.isPending}
          message={remove.message}
          onClose={remove.close}
          onConfirm={remove.confirm}
        />
      )}
    </>
  )
}
