import type { ReactNode } from 'react'

/** 마이페이지 다섯 화면을 1440 안에서 가운데로 옮긴다. transform 을 쓰면 안의 유리 패널이 죽어 위치로 민다. */
export default function MyPageFrame({ children }: { children: ReactNode }) {
  return (
    <div
      className="absolute"
      style={{ left: '18px', top: '-17px', width: '1440px', height: '1024px' }}
    >
      {children}
    </div>
  )
}
