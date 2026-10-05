import StatusDot from '@/components/StatusDot'

import type { Toast } from './useProfileEdit'

/** 저장 · 사진 고르기 결과를 화면 위에 띄운다. */
export default function SaveToast({ toast }: { toast: Toast }) {
  return (
    <div
      className="pointer-events-none absolute left-0 flex justify-center"
      style={{
        top: '40px',
        width: '1440px',
        zIndex: '9',
        animation: 'dropInDown .3s cubic-bezier(.2,.9,.3,1) both',
      }}
    >
      <div
        className="flex items-center"
        style={{
          height: '44px',
          padding: '0 24px',
          borderRadius: '22px',
          background: 'rgba(20,10,32,.9)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
          boxShadow: '0 16px 40px rgba(6,3,14,.45)',
          gap: '10px',
        }}
      >
        <StatusDot color={toast.ok ? '#84bf9f' : '#f0b6c8'} />
        <div className="text-[15px] font-semibold tracking-[-.01em] text-white">{toast.text}</div>
      </div>
    </div>
  )
}
