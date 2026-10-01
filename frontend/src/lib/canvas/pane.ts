/** 작업면 div 의 설계 폭. 창 좌표를 설계 좌표로 되돌릴 때 배율의 기준이 된다. */
export const PANE_WIDTH = 1076
/** 작업면 div 의 설계 높이. 창이 더 길면 `--edge-y` 로 넘겨 채운다. */
export const PANE_HEIGHT = 1024

/** 패널이 작업면을 덮기 시작하는 자리. 오른쪽 끝에서 이만큼 안쪽이다. */
const PANEL_OVERLAP = 96
export const PANEL_EDGE = `calc(${PANEL_OVERLAP}px - var(--edge, 0px))`
/** 패널에 가리지 않고 실제로 보이는 폭. */
export const PANE_CONTENT_WIDTH = PANE_WIDTH - PANEL_OVERLAP
/**
 * 패널 뒤로 넘어간 만큼만 가리는 클립.
 *
 * 패널은 칠하지 않아 뒤가 비친다. 오른쪽만 패널 앞에서 자르고 나머지 세 방향은
 * 넉넉히 열어 둔다 — 카드가 작업면 끝에 있어도 거기서 나가는 선이 잘리면 안 된다.
 */
export const PANEL_CLIP = `inset(-200% ${PANEL_EDGE} -200% -200%)`
