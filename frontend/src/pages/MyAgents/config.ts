import type { MyAgentSort } from '@/api/agents'
import type { WorkflowSort } from '@/api/workflows'
import { paths } from '@/routes/paths'

/** 마켓 목록(9)과 다르다. */
export const PAGE_SIZE = 10

export type Tab = 'selling' | 'favorites' | 'flows'

export const TABS = [
  { key: 'selling', label: '판매 중' },
  { key: 'favorites', label: '즐겨찾기' },
  { key: 'flows', label: '워크플로우' },
] as const satisfies readonly { key: Tab; label: string }[]

/**
 * 탭마다 정렬 옵션이 다르다. 즐겨찾기는 `sort` 가 없어 빈 배열이고 필을 감춘다.
 * 값은 `api/` 의 유니온에서 온다 — 화면에 새로 적으면 서버가 안 받는 값이 나간다.
 */
export const SORTS = {
  selling: ['LATEST', 'EXECUTIONS', 'REVENUE'],
  favorites: [],
  flows: ['LAST_EXECUTED', 'LATEST'],
} as const satisfies Record<Tab, readonly string[]>

export const SORT_LABEL = {
  LATEST: '최신 순',
  EXECUTIONS: '실행 많은 순',
  REVENUE: '수익 높은 순',
  LAST_EXECUTED: '최근 실행 순',
} as const satisfies Record<MyAgentSort | WorkflowSort, string>

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다 — 행동을 최소 하나 준다. */
export const EMPTY = {
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
export const DELETE_MESSAGE: Record<string, string> = {
  WORKFLOW_002: '이미 삭제된 워크플로우입니다',
  WORKFLOW_003: '내가 만든 워크플로우만 삭제할 수 있습니다',
  WORKFLOW_009: '에이전트로 등록된 워크플로우는 삭제할 수 없습니다',
  WORKFLOW_010: '실행이 진행 중입니다. 끝난 뒤에 삭제해 주세요',
}

export const CHIP = 'flex cursor-pointer items-center transition-colors duration-[160ms]'

/** 탭마다 라벨과 값만 다르고 폭은 같다 — 흔들리면 탭을 옮길 때 행이 들썩인다. */
export interface Row {
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
