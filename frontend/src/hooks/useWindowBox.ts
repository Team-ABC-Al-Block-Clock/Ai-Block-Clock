import { useLayoutEffect, useState, type RefObject } from 'react'

/** 창 위에 직접 놓는 레이어의 크기 — 설계 단위로 환산해 돌려준다(3절).
 *
 *  배율은 `screen-frame` 의 `framefit` 에서 받는다. 같은 계산을 두 벌 두면
 *  리사이즈 중 한쪽만 갱신돼 껍데기 · 팝업이 화면과 어긋난다. */
export function useWindowBox(frame?: RefObject<HTMLElement | null>) {
  const [box, setBox] = useState({ scale: 1, width: 1440, height: 1024 })

  // 레이아웃 단계에서 재야 첫 프레임에 배율 1 로 한 번 커졌다 줄지 않는다.
  useLayoutEffect(() => {
    const el = (frame?.current ?? document.querySelector('screen-frame')) as
      (HTMLElement & { scale: number }) | null
    if (!el) return
    const sync = () => {
      const scale = el.scale || 1
      setBox({ scale, width: window.innerWidth / scale, height: window.innerHeight / scale })
    }
    sync()
    el.addEventListener('framefit', sync)
    return () => el.removeEventListener('framefit', sync)
  }, [frame])

  return box
}
