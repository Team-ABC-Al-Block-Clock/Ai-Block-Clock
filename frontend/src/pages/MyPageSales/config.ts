export const PAGE_SIZE = 20

export type SalesTab = 'entry' | 'agent'

export const TABS = [
  { key: 'entry', label: '건별' },
  { key: 'agent', label: '에이전트별' },
] as const satisfies readonly { key: SalesTab; label: string }[]

export const SHARE_COLORS = ['#e9aec4', '#dba18b', '#a094c2']

export interface Column {
  label: string
  w: string
}

export const AGENT_COLUMNS: Column[] = [
  { label: '에이전트', w: '380px' },
  { label: '호출 수', w: '160px' },
  { label: '받은 정산금', w: '200px' },
  { label: '비중', w: '120px' },
]

export const ENTRY_COLUMNS: Column[] = [
  { label: '일시', w: '190px' },
  { label: '에이전트', w: '370px' },
  { label: '정산금', w: '190px' },
  { label: '구분', w: '110px' },
]

/** 두 표 모두 셋째 칸이 금액이다. */
export const AMOUNT_AT = 2
