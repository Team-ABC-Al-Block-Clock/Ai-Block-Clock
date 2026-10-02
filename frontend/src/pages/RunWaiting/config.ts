import type { CSSProperties } from 'react'

import { REF_FIELD_HEIGHT } from '@/components/RefFileField'

/**
 * 판매자가 비워 두었거나, 놓인 에이전트가 없어 빌려 올 인사말이 없을 때 쓴다.
 * 서버 상한은 20자라 한 줄에 들어간다.
 */
export const DEFAULT_ONBOARDING_MESSAGE = '무엇을 도와드릴까요?'

/**
 * 입력 예시도 같은 규칙으로 떨어진다. 이 화면이 받는 것은 프롬프트와 엑셀·CSV 한 장뿐이라
 * (`RUN_FILE_LIMITS`), 폴더나 메일처럼 우리가 닿지 못하는 것을 시키는 예시를 두지 않는다.
 */
export const DEFAULT_PROMPT_EXAMPLE = '예: 올린 성적표로 반별 평균을 내고 가정통신문을 써 줘'

/**
 * placeholder 자리에 담기는 글자 수. 364×104px 에 16.5px 글꼴이라 세 줄 남짓이다.
 * placeholder 는 `text-overflow` 가 듣지 않아 넘치면 그냥 잘린다 — 여기서 먼저 줄인다.
 */
export const PROMPT_EXAMPLE_MAX = 60

/** 서버가 받는 코멘트 길이. 넘겨 보내면 `COMMON_001` 이라 입력칸에서 먼저 막는다. */
export const COMMENT_MAX = 500

/** 잔액 부족. 금액은 위 카드에 이미 있어 문구만 쓴다. */
export const INSUFFICIENT = 'PAYMENT_008'

/** 충전으로 가는 길. 견적이 막았을 때와 접수가 막혔을 때 같은 모양을 쓴다. */
export const CHARGE_LINK: CSSProperties = { color: '#d7bce8', textDecoration: 'underline' }

/**
 * 패널 세로 좌표. 파일 칸이 늘어난 만큼 아래가 통째로 내려가고, 맨 아래 단계 목록이
 * 그만큼 덜어 준다 — 바닥(976)은 움직이지 않는다.
 */
export const FILE_Y = 292
export const COST_Y = FILE_Y + REF_FIELD_HEIGHT + 16
export const RUN_BUTTON_Y = COST_Y + 210
export const NOTICE_Y = RUN_BUTTON_Y + 66
export const STEPS_Y = RUN_BUTTON_Y + 140
/** 스무 개까지 늘어나는 목록이라 남는 세로를 전부 준다. */
export const STEPS_HEIGHT = 976 - STEPS_Y
