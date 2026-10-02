export type Mode = 'charge' | 'withdraw' | 'convert'

export const MODES: { key: Mode; label: string }[] = [
  { key: 'charge', label: '충전' },
  { key: 'withdraw', label: '출금' },
  { key: 'convert', label: 'ABC 전환' },
]

/** 처음 고른 칸. 탭을 바꿀 때도 여기로 돌아간다. */
export const DEFAULT_PICK = 2

/** 탭마다 바뀌는 고정 문구. */
export const MODE_TEXT = {
  charge: {
    bannerLabel: '현재 사용 가능 금액',
    pickerTitle: '충전 금액 선택',
    placeholder: '충전할 금액을 입력하세요',
    summaryTitle: '충전 요약',
    sumLabel: '충전 금액',
    ctaLabel: '충전하기',
  },
  withdraw: {
    bannerLabel: '출금 가능 정산금',
    pickerTitle: '출금 금액 선택',
    placeholder: '출금할 정산금을 입력하세요',
    summaryTitle: '출금 요약',
    sumLabel: '출금 정산금',
    ctaLabel: '출금 신청',
  },
  convert: {
    bannerLabel: '전환 가능 정산금',
    pickerTitle: '전환 금액 선택',
    placeholder: '전환할 정산금을 입력하세요',
    summaryTitle: '전환 요약',
    sumLabel: '전환 정산금',
    ctaLabel: 'ABC로 전환하기',
  },
} as const satisfies Record<Mode, Record<string, string>>

/** 요약 카드와 진행 카드가 함께 쓰는 강조 버튼. */
export const ACCENT_BUTTON = {
  borderRadius: '10px',
  background: 'var(--color-accent)',
  color: 'var(--color-accent-ink)',
  transition: 'filter .16s',
} as const
