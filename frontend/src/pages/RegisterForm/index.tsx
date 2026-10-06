import { AGENT_LIMITS } from '@/api/agents'

import { FIELD } from './fields'
import IconField from './IconField'
import NameField from './NameField'
import RegisterActions from './RegisterActions'
import RegisterConfirm from './RegisterConfirm'
import RegisterDoneModal from './RegisterDoneModal'
import ShotRow from './ShotRow'
import TextField from './TextField'
import { useAgentRegister } from './useAgentRegister'
import { useImagePicks } from './useImagePicks'

/** 긴 글을 받는 두 칸의 높이. 이 아래는 칸 높이에 따라 함께 내려간다. */
const DESCRIPTION_HEIGHT = 220
const USAGE_HEIGHT = 180
const SHOTS_Y = 798 + 38 + DESCRIPTION_HEIGHT + 24
const USAGE_Y = SHOTS_Y + 406
const ACTIONS_Y = USAGE_Y + 38 + USAGE_HEIGHT + 36
const PAGE_HEIGHT = ACTIONS_Y + 150

/**
 * 에이전트 판매 등록 화면.
 *
 * 등록 흐름은 `useAgentRegister`, 아이콘 · 스크린샷 고르기는 `useImagePicks` 가 든다.
 * 여기는 설계 좌표대로 칸을 늘어놓는다.
 */
export default function RegisterForm() {
  const form = useAgentRegister()
  const images = useImagePicks()
  const problemAt = (where: 'icon' | 'in' | 'out') =>
    images.problem?.where === where ? images.problem.text : null

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
        <div className="relative" style={{ width: '1440px', height: `${PAGE_HEIGHT}px` }}>
          <div
            className="absolute"
            style={{
              left: '236px',
              top: '56px',
              width: '968px',
              height: `${PAGE_HEIGHT - 96}px`,
              borderRadius: '24px',
              background: 'rgba(18,9,32,.44)',
              backdropFilter: 'blur(24px) saturate(118%)',
              WebkitBackdropFilter: 'blur(24px) saturate(118%)',
              animation: 'rise .8s cubic-bezier(.2,.8,.25,1) both',
            }}
          ></div>

          {/* 완료 화면에서 들어온다. '취소하기' 와 같은 곳으로 되돌린다. */}
          <a
            href={form.backTo}
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

          <IconField icon={images.icon} onPick={images.pickIcon} problem={problemAt('icon')} />

          <TextField
            label="웰컴 메시지"
            top={428}
            id={FIELD.onboardingMessage}
            maxLength={AGENT_LIMITS.onboardingMessage.max}
            placeholder="웰컴 메시지를 입력해주세요"
          />

          <NameField value={form.workflowName} placeholder={form.namePlaceholder} />

          <TextField
            label="한 줄 설명"
            top={684}
            id={FIELD.summary}
            maxLength={AGENT_LIMITS.summary.max}
            placeholder="예: 올린 문서를 읽고 주제별로 분류해 표로 돌려줍니다"
          />

          <TextField
            label="상세 설명"
            top={798}
            id={FIELD.description}
            maxLength={AGENT_LIMITS.description.max}
            placeholder="에이전트의 기능을 자세히 설명해주세요"
            boxHeight={DESCRIPTION_HEIGHT}
          />

          <div
            className="absolute text-[18px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: `${SHOTS_Y}px`, color: 'rgba(255,255,255,.86)' }}
          >
            스크린샷
          </div>
          <ShotRow
            top={SHOTS_Y + 34}
            title="입력 · 에이전트 실행 전 화면"
            shots={images.inShots}
            captionPlaceholder="입력 예시 사진 설명"
            onPick={images.pickInShots}
            onDrop={images.dropInShot}
            label="입력"
            problem={problemAt('in')}
          />
          <ShotRow
            top={SHOTS_Y + 215}
            title="출력 · 에이전트 실행 후 화면"
            shots={images.outShots}
            captionPlaceholder="출력 예시 사진 설명"
            onPick={images.pickOutShots}
            onDrop={images.dropOutShot}
            label="출력"
            problem={problemAt('out')}
          />

          <TextField
            label="입력 예시"
            top={USAGE_Y}
            id={FIELD.usageExample}
            maxLength={AGENT_LIMITS.usageExample.max}
            placeholder="입력 프롬프트 예시를 입력해주세요"
            boxHeight={USAGE_HEIGHT}
          />

          <RegisterActions
            top={ACTIONS_Y}
            backTo={form.backTo}
            isPending={form.isPending}
            canSubmit={form.canSubmit}
            onSubmit={form.askSubmit}
            errorMessage={form.errorMessage}
          />
        </div>
      </div>

      {form.registered && (
        <RegisterDoneModal agentId={form.registered.agentId} price={form.registered.price} />
      )}

      <RegisterConfirm asking={form.asking} onConfirm={form.submit} onCancel={form.cancelAsking} />
    </>
  )
}
