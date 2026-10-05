import { useEffect, useRef } from 'react'

import WindowLayer from '@/components/WindowLayer'

interface RegisterConfirmProps {
  /** 물어볼 에이전트 이름. 없으면 닫혀 있다. */
  asking: { name: string } | null
  onConfirm: () => void
  onCancel: () => void
}

/**
 * 등록하면 곧바로 공개되고 내릴 길이 없어 한 번 더 묻는 팝업.
 *
 * `<confirm-dialog>` 는 커스텀 이벤트를 올린다. React 는 커스텀 이벤트를 on* 으로 받지 않아 직접 붙인다.
 */
export default function RegisterConfirm({ asking, onConfirm, onCancel }: RegisterConfirmProps) {
  const ref = useRef<HTMLElement>(null)

  // 의존성 배열을 두지 않는다 — `[]` 로 묶으면 처음 렌더의 `onConfirm` 이 굳는다.
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const handleConfirm = () => onConfirm()
    const handleCancel = () => onCancel()
    el.addEventListener('confirm', handleConfirm)
    el.addEventListener('cancel', handleCancel)
    return () => {
      el.removeEventListener('confirm', handleConfirm)
      el.removeEventListener('cancel', handleCancel)
    }
  })

  return (
    <WindowLayer>
      <confirm-dialog
        ref={ref}
        open={asking ? '' : undefined}
        heading="마켓에 등록하시겠습니까?"
        body-text={
          '등록하면 곧바로 공개되고, 다시 내릴 수 없습니다.\n소유권은 블록체인에 기록될 예정입니다.'
        }
        note-text={asking?.name || undefined}
        confirm-label="등록하기"
        cancel-label="취소"
      />
    </WindowLayer>
  )
}
