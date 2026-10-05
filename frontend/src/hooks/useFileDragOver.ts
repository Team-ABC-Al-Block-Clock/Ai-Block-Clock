import { useEffect, useState } from 'react'

/** 창 위로 파일을 끌고 있는 동안 true. 글이나 링크를 끄는 것은 무시한다. */
export function useFileDragOver() {
  const [dragging, setDragging] = useState(false)

  useEffect(() => {
    // 자식 요소를 지날 때마다 enter·leave 가 짝으로 오므로 깊이를 세어 진짜로 나간 순간만 잡는다
    let depth = 0
    const onEnter = (e: DragEvent) => {
      if (!e.dataTransfer?.types.includes('Files')) return
      e.preventDefault()
      depth++
      setDragging(true)
    }
    // dragover 를 막지 않으면 브라우저가 drop 을 쏘지 않는다
    const onOver = (e: DragEvent) => {
      if (depth) e.preventDefault()
    }
    const onLeave = () => {
      depth = Math.max(0, depth - 1)
      if (!depth) setDragging(false)
    }
    const onDrop = (e: DragEvent) => {
      e.preventDefault()
      depth = 0
      setDragging(false)
    }
    window.addEventListener('dragenter', onEnter)
    window.addEventListener('dragover', onOver)
    window.addEventListener('dragleave', onLeave)
    window.addEventListener('drop', onDrop)
    return () => {
      window.removeEventListener('dragenter', onEnter)
      window.removeEventListener('dragover', onOver)
      window.removeEventListener('dragleave', onLeave)
      window.removeEventListener('drop', onDrop)
    }
  }, [])

  return dragging
}
