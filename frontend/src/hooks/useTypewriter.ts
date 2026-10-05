import { useEffect, useRef } from 'react'

const TICK_MS = 55
const CARET_LINGER_MS = 900

/** 제목을 한 글자씩 친다. 글자마다 리렌더하지 않도록 state 대신 ref 로 DOM 을 바로 고친다. */
export function useTypewriter(text: string, startDelayMs: number) {
  const textRef = useRef<HTMLSpanElement>(null)
  const caretRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    let typing: ReturnType<typeof setInterval> | undefined
    let hideCaret: ReturnType<typeof setTimeout> | undefined
    const start = setTimeout(() => {
      const t0 = performance.now()
      typing = setInterval(() => {
        const i = Math.min(text.length, Math.floor((performance.now() - t0) / TICK_MS))
        if (textRef.current) textRef.current.textContent = text.slice(0, i)
        if (i < text.length) return
        clearInterval(typing)
        hideCaret = setTimeout(() => {
          if (caretRef.current) caretRef.current.style.display = 'none'
        }, CARET_LINGER_MS)
      }, TICK_MS)
    }, startDelayMs)

    return () => {
      clearTimeout(start)
      clearInterval(typing)
      clearTimeout(hideCaret)
    }
  }, [text, startDelayMs])

  return { textRef, caretRef }
}
