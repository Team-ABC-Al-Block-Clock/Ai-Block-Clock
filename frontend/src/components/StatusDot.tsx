import type { CSSProperties } from 'react'

interface StatusDotProps {
  /** 지름(px). 화면마다 5~8 로 조금씩 다르다. */
  size?: number
  /** 점 색. 색 하나로만 알리지 않도록 늘 글자와 짝지어 쓴다. */
  color: string
  /** 깜빡임 같은 상태 표시. 없으면 멈춰 있다. */
  animation?: string
  /** 절대 배치가 필요한 자리에서 좌표를 얹는다. */
  style?: CSSProperties
}

/**
 * 상태를 알리는 작은 원. 줄 안에서 글자 옆에 붙는다.
 *
 * `flex-none` 을 늘 붙인다 — flex 줄에서는 줄어들지 않아야 하고, 밖에서는 아무 일도 하지 않는다.
 */
export default function StatusDot({ size = 7, color, animation, style }: StatusDotProps) {
  return (
    <div
      className="flex-none rounded-full"
      style={{
        width: `${size}px`,
        height: `${size}px`,
        background: color,
        animation,
        ...style,
      }}
    />
  )
}
