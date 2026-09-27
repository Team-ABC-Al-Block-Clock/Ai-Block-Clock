import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'

import {
  addAgentFavorite,
  getMyAgents,
  getMyFavorites,
  removeAgentFavorite,
  type AgentDetail,
  type MyAgentSort,
} from '@/api/agents'
import { ApiError } from '@/api/errors'
import { deleteWorkflow, getMyWorkflows, type WorkflowSort } from '@/api/workflows'
import { josa, priceLabel, runLabel, timeLabel } from '@/lib/format'
import { queryKeys } from '@/lib/queryKeys'
import { sparkPath } from '@/lib/spark'
import { paths } from '@/routes/paths'
import Sidebar from '../components/Sidebar'
import MyPageFrame from '@/components/MyPageFrame'
import SearchIcon from '@/components/SearchIcon'

/** 마켓 목록(9)과 다르다. */
const PAGE_SIZE = 10
/** 입력이 멈춘 뒤에만 검색한다. */
const DEBOUNCE_MS = 300

type Tab = 'selling' | 'favorites' | 'flows'

const TABS = [
  { key: 'selling', label: '판매 중' },
  { key: 'favorites', label: '즐겨찾기' },
  { key: 'flows', label: '워크플로우' },
] as const satisfies readonly { key: Tab; label: string }[]

/**
 * 탭마다 정렬 옵션이 다르다. 즐겨찾기는 `sort` 가 없어 빈 배열이고 필을 감춘다.
 * 값은 `api/` 의 유니온에서 온다 — 화면에 새로 적으면 서버가 안 받는 값이 나간다.
 */
const SORTS = {
  selling: ['LATEST', 'EXECUTIONS', 'REVENUE'],
  favorites: [],
  flows: ['LAST_EXECUTED', 'LATEST'],
} as const satisfies Record<Tab, readonly string[]>

const SORT_LABEL = {
  LATEST: '최신 순',
  EXECUTIONS: '실행 많은 순',
  REVENUE: '수익 높은 순',
  LAST_EXECUTED: '최근 실행 순',
} as const satisfies Record<MyAgentSort | WorkflowSort, string>

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다 — 행동을 최소 하나 준다. */
const EMPTY = {
  selling: {
    heading: '판매 중인 에이전트가 없습니다',
    body:
      '워크플로우를 만들어 한 번 실행하면 그 결과 화면에서 마켓에 등록할 수 있습니다.\n ' +
      '등록한 에이전트는 여기서 실행 횟수와 수익을 확인할 수 있습니다.',
    // 등록은 굳힐 워크플로우와 보던 회차가 있어야 한다. 빈손으로 등록 화면에 보내면 막힌다.
    actions: JSON.stringify([{ label: '워크플로우 만들기', href: paths.home, primary: true }]),
  },
  favorites: {
    heading: '즐겨찾기한 에이전트가 없습니다',
    body: '마켓에서 마음에 드는 에이전트에 별을 눌러 두면 여기에 모입니다.',
    actions: JSON.stringify([{ label: '마켓 둘러보기', href: paths.search, primary: true }]),
  },
  flows: {
    heading: '아직 만든 워크플로우가 없습니다',
    body:
      '하고 싶은 일을 적으면 필요한 에이전트를 이어 붙여 워크플로우를 만들어 줍니다.\n ' +
      '만든 워크플로우는 여기에 쌓입니다.',
    actions: JSON.stringify([{ label: '워크플로우 만들기', href: paths.home, primary: true }]),
  },
} as const satisfies Record<Tab, { heading: string; body: string; actions: string }>

/** 삭제 실패는 status 가 아니라 `code` 로 갈린다 — 409 하나에 두 가지 이유가 섞여 있다. */
const DELETE_MESSAGE: Record<string, string> = {
  WORKFLOW_002: '이미 삭제된 워크플로우입니다',
  WORKFLOW_003: '내가 만든 워크플로우만 삭제할 수 있습니다',
  WORKFLOW_009: '에이전트로 등록된 워크플로우는 삭제할 수 없습니다',
  WORKFLOW_010: '실행이 진행 중입니다. 끝난 뒤에 삭제해 주세요',
}

/** 오른쪽 열의 가로 격자. 컨트롤과 카드의 오른쪽 끝을 1338 에 맞춘다. */
const LEFT = 442
const SORT_W = 148
/** 즐겨찾기 토글 필이 없으므로 검색바가 그 자리까지 넓어진다. 896 - 148 - 12 */
const SEARCH_W = 736

/** 442 + (896 - 620) / 2 = 580. translateX 로 중앙 정렬하지 않는다 — transform 이
 *  걸리면 backdrop 루트가 되어 카드가 바깥 배경을 못 가져온다. */
const NOTICE_BOX = { left: '580px', top: '430px', width: '620px' } as const

const CHIP = 'flex cursor-pointer items-center transition-colors duration-[160ms]'
const ROW =
  'bg-surface-card box-border flex flex-none items-center transition-colors duration-200 hover:bg-[rgba(255,255,255,.14)]'

/** 탭마다 라벨과 값만 다르고 폭은 같다 — 흔들리면 탭을 옮길 때 행이 들썩인다. */
interface Row {
  agentId: string
  name: string
  desc: string
  iconUrl: string | null
  /** 내가 등록한 것. 「판매 중」 목록은 정의상 전부 내 것이다 */
  mine: boolean
  favorited: boolean
  /** 설명 아래 두 줄짜리 지표 */
  metrics: [string, string]
  /** 스파크라인. 값이 없는 탭은 빈 배열 — 명세가 `trend` 를 주지 않는다 */
  trend: number[]
  /** 그래프 아래 보조 한 줄. 없는 탭은 undefined */
  note?: string
}

export default function MyAgents() {
  const [tab, setTab] = useState<Tab>('selling')
  const [q, setQ] = useState('')
  const [keyword, setKeyword] = useState('')
  /** 탭마다 자기 정렬을 기억한다. 하나로 두면 탭을 옮길 때마다 다른 탭의 정렬로 조회한다. */
  const [sortAt, setSortAt] = useState<Record<Tab, number>>({
    selling: 0,
    favorites: 0,
    flows: 0,
  })
  /** 이름을 함께 붙들어 둔다 — 목록을 무효화하면 그 행이 사라져 제목이 비어 버린다 */
  const [deleteAt, setDeleteAt] = useState<{ workflowId: string; name: string } | null>(null)

  /** 주소를 되읽어 value 로 쓰지 않는다 — 리렌더가 조합 중인 input 을 덮으면 한글이
   *  낱자로 떨어진다. 입력값의 주인은 `q` 하나다. */
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(q.trim()), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [q])

  const sellingSort = SORTS.selling[sortAt.selling]
  const flowSort = SORTS.flows[sortAt.flows]

  // 탭 하나가 API 하나다. 보이지 않는 목록까지 요청하지 않게 활성 탭만 enabled.
  const selling = useInfiniteQuery({
    queryKey: queryKeys.agents.list({ keyword, sort: sellingSort, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getMyAgents({
        keyword: keyword || undefined,
        sort: sellingSort,
        cursor: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    // 검색어가 바뀌는 동안 목록이 비었다가 차오르지 않게 이전 결과를 남긴다.
    placeholderData: keepPreviousData,
    enabled: tab === 'selling',
  })
  const favorites = useInfiniteQuery({
    queryKey: queryKeys.users.favorites.list({ size: PAGE_SIZE }),
    queryFn: ({ pageParam }) => getMyFavorites({ cursor: pageParam, size: PAGE_SIZE }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: tab === 'favorites',
  })
  const flows = useInfiniteQuery({
    queryKey: queryKeys.workflows.list({ keyword, sort: flowSort, size: PAGE_SIZE }),
    queryFn: ({ pageParam }) =>
      getMyWorkflows({
        keyword: keyword || undefined,
        sort: flowSort,
        cursor: pageParam,
        size: PAGE_SIZE,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    placeholderData: keepPreviousData,
    enabled: tab === 'flows',
  })

  const active = tab === 'selling' ? selling : tab === 'favorites' ? favorites : flows

  const queryClient = useQueryClient()

  /** 즐겨찾기는 화면 안 상태가 아니라 서버 값이다. 상세에서 켠 별이 목록에도 보여야 한다. */
  const favorite = useMutation({
    mutationFn: ({ agentId, next }: { agentId: string; next: boolean }) =>
      next ? addAgentFavorite(agentId) : removeAgentFavorite(agentId),
    /** 갱신된 횟수가 함께 오므로 상세는 덮고 목록은 무효화한다 —
     *  즐겨찾기 탭에서 해제하면 그 행이 사라지는 것이 맞다. */
    onSuccess: (res) => {
      queryClient.setQueryData(
        queryKeys.marketplace.agents.detail(res.agentId),
        (prev: AgentDetail | undefined) =>
          prev && { ...prev, favorited: res.favorited, favoriteCount: res.favoriteCount },
      )
      void queryClient.invalidateQueries({ queryKey: queryKeys.users.favorites.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.agents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.marketplace.agents.lists() })
    },
  })

  const remove = useMutation({
    mutationFn: (workflowId: string) => deleteWorkflow(workflowId),
    onSuccess: () => {
      setDeleteAt(null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.lists() })
    },
    onError: (error) => {
      // 이미 지워진 것이면 목록이 낡았다. 모달은 열어 두고 목록만 맞춘다.
      if (error instanceof ApiError && error.code === 'WORKFLOW_002')
        void queryClient.invalidateQueries({ queryKey: queryKeys.workflows.lists() })
    },
  })

  const handleTab = (next: Tab) => () => {
    setTab(next)
    // 검색어를 남기면 검색을 받지 않는 탭(즐겨찾기)에서 입력만 남아 아무 효과가 없다.
    setQ('')
    setKeyword('')
    remove.reset()
  }
  const handleChangeQuery = (e: React.ChangeEvent<HTMLInputElement>) => setQ(e.target.value)
  const options: readonly string[] = SORTS[tab]
  const sortLabel = options.length
    ? SORT_LABEL[options[sortAt[tab]] as keyof typeof SORT_LABEL]
    : ''
  /** 옵션이 둘·셋뿐이라 눌러서 다음 값으로 넘긴다. 팝업·바깥 클릭 닫기가 필요 없다.
   *  넷을 넘기면 그때 드롭다운을 만든다. */
  const cycleSort = () =>
    setSortAt((prev) => ({ ...prev, [tab]: (prev[tab] + 1) % Math.max(1, options.length) }))
  /** 같은 행을 연달아 누르면 등록·해제가 엇갈리므로 막는다. 다른 행은 막지 않는다 —
   *  전역 `isPending` 으로 막으면 옆 행의 별 클릭이 아무 표시 없이 사라진다. */
  const pendingFavAt = favorite.isPending ? favorite.variables.agentId : null
  const toggleFav = (agentId: string, next: boolean) => () => {
    if (pendingFavAt !== agentId) favorite.mutate({ agentId, next })
  }

  const rows: Row[] =
    tab === 'selling'
      ? (selling.data?.pages.flatMap((page) => page.content) ?? []).map((a) => ({
          agentId: a.agentId,
          name: a.name,
          desc: a.summary,
          iconUrl: a.iconUrl,
          mine: true,
          favorited: a.favorited,
          metrics: [`사용 ${runLabel(a.executionCount)}`, `단가 ${priceLabel(a.price)}`],
          trend: a.trend,
          note: `누적 ${priceLabel(a.revenue)}`,
        }))
      : tab === 'favorites'
        ? (favorites.data?.pages.flatMap((page) => page.content) ?? []).map((a) => ({
            agentId: a.agentId,
            name: a.name,
            desc: a.summary,
            iconUrl: a.iconUrl,
            mine: false,
            // 이 목록에 있다는 것이 곧 즐겨찾기했다는 뜻이다 — 응답에 favorited 가 없다.
            favorited: true,
            metrics: [`사용 ${runLabel(a.executionCount)}`, `단가 ${priceLabel(a.price)}`],
            trend: [],
          }))
        : []

  const flowRows = tab === 'flows' ? (flows.data?.pages.flatMap((page) => page.content) ?? []) : []
  const count = tab === 'flows' ? flowRows.length : rows.length

  // 목록이 #myRows 안에서 스크롤되므로 그것을 root 로 삼는다 — 뷰포트를 root 로 두면
  // 컨테이너를 끝까지 내려도 sentinel 이 교차하지 않는다.
  const sentinelRef = useRef<HTMLDivElement>(null)
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError } = active

  /** 활성 탭의 콜백은 렌더마다 새 함수다. deps 에 넣으면 observer 가 매 렌더 다시
   *  만들어지고 다음 장이 줄줄이 딸려 온다 — 최신 함수만 참조로 들고 간다. */
  const latest = useRef({ fetchNextPage: active.fetchNextPage, refetch: active.refetch })
  useEffect(() => {
    latest.current = { fetchNextPage: active.fetchNextPage, refetch: active.refetch }
  })

  useEffect(() => {
    const node = sentinelRef.current
    // 실패한 커서로는 다시 시도하지 않는다 — sentinel 이 교차할 때마다 같은 요청이 반복된다.
    if (!node || !hasNextPage || isFetchNextPageError) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        // cancelRefetch 기본값이 true 라 진행 중이면 취소하고 같은 커서로 다시 보낸다.
        if (entry.isIntersecting && !isFetchingNextPage) void latest.current.fetchNextPage()
      },
      { root: document.getElementById('myRows'), rootMargin: '200px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
    // isFetchingNextPage 가 deps 에 있어야 한다. 이 값이 바뀔 때 observer 가 다시
    // 만들어져 이미 교차 중인 sentinel 에서 다음 장을 잇는다.
  }, [tab, hasNextPage, isFetchNextPageError, isFetchingNextPage])

  /** observer 는 실패한 커서를 다시 밟지 않으려고 스스로를 끈다. */
  const handleRetryNextPage = () => {
    if (!isFetchingNextPage) void latest.current.fetchNextPage()
  }

  // 이전 검색어의 결과를 보여주는 동안(placeholder)은 아직 '이번 결과' 가 아니다.
  const isSettled = !active.isPending && !active.isPlaceholderData
  // 불러오는 중에는 '없음' 을 띄우지 않는다 — 요청마다 한 번씩 깜빡인다.
  const notice = isSettled && count === 0
  const countLabel = isSettled ? `${count}개${hasNextPage ? '+' : ''}` : '…'

  /** React 는 커스텀 이벤트를 on* 으로 받지 않아 직접 붙인다. effect + deps 로 붙이면
   *  빈 상태와 실패 분기 사이를 오갈 때 deps 가 안 바뀌어 '다시 시도' 가 죽는다 —
   *  노드가 붙는 시점에 매달아 분기와 무관하게 만든다. */
  const bindNotice = useCallback((node: HTMLElement | null) => {
    if (!node) return
    const onClear = () => setQ('')
    const onRetry = () => void latest.current.refetch()
    node.addEventListener('clear', onClear)
    node.addEventListener('retry', onRetry)
    return () => {
      node.removeEventListener('clear', onClear)
      node.removeEventListener('retry', onRetry)
    }
  }, [])

  const deleteError = remove.error
  const deleteMessage =
    deleteError instanceof ApiError
      ? (DELETE_MESSAGE[deleteError.code ?? ''] ??
        (deleteError.isNetworkError ? '서버에 연결할 수 없습니다' : '삭제에 실패했습니다'))
      : deleteError
        ? '삭제에 실패했습니다'
        : null
  const handleCloseDelete = () => {
    setDeleteAt(null)
    remove.reset()
  }
  const handleConfirmDelete = () => {
    if (deleteAt && !remove.isPending) remove.mutate(deleteAt.workflowId)
  }

  return (
    <>
      <MyPageFrame>
        <Sidebar active="agents" />

        <div
          className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
          style={{
            left: `${LEFT}px`,
            top: '158px',
            textShadow: '0 2px 20px rgba(35,8,45,.6)',
            animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          나의 에이전트
        </div>
        <div
          className="absolute text-[13px] tracking-[-.01em]"
          style={{
            left: `${LEFT}px`,
            top: '206px',
            width: '560px',
            lineHeight: '1.5',
            color: 'rgba(255,236,246,.7)',
            animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          내가 만든 에이전트와 저장한 에이전트를 사용하고 수정할 수 있는 공간입니다.
        </div>

        {/* 탭 — 하나가 API 하나다 */}
        <div
          className="absolute flex"
          style={{
            left: `${LEFT}px`,
            top: '250px',
            height: '36px',
            gap: '8px',
            zIndex: 5,
            animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          {TABS.map((t) => {
            const on = tab === t.key
            return (
              <div
                key={t.key}
                className={`${CHIP} text-[13px] font-semibold whitespace-nowrap ${on ? '' : 'hover:bg-[rgba(255,255,255,.16)]'}`}
                onClick={handleTab(t.key)}
                style={{
                  height: '36px',
                  padding: '0 18px',
                  borderRadius: '12px',
                  background: on ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.09)',
                  color: on ? '#3a1030' : 'rgba(255,240,247,.78)',
                }}
              >
                {t.label}
              </div>
            )
          })}
        </div>

        {/* 개수 — 커서 목록은 전체 개수를 주지 않는다. 불러온 만큼만 세고 더 있으면 '+' */}
        <div
          className="absolute text-right text-[13.5px] tracking-[-.01em]"
          style={{
            left: '1178px',
            top: '260px',
            width: '160px',
            color: 'rgba(255,240,247,.55)',
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {countLabel}
        </div>

        {/* 검색 */}
        <div
          className="absolute bg-[rgba(255,255,255,.09)]"
          style={{
            left: `${LEFT}px`,
            top: '302px',
            width: `${SEARCH_W}px`,
            height: '44px',
            borderRadius: '22px',
            animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <SearchIcon left={18} top={15} color="rgba(255,255,255,.62)" handleLength={8} />
          <input
            className="absolute border-0 bg-transparent font-[inherit] text-[13px] tracking-[-.01em] text-white outline-0 placeholder:text-white/50 disabled:cursor-not-allowed"
            type="text"
            value={q}
            onChange={handleChangeQuery}
            // 명세에 keyword 가 없는 탭이다. 열어 두면 입력은 되는데 아무 효과가 없다.
            disabled={tab === 'favorites'}
            placeholder={
              tab === 'favorites'
                ? '즐겨찾기 탭은 이름 검색을 지원하지 않습니다'
                : tab === 'flows'
                  ? '워크플로우 이름 검색'
                  : '에이전트 이름 또는 설명 검색'
            }
            style={{ left: '46px', top: '11px', width: `${SEARCH_W - 66}px`, height: '22px' }}
          />
        </div>

        {/* 정렬 — 눌러서 다음 값으로. 즐겨찾기 탭은 명세에 sort 가 없어 감춘다 */}
        {options.length > 0 && (
          <div
            onClick={cycleSort}
            style={{
              left: '1190px',
              top: '305px',
              width: `${SORT_W}px`,
              height: '38px',
              borderRadius: '19px',
              padding: '0 14px',
              animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
            }}
            className={`${CHIP} absolute box-border justify-between bg-[rgba(255,255,255,.09)] hover:bg-[rgba(255,255,255,.16)]`}
          >
            <div
              className="text-[13.5px] whitespace-nowrap"
              style={{ color: 'rgba(255,240,247,.82)' }}
            >
              {sortLabel}
            </div>
            <div
              style={{
                width: '0',
                height: '0',
                borderLeft: '4px solid transparent',
                borderRight: '4px solid transparent',
                borderTop: '5px solid rgba(255,255,255,.6)',
              }}
            ></div>
          </div>
        )}

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
            {rows.map((row, i) => (
              <AgentRow
                key={row.agentId}
                row={row}
                delay={i % PAGE_SIZE}
                onToggleFav={toggleFav(row.agentId, !row.favorited)}
              />
            ))}
            {flowRows.map((flow, i) => (
              <WorkflowCard
                key={flow.workflowId}
                name={flow.name}
                summary={flow.summary}
                executionCount={flow.executionCount}
                lastExecutedAt={flow.lastExecutedAt}
                href={paths.edit.nodes(flow.workflowId)}
                delay={i % PAGE_SIZE}
                onDelete={() => setDeleteAt({ workflowId: flow.workflowId, name: flow.name })}
              />
            ))}
            {/* 이 칸이 보이면 다음 장을 불러온다. 마지막 장이면 걸지 않는다. */}
            <div ref={sentinelRef} style={{ width: '100%', height: '1px' }} />
            {isFetchNextPageError && (
              <div
                className="flex flex-none items-center justify-center"
                style={{
                  width: '896px',
                  gap: '12px',
                  padding: '10px 0 2px',
                  animation: 'fadeIn .2s ease both',
                }}
              >
                <div
                  className="text-[14px] tracking-[-.01em]"
                  style={{ color: 'rgba(255,196,214,.9)' }}
                >
                  다음 목록을 불러오지 못했습니다
                </div>
                <div
                  onClick={handleRetryNextPage}
                  className={`${CHIP} justify-center text-[14px] font-semibold text-white hover:bg-[rgba(255,255,255,.16)]`}
                  style={{
                    height: '30px',
                    padding: '0 14px',
                    borderRadius: '15px',
                    background: 'rgba(255,255,255,.09)',
                    opacity: isFetchingNextPage ? 0.6 : 1,
                    cursor: isFetchingNextPage ? 'default' : 'pointer',
                  }}
                >
                  {isFetchingNextPage ? '불러오는 중…' : '다시 시도'}
                </div>
              </div>
            )}
          </div>
        </div>

        {notice &&
          (active.isError ? (
            <empty-state
              ref={bindNotice}
              style={{ position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' }}
              heading="목록을 불러오지 못했습니다"
              body={
                active.error instanceof ApiError && active.error.isNetworkError
                  ? '서버에 연결할 수 없습니다. 네트워크를 확인해 주세요.'
                  : '잠시 후 다시 시도해 주세요.'
              }
              actions={JSON.stringify([{ label: '다시 시도', event: 'retry', primary: true }])}
            />
          ) : keyword ? (
            <empty-state
              ref={bindNotice}
              style={{ position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' }}
              heading={`‘${keyword}’ 에 맞는 결과가 없습니다`}
              body="찾는 이름을 다르게 적어보거나 검색어를 지워 전체를 확인해 보세요."
              actions={JSON.stringify([{ label: '검색어 지우기', event: 'clear', primary: true }])}
            />
          ) : (
            <empty-state
              ref={bindNotice}
              style={{ position: 'absolute', ...NOTICE_BOX, animation: 'fadeIn .3s ease both' }}
              heading={EMPTY[tab].heading}
              body={EMPTY[tab].body}
              actions={EMPTY[tab].actions}
            />
          ))}
      </MyPageFrame>

      {/* 삭제 확인 — 되돌릴 수 없으므로 한 번 묻는다 */}
      {deleteAt !== null && (
        <div className="absolute inset-0 flex items-center justify-center" style={{ zIndex: 70 }}>
          {/* 이 패널에 opacity·transform 을 걸지 않는다. backdrop 루트가 되어 blur 가 죽기 때문. 등장 모션은 위의 패널에만 준다. */}
          <div
            onClick={handleCloseDelete}
            className="absolute inset-0"
            style={{
              background: 'rgba(12,5,22,.55)',
              backdropFilter: 'blur(6px)',
              WebkitBackdropFilter: 'blur(6px)',
            }}
          ></div>
          <div
            className="relative box-border text-center"
            style={{
              width: '400px',
              padding: '28px',
              borderRadius: '20px',
              background: 'rgba(22,12,38,.96)',
              boxShadow: '0 20px 52px rgba(4,2,12,.55)',
              animation: 'rise .3s cubic-bezier(.2,.9,.3,1) both',
            }}
          >
            <div className="text-[17px] font-semibold tracking-[-.01em] text-white">
              ‘{deleteAt.name}’{josa(deleteAt.name)} 삭제할까요?
            </div>
            <div
              className="text-[13px]"
              style={{ marginTop: '8px', lineHeight: '1.6', color: 'rgba(255,236,246,.68)' }}
            >
              삭제하면 되돌릴 수 없습니다. 노드는 그대로 남고, 지난 실행 기록도 사라지지 않습니다.
            </div>
            {/* 실패를 조용히 넘기지 않는다. 토스트가 없어 버튼 위에 한 줄로 둔다. */}
            {deleteMessage && (
              <div
                className="text-[14px] tracking-[-.01em]"
                style={{
                  marginTop: '14px',
                  color: 'rgba(255,196,214,.9)',
                  animation: 'fadeIn .2s ease both',
                }}
              >
                {deleteMessage}
              </div>
            )}
            <div className="flex" style={{ marginTop: '20px', gap: '10px' }}>
              <div
                onClick={handleCloseDelete}
                className={`${CHIP} bg-surface-card flex-1 justify-center text-[14px] font-semibold text-white hover:bg-[rgba(255,255,255,.16)]`}
                style={{ height: '46px', borderRadius: '12px' }}
              >
                취소
              </div>
              <div
                onClick={handleConfirmDelete}
                className={`${CHIP} flex-1 justify-center text-[14px] font-bold hover:brightness-[1.12]`}
                style={{
                  height: '46px',
                  borderRadius: '12px',
                  background: 'rgba(233,174,196,.24)',
                  color: '#ffd7e6',
                  opacity: remove.isPending ? 0.6 : 1,
                  cursor: remove.isPending ? 'default' : 'pointer',
                }}
              >
                {remove.isPending ? '삭제 중…' : '삭제'}
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

/** 지표 문자열을 밖에서 받는다 — 안에서 탭으로 가르면 같은 숫자에 다른 이름이 붙는다. */
function AgentRow({
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

/**
 * 워크플로우 한 장. 행을 누르면 캔버스를 복원한다.
 * 「실행하기」도 지금은 캔버스로 보낸다 — 실행 생성 API 가 오면 그 요청으로 바꾼다.
 */
function WorkflowCard({
  name,
  summary,
  executionCount,
  lastExecutedAt,
  href,
  delay,
  onDelete,
}: {
  name: string
  summary: string
  executionCount: number
  lastExecutedAt: string | null
  href: string
  delay: number
  onDelete: () => void
}) {
  const handleDelete = (e: React.MouseEvent) => {
    // 카드 전체가 링크다. 삭제를 누를 때 캔버스로 이동하면 모달이 사라진다.
    e.preventDefault()
    e.stopPropagation()
    onDelete()
  }

  return (
    <a
      href={href}
      className="bg-surface-card box-border block flex-none transition-colors duration-200 hover:bg-[rgba(255,255,255,.13)]"
      style={{
        width: '896px',
        padding: '22px 24px',
        borderRadius: '18px',
        animation: `rise .6s ${(0.06 + delay * 0.05).toFixed(2)}s cubic-bezier(.2,.8,.25,1) both`,
      }}
    >
      <div className="flex items-center" style={{ gap: '11px' }}>
        <div className="text-[16px] font-semibold tracking-[-.01em] text-white">{name}</div>
        <div className="flex" style={{ marginLeft: 'auto', gap: '8px' }}>
          <div
            className={`${CHIP} text-[13.5px] font-semibold tracking-[-.01em] hover:brightness-[1.08]`}
            style={{
              height: '34px',
              padding: '0 16px',
              borderRadius: '17px',
              background: 'var(--color-accent)',
              color: 'var(--color-accent-ink)',
            }}
          >
            실행하기
          </div>
          <div
            onClick={handleDelete}
            className={`${CHIP} text-[13.5px] font-semibold tracking-[-.01em] hover:bg-[rgba(233,174,196,.24)] hover:text-[#ffd7e6]`}
            style={{
              height: '34px',
              padding: '0 14px',
              borderRadius: '17px',
              background: 'rgba(255,255,255,.09)',
              color: 'rgba(255,240,247,.72)',
            }}
          >
            삭제
          </div>
        </div>
      </div>

      <div
        className="text-[14px] tracking-[-.01em]"
        style={{ marginTop: '9px', color: 'rgba(255,236,246,.62)' }}
      >
        {summary}
      </div>

      <div
        className="flex text-[13px]"
        style={{
          marginTop: '14px',
          gap: '20px',
          color: 'rgba(255,240,247,.55)',
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        <div>실행 {runLabel(executionCount)}</div>
        <div>마지막 실행 {timeLabel(lastExecutedAt)}</div>
      </div>
    </a>
  )
}
