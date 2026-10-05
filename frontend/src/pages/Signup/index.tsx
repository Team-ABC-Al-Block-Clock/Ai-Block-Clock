import { Navigate } from 'react-router-dom'

import { PROFILE_LIMITS } from '@/api/auth'
import { shortAddress } from '@/lib/format'
import { paths } from '@/routes/paths'
import logo from '@/assets/logo.png'

import CheckRow from './CheckRow'
import FieldLabel from './FieldLabel'
import ProfileImageField from './ProfileImageField'
import { useSignup } from './useSignup'

const FIELD_STYLE = {
  marginTop: '8px',
  width: '100%',
  height: '48px',
  padding: '0 15px',
  borderRadius: '12px',
}

/** 가입 화면 — 서명이 끝났고 프로필이 비어 있을 때만 들른다(시안 4).
 *  카드 없이 로그인 화면과 같은 열(504·432)에 세워 화면이 이어져 보이게 한다. */
export default function Signup() {
  const form = useSignup()

  // 이미 가입한 사람에게 빈 양식을 보여 주면, 그대로 저장해 쓰던 이름이 지워진다
  if (form.onboarded) return <Navigate to={paths.home} replace />

  return (
    <>
      {/* 돌아갈 길 — 서명까지 마친 사람이 프로필 앞에서 멈췄을 때 빠져나갈 곳이
          없으면 새로고침 말고는 방법이 없다. 껍데기 헤더가 꺼져 있어 직접 그린다. */}
      <button
        type="button"
        onClick={form.goBack}
        aria-label="뒤로"
        className="animate-rise bg-surface-card absolute flex cursor-pointer items-center justify-center text-[15px] text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.16)]"
        style={{ left: '44px', top: '34px', width: '40px', height: '40px', borderRadius: '12px' }}
      >
        ←
      </button>

      <div className="absolute" style={{ left: '504px', top: '116px', width: '432px' }}>
        <img
          src={logo}
          alt=""
          className="animate-rise block"
          style={{ width: '64px', height: '64px', margin: '0 auto', objectFit: 'contain' }}
        />
        <div
          className="animate-rise text-center text-[30px] font-bold tracking-[-.02em] text-white [animation-delay:.06s]"
          style={{ marginTop: '20px', textShadow: '0 2px 22px rgba(35,8,45,.6)' }}
        >
          회원가입
        </div>
        <div
          className="animate-rise text-center text-[14px] tracking-[-.01em] [animation-delay:.1s]"
          style={{ marginTop: '8px', color: 'rgba(255,236,246,.72)' }}
        >
          프로필만 설정하면 바로 시작할 수 있어요
        </div>
        <div
          className="animate-rise text-center text-[12.5px] tracking-[-.01em] [animation-delay:.12s]"
          style={{ marginTop: '7px', color: 'rgba(255,236,246,.52)' }}
        >
          {form.walletAddress
            ? `${shortAddress(form.walletAddress)} 연결됨`
            : '지갑이 연결되지 않았습니다'}
        </div>

        <div className="animate-rise [animation-delay:.16s]">
          <ProfileImageField
            picked={form.picked}
            error={form.imageError}
            onPick={form.pickImage}
            onClear={form.clearImage}
          />

          <FieldLabel text="이름" required />
          <input
            className="bg-surface-card box-border border-0 font-[inherit] text-[14px] text-white outline-0"
            type="text"
            value={form.nickname}
            onChange={(e) => form.setNickname(e.target.value)}
            placeholder="마켓플레이스에 표시될 이름을 입력하세요"
            maxLength={PROFILE_LIMITS.nickname.max}
            style={FIELD_STYLE}
          />
          {form.nickname.length > 0 && form.nicknameError && (
            <div
              className="text-[11.5px] tracking-[-.01em]"
              style={{ marginTop: '7px', color: '#ffb4c8' }}
            >
              {form.nicknameError}
            </div>
          )}

          <FieldLabel text="한 줄 소개" suffix="(선택)" />
          <input
            className="bg-surface-card box-border border-0 font-[inherit] text-[14px] text-white outline-0"
            type="text"
            value={form.introduction}
            onChange={(e) => form.setIntroduction(e.target.value)}
            placeholder="예) AI로 문서를 정리하는 걸 좋아합니다"
            maxLength={PROFILE_LIMITS.introduction.max}
            style={FIELD_STYLE}
          />
          <div
            className="text-right text-[11.5px] tracking-[-.01em]"
            style={{
              marginTop: '7px',
              color: form.introError ? '#ffb4c8' : 'rgba(255,236,246,.5)',
            }}
          >
            {`${form.introLength} / ${PROFILE_LIMITS.introduction.max}`}
          </div>

          {/* 좌우 -10px — 행의 hover 여백만큼 밀려 나가 체크박스가 입력칸 왼쪽 선에 맞는다 */}
          <div
            className="flex flex-col"
            style={{ marginTop: '10px', marginLeft: '-10px', marginRight: '-10px', gap: '2px' }}
          >
            <CheckRow
              on={form.agreed}
              toggle={form.toggleAgreed}
              main="이용약관 및 개인정보처리방침에 동의합니다 · 필수"
            />
            <CheckRow
              on={form.lossAck}
              toggle={form.toggleLossAck}
              main="지갑을 분실하면 계정을 복구할 수 없다는 점을 확인했습니다 · 필수"
              sub="비밀번호 재발급이나 계정 이관이 불가능합니다"
            />
          </div>

          <button
            type="button"
            onClick={form.submit}
            disabled={!form.canSubmit}
            style={{
              marginTop: '18px',
              width: '100%',
              height: '52px',
              borderRadius: '14px',
              background: form.canSubmit ? 'var(--color-accent)' : 'rgba(255,255,255,.12)',
              boxShadow: form.canSubmit ? '0 10px 24px rgba(140,100,130,.2)' : undefined,
              color: form.canSubmit ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.45)',
              transition: 'transform .18s,background .18s,color .18s',
            }}
            className="flex cursor-pointer items-center justify-center text-[16px] font-bold tracking-[-.01em] hover:-translate-y-[2px] disabled:cursor-default disabled:hover:translate-y-0"
          >
            {form.isPending ? '가입 중…' : '가입하기'}
          </button>

          {form.errorMessage !== null && (
            <div
              className="text-center text-[12.5px] leading-[1.5] tracking-[-.01em]"
              style={{ marginTop: '12px', color: '#ffb4c8' }}
            >
              {form.errorMessage}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
