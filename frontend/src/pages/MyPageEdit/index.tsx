import { PROFILE_LIMITS } from '@/api/auth'
import MyPageFrame from '@/components/mypage/MyPageFrame'
import MyPageHeader from '@/components/mypage/MyPageHeader'
import Sidebar from '@/components/mypage/Sidebar'

import AvatarPicker from './AvatarPicker'
import ProfileInput from './ProfileInput'
import SaveActions from './SaveActions'
import SaveToast from './SaveToast'
import { useProfileEdit } from './useProfileEdit'
import WalletSection from './WalletSection'

/** 내 정보 수정 화면. 입력 · 저장 흐름은 `useProfileEdit` 가 든다. */
export default function MyPageEdit() {
  const form = useProfileEdit()

  return (
    <>
      <MyPageFrame>
        <Sidebar editing />

        <MyPageHeader
          title="내 정보 수정"
          description="프로필과 연결된 지갑을 확인·수정할 수 있습니다. 결제 수단은 저장하지 않습니다."
          descriptionWidth={600}
          descriptionStyle={{ fontSize: '14.5px' }}
        />

        <div
          className="bg-surface-card absolute box-border"
          style={{
            left: '442px',
            top: '248px',
            width: '900px',
            height: '196px',
            padding: '24px',
            borderRadius: '16px',
            animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
          }}
        >
          <div className="text-[16px] font-semibold tracking-[-.01em] text-white">프로필 정보</div>
          <div className="flex" style={{ marginTop: '22px', gap: '28px' }}>
            <AvatarPicker
              avatarUrl={form.avatarUrl}
              hasPicked={form.hasPicked}
              onPick={form.handlePickImage}
              onCancelPick={form.handleCancelPick}
              onRemove={form.handleRemoveImage}
            />

            <div className="flex flex-1 flex-col" style={{ gap: '20px' }}>
              <div className="flex" style={{ gap: '24px' }}>
                <ProfileInput label="이름" value={form.nickname} onChange={form.setNickname}>
                  {form.nicknameError && (
                    <div
                      className="text-[14.5px] tracking-[-.01em]"
                      style={{ marginTop: '6px', color: '#ffb4c8' }}
                    >
                      {form.nicknameError}
                    </div>
                  )}
                </ProfileInput>
                <ProfileInput
                  label="한 줄 소개"
                  value={form.introduction}
                  onChange={form.setIntroduction}
                >
                  <div
                    className="flex items-center justify-between text-[14.5px] tracking-[-.01em]"
                    style={{ marginTop: '6px' }}
                  >
                    <span style={{ color: '#ffb4c8' }}>{form.introError}</span>
                    <span style={{ color: form.introError ? '#ffb4c8' : 'rgba(255,236,246,.5)' }}>
                      {`${form.introLength} / ${PROFILE_LIMITS.introduction.max}`}
                    </span>
                  </div>
                </ProfileInput>
              </div>
            </div>
          </div>
        </div>

        <WalletSection />

        <SaveActions canSave={form.canSave} isPending={form.isPending} onSave={form.handleSave} />
      </MyPageFrame>

      {/* 프레임 밖에 둔다 — 안에 두면 가운데로 민 만큼 비껴 난다. */}
      {form.toast && <SaveToast toast={form.toast} />}
    </>
  )
}
