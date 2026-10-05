import { PANEL_CONTENT_WIDTH, PANEL_INSET } from '@/components/SidePanel'

/** 패널 폭을 꽉 채우는 한 줄. 좌표는 탭 아래 스크롤 영역 기준이다. */
export const rowStyle = (top: string) => ({
  left: `${PANEL_INSET}px`,
  top,
  width: `${PANEL_CONTENT_WIDTH}px`,
})

export const labelStyle = (top: string) => ({
  left: `${PANEL_INSET}px`,
  top,
  color: 'rgba(255,255,255,.92)',
})
