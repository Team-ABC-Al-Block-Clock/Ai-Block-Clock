import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'

import { updateMyProfile } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { describeImageProblem } from '@/lib/imageFile'
import { introProblem, nicknameProblem } from '@/lib/profileRules'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'

export type Toast = { ok: boolean; text: string }

/** 프로필 입력 · 사진 고르기 · 저장. 저장 결과는 스토어로 되돌려 넣는다. */
export function useProfileEdit() {
  const navigate = useNavigate()
  const profile = useAuthStore((s) => s.profile)
  const setProfile = useAuthStore((s) => s.setProfile)

  // 보호 라우트라 첫 렌더에 프로필이 이미 있다.
  const [nickname, setNickname] = useState(profile?.nickname ?? '')
  const [introduction, setIntroduction] = useState(profile?.introduction ?? '')
  const [toast, setToast] = useState<Toast | null>(null)

  /**
   * 고른 파일과 삭제 의사는 배타적이다.
   *
   * 미리보기 URL 을 파일과 한 덩어리로 들고 다닌다. 저장 전에도 얼굴이 바뀌어야 무엇을
   * 올리는지 알 수 있고, URL 을 이펙트에서 만들면 렌더 한 번은 옛 얼굴로 지나간다.
   */
  const [picked, setPicked] = useState<{ file: File; url: string } | null>(null)
  const [removeImage, setRemoveImage] = useState(false)

  // 바뀌거나 화면을 떠날 때 푼다. 안 풀면 고를 때마다 blob 이 쌓인다.
  useEffect(() => {
    const url = picked?.url
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [picked])

  // 렌더마다 새로 만들어지는 지역 변수로는 정리 함수가 엉뚱한 핸들을 지운다
  const saveTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => {
    return () => {
      clearTimeout(saveTimer.current)
    }
  }, [])

  const trimmedNickname = nickname.trim()
  const trimmedIntro = introduction.trim()

  const nicknameError = nicknameProblem(trimmedNickname)
  const introError = introProblem(trimmedIntro)
  const invalid = nicknameError !== null || introError !== null

  const dirty =
    trimmedNickname !== (profile?.nickname ?? '') ||
    trimmedIntro !== (profile?.introduction ?? '') ||
    picked !== null ||
    removeImage

  /** 지금 얼굴로 보여줄 것. 고른 파일 > 삭제 예정 > 저장된 이미지 순으로 이긴다. */
  const avatarUrl = picked?.url ?? (removeImage ? null : (profile?.profileImageUrl ?? null))

  const save = useMutation({
    mutationFn: () =>
      updateMyProfile({
        nickname: trimmedNickname,
        introduction: trimmedIntro,
        // 둘 중 하나만 보낸다.
        ...(picked ? { profileImageFile: picked.file } : {}),
        ...(removeImage ? { removeProfileImage: true } : {}),
      }),
    onSuccess: (updated) => {
      // 프로필은 쿼리가 아니라 스토어에 있다(7절). 저장 결과를 되돌려 넣어야
      // 사이드바 이름·소개가 같이 바뀐다. 응답에 없는 필드는 스토어가 유지한다.
      setProfile({
        nickname: updated.nickname,
        introduction: updated.introduction,
        profileImageUrl: updated.profileImageUrl,
      })
      // 저장된 값이 스토어로 들어왔으니 고른 파일과 삭제 의사는 비운다 —
      // 남겨 두면 dirty 가 계속 참이라 저장 버튼이 살아 있다.
      setPicked(null)
      setRemoveImage(false)
      setToast({ ok: true, text: '변경사항이 저장되었습니다' })
      saveTimer.current = setTimeout(() => navigate(paths.my.agents), 1100)
    },
    onError: (e) => {
      setToast({
        ok: false,
        text: e instanceof ApiError ? e.message : '저장하지 못했습니다. 다시 시도해 주세요.',
      })
    },
  })

  const canSave = !invalid && dirty && !save.isPending

  const handleSave = () => {
    if (!canSave) return
    setToast(null)
    save.mutate()
  }

  /** 고르는 즉시 막는다 — 5MB 를 올렸다가 서버가 거절하는 왕복을 없앤다. */
  const handlePickImage = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    // 같은 파일을 다시 고를 수 있게 값을 비운다. 안 그러면 onChange 가 안 뜬다.
    e.target.value = ''
    if (!file) return

    // 서버는 형식·크기를 검사하지 않아 여기서만 거른다.
    const problem = describeImageProblem(file)
    if (problem) {
      // 거절하면 앞서 고른 것도 버린다 — 토스트가 사라진 뒤 다른 파일이 저장되지 않게.
      setPicked(null)
      setToast({ ok: false, text: problem })
      return
    }
    setToast(null)
    setRemoveImage(false)
    setPicked({ file, url: URL.createObjectURL(file) })
  }

  /** 고른 것을 되돌리는 것과 저장된 사진을 지우는 것은 다른 일이다.
   *
   *  하나로 묶었더니, 실수로 고른 파일을 취소하려고 ✕ 를 누른 사람이 저장돼 있던
   *  사진까지 지우게 됐다 — 화면 안에서는 되돌릴 방법도 없었다. */
  const handleCancelPick = () => {
    setToast(null)
    setPicked(null)
  }

  const handleRemoveImage = () => {
    setToast(null)
    setRemoveImage(true)
  }

  return {
    nickname,
    setNickname,
    nicknameError,
    introduction,
    setIntroduction,
    introError,
    introLength: trimmedIntro.length,
    avatarUrl,
    hasPicked: picked !== null,
    handlePickImage,
    handleCancelPick,
    handleRemoveImage,
    canSave,
    isPending: save.isPending,
    handleSave,
    toast,
  }
}
