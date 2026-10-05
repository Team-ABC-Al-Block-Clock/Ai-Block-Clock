import { paths } from '@/routes/paths'

export const FULL_TITLE = '어떤 일을 맡겨볼까요?'
/** 명세 기본값 */
export const PAGE_SIZE = 9
/** 입력이 멈춘 뒤에만 검색한다. 없으면 타자 한 번에 요청 한 번이다. */
export const DEBOUNCE_MS = 300

/** 빈 상태는 실패가 아니라 다음 행동을 고르는 자리다 — 행동을 최소 하나 준다. */
export const EMPTY_ACTIONS = JSON.stringify([
  { label: '직접 만들어보기', href: paths.home, primary: true },
  { label: '검색어 지우기', event: 'clear' },
])

export const EMPTY_BODY =
  '찾는 일을 다르게 적어보거나, 프롬프트로 에이전트를 직접 만들어 보세요.\n ' +
  '여러 에이전트를 이어 붙이는 일도 프롬프트 한 줄로 됩니다.'

/** 실패 안내는 그리드 안쪽 폭(272~1168)에 맞춘다. */
export const NOTICE_BOX = { left: '272px', top: '470px', width: '896px' } as const

/** 272 + (896 - 620) / 2 = 410. translateX 로 중앙 정렬하지 않는다 — transform 이
 *  걸리면 backdrop 루트가 되어 카드가 바깥 배경을 못 가져온다. */
export const EMPTY_BOX = { left: '410px', top: '486px', width: '620px' } as const

/** 설명이 서버 상한(80자)이면 이 폭에서 다섯 줄이다. 다 들어가는 높이로 둔다. */
export const CARD_HEIGHT = 240
