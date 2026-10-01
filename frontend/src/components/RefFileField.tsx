import { useRef, type CSSProperties } from 'react'

import { REF_FILE_LIMITS } from '@/api/nodes'
import type { RefFileSlot } from '@/hooks/canvas/useNodeRefFiles'
import { fileSizeLabel } from '@/lib/format'

/**
 * 파일을 고르고 고른 것을 목록으로 보여주는 칸. 편집 화면의 참고 자료와 실행 화면의 입력 파일이 쓴다.
 *
 * 붙은 자료(`attached`)는 서버가 원본이고, 아직 못 올린 것(`pending`)은 원본이 생길 때까지
 * 화면이 들고 있는 파일이다. 둘을 한 목록으로 보여주되 떼는 길이 달라 나눠 받는다.
 */

/** 칸 전체 높이. 절대 배치 쪽이 아래 줄 좌표를 여기서 잡는다. */
export const REF_FIELD_HEIGHT = 130
const HEAD_HEIGHT = 42
const ROW_HEIGHT = 26
/** 사유가 길면 접어서 다 보여준다 — 잘라 내면 무엇이 문제인지 못 읽는다. 목록이 그만큼 양보한다. */
const PROBLEM_LINE_HEIGHT = 1.35

/** 문장마다 줄을 나눈다. 낱말 가운데서 접히면 「삭제해 주 / 세요」처럼 읽힌다. */
const sentencesOf = (text: string) =>
  text
    .split('. ')
    .map((part, at, all) => (at < all.length - 1 ? `${part}.` : part))
    .filter(Boolean)

const ACCEPT = REF_FILE_LIMITS.extensions.join(',')
const EMPTY_HINT = '파일을 선택하세요 (10MB 까지)'

function Row({
  name,
  size,
  hint,
  disabled,
  onRemove,
}: {
  name: string
  size: number
  /** 올라간 것과 아직인 것이 한 목록에 섞였을 때만 온다 */
  hint?: string
  disabled?: boolean
  onRemove: () => void
}) {
  return (
    <div className="flex items-center" style={{ height: `${ROW_HEIGHT}px`, gap: '8px' }}>
      <span
        className="truncate text-[14px]"
        style={{ color: 'rgba(255,255,255,.88)', flex: '1 1 auto' }}
        title={name}
      >
        {name}
      </span>
      {hint && (
        <span className="flex-none text-[12px]" style={{ color: 'rgba(255,214,232,.72)' }}>
          {hint}
        </span>
      )}
      <span className="flex-none text-[12.5px]" style={{ color: 'rgba(255,255,255,.5)' }}>
        {fileSizeLabel(size)}
      </span>
      <button
        type="button"
        disabled={disabled}
        onClick={onRemove}
        aria-label={`${name} 삭제`}
        title="삭제"
        className="flex flex-none cursor-pointer items-center justify-center border-0 bg-transparent text-[14px] hover:text-[#fff] disabled:cursor-default disabled:opacity-40"
        style={{ width: '18px', height: '18px', color: 'rgba(255,255,255,.55)' }}
      >
        ×
      </button>
    </div>
  )
}

export default function RefFileField({
  slot: { attached, pending, busy, loading, problem, onPick, onDetach, onDropPending },
  accept = ACCEPT,
  multiple = true,
  emptyHint = EMPTY_HINT,
  style,
}: {
  slot: RefFileSlot
  /** 고르는 창이 거는 형식. 강제가 아니라 힌트다 — 윈도우는 「모든 파일」로 바꿀 수 있다 */
  accept?: string
  /** 실행 입력 파일은 명세가 한 건이라(`ExecutionStartRequest`) 꺼서 쓴다 */
  multiple?: boolean
  emptyHint?: string
  style?: CSSProperties
}) {
  const picker = useRef<HTMLInputElement>(null)
  const count = attached.length + pending.length
  /** 섞였을 때만 가른다 — 한쪽뿐이면 모든 줄에 같은 꼬리표가 붙어 읽을 것만 는다. */
  const mixed = !!attached.length && !!pending.length
  const totalBytes =
    attached.reduce((sum, file) => sum + file.size, 0) +
    pending.reduce((sum, file) => sum + file.size, 0)

  return (
    <div
      className="bg-surface-card box-border flex flex-col overflow-hidden"
      style={{ height: `${REF_FIELD_HEIGHT}px`, borderRadius: '11px', ...style }}
    >
      <div
        className="flex flex-none items-center justify-between"
        style={{ height: `${HEAD_HEIGHT}px`, padding: '0 6px 0 12px' }}
      >
        {/* 개수와 합계는 늘 남긴다 — 거절 사유로 덮으면 상한에 얼마나 닿았는지가 사라진다. */}
        <span
          className="truncate text-[15.5px]"
          style={{ color: count ? 'rgba(255,255,255,.8)' : 'rgba(255,255,255,.5)' }}
        >
          {count ? `${count}개 · ${fileSizeLabel(totalBytes)} / 10MB` : emptyHint}
        </span>
        <button
          type="button"
          disabled={busy || loading}
          onClick={() => picker.current?.click()}
          className="flex flex-none cursor-pointer items-center border-0 bg-[rgba(255,255,255,.16)] font-[inherit] text-[12.5px] font-semibold text-white hover:bg-[rgba(255,255,255,.28)] disabled:cursor-default disabled:opacity-45"
          style={{ height: '30px', padding: '0 14px', borderRadius: '8px' }}
        >
          {busy ? '올리는 중…' : loading ? '불러오는 중…' : '찾기'}
        </button>
        <input
          ref={picker}
          type="file"
          multiple={multiple}
          accept={accept}
          className="hidden"
          onChange={(ev) => {
            onPick(Array.from(ev.target.files ?? []))
            // 같은 파일을 다시 골라도 change 가 나게 비운다.
            ev.target.value = ''
          }}
        />
      </div>

      {/* 사유는 칸 안에서 제 줄을 갖는다. 목록을 그만큼 덜어 쓰므로 아래 줄은 움직이지 않는다. */}
      {problem && (
        <div
          className="flex-none text-[12.5px]"
          style={{
            padding: '4px 12px 2px',
            lineHeight: PROBLEM_LINE_HEIGHT,
            color: 'rgba(255,176,205,.95)',
            borderTop: '1px solid rgba(255,255,255,.08)',
          }}
        >
          {sentencesOf(problem).map((sentence) => (
            <div key={sentence}>{sentence}</div>
          ))}
        </div>
      )}

      {!!count && (
        <div
          className="scroll-thin flex-1"
          style={{
            // 줄바꿈으로 사유가 길어지면 목록이 줄어든다. 0 을 안 주면 대신 상자가 넘친다.
            minHeight: 0,
            padding: '0 8px 6px 12px',
            borderTop: problem ? undefined : '1px solid rgba(255,255,255,.08)',
          }}
        >
          {attached.map((file) => (
            <Row
              key={file.refFileId}
              name={file.filename}
              size={file.size}
              hint={mixed ? '업로드 완료' : undefined}
              // 올리는 중에 지우면 합계가 어긋난 채로 다음 건이 나간다.
              disabled={busy || loading}
              onRemove={() => onDetach(file.refFileId)}
            />
          ))}
          {pending.map((file, at) => (
            <Row
              key={`${file.name}-${at}`}
              name={file.name}
              size={file.size}
              hint={mixed ? '대기중' : undefined}
              disabled={busy || loading}
              onRemove={() => onDropPending(at)}
            />
          ))}
        </div>
      )}
    </div>
  )
}
