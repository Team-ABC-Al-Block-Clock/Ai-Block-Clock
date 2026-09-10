/**
 * 라우트 패턴의 단일 소스. App.tsx 의 <Route>, lib/shell.ts 의 껍데기 설정,
 * 화면의 링크 주소가 모두 여기서 파생된다.
 * 규칙은 docs/frontend-conventions.md 4절.
 */

/** ID 를 받는 화면 중 mock 핸들러가 아직 없는 것은 `[ID 없는 경로, ID 있는 경로]` 순으로 둔다.
 *  넘길 ID 가 없어서다. 핸들러가 붙으면 앞쪽 경로를 지운다 — `agentDetail` 이 그렇게 했다. */
export const routePatterns = {
  home: ['/'],
  login: ['/login'],
  signup: ['/signup'],
  register: ['/register'],

  /** 마켓 목록. GET /api/v1/marketplace/agents */
  search: ['/search'],
  /** 마켓 상세. GET /api/v1/marketplace/agents/{agentId} — 목록·마이페이지·등록 완료
   *  화면이 모두 실제 ID 를 넘기므로 ID 없는 경로를 지웠다. */
  agentDetail: ['/agent/:agentId'],

  /** 노드 팔레트. GET /api/v1/workflows/node-palette */
  editSearching: ['/edit/searching'],
  /** ID 없으면 신규 생성(POST /workflows), 있으면 편집(PUT /workflows/{id}) */
  editNodes: ['/edit/nodes', '/edit/nodes/:workflowId'],
  editNodesSeller: ['/edit/nodes-seller', '/edit/nodes-seller/:workflowId'],

  /** GET /api/v1/executions/{executionId} */
  runWaiting: ['/run/waiting', '/run/:executionId/waiting'],
  /** GET /api/v1/executions/{executionId}/events (SSE) */
  runRunning: ['/run/running', '/run/:executionId/running'],

  /** GET /api/v1/agents */
  myAgents: ['/my/agents'],
  /** GET·PATCH /api/v1/users/me */
  myEdit: ['/my/edit'],
  /** GET /api/v1/credits/transactions */
  myUsage: ['/my/usage'],
  /** GET /api/v1/settlements/*, /api/v1/withdrawals */
  mySales: ['/my/sales'],
  /** GET /api/v1/credits/balance */
  myCredit: ['/my/credit'],
} as const satisfies Record<string, readonly string[]>

export type ScreenKey = keyof typeof routePatterns

/** `:param` 자리를 채운 경로를 만든다. ID 는 필수다.
 *  아직 ID 가 없는 화면은 `.withoutId` 를 쓴다 — 인자를 빠뜨렸을 때 조용히 옛 경로로
 *  착지하지 않도록 둘을 갈라 둔다. */
/** ID 없는 경로가 사라진 화면. mock 핸들러가 붙어 넘길 ID 가 언제나 있다.
 *  나머지 도메인도 핸들러가 붙으면 이쪽으로 옮기고 idPath 를 지운다. */
function requiredIdPath([withId]: readonly [string]) {
  return (id: string) => withId.replace(/:[^/]+/, id)
}

function idPath([plain, withId]: readonly [string, string]) {
  const build = (id: string) => withId.replace(/:[^/]+/, id)
  /** 해당 도메인 mock 핸들러가 붙기 전까지만 쓰는 임시 경로.
   *  routePatterns 의 앞쪽 패턴과 같이 지우면 남은 호출부가 전부 컴파일 에러가 된다. */
  build.withoutId = plain
  return build
}

/** 화면에서 쓰는 링크 주소. 경로 문자열을 컴포넌트에 직접 쓰지 않는다. */
export const paths = {
  home: routePatterns.home[0],
  login: routePatterns.login[0],
  signup: routePatterns.signup[0],
  register: routePatterns.register[0],

  search: routePatterns.search[0],
  agentDetail: requiredIdPath(routePatterns.agentDetail),

  edit: {
    searching: routePatterns.editSearching[0],
    nodes: idPath(routePatterns.editNodes),
    nodesSeller: idPath(routePatterns.editNodesSeller),
  },

  run: {
    waiting: idPath(routePatterns.runWaiting),
    running: idPath(routePatterns.runRunning),
  },

  my: {
    agents: routePatterns.myAgents[0],
    edit: routePatterns.myEdit[0],
    usage: routePatterns.myUsage[0],
    sales: routePatterns.mySales[0],
    credit: routePatterns.myCredit[0],
  },
} as const
