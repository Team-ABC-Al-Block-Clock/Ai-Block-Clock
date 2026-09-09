import { Fragment, useState } from 'react'
import { paths } from '@/routes/paths'

export default function Signup() {
  const [checked, setChecked] = useState([false, false, false])
  const [termsOpen, setTermsOpen] = useState(false)
  const [termsAt, setTermsAt] = useState(0)
  const [walletOpen, setWalletOpen] = useState(false)
  /** ACC-005 확인. 약관 배열(`checked`)에 넣지 않는다 — `전체 동의` 로 한 번에
   *  넘어가면 계정을 복구할 수 없다는 고지를 읽지 않고 지나친다. */
  const [lossAck, setLossAck] = useState(false)

  const labels = [
    '이용약관에 동의합니다 (필수)',
    '개인정보 처리방침에 동의합니다 (필수)',
    '마케팅 정보 수신에 동의합니다 (선택)',
  ]
  const titles = ['이용약관', '개인정보 처리방침', '마케팅 정보 수신 동의']
  const bodies = [
    [
      {
        head: '제1조 (목적)',
        body: '본 약관은 서비스 이용과 관련하여 회사와 회원 간의 권리, 의무 및 책임사항을 규정합니다.',
      },
      {
        head: '제2조 (에이전트의 등록)',
        body: '회원은 직접 구성한 에이전트를 마켓에 등록할 수 있으며, 등록된 에이전트의 동작과 결과에 대한 책임은 등록한 회원에게 있습니다.',
      },
      {
        head: '제3조 (코인 정산)',
        body: '에이전트가 사용된 만큼 적립된 코인은 회원이 연결한 지갑으로 정산됩니다. 정산 주기와 수수료는 서비스 내 안내를 따릅니다.',
      },
      {
        head: '제4조 (이용 제한)',
        body: '타인의 권리를 침해하거나 법령을 위반하는 에이전트는 사전 통지 없이 노출이 제한될 수 있습니다.',
      },
      {
        head: '제5조 (약관의 변경)',
        body: '회사는 약관을 변경할 수 있으며, 변경 시 적용일 7일 전부터 서비스 내에 공지합니다.',
      },
    ],
    [
      {
        head: '수집하는 정보',
        body: '지갑 주소, 닉네임, 한 줄 소개를 수집합니다. 프로필 이미지는 선택 항목입니다.',
      },
      {
        head: '이용 목적',
        body: '계정 식별, 에이전트 실행 기록 관리, 코인 정산, 서비스 개선을 위한 통계 분석에 이용합니다.',
      },
      {
        head: '보관 기간',
        body: '회원 탈퇴 시 지체 없이 파기합니다. 다만 정산 기록은 관계 법령에 따라 5년간 보관합니다.',
      },
      {
        head: '제3자 제공',
        body: '법령에 근거한 경우를 제외하고 회원의 정보를 제3자에게 제공하지 않습니다.',
      },
    ],
    [
      {
        head: '수신 항목',
        body: '새로운 에이전트 소식, 기능 업데이트, 이벤트 안내를 서비스 알림과 메일로 받습니다.',
      },
      {
        head: '철회 방법',
        body: '마이페이지 > 내 정보 수정에서 언제든 수신 동의를 철회할 수 있습니다. 철회해도 서비스 이용에는 제한이 없습니다.',
      },
    ],
  ]
  const c = checked
  const all = c.every(Boolean)
  const required = c[0] && c[1] && lossAck
  const terms = labels.map((label, i) => ({
    label,
    bg: c[i] ? '#e9b7d2' : 'rgba(255,255,255,.12)',
    mark: c[i] ? '✓' : '',
    toggle: () =>
      setChecked((s) => {
        const n = s.slice()
        n[i] = !n[i]
        return n
      }),
    view: () => {
      setTermsOpen(true)
      setTermsAt(i)
    },
  }))
  const allBg = all ? '#e9b7d2' : 'rgba(255,255,255,.12)'
  const allMark = all ? '✓' : ''
  const handleToggleAll = () => setChecked((v) => v.map(() => !v.every(Boolean)))
  const submitBg = required ? '#b98da9' : 'rgba(255,255,255,.12)'
  const submitFg = required ? '#2f1527' : 'rgba(255,255,255,.45)'
  const handleSubmit = () => {
    if (!required) return
    // 확인 사실을 서버에 남기는 것은 `completeOnboarding`(PATCH /users/me/onboarding) 인데
    // 인증이 필요하다. 지갑 로그인이 붙는 182 에서 이 자리에 이어 붙인다.
    setWalletOpen(true)
  }
  const termsTitle = titles[termsAt]
  const termsBody = bodies[termsAt]
  const handleCloseTerms = () => setTermsOpen(false)
  const handleAgreeTerms = () => {
    setChecked((prev) => {
      const n = prev.slice()
      n[termsAt] = true
      return n
    })
    setTermsOpen(false)
  }

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(20,9,34,.32) 0%, rgba(20,9,34,.14) 34%, rgba(20,9,34,.3) 100%)',
        }}
      ></div>

      <div
        className="absolute top-0 left-0 overflow-x-hidden"
        id="signupScroll"
        style={{
          width: '1440px',
          height: '1024px',
          overflowY: 'auto',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,.24) transparent',
        }}
      >
        <div className="relative" style={{ width: '1440px', height: '1230px' }}>
          <div
            className="absolute text-center text-[34px] font-bold tracking-[-.02em] text-white"
            style={{
              left: '504px',
              top: '136px',
              width: '432px',
              textShadow: '0 2px 22px rgba(35,8,45,.6)',
              animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            계정 만들기
          </div>
          <div
            className="absolute text-center text-[14px] tracking-[-.01em]"
            style={{
              left: '504px',
              top: '188px',
              width: '432px',
              color: 'rgba(255,236,246,.72)',
              animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            에이전트를 만들고 거래하려면 계정이 필요합니다
          </div>

          <div
            className="absolute box-border"
            style={{
              left: '504px',
              top: '246px',
              width: '432px',
              padding: '30px',
              borderRadius: '22px',
              background: 'rgba(20,11,36,.5)',
              backdropFilter: 'blur(26px) saturate(120%)',
              WebkitBackdropFilter: 'blur(26px) saturate(120%)',
              animation: 'rise .8s .14s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div className="text-[15px] font-semibold tracking-[-.01em] text-white">
              연결된 지갑
            </div>
            <div
              className="box-border flex items-center"
              style={{
                marginTop: '14px',
                height: '66px',
                padding: '0 16px',
                borderRadius: '12px',
                background: 'rgba(255,255,255,.07)',
                gap: '13px',
              }}
            >
              <div
                className="flex flex-none items-center justify-center text-[12px] font-bold"
                style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '10px',
                  background: 'rgba(233,174,196,.24)',
                  color: '#ffd7e6',
                }}
              >
                M
              </div>
              <div className="flex-1">
                <div
                  className="text-[14px] font-semibold text-white"
                  style={{ letterSpacing: '.01em' }}
                >
                  0xA1B2...7F3e
                </div>
                <div
                  className="text-[11.5px]"
                  style={{ marginTop: '4px', color: 'rgba(255,236,246,.62)' }}
                >
                  MetaMask · Polygon Mainnet
                </div>
              </div>
              <div className="flex items-center" style={{ gap: '6px' }}>
                <div
                  className="rounded-full"
                  style={{ width: '7px', height: '7px', background: '#84bf9f' }}
                ></div>
                <div className="text-[11.5px] font-semibold" style={{ color: '#9ad0b4' }}>
                  연결됨
                </div>
              </div>
            </div>
            <div
              className="text-[11.5px] tracking-[-.01em]"
              style={{ marginTop: '10px', color: 'rgba(255,236,246,.58)' }}
            >
              지갑 서명이 인증을 대신하므로 아이디·비밀번호는 받지 않습니다.
            </div>

            <div
              className="bg-[rgba(255,255,255,.12)]"
              style={{ marginTop: '26px', height: '1px' }}
            ></div>

            <div
              className="text-[15px] font-semibold tracking-[-.01em] text-white"
              style={{ marginTop: '24px' }}
            >
              프로필
            </div>
            <div className="flex items-center" style={{ marginTop: '14px', gap: '16px' }}>
              <div
                style={{
                  width: '72px',
                  height: '72px',
                  borderRadius: '36px',
                  fontWeight: '300',
                  color: 'rgba(255,255,255,.6)',
                }}
                className="bg-surface-card flex flex-none cursor-pointer items-center justify-center text-[20px] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.15)]"
              >
                +
              </div>
              <div className="flex-1">
                <div
                  className="text-[12.5px] font-semibold"
                  style={{ color: 'rgba(255,236,246,.78)' }}
                >
                  닉네임
                </div>
                <input
                  className="bg-surface-card box-border border-0 font-[inherit] text-[14px] text-white outline-0"
                  type="text"
                  placeholder="표시될 이름"
                  style={{
                    marginTop: '7px',
                    width: '100%',
                    height: '46px',
                    padding: '0 15px',
                    borderRadius: '12px',
                  }}
                />
              </div>
            </div>
            <div
              className="text-[12.5px] font-semibold"
              style={{ marginTop: '14px', color: 'rgba(255,236,246,.78)' }}
            >
              한 줄 소개
            </div>
            <input
              className="bg-surface-card box-border border-0 font-[inherit] text-[14px] text-white outline-0"
              type="text"
              placeholder="예: 문서 자동화를 만듭니다"
              style={{
                marginTop: '7px',
                width: '100%',
                height: '46px',
                padding: '0 15px',
                borderRadius: '12px',
              }}
            />

            <div
              className="bg-[rgba(255,255,255,.12)]"
              style={{ marginTop: '26px', height: '1px' }}
            ></div>

            <div
              className="text-[15px] font-semibold tracking-[-.01em] text-white"
              style={{ marginTop: '24px' }}
            >
              약관 동의
            </div>
            <div
              onClick={handleToggleAll}
              style={{
                marginTop: '14px',
                height: '48px',
                padding: '0 16px',
                borderRadius: '12px',
                background: 'rgba(255,255,255,.07)',
                gap: '11px',
              }}
              className="flex cursor-pointer items-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.12)]"
            >
              <div
                className="flex flex-none items-center justify-center text-[11px] font-bold transition-colors duration-[160ms]"
                style={{
                  width: '19px',
                  height: '19px',
                  borderRadius: '6px',
                  background: `${allBg}`,
                  color: '#2f1527',
                }}
              >
                {allMark}
              </div>
              <div className="text-[13.5px] font-semibold tracking-[-.01em] text-white">
                전체 동의
              </div>
            </div>
            <div className="flex flex-col" style={{ marginTop: '10px', gap: '2px' }}>
              {terms.map((t, i) => (
                <Fragment key={i}>
                  <div
                    style={{
                      height: '40px',
                      padding: '0 16px',
                      borderRadius: '10px',
                      gap: '11px',
                      transition: 'background .14s',
                    }}
                    className="flex items-center hover:bg-[rgba(255,255,255,.06)]"
                  >
                    <div
                      className="flex flex-none cursor-pointer items-center justify-center text-[10px] font-bold transition-colors duration-[160ms]"
                      onClick={t.toggle}
                      style={{
                        width: '17px',
                        height: '17px',
                        borderRadius: '5px',
                        background: `${t.bg}`,
                        color: '#2f1527',
                      }}
                    >
                      {t.mark}
                    </div>
                    <div
                      className="flex-1 cursor-pointer text-[13px] tracking-[-.01em]"
                      onClick={t.toggle}
                      style={{ color: 'rgba(255,240,247,.84)' }}
                    >
                      {t.label}
                    </div>
                    <div
                      onClick={t.view}
                      style={{
                        color: 'rgba(255,236,246,.56)',
                        textDecoration: 'underline',
                        textUnderlineOffset: '3px',
                      }}
                      className="cursor-pointer text-[12px] tracking-[-.01em] hover:text-[#fff]"
                    >
                      보기
                    </div>
                  </div>
                </Fragment>
              ))}
            </div>

            {/* ACC-005 — 지갑 분실 시 복구 불가 고지. 약관과 같은 줄에 두지 않고
                한 단 아래에서 따로 받는다. */}
            <div
              onClick={() => setLossAck((v) => !v)}
              style={{ marginTop: '10px', padding: '11px 16px', borderRadius: '10px', gap: '11px' }}
              className="flex cursor-pointer items-start transition-colors duration-[140ms] hover:bg-[rgba(255,255,255,.06)]"
            >
              <div
                className="flex flex-none items-center justify-center text-[10px] font-bold transition-colors duration-[160ms]"
                style={{
                  width: '17px',
                  height: '17px',
                  marginTop: '1px',
                  borderRadius: '5px',
                  background: lossAck ? '#e9b7d2' : 'rgba(255,255,255,.12)',
                  color: '#2f1527',
                }}
              >
                {lossAck ? '✓' : ''}
              </div>
              <div className="flex-1">
                <div
                  className="text-[13px] leading-[1.45] tracking-[-.01em]"
                  // keep-all — 없으면 패널 폭에서 `확인했습니 / 다` 로 단어 중간이 끊긴다
                  style={{ color: 'rgba(255,240,247,.84)', wordBreak: 'keep-all' }}
                >
                  지갑을 분실하면 계정을 복구할 수 없다는 점을 확인했습니다
                </div>
                <div
                  className="text-[11.5px] tracking-[-.01em]"
                  style={{ marginTop: '4px', color: 'rgba(255,236,246,.56)' }}
                >
                  비밀번호 재발급이나 계정 이관이 불가능합니다 · 필수
                </div>
              </div>
            </div>

            <div
              onClick={handleSubmit}
              style={{
                marginTop: '26px',
                height: '52px',
                borderRadius: '14px',
                background: `${submitBg}`,
                boxShadow: '0 10px 24px rgba(140,100,130,.2)',
                color: `${submitFg}`,
                transition: 'transform .18s,background .18s',
              }}
              className="flex cursor-pointer items-center justify-center text-[16px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
            >
              가입하기
            </div>
            <div
              className="text-center text-[12.5px] tracking-[-.01em]"
              style={{ marginTop: '24px', marginBottom: '6px', color: 'rgba(255,236,246,.62)' }}
            >
              이미 계정이 있으신가요?{' '}
              <a className="font-semibold" href={paths.login} style={{ color: '#e9b7d2' }}>
                로그인
              </a>
            </div>
          </div>
        </div>
      </div>

      {termsOpen && (
        <>
          <div
            className="absolute top-0 left-0"
            onClick={handleCloseTerms}
            style={{
              width: '1440px',
              height: '1024px',
              background: 'rgba(12,5,22,.55)',
              backdropFilter: 'blur(6px)',
              WebkitBackdropFilter: 'blur(6px)',
              animation: 'fadeIn .2s ease both',
              zIndex: '20',
            }}
          ></div>
          <div
            className="absolute box-border flex flex-col"
            style={{
              left: '440px',
              top: '172px',
              width: '560px',
              height: '680px',
              padding: '28px',
              borderRadius: '20px',
              background: 'rgba(22,12,38,.97)',
              backdropFilter: 'blur(26px)',
              WebkitBackdropFilter: 'blur(26px)',
              boxShadow: '0 20px 52px rgba(4,2,12,.55)',
              animation: 'rise .3s cubic-bezier(.2,.9,.3,1) both',
              zIndex: '21',
            }}
          >
            <div className="flex flex-none items-center justify-between">
              <div className="text-[18px] font-semibold tracking-[-.01em] text-white">
                {termsTitle}
              </div>
              <div
                onClick={handleCloseTerms}
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: '10px',
                  color: 'rgba(255,255,255,.8)',
                }}
                className="bg-surface-card flex cursor-pointer items-center justify-center text-[15px] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.18)]"
              >
                ✕
              </div>
            </div>
            <div
              className="flex-1"
              id="termsBody"
              style={{
                marginTop: '18px',
                minHeight: '0',
                overflowY: 'auto',
                paddingRight: '8px',
                scrollbarWidth: 'thin',
                scrollbarColor: 'rgba(255,255,255,.24) transparent',
              }}
            >
              {termsBody.map((p, i) => (
                <Fragment key={i}>
                  <div style={{ marginBottom: '18px' }}>
                    <div className="text-[13.5px] font-semibold tracking-[-.01em] text-white">
                      {p.head}
                    </div>
                    <div
                      className="text-[13px] tracking-[-.01em]"
                      style={{
                        marginTop: '7px',
                        lineHeight: '1.8',
                        color: 'rgba(255,240,247,.72)',
                        textWrap: 'pretty',
                      }}
                    >
                      {p.body}
                    </div>
                  </div>
                </Fragment>
              ))}
            </div>
            <div
              onClick={handleAgreeTerms}
              style={{
                marginTop: '20px',
                height: '50px',
                borderRadius: '13px',
                background: '#b98da9',
                color: '#2f1527',
                transition: 'transform .16s',
              }}
              className="flex flex-none cursor-pointer items-center justify-center text-[15px] font-bold hover:-translate-y-[2px]"
            >
              동의하고 닫기
            </div>
          </div>
        </>
      )}

      {walletOpen && (
        <>
          <div
            className="absolute top-0 left-0"
            style={{
              width: '1440px',
              height: '1024px',
              background: 'rgba(12,5,22,.55)',
              backdropFilter: 'blur(6px)',
              WebkitBackdropFilter: 'blur(6px)',
              animation: 'fadeIn .2s ease both',
              zIndex: '22',
            }}
          ></div>
          <div
            className="absolute box-border text-center"
            style={{
              left: '520px',
              top: '376px',
              width: '400px',
              padding: '28px',
              borderRadius: '20px',
              background: 'rgba(22,12,38,.97)',
              backdropFilter: 'blur(26px)',
              WebkitBackdropFilter: 'blur(26px)',
              boxShadow: '0 20px 52px rgba(4,2,12,.55)',
              animation: 'rise .3s cubic-bezier(.2,.9,.3,1) both',
              zIndex: '23',
            }}
          >
            <div
              className="flex items-center justify-center text-[19px] font-bold"
              style={{
                width: '52px',
                height: '52px',
                margin: '0 auto',
                borderRadius: '15px',
                background: 'rgba(233,174,196,.2)',
                color: '#ffd7e6',
              }}
            >
              M
            </div>
            <div
              className="text-[18px] font-semibold tracking-[-.01em] text-white"
              style={{ marginTop: '16px' }}
            >
              지금 지갑을 연결하실래요?
            </div>
            <div
              className="text-[13px] tracking-[-.01em]"
              style={{ marginTop: '9px', lineHeight: '1.65', color: 'rgba(255,236,246,.68)' }}
            >
              에이전트를 판매하거나 코인을 정산받으려면
              <br />
              지갑 연결이 필요합니다. 나중에 연결해도 됩니다.
            </div>
            <div className="flex flex-col" style={{ marginTop: '22px', gap: '10px' }}>
              <a
                href={paths.home}
                style={{
                  height: '48px',
                  borderRadius: '12px',
                  background: '#b98da9',
                  gap: '9px',
                  color: '#2f1527',
                  transition: 'transform .16s',
                }}
                className="flex items-center justify-center text-[15px] font-bold hover:-translate-y-[2px]"
              >
                MetaMask 연결하기
              </a>
              <a
                href={paths.home}
                style={{ height: '48px', borderRadius: '12px' }}
                className="bg-surface-card flex items-center justify-center text-[14px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.16)]"
              >
                나중에 하기
              </a>
            </div>
          </div>
        </>
      )}
    </>
  )
}
