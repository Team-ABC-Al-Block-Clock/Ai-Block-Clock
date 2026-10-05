import { RECOMMEND_LIMITS } from '@/api/agents'

/** 한 번에 받는 개수. 상한까지 받는다 — 선택할 것이 많을수록 낫고, 서버도 한 번에 잘라 준다. */
export const SIZE = RECOMMEND_LIMITS.size.max

/**
 * 「검색 중」을 최소한 이만큼은 보여준다.
 *
 * 추천은 임베딩 검색이라 수십 ms 에 끝난다. 그대로 두면 안내가 한 프레임 번쩍이고 사라져
 * 화면이 고장 난 것처럼 보인다. 반대로 더 오래 붙들지는 않는다 — 없는 대기를 만드는 것이다.
 */
export const MIN_SEARCHING_MS = 700

/** 미리보기 카드의 등장 모션. 순서에 따라 달라지면 하나를 뺄 때 남은 카드가 전부 다시 나타난다. */
export const PREVIEW_ENTER = 'uiIn .5s cubic-bezier(.2,.8,.25,1) backwards'

/** 작업면 한가운데. 안내와 검색 중 표시가 같은 축에 선다. */
export const PANE_TEXT_WIDTH = 980

/** 고른 순서대로 한 줄로 잇는다. 서버 깊이 한도(`WORKFLOW_007`)를 넘지 않게 자른다. */
export const MAX_PICKED = 10
