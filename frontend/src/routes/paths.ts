/**
 * 라우트 패턴의 단일 소스. `App.tsx` 의 <Route>, `lib/shell.ts` 의 껍데기 설정, 화면의 링크 주소 파생.
 */

/** 핸들러가 아직 없어 넘길 ID 가 없는 화면만 `[ID 없는 경로, ID 있는 경로]` 순. */
export const routePatterns = {
  home: ['/'],
  login: ['/login'],
  signup: ['/signup'],
  register: ['/register'],

  /** 마켓 목록 */
  search: ['/search'],
  /** 마켓 상세 */
  agentDetail: ['/agent/:agentId'],

  /** 프롬프트로 에이전트를 찾는 전환 화면 */
  editSearching: ['/edit/searching'],
  /** ID 없으면 신규 생성, 있으면 편집. */
  editNodes: ['/edit/nodes', '/edit/nodes/:workflowId'],

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
 * ID 필수. 아직 ID가 없으면 `.withoutId` 사용.
 */

/** 핸들러가 붙어 넘길 ID 가 항상 있는 화면. */
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
