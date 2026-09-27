import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fragment, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { AGENT_LIMITS, createAgent } from '@/api/agents'
import { ApiError } from '@/api/errors'
import { getWorkflow } from '@/api/workflows'
import { sellProblem } from '@/lib/canvasWorkflow'
import { describeImageProblem } from '@/lib/imageFile'
import { queryKeys } from '@/lib/queryKeys'
import { paths } from '@/routes/paths'

/** 입력이 제어 컴포넌트가 아니라 제출할 때 한 번 읽는다.
 *  JSX 도 이 맵으로 id 를 붙인다. 리터럴로 적어 두면 한쪽만 고쳐졌을 때 read() 가
 *  빈 문자열을 돌려주고, 설명은 조용히 사라지고 이름은 원인 모를 COMMON_001 이 된다. */
const FIELD = {
  onboardingMessage: 'regOnboarding',
  name: 'regName',
  summary: 'regSummary',
  description: 'regDescription',
  usageExample: 'regUsageExample',
} as const

function read(id: string) {
  const node = document.getElementById(id)
  return node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement
    ? node.value.trim()
    : ''
}

type Picked = { file: File; url: string }

const SHOT_SLOTS = [0, 1, 2, 3, 4]

/** 화면이 길어 문제를 고른 자리 옆에 띄운다. */
type ImageProblem = { where: 'icon' | 'in' | 'out'; text: string }

const FAILED = '등록에 실패했습니다. 잠시 후 다시 시도해 주세요'

/** 다시 눌러도 통과하지 않는 코드는 문구를 따로 가른다 — "잠시 후 다시" 로 뭉개면 계속 누른다. */
function describeRegisterError(error: unknown): string | null {
  if (!error) return null
  if (!(error instanceof ApiError)) return FAILED
  if (error.code === 'COMMON_001') return '이름 · 한 줄 설명 · 상세 설명을 확인해 주세요'
  // 사용자가 보던 회차와 굳히는 회차가 다르다. 편집 화면을 다시 열어야 한다.
  if (error.code === 'WORKFLOW_016') return '워크플로우가 그 사이 수정되었습니다. 다시 열어 주세요'
  if (error.code === 'AGENT_002' || error.code === 'WORKFLOW_003')
    return '다른 사람의 노드가 섞여 있어 등록할 수 없습니다'
  if (error.code === 'AGENT_001') return '노드가 없는 워크플로우는 등록할 수 없습니다'
  if (error.code === 'WORKFLOW_002' || error.code === 'WORKFLOW_015')
    return '워크플로우를 찾을 수 없습니다'
  return FAILED
}

function ImageProblemLine({ text, top }: { text: string; top: number }) {
  return (
    <div
      className="absolute text-right text-[13px] tracking-[-.005em]"
      style={{ left: '272px', top: `${top}px`, width: '896px', color: 'rgba(255,196,214,.9)' }}
    >
      {text}
    </div>
  )
}

/** 스크린샷 다섯 칸과 「사진 추가」. 입력·출력이 같은 모양이라 한 곳에 둔다. */
function ShotRow({
  top,
  shots,
  captionPlaceholder,
  fileRef,
  onPick,
  onDrop,
  label,
}: {
  top: number
  shots: Picked[]
  captionPlaceholder: string
  fileRef: React.RefObject<HTMLInputElement | null>
  onPick: (ev: React.ChangeEvent<HTMLInputElement>) => void
  onDrop: (index: number) => void
  label: string
}) {
  return (
    <div
      className="absolute flex items-start"
      style={{ left: '272px', top: `${top}px`, width: '896px', gap: '12px' }}
    >
      {SHOT_SLOTS.map((i) => (
        <Fragment key={i}>
          <div className="flex flex-none flex-col" style={{ width: '128px', gap: '7px' }}>
            <div
              style={{ width: '128px', height: '84px', borderRadius: '10px' }}
              className="relative flex-none overflow-hidden bg-[rgba(255,255,255,.09)]"
            >
              {shots[i] ? (
                <>
                  <img
                    src={shots[i].url}
                    alt=""
                    className="absolute inset-0 h-full w-full object-cover"
                  />
                  {/* 잘못 고른 것을 무르는 길 */}
                  <button
                    type="button"
                    aria-label={`${label} ${i + 1}번째 사진 지우기`}
                    onClick={() => onDrop(i)}
                    style={{
                      position: 'absolute',
                      right: '6px',
                      top: '6px',
                      width: '22px',
                      height: '22px',
                      border: '0',
                      borderRadius: '11px',
                      background: 'rgba(12,5,22,.62)',
                      color: '#fff',
                      lineHeight: '1',
                    }}
                    className="cursor-pointer text-[13px] transition-colors duration-[160ms] hover:bg-[rgba(12,5,22,.86)]"
                  >
                    ×
                  </button>
                </>
              ) : (
                <>
                  <div
                    className="absolute"
                    style={{
                      left: '-20px',
                      top: '41px',
                      width: '168px',
                      height: '1px',
                      background: 'rgba(255,255,255,.16)',
                      transform: 'rotate(33deg)',
                    }}
                  ></div>
                  <div
                    className="absolute"
                    style={{
                      left: '-20px',
                      top: '41px',
                      width: '168px',
                      height: '1px',
                      background: 'rgba(255,255,255,.16)',
                      transform: 'rotate(-33deg)',
                    }}
                  ></div>
                </>
              )}
            </div>
            <input
              className="box-border border-0 text-center font-[inherit] text-[13px] tracking-[-.005em] text-white outline-0"
              type="text"
              placeholder={captionPlaceholder}
              style={{
                width: '128px',
                height: '32px',
                padding: '0 8px',
                borderRadius: '9px',
                background: 'rgba(255,255,255,.06)',
              }}
            />
          </div>
        </Fragment>
      ))}
      {/* 숨긴 칸은 여는 버튼 밖에 둔다 — 안에 두면 click() 이 버블링해 핸들러가 두 번 돈다. */}
      <input
        ref={fileRef}
        type="file"
        accept="image/png,image/jpeg"
        multiple
        hidden
        onChange={onPick}
      />
      <button
        type="button"
        onClick={() => fileRef.current?.click()}
        style={{
          width: '128px',
          height: '123px',
          border: '0',
          borderRadius: '10px',
          background: 'rgba(255,255,255,.05)',
          gap: '5px',
        }}
        className="flex flex-none cursor-pointer flex-col items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.14)]"
      >
        <div
          className="text-[18px] font-semibold"
          style={{ color: 'rgba(255,255,255,.72)', lineHeight: '1' }}
        >
          +
        </div>
        <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.76)' }}>
          사진 추가
        </div>
      </button>
    </div>
  )
}

export default function RegisterForm() {
  /** 등록된 에이전트 ID. 완료 화면의 「상세 페이지 보기」가 이걸로 주소를 만든다 —
   *  별도 done 플래그를 두면 ID 없이 완료 화면이 뜨는 상태가 생긴다. */
  const [agentId, setAgentId] = useState<string | null>(null)
  /** 서버가 산정한 판매 단가. 완료 화면이 그대로 보여 준다 — 화면은 계산하지 않는다. */
  const [price, setPrice] = useState<string | null>(null)

  const queryClient = useQueryClient()
  const register = useMutation({
    mutationFn: createAgent,
    onSuccess: (res) => {
      setAgentId(res.agentId)
      setPrice(res.price)
      // 등록하면 마켓 목록과 마이페이지에 바로 보여야 한다.
      void queryClient.invalidateQueries({ queryKey: queryKeys.marketplace.agents.lists() })
      void queryClient.invalidateQueries({ queryKey: queryKeys.agents.lists() })
    },
  })

  // TODO: 굳힐 워크플로우와 보던 회차. 캔버스 저장이 붙을 때까지 비어 나간다.
  const [searchParams] = useSearchParams()
  const workflowId = searchParams.get('workflowId') ?? ''
  const revision = Number(searchParams.get('revision') ?? 0)
  const workflow = useQuery({
    queryKey: queryKeys.workflows.detail(workflowId),
    queryFn: () => getWorkflow(workflowId),
    enabled: !!workflowId,
  })
  const registrationProblem = !workflowId
    ? '등록할 워크플로우를 선택해 주세요'
    : workflow.isError
      ? '워크플로우를 확인하지 못했습니다. 다시 열어 주세요'
      : !workflow.data
        ? '워크플로우를 확인하고 있습니다'
        : sellProblem(workflow.data.nodes)
  /**
   * 취소하고 돌아갈 자리. 실행 결과에서 들어왔으면 그 실행으로 되돌린다 — 워크플로우를 잃으면
   * 빈 캔버스가 뜬다. 접수 번호 없이 들어오는 길은 내 에이전트 목록뿐이라 그리로 보낸다.
   */
  const executionId = searchParams.get('executionId') ?? ''
  const backTo = executionId
    ? `${paths.run.running(executionId)}?workflowId=${workflowId}`
    : paths.my.agents

  /** 등록은 수정도 삭제도 되지 않아 한 번 더 묻는다. */
  const [asking, setAsking] = useState<{ name: string } | null>(null)

  const askSubmit = () => {
    if (register.isPending || registrationProblem) return
    setAsking({ name: read(FIELD.name) })
  }

  const submit = () => {
    setAsking(null)
    if (register.isPending || registrationProblem) return
    register.mutate({
      workflowId,
      revision,
      name: read(FIELD.name),
      summary: read(FIELD.summary),
      description: read(FIELD.description),
      usageExample: read(FIELD.usageExample),
      onboardingMessage: read(FIELD.onboardingMessage),
    })
  }

  const errorMessage = registrationProblem ?? describeRegisterError(register.error)

  const [inShots, setInShots] = useState<Picked[]>([])
  const [outShots, setOutShots] = useState<Picked[]>([])
  const [icon, setIcon] = useState<Picked | null>(null)
  const [imageProblem, setImageProblem] = useState<ImageProblem | null>(null)
  const iconFileRef = useRef<HTMLInputElement>(null)
  const inFileRef = useRef<HTMLInputElement>(null)
  const outFileRef = useRef<HTMLInputElement>(null)

  const previewsRef = useRef<string[]>([])
  useEffect(() => {
    previewsRef.current = [...inShots, ...outShots, ...(icon ? [icon] : [])].map((p) => p.url)
  }, [inShots, outShots, icon])
  // 언마운트에서만 해제한다 — 목록을 의존성에 넣으면 한 장 더할 때 앞 장들의 URL 이 함께 풀린다.
  useEffect(() => () => previewsRef.current.forEach((url) => URL.revokeObjectURL(url)), [])

  // 고른 그림은 아직 서버로 가지 않는다 — 파일을 받는 자리가 없다(`api/agents.ts`).
  const pickShots =
    (where: 'in' | 'out', list: Picked[], setter: React.Dispatch<React.SetStateAction<Picked[]>>) =>
    (ev: React.ChangeEvent<HTMLInputElement>) => {
      const files = Array.from(ev.target.files ?? [])
      ev.target.value = ''
      // 한 장이 어긋났다고 나머지까지 버리지 않는다.
      const good = files.filter((file) => !describeImageProblem(file))
      const room = SHOT_SLOTS.length - list.length
      const text =
        files.map((file) => describeImageProblem(file)).find(Boolean) ??
        (good.length > room ? `스크린샷은 ${SHOT_SLOTS.length}장까지 올릴 수 있습니다` : null)
      setImageProblem(text ? { where, text } : null)
      // 칸이 남은 만큼만 만든다 — 잘라낸 것의 URL 은 해제할 곳이 없다.
      const picked = good.slice(0, room).map((file) => ({ file, url: URL.createObjectURL(file) }))
      if (picked.length) setter((prev) => [...prev, ...picked])
    }
  const handleAddInShots = pickShots('in', inShots, setInShots)
  const handleAddOutShots = pickShots('out', outShots, setOutShots)
  /** 잘못 고른 것을 무를 길. 등록은 되돌릴 수 없어 보내기 전에 지울 수 있어야 한다. */
  const dropShot =
    (list: Picked[], setter: React.Dispatch<React.SetStateAction<Picked[]>>) => (index: number) => {
      const gone = list[index]
      if (gone) URL.revokeObjectURL(gone.url)
      setter((prev) => prev.filter((_, i) => i !== index))
      setImageProblem(null)
    }
  const dropInShot = dropShot(inShots, setInShots)
  const dropOutShot = dropShot(outShots, setOutShots)
  const openIconFile = () => iconFileRef.current?.click()
  const handlePickIcon = (ev: React.ChangeEvent<HTMLInputElement>) => {
    const file = ev.target.files?.[0]
    ev.target.value = ''
    if (!file) return
    const problem = describeImageProblem(file)
    setImageProblem(problem ? { where: 'icon', text: problem } : null)
    if (problem) return
    if (icon) URL.revokeObjectURL(icon.url)
    setIcon({ file, url: URL.createObjectURL(file) })
  }

  const confirmRef = useRef<HTMLElement>(null)
  // 의존성 배열을 두지 않는다 — `[]` 로 묶으면 처음 렌더의 `submit` 이 굳는다.
  useEffect(() => {
    const el = confirmRef.current
    if (!el) return
    const onConfirm = () => submit()
    const onCancel = () => setAsking(null)
    el.addEventListener('confirm', onConfirm)
    el.addEventListener('cancel', onCancel)
    return () => {
      el.removeEventListener('confirm', onConfirm)
      el.removeEventListener('cancel', onCancel)
    }
  })

  return (
    <>
      <div
        className="scroll-quiet absolute left-0 overflow-x-hidden"
        id="formScroll"
        style={{
          width: '1440px',
          // 폼이 가로로 잘리지 않게 스크롤 영역을 창 위아래 끝까지 늘린다.
          top: 'calc(-1 * var(--edge-y, 0px))',
          height: 'calc(1024px + 2 * var(--edge-y, 0px))',
        }}
      >
        <div className="relative" style={{ width: '1440px', height: '1738px' }}>
          <div
            className="absolute"
            style={{
              left: '236px',
              top: '56px',
              width: '968px',
              height: '1642px',
              borderRadius: '24px',
              background: 'rgba(18,9,32,.44)',
              backdropFilter: 'blur(24px) saturate(118%)',
              WebkitBackdropFilter: 'blur(24px) saturate(118%)',
              animation: 'rise .8s cubic-bezier(.2,.8,.25,1) both',
            }}
          ></div>

          {/* 완료 화면에서 들어온다. '취소하기' 와 같은 곳으로 되돌린다. */}
          <a
            href={backTo}
            style={{
              left: '272px',
              top: '94px',
              color: 'var(--color-muted)',
              transition: 'color .16s',
              animation: 'rise .7s .04s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute text-[14.5px] tracking-[-.005em] hover:text-[#fff]"
          >
            ← 돌아가기
          </a>

          <div
            className="absolute text-[32px] font-semibold tracking-[-.02em] text-white"
            style={{
              left: '272px',
              top: '130px',
              textShadow: '0 2px 20px rgba(35,8,45,.6)',
              animation: 'rise .7s .08s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            에이전트 정보 입력
          </div>
          <div
            className="absolute text-[15.5px] tracking-[-.005em]"
            style={{
              left: '272px',
              top: '182px',
              width: '600px',
              color: 'rgba(255,236,246,.68)',
              animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            에이전트 상세 페이지에 표시될 내용을 입력해주세요
          </div>

          <div
            className="absolute bg-[rgba(255,255,255,.12)]"
            style={{ left: '272px', top: '226px', width: '896px', height: '1px' }}
          ></div>

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{
              left: '272px',
              top: '254px',
              color: 'rgba(255,255,255,.86)',
              animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            에이전트 아이콘
          </div>
          {/* 숨긴 칸은 여는 버튼 밖에 둔다 — 안에 두면 click() 이 버블링해 핸들러가 두 번 돈다. */}
          <input
            ref={iconFileRef}
            type="file"
            accept="image/png,image/jpeg"
            hidden
            onChange={handlePickIcon}
          />
          {/* div 가 아니라 button 이다 — 키보드로도 닿아야 한다. */}
          <button
            type="button"
            aria-label={icon ? '에이전트 아이콘 바꾸기' : '에이전트 아이콘 고르기'}
            style={{
              left: '272px',
              top: '292px',
              width: '100px',
              height: '100px',
              borderRadius: '20px',
              border: '0',
              padding: '0',
              background: 'var(--color-surface-inset)',
              animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
            }}
            onClick={openIconFile}
            className="absolute flex cursor-pointer items-center justify-center overflow-hidden transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.13)]"
          >
            {icon ? (
              <img src={icon.url} alt="" className="h-full w-full object-cover" />
            ) : (
              <div
                className="text-[26px]"
                style={{ fontWeight: '300', color: 'rgba(255,255,255,.62)', lineHeight: '1' }}
              >
                +
              </div>
            )}
          </button>
          <div
            className="absolute text-[13px] tracking-[-.005em]"
            style={{ left: '272px', top: '400px', color: 'rgba(255,236,246,.68)' }}
          >
            권장 크기: 256×256px
          </div>
          {imageProblem?.where === 'icon' && (
            <ImageProblemLine text={imageProblem.text} top={400} />
          )}

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '428px', color: 'rgba(255,255,255,.86)' }}
          >
            웰컴 메시지
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '466px',
              width: '896px',
              height: '52px',
              borderRadius: '12px',
            }}
          >
            <input
              className="absolute border-0 bg-transparent font-[inherit] text-[15.5px] tracking-[-.005em] text-white outline-0"
              type="text"
              id={FIELD.onboardingMessage}
              maxLength={AGENT_LIMITS.onboardingMessage.max}
              placeholder="웰컴 메시지를 입력해주세요"
              style={{ left: '18px', top: '15px', width: '860px', height: '22px' }}
            />
          </div>

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{
              left: '272px',
              top: '542px',
              color: 'rgba(255,255,255,.86)',
              animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            에이전트 이름
          </div>
          <div
            className="absolute text-[13.5px] tracking-[-.005em]"
            style={{
              left: '272px',
              top: '576px',
              color: 'rgba(255,236,246,.6)',
              animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            편집 화면에서 정한 워크플로우 이름을 그대로 씁니다
          </div>
          <div
            className="absolute"
            style={{
              left: '272px',
              top: '608px',
              width: '896px',
              height: '52px',
              borderRadius: '12px',
              background: 'rgba(8,3,16,.70)',
              boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.07)',
              animation: 'rise .7s .16s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {/* TODO: 워크플로우 이름을 그대로 쓴다. 지금은 자리표시자라 모두 같은 이름이 된다. */}
            <input
              className="absolute border-0 bg-transparent font-[inherit] text-[15.5px] tracking-[-.005em] outline-0"
              type="text"
              id={FIELD.name}
              defaultValue="문서 정리"
              maxLength={AGENT_LIMITS.name.max}
              readOnly
              style={{
                left: '18px',
                top: '15px',
                width: '820px',
                height: '22px',
                color: 'rgba(255,255,255,.38)',
                cursor: 'not-allowed',
              }}
            />
            {/* 자물쇠 아이콘 — 수정 불가 표시 */}
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{
                position: 'absolute',
                right: '16px',
                top: '50%',
                transform: 'translateY(-50%)',
                width: '16px',
                height: '16px',
                color: 'rgba(255,255,255,.28)',
                pointerEvents: 'none',
              }}
            >
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
          </div>

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '684px', color: 'rgba(255,255,255,.86)' }}
          >
            한 줄 설명
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '722px',
              width: '896px',
              height: '52px',
              borderRadius: '12px',
            }}
          >
            <input
              className="absolute border-0 bg-transparent font-[inherit] text-[15.5px] tracking-[-.005em] text-white outline-0"
              type="text"
              id={FIELD.summary}
              maxLength={AGENT_LIMITS.summary.max}
              placeholder="예: 지정한 폴더의 문서를 읽고 주제별 폴더로 분류합니다"
              style={{ left: '18px', top: '15px', width: '860px', height: '22px' }}
            />
          </div>

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '798px', color: 'rgba(255,255,255,.86)' }}
          >
            상세 설명
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '836px',
              width: '896px',
              height: '124px',
              borderRadius: '12px',
            }}
          >
            <textarea
              className="absolute border-0 bg-transparent font-[inherit] text-[15.5px] tracking-[-.005em] text-white outline-0"
              id={FIELD.description}
              maxLength={AGENT_LIMITS.description.max}
              placeholder="에이전트의 기능을 자세히 설명해주세요"
              style={{
                left: '18px',
                top: '16px',
                width: '860px',
                height: '92px',
                resize: 'none',
                lineHeight: '1.6',
              }}
            ></textarea>
          </div>

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '984px', color: 'rgba(255,255,255,.86)' }}
          >
            스크린샷
          </div>

          <div
            className="absolute text-[13.5px] tracking-[-.005em]"
            style={{ left: '272px', top: '1018px', color: 'rgba(255,236,246,.74)' }}
          >
            입력 · 에이전트 실행 전 화면
          </div>
          {imageProblem?.where === 'in' && <ImageProblemLine text={imageProblem.text} top={1018} />}
          <ShotRow
            top={1050}
            shots={inShots}
            captionPlaceholder="입력 예시 사진 설명"
            fileRef={inFileRef}
            onPick={handleAddInShots}
            onDrop={dropInShot}
            label="입력"
          />

          <div
            className="absolute text-[13.5px] tracking-[-.005em]"
            style={{ left: '272px', top: '1199px', color: 'rgba(255,236,246,.74)' }}
          >
            출력 · 에이전트 실행 후 화면
          </div>
          {imageProblem?.where === 'out' && (
            <ImageProblemLine text={imageProblem.text} top={1199} />
          )}
          <ShotRow
            top={1231}
            shots={outShots}
            captionPlaceholder="출력 예시 사진 설명"
            fileRef={outFileRef}
            onPick={handleAddOutShots}
            onDrop={dropOutShot}
            label="출력"
          />

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '1390px', color: 'rgba(255,255,255,.86)' }}
          >
            입력 예시
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '1428px',
              width: '896px',
              height: '124px',
              borderRadius: '12px',
            }}
          >
            <textarea
              className="absolute border-0 bg-transparent font-[inherit] text-[15.5px] tracking-[-.005em] text-white outline-0"
              id={FIELD.usageExample}
              maxLength={AGENT_LIMITS.usageExample.max}
              placeholder="입력 프롬프트 예시를 입력해주세요"
              style={{
                left: '18px',
                top: '16px',
                width: '860px',
                height: '92px',
                resize: 'none',
                lineHeight: '1.6',
              }}
            ></textarea>
          </div>

          <div
            className="absolute bg-[rgba(255,255,255,.12)]"
            style={{ left: '272px', top: '1588px', width: '896px', height: '1px' }}
          ></div>

          <a
            href={backTo}
            style={{
              left: '832px',
              top: '1628px',
              width: '160px',
              height: '52px',
              borderRadius: '26px',
              color: 'rgba(255,255,255,.9)',
            }}
            className="absolute flex items-center justify-center bg-[rgba(255,255,255,.10)] text-[16.5px] font-semibold tracking-[-.005em] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
          >
            취소하기
          </a>
          <button
            type="button"
            onClick={askSubmit}
            disabled={register.isPending || !!registrationProblem}
            style={{
              left: '1008px',
              top: '1628px',
              width: '160px',
              height: '52px',
              borderRadius: '26px',
              background: 'var(--color-accent)',
              boxShadow: '0 10px 26px rgba(140,100,130,.24)',
              color: 'var(--color-accent-ink)',
              transition: 'transform .18s',
            }}
            className="absolute flex cursor-pointer items-center justify-center text-[16.5px] font-bold tracking-[-.005em] hover:-translate-y-[2px] disabled:cursor-default disabled:opacity-45"
          >
            {register.isPending ? '등록 중…' : '등록 완료'}
          </button>

          {/* 실패를 조용히 넘기지 않는다. 토스트가 없어 버튼 위에 한 줄로 둔다. */}
          {errorMessage && (
            <div
              className="absolute text-right text-[14px] tracking-[-.005em]"
              style={{
                left: '672px',
                top: '1600px',
                width: '496px',
                color: 'rgba(255,196,214,.9)',
                animation: 'fadeIn .2s ease both',
              }}
            >
              {errorMessage}
            </div>
          )}
        </div>
      </div>

      {agentId && (
        <>
          <div
            className="absolute top-0 left-0"
            style={{
              width: '1440px',
              height: '1024px',
              background: 'rgba(6,3,14,.55)',
              backdropFilter: 'blur(4px)',
              WebkitBackdropFilter: 'blur(4px)',
              animation: 'fadeIn .24s ease both',
              zIndex: '9',
            }}
          ></div>
          <div
            className="absolute box-border text-center"
            style={{
              left: '500px',
              top: '404px',
              width: '440px',
              padding: '34px',
              borderRadius: '20px',
              background: 'rgba(26,13,42,.94)',
              backdropFilter: 'blur(28px)',
              WebkitBackdropFilter: 'blur(28px)',
              boxShadow: '0 30px 70px rgba(6,3,14,.6)',
              animation: 'rise .32s cubic-bezier(.2,.9,.3,1) both',
              zIndex: '10',
            }}
          >
            <div
              className="flex items-center justify-center rounded-full text-[24px]"
              style={{
                width: '56px',
                height: '56px',
                margin: '0 auto 18px',
                background: 'rgba(255,179,205,.2)',
                color: '#ffd0e0',
              }}
            >
              ✓
            </div>
            <div className="text-[21px] font-semibold tracking-[-.005em] text-white">
              등록이 완료되었습니다
            </div>
            <div
              className="text-[14.5px]"
              style={{ marginTop: '10px', lineHeight: '1.6', color: 'rgba(255,236,246,.68)' }}
            >
              에이전트 올려줘서 ㄱㅅㄱㅅ!!
              <br />
            </div>
            {/* 판매가는 등록해야 나온다 — 확인 팝업에는 띄울 수 없다. */}
            {price && (
              <div
                className="text-[14.5px] font-semibold"
                style={{
                  marginTop: '16px',
                  padding: '12px 14px',
                  borderRadius: '11px',
                  background: 'rgba(255,255,255,.06)',
                  color: '#fff',
                }}
              >
                실행 1회당 판매가 {price} ABC
              </div>
            )}
            <div className="flex" style={{ marginTop: '26px', gap: '12px' }}>
              <a
                href={paths.my.agents}
                style={{ height: '50px', borderRadius: '25px', color: 'rgba(255,255,255,.9)' }}
                className="flex flex-1 items-center justify-center bg-[rgba(255,255,255,.09)] text-[15.5px] font-semibold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.18)] hover:text-[#fff]"
              >
                마이페이지 가기
              </a>
              <a
                className="flex flex-1 items-center justify-center text-[15.5px] font-bold"
                href={paths.agentDetail(agentId)}
                style={{
                  height: '50px',
                  borderRadius: '25px',
                  background: 'var(--color-accent)',
                  color: 'var(--color-accent-ink)',
                }}
              >
                상세 페이지 보기
              </a>
            </div>
          </div>
        </>
      )}

      <confirm-dialog
        ref={confirmRef}
        open={asking ? '' : undefined}
        heading="마켓에 등록하시겠습니까?"
        body-text={'등록 후에는 수정 및 삭제가 불가합니다.\n소유권이 블록체인에 기록됩니다.'}
        note-text={asking?.name || undefined}
        confirm-label="등록하기"
        cancel-label="취소"
      />
    </>
  )
}
