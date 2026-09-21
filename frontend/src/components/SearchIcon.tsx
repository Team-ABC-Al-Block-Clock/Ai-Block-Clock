interface SearchIconProps {
  /** 감싸는 상자 안에서의 원 좌표(px). */
  left: number
  top: number
  /** 원의 지름(px). */
  size?: number
  color: string
  /** 손잡이 길이(px). 기본은 지름의 절반. */
  handleLength?: number
}

/**
 * 검색칸의 돋보기. 원 하나와 45도로 뻗는 막대 하나로 그린다.
 *
 * 장식이라 클릭을 흘려보낸다 — 상자 어디를 눌러도 뒤의 input 으로 들어가야 한다.
 */
export default function SearchIcon({ left, top, size = 14, color, handleLength }: SearchIconProps) {
  // 손잡이는 원의 오른쪽 아래에서 시작한다. 지름에 비례해 따라간다.
  const handleLeft = left + Math.round(size * 0.79)
  const handleTop = top + Math.round(size * 0.855)

  return (
    <>
      <div
        className="pointer-events-none absolute rounded-full"
        style={{
          left: `${left}px`,
          top: `${top}px`,
          width: `${size}px`,
          height: `${size}px`,
          // 안쪽 그림자로 테두리를 그린다 — border 와 달리 상자 크기를 건드리지 않는다.
          boxShadow: `inset 0 0 0 2px ${color}`,
        }}
      ></div>
      <div
        className="pointer-events-none absolute"
        style={{
          left: `${handleLeft}px`,
          top: `${handleTop}px`,
          width: `${handleLength ?? Math.round(size / 2)}px`,
          height: '2px',
          borderRadius: '1px',
          background: color,
          transform: 'rotate(45deg)',
        }}
      ></div>
    </>
  )
}
