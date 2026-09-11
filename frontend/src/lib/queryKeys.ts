/**
 * 쿼리 키 팩토리. 키 구조를 API 경로와 일치시킨다 —
 * `/api/v1/marketplace/agents` 는 `queryKeys.marketplace.agents`.
 *
 * `all` 은 배열, 그 아래는 함수. 목록은 예외 없이 `lists()` → `list(params)`,
 * 상세는 `details()` → `detail(id)`. 규칙은 docs/frontend-conventions.md 6절.
 */

/** 목록 조회 쿼리 파라미터. 서버가 받는 query parameter 를 그대로 담는다. */
export type ListParams = Record<string, string | number | boolean | undefined>

/** `marketplace.agents` 처럼 `all` 을 따로 갖는 하위 리소스가 파생해 쓴다.
 *  `all` 은 함수가 아니라 배열이라 즉시 평가되고, 그 시점의 `queryKeys` 는 아직 TDZ 라
 *  자기 참조가 불가능하다 — 루트만 밖에 둔다. */
const MARKETPLACE = ['marketplace'] as const
/** `/users/me/*` 하위 목록들이 파생해 쓴다. `all` 이 배열이라 즉시 평가되므로 루트만 밖에 둔다. */
const USERS_ME = ['users', 'me'] as const

export const queryKeys = {
  /** 인증. /api/v1/auth/*, /api/v1/users/me */
  auth: {
    all: ['auth'] as const,
    /** GET /users/me */
    session: () => [...queryKeys.auth.all, 'session'] as const,
    /** GET /users/me/verification */
    verification: () => [...queryKeys.auth.all, 'verification'] as const,
  },

  /** 마켓플레이스. /api/v1/marketplace/agents */
  marketplace: {
    all: MARKETPLACE,
    agents: {
      all: [...MARKETPLACE, 'agents'] as const,
      lists: () => [...queryKeys.marketplace.agents.all, 'list'] as const,
      list: (params: ListParams) => [...queryKeys.marketplace.agents.lists(), params] as const,
      details: () => [...queryKeys.marketplace.agents.all, 'detail'] as const,
      detail: (agentId: string) => [...queryKeys.marketplace.agents.details(), agentId] as const,
      /** GET /marketplace/agents/{agentId}/provenance */
      provenance: (agentId: string) =>
        [...queryKeys.marketplace.agents.detail(agentId), 'provenance'] as const,
      /** GET /marketplace/agents/{agentId}/reviews */
      reviews: {
        lists: (agentId: string) =>
          [...queryKeys.marketplace.agents.detail(agentId), 'reviews'] as const,
        list: (agentId: string, params: ListParams) =>
          [...queryKeys.marketplace.agents.reviews.lists(agentId), params] as const,
      },
    },
  },

  /** 내가 등록한 에이전트. /api/v1/agents */
  agents: {
    all: ['agents'] as const,
    lists: () => [...queryKeys.agents.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.agents.lists(), params] as const,
    details: () => [...queryKeys.agents.all, 'detail'] as const,
    detail: (agentId: string) => [...queryKeys.agents.details(), agentId] as const,
  },

  /** 내 계정 하위 목록. /api/v1/users/me/*
   *
   *  `auth` 와 경로가 겹치지만 성격이 다르다 — `auth.session()` 은 로그인 상태이고,
   *  이쪽은 내가 가진 리소스 목록이다. 키를 합치면 세션 무효화가 목록까지 날린다. */
  users: {
    all: USERS_ME,
    /** GET /users/me/favorites */
    favorites: {
      lists: () => [...queryKeys.users.all, 'favorites'] as const,
      list: (params: ListParams) => [...queryKeys.users.favorites.lists(), params] as const,
    },
    /** GET /users/me/used-agents */
    usedAgents: {
      lists: () => [...queryKeys.users.all, 'used-agents'] as const,
      list: (params: ListParams) => [...queryKeys.users.usedAgents.lists(), params] as const,
    },
  },

  /** 워크플로우. /api/v1/workflows */
  workflows: {
    all: ['workflows'] as const,
    lists: () => [...queryKeys.workflows.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.workflows.lists(), params] as const,
    details: () => [...queryKeys.workflows.all, 'detail'] as const,
    detail: (workflowId: string) => [...queryKeys.workflows.details(), workflowId] as const,
    /** GET /workflows/node-palette */
    nodePalette: () => [...queryKeys.workflows.all, 'node-palette'] as const,
  },

  /** 실행. /api/v1/executions */
  executions: {
    all: ['executions'] as const,
    lists: () => [...queryKeys.executions.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.executions.lists(), params] as const,
    details: () => [...queryKeys.executions.all, 'detail'] as const,
    detail: (executionId: string) => [...queryKeys.executions.details(), executionId] as const,
    /** GET /executions/{executionId}/nodes/{nodeId}/logs */
    nodeLogs: (executionId: string, nodeId: string) =>
      [...queryKeys.executions.detail(executionId), 'nodes', nodeId, 'logs'] as const,
  },

  /** ABC 크레딧. /api/v1/credits */
  credits: {
    all: ['credits'] as const,
    /** GET /credits/balance */
    balance: () => [...queryKeys.credits.all, 'balance'] as const,
    /** GET /credits/transactions */
    transactions: {
      lists: () => [...queryKeys.credits.all, 'transactions'] as const,
      list: (params: ListParams) => [...queryKeys.credits.transactions.lists(), params] as const,
    },
  },

  /** 정산금. /api/v1/settlements */
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

  /** 출금. /api/v1/withdrawals */
  withdrawals: {
    all: ['withdrawals'] as const,
    lists: () => [...queryKeys.withdrawals.all, 'list'] as const,
    list: (params: ListParams) => [...queryKeys.withdrawals.lists(), params] as const,
    details: () => [...queryKeys.withdrawals.all, 'detail'] as const,
    detail: (withdrawalId: string) => [...queryKeys.withdrawals.details(), withdrawalId] as const,
  },
} as const
