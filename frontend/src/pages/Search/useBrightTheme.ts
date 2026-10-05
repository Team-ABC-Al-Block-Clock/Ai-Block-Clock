import { useEffect, useState } from 'react'

import { OceanTheme } from '@/stores/themeStore'

export function useBrightTheme() {
  // OceanTheme 는 프레임워크 밖의 상태 — subscribe 로 받아 리렌더를 유발한다
  const [bright, setBright] = useState(OceanTheme.isBright)
  useEffect(() => OceanTheme.subscribe(() => setBright(OceanTheme.isBright())), [])
  return bright
}
