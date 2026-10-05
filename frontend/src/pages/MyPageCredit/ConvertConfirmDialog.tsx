import { useEffect, useRef } from 'react'

import WindowLayer from '@/components/WindowLayer'
import { priceLabel } from '@/lib/format'

interface ConvertConfirmDialogProps {
  /** 묻고 있는 금액. 없으면 닫혀 있다 */
  asking: string | null
  onConfirm: (amount: string) => void
  onCancel: () => void
}

/** 전환은 되돌릴 수 없어 한 번 더 묻는다. */
export default function ConvertConfirmDialog({
  asking,
  onConfirm,
  onCancel,
}: ConvertConfirmDialogProps) {
  const ref = useRef<HTMLElement>(null)
  // 의존성 배열을 두지 않는다 — 처음 렌더의 asking 이 굳는다.
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const handleConfirm = () => {
      if (asking) onConfirm(asking)
      else onCancel()
    }
    el.addEventListener('confirm', handleConfirm)
    el.addEventListener('cancel', onCancel)
    return () => {
      el.removeEventListener('confirm', handleConfirm)
      el.removeEventListener('cancel', onCancel)
    }
  })

  return (
    <WindowLayer>
      <confirm-dialog
        ref={ref}
        open={asking ? '' : undefined}
        heading="정산금을 ABC 로 전환할까요?"
        body-text={'전환한 ABC 는 실행에만 쓸 수 있고\n정산금으로 되돌릴 수 없습니다.'}
        note-text={asking ? `${priceLabel(asking)} 전환` : undefined}
        confirm-label="전환하기"
        cancel-label="취소"
      />
    </WindowLayer>
  )
}
