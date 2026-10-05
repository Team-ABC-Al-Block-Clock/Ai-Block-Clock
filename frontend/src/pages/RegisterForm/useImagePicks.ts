import {
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type Dispatch,
  type SetStateAction,
} from 'react'

import { describeImageProblem } from '@/lib/imageFile'

export type Picked = { file: File; url: string }

/** 스크린샷 줄 하나의 칸 수. */
export const SHOT_LIMIT = 5

/** 화면이 길어 문제를 고른 자리 옆에 띄운다. */
export type ImageProblem = { where: 'icon' | 'in' | 'out'; text: string }

/**
 * 아이콘과 입력 · 출력 스크린샷 고르기. 미리보기 URL 을 만들고 치운다.
 *
 * 고른 그림은 아직 서버로 가지 않는다 — 파일을 받는 자리가 없다(`api/agents.ts`).
 */
export function useImagePicks() {
  const [inShots, setInShots] = useState<Picked[]>([])
  const [outShots, setOutShots] = useState<Picked[]>([])
  const [icon, setIcon] = useState<Picked | null>(null)
  const [problem, setProblem] = useState<ImageProblem | null>(null)

  const previewsRef = useRef<string[]>([])
  useEffect(() => {
    previewsRef.current = [...inShots, ...outShots, ...(icon ? [icon] : [])].map((p) => p.url)
  }, [inShots, outShots, icon])
  // 언마운트에서만 해제한다 — 목록을 의존성에 넣으면 한 장 더할 때 앞 장들의 URL 이 함께 풀린다.
  useEffect(() => () => previewsRef.current.forEach((url) => URL.revokeObjectURL(url)), [])

  const pickShots =
    (where: 'in' | 'out', list: Picked[], setter: Dispatch<SetStateAction<Picked[]>>) =>
    (ev: ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(ev.target.files ?? [])
      ev.target.value = ''
      // 한 장이 어긋났다고 나머지까지 버리지 않는다.
      const good = files.filter((file) => !describeImageProblem(file))
      const room = SHOT_LIMIT - list.length
      const text =
        files.map((file) => describeImageProblem(file)).find(Boolean) ??
        (good.length > room ? `스크린샷은 ${SHOT_LIMIT}장까지 올릴 수 있습니다` : null)
      setProblem(text ? { where, text } : null)
      // 칸이 남은 만큼만 만든다 — 잘라낸 것의 URL 은 해제할 곳이 없다.
      const picked = good.slice(0, room).map((file) => ({ file, url: URL.createObjectURL(file) }))
      if (picked.length) setter((prev) => [...prev, ...picked])
    }

  /** 잘못 고른 것을 무를 길. 등록은 되돌릴 수 없어 보내기 전에 지울 수 있어야 한다. */
  const dropShot =
    (list: Picked[], setter: Dispatch<SetStateAction<Picked[]>>) => (index: number) => {
      const gone = list[index]
      if (gone) URL.revokeObjectURL(gone.url)
      setter((prev) => prev.filter((_, i) => i !== index))
      setProblem(null)
    }

  const pickIcon = (ev: ChangeEvent<HTMLInputElement>) => {
    const file = ev.target.files?.[0]
    ev.target.value = ''
    if (!file) return
    const text = describeImageProblem(file)
    setProblem(text ? { where: 'icon', text } : null)
    if (text) return
    if (icon) URL.revokeObjectURL(icon.url)
    setIcon({ file, url: URL.createObjectURL(file) })
  }

  return {
    icon,
    pickIcon,
    inShots,
    pickInShots: pickShots('in', inShots, setInShots),
    dropInShot: dropShot(inShots, setInShots),
    outShots,
    pickOutShots: pickShots('out', outShots, setOutShots),
    dropOutShot: dropShot(outShots, setOutShots),
    problem,
  }
}
