import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useDisconnect } from 'wagmi'

import { PROFILE_LIMITS, signup } from '@/api/auth'
import { ApiError, USER_ERROR } from '@/api/errors'
import { shortAddress } from '@/lib/format'
import { describeImageProblem } from '@/lib/imageFile'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'
import logo from '@/assets/logo.png'

/** 가입 화면 — 서명이 끝났고 프로필이 비어 있을 때만 들른다(시안 4).
 *  서명이 인증을 대신하므로 받을 것은 프로필과 두 가지 확인뿐이다.
 *  카드 없이 로그인 화면과 같은 열(504·432)에 세워 화면이 이어져 보이게 한다. */
export default function Signup() {
  const navigate = useNavigate()
  const { disconnectAsync } = useDisconnect()
  const setProfile = useAuthStore((s) => s.setProfile)
  const walletAddress = useAuthStore((s) => s.walletAddress)
  const onboarded = useAuthStore((s) => s.profile?.onboardingCompleted === true)

  const [nickname, setNickname] = useState('')
  const [introduction, setIntroduction] = useState('')
  /** 고른 프로필 이미지와 미리보기 URL. 선택 항목이라 안 골라도 가입된다. */
  const [picked, setPicked] = useState<{ file: File; url: string } | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  // 바뀌거나 화면을 떠날 때 푼다. 안 풀면 고를 때마다 blob 이 쌓인다.
  useEffect(() => {
    const url = picked?.url
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [picked])

  const [agreed, setAgreed] = useState(false)
  /** 약관과 한 줄로 묶지 않는다 — 한 번에 넘어가면 계정을 복구할 수 없다는
   *  고지를 읽지 않고 지나친다. */
  const [lossAck, setLossAck] = useState(false)

  const [imageError, setImageError] = useState<string | null>(null)

  const trimmedNickname = nickname.trim()
  const trimmedIntro = introduction.trim()

  // 서버가 USER_002 로 막는 범위를 보내기 전에 여기서 먼저 잡는다.
  const { nickname: nickLimit, introduction: introLimit } = PROFILE_LIMITS
  const nicknameError =
    trimmedNickname.length < nickLimit.min || trimmedNickname.length > nickLimit.max
      ? `이름은 ${nickLimit.min}~${nickLimit.max}자여야 합니다`
      : null
  const introError =
    trimmedIntro.length > introLimit.max ? `한 줄 소개는 ${introLimit.max}자까지입니다` : null

  const join = useMutation({
    // 저장과 동의 기록이 서버에서 한 트랜잭션이다 — 화면이 호출 순서를 정하지 않는다.
    mutationFn: () =>
      signup({
        nickname: trimmedNickname,
        // 선택 항목이라 비어 있으면 필드를 빼고 보낸다 — 명세가 보낸 것만 반영한다.
        ...(trimmedIntro ? { introduction: trimmedIntro } : {}),
        ...(picked ? { profileImageFile: picked.file } : {}),
        // 버튼을 막는 것만으로는 동의를 받았다는 기록이 어디에도 남지 않는다.
        termsAgreed: agreed,
        recoveryNoticeConfirmed: lossAck,
      }),
    onSuccess: (result) => {
      // 프로필은 쿼리가 아니라 스토어에 있다(7절). 온보딩 여부까지 같이 덮어야
      // 다음 로그인이 이 화면으로 되돌리지 않는다.
      // 응답이 곧 프로필이라 통째로 넣는다 — 지갑도 같은 모양으로 온다.
      setProfile(result)
      navigate(paths.home, { replace: true })
    },
    onError: (error) => {
      // 이미 가입을 마친 계정이다 — 뒤로가기나 새로고침으로 두 번 제출될 수 있다.
      // 붉은 오류로 가두면 나갈 길이 없으므로 스토어를 맞추고 보내 준다.
      if (error instanceof ApiError && error.code === USER_ERROR.ALREADY_SIGNED_UP) {
        setProfile({ onboardingCompleted: true })
        navigate(paths.home, { replace: true })
      }
    },
  })

  const canSubmit = agreed && lossAck && !nicknameError && !introError && !join.isPending

  const handleSubmit = () => {
    if (!canSubmit) return
    join.mutate()
  }

  /** 서버는 형식·크기를 검사하지 않아 고르는 즉시 여기서 막는다. */
  const handlePickImage = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    // 같은 파일을 다시 고를 수 있게 값을 비운다. 안 그러면 onChange 가 안 뜬다.
    e.target.value = ''
    if (!file) return

    const problem = describeImageProblem(file)
    // 거절하면 앞서 고른 것도 버린다. 남겨 두면 오류 문구 아래에 다른 파일의
    // 미리보기가 떠 있고, 그대로 제출하면 그 파일이 올라간다.
    setImageError(problem)
    setPicked(problem ? null : { file, url: URL.createObjectURL(file) })
  }

  /** 미리보기와 오류 문구를 함께 비운다 — 문구만 남으면 지운 뒤에도 빨간 줄이 계속 보인다. */
  const handleClearImage = () => {
    setImageError(null)
    setPicked(null)
  }

  /** 프로필을 채우기 전에 나가면 반쯤 열린 세션이 남는다 — 로그아웃처럼 끊고 돌려보낸다.
   *  세션을 먼저 끊는다: 지갑이 먼저면 App 의 가드가 "연결 해제" 로 읽는다(7절). */
  const handleBack = () => {
    void useAuthStore
      .getState()
      .signOut()
      .catch(() => undefined)
      .then(() => disconnectAsync().catch(() => undefined))
      .finally(() => navigate(paths.login, { replace: true }))
  }

  // 이미 가입한 사람에게 빈 양식을 보여 주면, 그대로 저장해 쓰던 이름이 지워진다
  if (onboarded) return <Navigate to={paths.home} replace />

  const label = (text: string, suffix?: string, required = false) => (
    <div
      className="text-[13px] font-semibold tracking-[-.01em] text-white"
      style={{ marginTop: '22px' }}
    >
      {text}
      {required && <span style={{ marginLeft: '3px', color: '#ffb4c8' }}>*</span>}
      {suffix && (
        <span
          className="text-[11.5px] font-normal"
          style={{ marginLeft: '5px', color: 'rgba(255,236,246,.56)' }}
        >
          {suffix}
        </span>
      )}
    </div>
  )

  const check = (on: boolean, toggle: () => void, main: string, sub?: string) => (
    <div
      onClick={toggle}
      style={{ padding: '8px 10px', borderRadius: '10px', gap: '10px' }}
      className="flex cursor-pointer items-start transition-colors duration-[140ms] hover:bg-[rgba(255,255,255,.06)]"
    >
      <div
        className="flex flex-none items-center justify-center text-[10px] font-bold transition-colors duration-[160ms]"
        style={{
          width: '17px',
          height: '17px',
          marginTop: '1px',
          borderRadius: '5px',
          background: on ? 'var(--color-accent-bright)' : 'rgba(255,255,255,.12)',
          color: 'var(--color-accent-ink)',
        }}
      >
        {on ? '✓' : ''}
      </div>
      <div className="flex-1">
        {/* keep-all — 없으면 열 폭에서 `확인했습니 / 다` 로 단어 중간이 끊긴다 */}
        <div
          className="text-[13px] leading-[1.45] tracking-[-.01em]"
          style={{ color: 'rgba(255,240,247,.84)', wordBreak: 'keep-all' }}
        >
          {main}
        </div>
        {sub && (
          <div
            className="text-[11.5px] tracking-[-.01em]"
            style={{ marginTop: '4px', color: 'rgba(255,236,246,.56)' }}
          >
            {sub}
          </div>
        )}
      </div>
    </div>
  )

  const field = {
    marginTop: '8px',
    width: '100%',
    height: '48px',
    padding: '0 15px',
    borderRadius: '12px',
  }

  return (
    <>
      {/* 돌아갈 길 — 서명까지 마친 사람이 프로필 앞에서 멈췄을 때 빠져나갈 곳이
          없으면 새로고침 말고는 방법이 없다. 껍데기 헤더가 꺼져 있어 직접 그린다. */}
      <button
        type="button"
        onClick={handleBack}
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
          {walletAddress ? `${shortAddress(walletAddress)} 연결됨` : '지갑이 연결되지 않았습니다'}
        </div>

        <div className="animate-rise [animation-delay:.16s]">
          {label('프로필 이미지')}
          <div className="flex items-center" style={{ marginTop: '12px', gap: '16px' }}>
            <div
              className="bg-surface-card flex-none overflow-hidden"
              style={{ width: '72px', height: '72px', borderRadius: '36px' }}
            >
              {picked && <img src={picked.url} alt="" className="h-full w-full object-cover" />}
            </div>
            <div className="flex-1">
              <input
                ref={fileInput}
                type="file"
                accept="image/png,image/jpeg"
                onChange={handlePickImage}
                className="hidden"
              />
              <div className="flex items-center" style={{ gap: '8px' }}>
                <button
                  type="button"
                  onClick={() => fileInput.current?.click()}
                  className="bg-surface-card box-border cursor-pointer text-[13px] font-semibold tracking-[-.01em] text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.16)]"
                  style={{ height: '40px', padding: '0 18px', borderRadius: '11px' }}
                >
                  {picked ? '사진 변경' : '+ 사진 추가'}
                </button>
                {picked && (
                  <button
                    type="button"
                    onClick={handleClearImage}
                    className="cursor-pointer text-[12.5px] tracking-[-.01em] transition-colors duration-[160ms] hover:text-[#fff]"
                    style={{ color: 'rgba(255,236,246,.56)' }}
                  >
                    지우기
                  </button>
                )}
              </div>
              <div
                className="text-[11.5px] tracking-[-.01em]"
                style={{
                  marginTop: '8px',
                  color: imageError ? '#ffb4c8' : 'rgba(255,236,246,.56)',
                }}
              >
                {imageError ?? 'PNG · JPG, 5MB 이하 · 나중에 변경할 수 있어요'}
              </div>
            </div>
          </div>

          {label('이름', undefined, true)}
          <input
            className="bg-surface-card box-border border-0 font-[inherit] text-[14px] text-white outline-0"
            type="text"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="마켓플레이스에 표시될 이름을 입력하세요"
            maxLength={nickLimit.max}
            style={field}
          />
          {nickname.length > 0 && nicknameError && (
            <div
              className="text-[11.5px] tracking-[-.01em]"
              style={{ marginTop: '7px', color: '#ffb4c8' }}
            >
              {nicknameError}
            </div>
          )}

          {label('한 줄 소개', '(선택)')}
          <input
            className="bg-surface-card box-border border-0 font-[inherit] text-[14px] text-white outline-0"
            type="text"
            value={introduction}
            onChange={(e) => setIntroduction(e.target.value)}
            placeholder="예) AI로 문서를 정리하는 걸 좋아합니다"
            maxLength={introLimit.max}
            style={field}
          />
          <div
            className="text-right text-[11.5px] tracking-[-.01em]"
            style={{ marginTop: '7px', color: introError ? '#ffb4c8' : 'rgba(255,236,246,.5)' }}
          >
            {`${trimmedIntro.length} / ${introLimit.max}`}
          </div>

          {/* 좌우 -10px — 행의 hover 여백만큼 밀려 나가 체크박스가 입력칸 왼쪽 선에 맞는다 */}
          <div
            className="flex flex-col"
            style={{ marginTop: '10px', marginLeft: '-10px', marginRight: '-10px', gap: '2px' }}
          >
            {check(
              agreed,
              () => setAgreed((v) => !v),
              '이용약관 및 개인정보처리방침에 동의합니다 · 필수',
            )}
            {check(
              lossAck,
              () => setLossAck((v) => !v),
              '지갑을 분실하면 계정을 복구할 수 없다는 점을 확인했습니다 · 필수',
              '비밀번호 재발급이나 계정 이관이 불가능합니다',
            )}
          </div>

          <button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit}
            style={{
              marginTop: '18px',
              width: '100%',
              height: '52px',
              borderRadius: '14px',
              background: canSubmit ? 'var(--color-accent)' : 'rgba(255,255,255,.12)',
              boxShadow: canSubmit ? '0 10px 24px rgba(140,100,130,.2)' : undefined,
              color: canSubmit ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.45)',
              transition: 'transform .18s,background .18s,color .18s',
            }}
            className="flex cursor-pointer items-center justify-center text-[16px] font-bold tracking-[-.01em] hover:-translate-y-[2px] disabled:cursor-default disabled:hover:translate-y-0"
          >
            {join.isPending ? '가입 중…' : '가입하기'}
          </button>

          {join.isError && (
            <div
              className="text-center text-[12.5px] leading-[1.5] tracking-[-.01em]"
              style={{ marginTop: '12px', color: '#ffb4c8' }}
            >
              {join.error instanceof ApiError
                ? join.error.message
                : '가입을 마치지 못했습니다. 다시 시도해 주세요.'}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
