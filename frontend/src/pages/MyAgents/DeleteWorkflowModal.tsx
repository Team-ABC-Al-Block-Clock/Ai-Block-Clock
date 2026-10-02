import { josa } from '@/lib/format'

import { CHIP } from './config'

interface DeleteWorkflowModalProps {
  name: string
  isPending: boolean
  /** 실패한 까닭. 토스트가 없어 버튼 위에 한 줄로 둔다. */
  message: string | null
  onClose: () => void
  onConfirm: () => void
}

/** 워크플로우 삭제 확인 — 되돌릴 수 없으므로 한 번 묻는다. */
export default function DeleteWorkflowModal({
  name,
  isPending,
  message,
  onClose,
  onConfirm,
}: DeleteWorkflowModalProps) {
  return (
    <div className="absolute inset-0 flex items-center justify-center" style={{ zIndex: 70 }}>
      {/* 이 패널에 opacity·transform 을 걸지 않는다. backdrop 루트가 되어 blur 가 죽기 때문. 등장 모션은 위의 패널에만 준다. */}
      <div
        onClick={onClose}
        className="absolute inset-0"
        style={{
          background: 'rgba(12,5,22,.55)',
          backdropFilter: 'blur(6px)',
          WebkitBackdropFilter: 'blur(6px)',
        }}
      ></div>
      <div
        className="relative box-border text-center"
        style={{
          width: '400px',
          padding: '28px',
          borderRadius: '20px',
          background: 'rgba(22,12,38,.96)',
          boxShadow: '0 20px 52px rgba(4,2,12,.55)',
          animation: 'rise .3s cubic-bezier(.2,.9,.3,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">
          ‘{name}’{josa(name)} 삭제할까요?
        </div>
        <div
          className="text-[13px]"
          style={{ marginTop: '8px', lineHeight: '1.6', color: 'rgba(255,236,246,.68)' }}
        >
          삭제하면 되돌릴 수 없습니다. 노드는 그대로 남고, 지난 실행 기록도 사라지지 않습니다.
        </div>
        {message && (
          <div
            className="text-[14px] tracking-[-.01em]"
            style={{
              marginTop: '14px',
              color: 'rgba(255,196,214,.9)',
              animation: 'fadeIn .2s ease both',
            }}
          >
            {message}
          </div>
        )}
        <div className="flex" style={{ marginTop: '20px', gap: '10px' }}>
          <div
            onClick={onClose}
            className={`${CHIP} bg-surface-card flex-1 justify-center text-[14px] font-semibold text-white hover:bg-[rgba(255,255,255,.16)]`}
            style={{ height: '46px', borderRadius: '12px' }}
          >
            취소
          </div>
          <div
            onClick={onConfirm}
            className={`${CHIP} flex-1 justify-center text-[14px] font-bold hover:brightness-[1.12]`}
            style={{
              height: '46px',
              borderRadius: '12px',
              background: 'rgba(233,174,196,.24)',
              color: '#ffd7e6',
              opacity: isPending ? 0.6 : 1,
              cursor: isPending ? 'default' : 'pointer',
            }}
          >
            {isPending ? '삭제 중…' : '삭제'}
          </div>
        </div>
      </div>
    </div>
  )
}
