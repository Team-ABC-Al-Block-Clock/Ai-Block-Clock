/**
 * 라우트 패턴의 단일 소스. `App.tsx` 의 <Route>, `lib/shell.ts` 의 껍데기 설정,
 * 화면의 링크 주소가 모두 여기서 파생된다.
 */

/** 핸들러가 아직 없어 넘길 ID 가 없는 화면만 `[ID 없는 경로, ID 있는 경로]` 순으로 둔다. */
export const routePatterns = {
  home: ['/'],
  login: ['/login'],
  signup: ['/signup'],
  register: ['/register'],

  /** 마켓 목록 */
  search: ['/search'],
  /** 마켓 상세. 넘어오는 화면이 모두 실제 ID 를 주므로 ID 없는 경로를 지웠다 */
  agentDetail: ['/agent/:agentId'],

  /** 프롬프트로 에이전트를 찾는 전환 화면 */
  editSearching: ['/edit/searching'],
  /** ID 없으면 신규 생성, 있으면 편집. 팔레트는 scope=ALL 로 부른다 */
  editNodes: ['/edit/nodes', '/edit/nodes/:workflowId'],
  /** 판매자 흐름. 내 것만 놓을 수 있어 팔레트를 scope=MINE 으로 부른다 */
  editNodesSeller: ['/edit/nodes-seller', '/edit/nodes-seller/:workflowId'],

  runWaiting: ['/run/waiting', '/run/:executionId/waiting'],
  runRunning: ['/run/running', '/run/:executionId/running'],

  myAgents: ['/my/agents'],
  myEdit: ['/my/edit'],
  myUsage: ['/my/usage'],
  mySales: ['/my/sales'],
  myCredit: ['/my/credit'],
} as const satisfies Record<string, readonly string[]>

export type ScreenKey = keyof typeof routePatterns

/**
 * ID 는 필수다. 아직 ID 가 없는 화면만 `.withoutId` 를 쓴다 — 인자를 빠뜨렸을 때
 * 조용히 옛 경로로 착지하지 않도록 둘을 갈라 둔다.
 */

/** 핸들러가 붙어 넘길 ID 가 언제나 있는 화면. 나머지도 붙으면 이쪽으로 옮긴다. */
function requiredIdPath([withId]: readonly [string]) {
  return (id: string) => withId.replace(/:[^/]+/, id)
}

function idPath([plain, withId]: readonly [string, string]) {
  const build = (id: string) => withId.replace(/:[^/]+/, id)
  /** 핸들러가 붙기 전까지만 쓰는 임시 경로 */
  build.withoutId = plain
  return build
}

/** 경로 문자열을 컴포넌트에 직접 쓰지 않는다. */
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
