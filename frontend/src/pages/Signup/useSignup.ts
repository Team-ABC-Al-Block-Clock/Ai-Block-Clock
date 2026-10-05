import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useDisconnect } from 'wagmi'

import { signup } from '@/api/auth'
import { ApiError, USER_ERROR } from '@/api/errors'
import { describeImageProblem } from '@/lib/imageFile'
import { introProblem, nicknameProblem } from '@/lib/profileRules'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'

/** 가입 양식 상태와 제출. 서명이 인증을 대신하므로 받을 것은 프로필과 두 가지 확인뿐이다. */
export function useSignup() {
  const navigate = useNavigate()
  const { disconnectAsync } = useDisconnect()
  const setProfile = useAuthStore((s) => s.setProfile)
  const walletAddress = useAuthStore((s) => s.walletAddress)
  const onboarded = useAuthStore((s) => s.profile?.onboardingCompleted === true)

  const [nickname, setNickname] = useState('')
  const [introduction, setIntroduction] = useState('')
  /** 고른 프로필 이미지와 미리보기 URL. 선택 항목이라 안 골라도 가입된다. */
  const [picked, setPicked] = useState<{ file: File; url: string } | null>(null)

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
  const nicknameError = nicknameProblem(trimmedNickname)
  const introError = introProblem(trimmedIntro)

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

  const submit = () => {
    if (!canSubmit) return
    join.mutate()
  }

  /** 서버는 형식·크기를 검사하지 않아 고르는 즉시 여기서 막는다. */
  const pickImage = (e: React.ChangeEvent<HTMLInputElement>) => {
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
  const clearImage = () => {
    setImageError(null)
    setPicked(null)
  }

  /** 프로필을 채우기 전에 나가면 반쯤 열린 세션이 남는다 — 로그아웃처럼 끊고 돌려보낸다.
   *  세션을 먼저 끊는다: 지갑이 먼저면 App 의 가드가 "연결 해제" 로 읽는다(7절). */
  const goBack = () => {
    void useAuthStore
      .getState()
      .signOut()
      .catch(() => undefined)
      .then(() => disconnectAsync().catch(() => undefined))
      .finally(() => navigate(paths.login, { replace: true }))
  }

  return {
    walletAddress,
    onboarded,
    nickname,
    setNickname,
    nicknameError,
    introduction,
    setIntroduction,
    introLength: trimmedIntro.length,
    introError,
    picked,
    imageError,
    pickImage,
    clearImage,
    agreed,
    toggleAgreed: () => setAgreed((v) => !v),
    lossAck,
    toggleLossAck: () => setLossAck((v) => !v),
    canSubmit,
    submit,
    isPending: join.isPending,
    errorMessage: join.isError
      ? join.error instanceof ApiError
        ? join.error.message
        : '가입을 마치지 못했습니다. 다시 시도해 주세요.'
      : null,
    goBack,
  }
}
