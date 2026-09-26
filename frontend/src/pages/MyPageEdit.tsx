import { useMutation } from '@tanstack/react-query'
import { Fragment, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import MyPageFrame from '@/components/MyPageFrame'
import { PROFILE_LIMITS, updateMyProfile } from '@/api/auth'
import { ApiError } from '@/api/errors'
import { shortAddress } from '@/lib/format'
import { describeImageProblem } from '@/lib/imageFile'
import { chainLabel, chainNameOf } from '@/lib/wagmi'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'
import StatusDot from '@/components/StatusDot'

export default function MyPageEdit() {
  const navigate = useNavigate()
  const profile = useAuthStore((s) => s.profile)
  const setProfile = useAuthStore((s) => s.setProfile)

  // 보호 라우트라 첫 렌더에 프로필이 이미 있다.
  const [nickname, setNickname] = useState(profile?.nickname ?? '')
  const [introduction, setIntroduction] = useState(profile?.introduction ?? '')
  const [toast, setToast] = useState<{ ok: boolean; text: string } | null>(null)

  /**
   * 고른 파일과 삭제 의사는 배타적이다 — 서버가 둘을 같이 받으면 USER_007 로 거절한다.
   *
   * 미리보기 URL 을 파일과 한 덩어리로 들고 다닌다. 저장 전에도 얼굴이 바뀌어야 무엇을
   * 올리는지 알 수 있고, URL 을 이펙트에서 만들면 렌더 한 번은 옛 얼굴로 지나간다.
   */
  const [picked, setPicked] = useState<{ file: File; url: string } | null>(null)
  const [removeImage, setRemoveImage] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  // 바뀌거나 화면을 떠날 때 푼다. 안 풀면 고를 때마다 blob 이 쌓인다.
  useEffect(() => {
    const url = picked?.url
    return () => {
      if (url) URL.revokeObjectURL(url)
    }
  }, [picked])

  // 렌더마다 새로 만들어지는 지역 변수로는 정리 함수가 엉뚱한 핸들을 지운다
  const saveTimer = useRef<ReturnType<typeof setTimeout>>(undefined)

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
        // 둘 중 하나만 보낸다. 같이 보내면 서버가 USER_007 로 거절한다.
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
  const handlePickImage = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    // 같은 파일을 다시 고를 수 있게 값을 비운다. 안 그러면 onChange 가 안 뜬다.
    e.target.value = ''
    if (!file) return

    // 서버도 USER_004·USER_005 로 막지만, 여기서 걸러야 5MB 를 올렸다가 거절당하지 않는다.
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
  const payments = [
    {
      icon: 'WALLET',
      label: '연결된 지갑',
      // 제공자·네트워크도 서버가 준다 — 손으로 적으면 배포가 바뀔 때 갈라진다
      brand: profile?.wallet.provider ?? 'MetaMask',
      number: shortAddress(profile?.wallet.address ?? ''),
      subLabel: '네트워크',
      subValue: profile ? chainNameOf(profile.wallet.chainId) : chainLabel,
    },
  ]

  useEffect(() => {
    return () => {
      clearTimeout(saveTimer.current)
    }
  }, [])

  return (
    <MyPageFrame>
      <Sidebar editing />

      <div
        className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '442px',
          top: '158px',
          textShadow: '0 2px 20px rgba(35,8,45,.6)',
          animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        내 정보 수정
      </div>
      <div
        className="absolute text-[14.5px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: '600px',
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        프로필과 연결된 지갑을 확인·수정할 수 있습니다. 결제 수단은 저장하지 않습니다.
      </div>

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
          <div className="relative flex-none" style={{ width: '104px', height: '104px' }}>
            <div
              className="relative overflow-hidden rounded-full bg-[rgba(255,255,255,.10)]"
              style={{ width: '104px', height: '104px' }}
            >
              {avatarUrl ? (
                <img
                  src={avatarUrl}
                  alt=""
                  className="absolute inset-0 h-full w-full object-cover"
                />
              ) : (
                <div
                  className="absolute inset-0"
                  style={{
                    background:
                      'radial-gradient(circle at 52% 28%, rgba(255,218,224,.20), rgba(255,214,232,0) 62%)',
                  }}
                ></div>
              )}
            </div>
            <input
              ref={fileInput}
              type="file"
              accept="image/png,image/jpeg"
              onChange={handlePickImage}
              className="hidden"
            />

            {/* 지울 것이 있을 때만 낸다 — 기본 아바타에 ✕ 는 아무 일도 하지 않는다.
                고른 것이 있으면 그 선택만 되돌린다(위 `handleCancelPick`). */}
            {avatarUrl && (
              <button
                type="button"
                onClick={picked ? handleCancelPick : handleRemoveImage}
                aria-label={picked ? '고른 사진 취소' : '프로필 사진 삭제'}
                style={{
                  left: '74px',
                  top: '0px',
                  width: '30px',
                  height: '30px',
                  background: 'rgba(28,12,42,.9)',
                  color: 'rgba(255,214,232,.9)',
                }}
                className="absolute flex cursor-pointer items-center justify-center rounded-full text-[14.5px] transition-colors duration-[160ms] hover:bg-[rgba(60,26,80,.95)]"
              >
                ✕
              </button>
            )}

            {/* role 만 붙인 div 는 포커스는 잡히지만 Enter·Space 로 눌리지 않는다 —
                옆의 삭제 버튼과 같은 <button> 으로 둔다 */}
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              aria-label="프로필 사진 선택"
              style={{
                left: '74px',
                top: '74px',
                width: '30px',
                height: '30px',
                background: 'rgba(28,12,42,.9)',
              }}
              className="absolute flex cursor-pointer items-center justify-center rounded-full transition-colors duration-[160ms] hover:bg-[rgba(60,26,80,.95)]"
            >
              <div
                className="relative"
                style={{
                  width: '15px',
                  height: '11px',
                  borderRadius: '3px',
                  background: 'rgba(255,214,232,.9)',
                }}
              >
                <div
                  className="absolute"
                  style={{
                    left: '4px',
                    top: '-2.5px',
                    width: '7px',
                    height: '3px',
                    borderRadius: '1px',
                    background: 'rgba(255,214,232,.9)',
                  }}
                ></div>
                <div
                  className="absolute rounded-full"
                  style={{
                    left: '5px',
                    top: '3px',
                    width: '5px',
                    height: '5px',
                    background: 'rgba(28,12,42,.9)',
                  }}
                ></div>
              </div>
            </button>
          </div>

          <div className="flex flex-1 flex-col" style={{ gap: '20px' }}>
            <div className="flex" style={{ gap: '24px' }}>
              <div className="flex-1">
                <div className="text-[15.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                  이름
                </div>
                <div
                  className="bg-surface-card flex items-center"
                  style={{
                    marginTop: '8px',
                    height: '42px',
                    borderRadius: '11px',
                    padding: '0 14px',
                  }}
                >
                  <input
                    className="border-0 bg-transparent font-[inherit] text-[15px] text-white outline-0"
                    type="text"
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    style={{ width: '100%' }}
                  />
                </div>
                {nicknameError && (
                  <div
                    className="text-[14.5px] tracking-[-.01em]"
                    style={{ marginTop: '6px', color: '#ffb4c8' }}
                  >
                    {nicknameError}
                  </div>
                )}
              </div>
              <div className="flex-1">
                <div className="text-[15.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                  한 줄 소개
                </div>
                <div
                  className="bg-surface-card flex items-center"
                  style={{
                    marginTop: '8px',
                    height: '42px',
                    borderRadius: '11px',
                    padding: '0 14px',
                  }}
                >
                  <input
                    className="border-0 bg-transparent font-[inherit] text-[15px] text-white outline-0"
                    type="text"
                    value={introduction}
                    onChange={(e) => setIntroduction(e.target.value)}
                    style={{ width: '100%' }}
                  />
                </div>
                <div
                  className="flex items-center justify-between text-[14.5px] tracking-[-.01em]"
                  style={{ marginTop: '6px' }}
                >
                  <span style={{ color: '#ffb4c8' }}>{introError}</span>
                  <span style={{ color: introError ? '#ffb4c8' : 'rgba(255,236,246,.5)' }}>
                    {`${trimmedIntro.length} / ${introLimit.max}`}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '442px',
          top: '464px',
          width: '900px',
          height: '166px',
          padding: '24px',
          borderRadius: '16px',
          animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[16px] font-semibold tracking-[-.01em] text-white">지갑 연결</div>
        <div className="flex" style={{ marginTop: '20px', gap: '24px' }}>
          {payments.map((p, i) => (
            <Fragment key={i}>
              <div
                className="box-border flex flex-1 items-center"
                style={{
                  height: '86px',
                  padding: '18px',
                  borderRadius: '12px',
                  background: 'rgba(255,255,255,.06)',
                  gap: '16px',
                }}
              >
                <div
                  className="flex flex-none items-center justify-center text-[11.5px] font-semibold tracking-[.02em]"
                  style={{
                    width: '56px',
                    height: '56px',
                    borderRadius: '11px',
                    background: 'rgba(255,220,235,.10)',
                    color: 'rgba(255,226,238,.8)',
                  }}
                >
                  {p.icon}
                </div>
                <div className="flex-1">
                  <div className="text-[15px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                    {p.label}
                  </div>
                  <div
                    className="flex"
                    style={{ marginTop: '5px', gap: '9px', alignItems: 'baseline' }}
                  >
                    <div className="text-[15px] font-semibold text-white">{p.brand}</div>
                    <div className="text-[15px]" style={{ color: 'rgba(255,255,255,.86)' }}>
                      {p.number}
                    </div>
                  </div>
                  <div className="flex" style={{ marginTop: '4px', gap: '7px' }}>
                    <div className="text-[14.5px]" style={{ color: 'rgba(255,236,246,.58)' }}>
                      {p.subLabel}
                    </div>
                    <div className="text-[14.5px]" style={{ color: 'rgba(255,255,255,.8)' }}>
                      {p.subValue}
                    </div>
                  </div>
                </div>
              </div>
            </Fragment>
          ))}
        </div>
      </div>

      <div
        className="absolute bg-[rgba(255,255,255,.12)]"
        style={{ left: '442px', top: '929px', width: '900px', height: '1px' }}
      ></div>

      <div
        className="absolute flex"
        style={{
          left: '1104px',
          top: '951px',
          width: '238px',
          height: '44px',
          justifyContent: 'flex-end',
          gap: '12px',
          animation: 'rise .7s .26s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <a
          href={paths.my.agents}
          style={{
            width: '84px',
            height: '44px',
            borderRadius: '22px',
            color: 'rgba(255,255,255,.9)',
          }}
          className="flex items-center justify-center bg-[rgba(255,255,255,.09)] text-[15.5px] font-semibold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
        >
          취소
        </a>
        <button
          type="button"
          onClick={handleSave}
          disabled={!canSave}
          style={{
            width: '142px',
            height: '44px',
            borderRadius: '22px',
            background: canSave ? 'var(--color-accent)' : 'rgba(255,255,255,.12)',
            boxShadow: canSave ? '0 8px 22px rgba(140,100,130,.24)' : 'none',
            color: canSave ? 'var(--color-accent-ink)' : 'rgba(255,255,255,.45)',
            transition: 'transform .18s, background .18s, color .18s',
          }}
          className="flex items-center justify-center text-[15.5px] font-bold enabled:cursor-pointer enabled:hover:-translate-y-[2px]"
        >
          {save.isPending ? '저장 중…' : '변경사항 저장'}
        </button>
      </div>

      {toast && (
        <>
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
              <div className="text-[15px] font-semibold tracking-[-.01em] text-white">
                {toast.text}
              </div>
            </div>
          </div>
        </>
      )}
    </MyPageFrame>
  )
}
