import { paths } from '@/routes/paths'

/** 이전 단계로 돌아가 워크플로우를 수정할 수 있다. */
export default function EditBackLink({ workflowId }: { workflowId: string | undefined }) {
  return (
    <a
      href={workflowId ? paths.edit.nodes(workflowId) : paths.edit.nodes.withoutId}
      title="워크플로우 수정"
      aria-label="워크플로우 수정"
      style={{
        right: '26px',
        top: '32px',
        width: '40px',
        height: '40px',
        borderRadius: '50%',
        background: 'rgba(255,255,255,.12)',
        boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.16)',
      }}
      className="absolute flex cursor-pointer items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.24)]"
    >
      <svg width="22" height="22" viewBox="0 0 22 22" fill="none">
        <path
          d="M17 11H6M11 6L6 11L11 16"
          stroke="rgba(255,255,255,.9)"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    </a>
  )
}
