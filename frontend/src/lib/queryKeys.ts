/**
 * 키 구조를 API 경로와 일치시킨다. `all` 은 배열, 그 아래는 함수 —
 * 목록은 `lists()` → `list(params)`, 상세는 `details()` → `detail(id)`.
 */

/** 서버가 받는 query parameter 를 그대로 담는다. */
export type ListParams = Record<string, string | number | boolean | undefined>

/** `all` 은 배열이라 즉시 평가된다 — 그 시점의 `queryKeys` 는 TDZ 라 루트만 밖에 둔다. */
const MARKETPLACE = ['marketplace'] as const
const USERS_ME = ['users', 'me'] as const

export const queryKeys = {
  /** /auth/*, /users/me */
  auth: {
    all: ['auth'] as const,
    session: () => [...queryKeys.auth.all, 'session'] as const,
    verification: () => [...queryKeys.auth.all, 'verification'] as const,
  },

  /** /marketplace/agents */
  marketplace: {
    all: MARKETPLACE,
    agents: {
      all: [...MARKETPLACE, 'agents'] as const,
      lists: () => [...queryKeys.marketplace.agents.all, 'list'] as const,
      list: (params: ListParams) => [...queryKeys.marketplace.agents.lists(), params] as const,
      details: () => [...queryKeys.marketplace.agents.all, 'detail'] as const,
      detail: (agentId: string) => [...queryKeys.marketplace.agents.details(), agentId] as const,
      /** 자연어 추천. 목록과 키를 나눈다 — 커서도 정렬도 없는 다른 조회다. */
      recommendations: (params: ListParams) =>
        [...queryKeys.marketplace.agents.all, 'recommendations', params] as const,
      provenance: (agentId: string) =>
        [...queryKeys.marketplace.agents.detail(agentId), 'provenance'] as const,
      reviews: {
        lists: (agentId: string) =>
          [...queryKeys.marketplace.agents.detail(agentId), 'reviews'] as const,
        list: (agentId: string, params: ListParams) =>
          [...queryKeys.marketplace.agents.reviews.lists(agentId), params] as const,
      },
    },
  },

  /** /agents */
  agents: {
    all: ['agents'] as const,
    lists: () => [...queryKeys.agents.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.agents.lists(), params] as const,
    details: () => [...queryKeys.agents.all, 'detail'] as const,
    detail: (agentId: string) => [...queryKeys.agents.details(), agentId] as const,
  },

  /** `auth` 와 경로가 겹치지만 키를 합치면 세션 무효화가 목록까지 날린다. */
  users: {
    all: USERS_ME,
    favorites: {
      lists: () => [...queryKeys.users.all, 'favorites'] as const,
      list: (params: ListParams) => [...queryKeys.users.favorites.lists(), params] as const,
    },
    usedAgents: {
      lists: () => [...queryKeys.users.all, 'used-agents'] as const,
      list: (params: ListParams) => [...queryKeys.users.usedAgents.lists(), params] as const,
    },
  },

  /** /workflows */
  workflows: {
    all: ['workflows'] as const,
    lists: () => [...queryKeys.workflows.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.workflows.lists(), params] as const,
    details: () => [...queryKeys.workflows.all, 'detail'] as const,
    detail: (workflowId: string) => [...queryKeys.workflows.details(), workflowId] as const,
    /** 캔버스에 놓을 수 있는 항목 */
    nodePalette: {
      lists: () => [...queryKeys.workflows.all, 'node-palette'] as const,
      list: (params: ListParams) => [...queryKeys.workflows.nodePalette.lists(), params] as const,
    },
  },

  /** /executions */
  executions: {
    all: ['executions'] as const,
    lists: () => [...queryKeys.executions.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.executions.lists(), params] as const,
    details: () => [...queryKeys.executions.all, 'detail'] as const,
    detail: (executionId: string) => [...queryKeys.executions.details(), executionId] as const,
    nodeLogs: (executionId: string, nodeId: string) =>
      [...queryKeys.executions.detail(executionId), 'nodes', nodeId, 'logs'] as const,
    /** 실행 전 견적. 워크플로우가 기준이라 실행 id 아래가 아니다. */
    costEstimate: (workflowId: string) =>
      [...queryKeys.executions.all, 'cost-estimate', workflowId] as const,
  },

  /** /credits */
  credits: {
    all: ['credits'] as const,
    balance: () => [...queryKeys.credits.all, 'balance'] as const,
    transactions: {
      lists: () => [...queryKeys.credits.all, 'transactions'] as const,
      list: (params: ListParams) => [...queryKeys.credits.transactions.lists(), params] as const,
    },
    charge: (chargeId: string) => [...queryKeys.credits.all, 'charges', chargeId] as const,
  },

  /** /settlements */
  settlements: {
    all: ['settlements'] as const,
    balance: () => [...queryKeys.settlements.all, 'balance'] as const,
    transactions: {
      lists: () => [...queryKeys.settlements.all, 'transactions'] as const,
      list: (params: ListParams) =>
        [...queryKeys.settlements.transactions.lists(), params] as const,
    },
    statistics: {
      lists: () => [...queryKeys.settlements.all, 'statistics'] as const,
      list: (params: ListParams) => [...queryKeys.settlements.statistics.lists(), params] as const,
    },
  },

  /** /withdrawals */
  withdrawals: {
    all: ['withdrawals'] as const,
    lists: () => [...queryKeys.withdrawals.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.withdrawals.lists(), params] as const,
    details: () => [...queryKeys.withdrawals.all, 'detail'] as const,
    detail: (withdrawalId: string) => [...queryKeys.withdrawals.details(), withdrawalId] as const,
  },
} as const
