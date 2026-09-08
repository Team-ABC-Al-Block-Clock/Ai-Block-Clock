import { Fragment, useState } from 'react'
import { paths } from '@/routes/paths'

export default function Detail() {
  const [creditOpen, setCreditOpen] = useState(false)
  const [fav, setFav] = useState(false)
  const [tab, setTab] = useState('in')
  const [shot, setShot] = useState(0)

  const captions: Record<string, string[]> = {
    in: ['원본 문서 화면', '폴더 선택 창', '분류 기준 입력', '실행 확인', '진행 상황'],
    out: ['생성된 폴더 목록', '이동 내역', '요약 리포트', '오류 항목', '완료 화면'],
  }
  const handleToggleFav = () => setFav((v) => !v)
  const handleTabIn = () => {
    setTab('in')
    setShot(0)
  }
  const handleTabOut = () => {
    setTab('out')
    setShot(0)
  }
  const handlePrev = () => setShot((v) => (v + 4) % 5)
  const handleNext = () => setShot((v) => (v + 1) % 5)
  const pickShot = (i: number) => () => setShot(i)
  const isIn = tab === 'in'
  const creditBtnBg = creditOpen ? 'rgba(255,255,255,.9)' : 'rgba(255,255,255,.72)'
  const creditLeft = 776
  const handleToggleCredit = () => setCreditOpen((v) => !v)
  const handleCloseCredit = () => setCreditOpen(false)
  const creditRows = [
    { label: '기본 실행', value: '540 Credit' },
    { label: '문서 100건 기준', value: '약 1,200 Credit' },
    { label: '이번 달 내 사용량', value: '3,860 Credit' },
  ]
  const chips = ['노드 4개', '평균 12초', '사용 128회', '공개', '즐겨찾기 42'].map((label) => ({
    label,
  }))
  const slotLabel = isIn ? '입력' : '출력'
  const shotCaption = captions[tab][shot]
  const counter = shot + 1 + ' / 5'
  const inBg = isIn ? 'rgba(255,255,255,.9)' : 'transparent'
  const inFg = isIn ? '#3a1030' : 'rgba(255,255,255,.72)'
  const outBg = isIn ? 'transparent' : 'rgba(255,255,255,.9)'
  const outFg = isIn ? 'rgba(255,255,255,.72)' : '#3a1030'
  const favBg = fav ? 'rgba(255,179,205,.26)' : 'rgba(255,255,255,.11)'
  const favStarColor = fav ? '#e3c8a3' : 'rgba(255,255,255,.5)'
  const thumbs = [0, 1, 2, 3, 4].map((i) => ({
    pick: pickShot(i),
    bg: i === shot ? 'rgba(255,255,255,.16)' : 'rgba(255,255,255,.08)',
    ring:
      i === shot ? 'inset 0 0 0 2px rgba(255,194,216,.9)' : 'inset 0 0 0 1px rgba(255,255,255,.12)',
  }))

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(22,10,36,.34) 0%, rgba(22,10,36,.18) 24%, rgba(22,10,36,.14) 58%, rgba(22,10,36,.26) 100%)',
        }}
      ></div>

      <div
        className="absolute top-0 left-0 overflow-x-hidden"
        id="detailScroll"
        style={{
          width: '1440px',
          height: '1024px',
          overflowY: 'auto',
          scrollbarWidth: 'thin',
          scrollbarColor: 'rgba(255,255,255,.28) transparent',
        }}
      >
        <div className="relative" style={{ width: '1440px', height: '1520px' }}>
          <a
            className="absolute flex items-center text-[14px] tracking-[-.01em]"
            href={paths.search}
            style={{
              left: '272px',
              top: '140px',
              height: '26px',
              gap: '7px',
              color: 'rgba(255,236,246,.78)',
              animation: 'rise .6s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            ← 목록으로
          </a>

          <div
            className="absolute overflow-hidden bg-[rgba(255,255,255,.10)]"
            style={{
              left: '272px',
              top: '176px',
              width: '120px',
              height: '120px',
              borderRadius: '16px',
              animation: 'unfold .7s .04s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="absolute inset-0"
              style={{
                background:
                  'radial-gradient(circle at 52% 28%, rgba(255,218,224,.16), rgba(255,214,232,0) 66%)',
              }}
            ></div>
            <div
              className="absolute inset-0 flex items-center justify-center text-[44px] font-semibold"
              style={{ color: 'rgba(255,226,238,.75)' }}
            >
              문
            </div>
          </div>

          <div
            className="absolute text-[32px] font-bold tracking-[-.02em] text-white"
            style={{
              left: '424px',
              top: '180px',
              textShadow: '0 2px 20px rgba(35,8,45,.6)',
              animation: 'rise .7s .08s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            문서 정리
          </div>
          <div
            className="absolute text-[15px] tracking-[-.01em]"
            style={{
              left: '424px',
              top: '228px',
              width: '520px',
              lineHeight: '1.5',
              color: 'rgba(255,236,246,.78)',
              animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            지정한 폴더의 문서를 읽고 주제별 폴더로 분류합니다.
          </div>

          <div
            className="absolute flex items-center"
            style={{
              left: '424px',
              top: '264px',
              width: '220px',
              height: '36px',
              gap: '12px',
              animation: 'rise .7s .16s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="flex-none rounded-full"
              style={{ width: '36px', height: '36px', background: 'rgba(255,255,255,.14)' }}
            ></div>
            <div className="flex flex-col" style={{ gap: '2px' }}>
              <div className="text-[14px] font-semibold tracking-[-.01em] text-white">김선미</div>
              <div className="text-[12px]" style={{ color: 'rgba(255,236,246,.6)' }}>
                만든 사람
              </div>
            </div>
          </div>

          <div
            onClick={handleToggleFav}
            style={{
              left: '860px',
              top: '210px',
              width: '129px',
              height: '52px',
              borderRadius: '26px',
              background: `${favBg}`,
              backdropFilter: 'blur(18px)',
              WebkitBackdropFilter: 'blur(18px)',
              gap: '8px',
              transition: 'background .18s',
              animation: 'rise .7s .2s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute flex cursor-pointer items-center justify-center hover:bg-[rgba(255,255,255,.2)]"
          >
            <div className="text-[15px]" style={{ color: `${favStarColor}` }}>
              ★
            </div>
            <div className="text-[15px] font-semibold tracking-[-.01em] text-white">즐겨찾기</div>
          </div>

          <a
            href={paths.run.waiting.withoutId}
            style={{
              left: '1008px',
              top: '210px',
              width: '160px',
              height: '52px',
              borderRadius: '26px',
              background: '#b98da9',
              boxShadow: '0 10px 26px rgba(140,100,130,.24)',
              color: '#2f1527',
              transition: 'transform .18s',
              animation: 'rise .7s .24s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute flex items-center justify-center text-[16px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
          >
            실행하기
          </a>

          <div
            className="absolute"
            style={{
              left: '272px',
              top: '344px',
              width: '896px',
              height: '1px',
              background: 'rgba(255,255,255,.16)',
            }}
          ></div>

          <div
            className="absolute flex"
            style={{
              left: '272px',
              top: '372px',
              width: '896px',
              height: '32px',
              gap: '10px',
              animation: 'rise .7s .28s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {chips.map((chip, i) => (
              <Fragment key={i}>
                <div
                  className="flex items-center text-[13px] tracking-[-.01em] whitespace-nowrap"
                  style={{
                    height: '32px',
                    padding: '0 15px',
                    borderRadius: '100px',
                    background: 'rgba(255,255,255,.11)',
                    color: 'rgba(255,240,247,.86)',
                  }}
                >
                  {chip.label}
                </div>
              </Fragment>
            ))}
            <div
              className="flex flex-none items-center whitespace-nowrap"
              style={{
                height: '32px',
                padding: '0 8px 0 15px',
                borderRadius: '100px',
                background: 'rgba(185,141,169,.26)',
                gap: '9px',
              }}
            >
              <div
                className="text-[13px] font-semibold tracking-[-.01em]"
                style={{ color: '#ffe4f0' }}
              >
                540 Credit
              </div>
              <div
                onClick={handleToggleCredit}
                title="사용량 안내"
                style={{
                  width: '18px',
                  height: '18px',
                  borderRadius: '9px',
                  background: `${creditBtnBg}`,
                  color: '#2f1527',
                }}
                className="flex flex-none cursor-pointer items-center justify-center text-[11px] font-bold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.9)]"
              >
                ?
              </div>
            </div>
          </div>

          {creditOpen && (
            <>
              <div
                className="absolute top-0 left-0"
                onClick={handleCloseCredit}
                style={{ width: '1440px', height: '1520px', zIndex: '8' }}
              ></div>
              <div
                className="absolute box-border flex flex-col"
                style={{
                  left: `${creditLeft}px`,
                  top: '416px',
                  width: '392px',
                  padding: '20px',
                  borderRadius: '16px',
                  background: 'rgba(20,11,36,.94)',
                  backdropFilter: 'blur(26px) saturate(122%)',
                  WebkitBackdropFilter: 'blur(26px) saturate(122%)',
                  boxShadow: '0 12px 34px rgba(4,2,12,.5)',
                  gap: '11px',
                  animation: 'fadeIn .18s ease both',
                  zIndex: '9',
                }}
              >
                <div className="text-[14px] font-semibold tracking-[-.01em] text-white">
                  크레딧 사용량
                </div>
                {creditRows.map((row, i) => (
                  <Fragment key={i}>
                    <div
                      className="flex justify-between text-[13px]"
                      style={{ alignItems: 'baseline' }}
                    >
                      <span
                        className="tracking-[-.01em]"
                        style={{ color: 'rgba(255,236,246,.66)' }}
                      >
                        {row.label}
                      </span>
                      <span
                        className="font-semibold tracking-[-.01em]"
                        style={{ color: '#e9b7d2' }}
                      >
                        {row.value}
                      </span>
                    </div>
                  </Fragment>
                ))}
                <div
                  className="text-[12px] tracking-[-.01em]"
                  style={{ marginTop: '3px', lineHeight: '1.5', color: 'rgba(255,236,246,.5)' }}
                >
                  문서 수와 길이에 따라 실제 사용량은 달라질 수 있습니다.
                </div>
              </div>
            </>
          )}

          <div
            className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
            style={{
              left: '272px',
              top: '444px',
              animation: 'rise .7s .32s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            설명
          </div>
          <div
            className="absolute text-[14.5px] tracking-[-.01em]"
            style={{
              left: '272px',
              top: '478px',
              width: '872px',
              lineHeight: '1.75',
              color: 'rgba(255,240,247,.76)',
              textWrap: 'pretty',
              animation: 'rise .7s .34s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            폴더를 지정하면 안에 있는 문서를 하나씩 읽어 주제를 파악하고, 비슷한 문서끼리 묶어 새
            폴더로 옮깁니다. 파일 이름은 바꾸지 않으며, 옮긴 내역은 실행이 끝난 뒤 목록으로 확인할
            수 있습니다. 분류 기준을 직접 정하고 싶다면 실행 전에 주제 목록을 넣어 주세요.
          </div>

          <div
            className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
            style={{
              left: '272px',
              top: '600px',
              animation: 'rise .7s .36s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            스크린샷
          </div>

          <div
            className="absolute box-border flex bg-[rgba(255,255,255,.10)]"
            style={{
              left: '1050px',
              top: '594px',
              width: '118px',
              height: '32px',
              borderRadius: '100px',
              padding: '3px',
              animation: 'rise .7s .36s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="flex cursor-pointer items-center justify-center text-[13px] font-semibold transition-colors duration-[160ms]"
              onClick={handleTabIn}
              style={{
                width: '56px',
                height: '26px',
                borderRadius: '100px',
                background: `${inBg}`,
                color: `${inFg}`,
              }}
            >
              입력
            </div>
            <div
              className="flex cursor-pointer items-center justify-center text-[13px] font-semibold transition-colors duration-[160ms]"
              onClick={handleTabOut}
              style={{
                width: '56px',
                height: '26px',
                borderRadius: '100px',
                background: `${outBg}`,
                color: `${outFg}`,
              }}
            >
              출력
            </div>
          </div>

          <div
            className="bg-surface-card absolute overflow-hidden"
            style={{
              left: '272px',
              top: '642px',
              width: '896px',
              height: '480px',
              borderRadius: '14px',
              animation: 'unfold .8s .4s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div
              className="absolute top-0 left-0 flex flex-col items-center justify-center"
              style={{ width: '896px', height: '480px', gap: '10px' }}
            >
              <div
                className="flex items-center justify-center bg-[rgba(255,255,255,.10)] text-[22px]"
                style={{
                  width: '52px',
                  height: '52px',
                  borderRadius: '14px',
                  fontWeight: '300',
                  color: 'rgba(255,255,255,.6)',
                }}
              >
                +
              </div>
              <div
                className="text-[13px] tracking-[-.01em]"
                style={{ color: 'rgba(255,240,247,.52)' }}
              >
                {slotLabel} 스크린샷이 들어갈 자리
              </div>
            </div>

            <div
              onClick={handlePrev}
              style={{
                left: '16px',
                top: '218px',
                width: '44px',
                height: '44px',
                background: 'rgba(20,9,34,.5)',
                backdropFilter: 'blur(14px)',
                WebkitBackdropFilter: 'blur(14px)',
              }}
              className="absolute flex cursor-pointer items-center justify-center rounded-full text-[16px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(20,9,34,.78)]"
            >
              ←
            </div>
            <div
              onClick={handleNext}
              style={{
                left: '836px',
                top: '218px',
                width: '44px',
                height: '44px',
                background: 'rgba(20,9,34,.5)',
                backdropFilter: 'blur(14px)',
                WebkitBackdropFilter: 'blur(14px)',
              }}
              className="absolute flex cursor-pointer items-center justify-center rounded-full text-[16px] font-semibold text-white transition-colors duration-[160ms] hover:bg-[rgba(20,9,34,.78)]"
            >
              →
            </div>

            <div
              className="absolute flex items-center"
              style={{
                left: '16px',
                top: '436px',
                height: '28px',
                padding: '0 13px',
                borderRadius: '100px',
                background: 'rgba(20,9,34,.55)',
                backdropFilter: 'blur(14px)',
                WebkitBackdropFilter: 'blur(14px)',
                gap: '9px',
              }}
            >
              <div className="text-[12px] font-semibold text-white">{slotLabel}</div>
              <div className="text-[12px]" style={{ color: 'rgba(255,255,255,.36)' }}>
                ·
              </div>
              <div className="text-[12px]" style={{ color: 'rgba(255,240,247,.74)' }}>
                {shotCaption}
              </div>
            </div>
            <div
              className="absolute flex items-center text-[12px] font-semibold text-white"
              style={{
                left: '812px',
                top: '436px',
                height: '28px',
                padding: '0 14px',
                borderRadius: '100px',
                background: 'rgba(20,9,34,.55)',
                backdropFilter: 'blur(14px)',
                WebkitBackdropFilter: 'blur(14px)',
              }}
            >
              {counter}
            </div>
          </div>

          <div
            className="absolute flex"
            style={{
              left: '272px',
              top: '1146px',
              width: '896px',
              height: '72px',
              gap: '12px',
              animation: 'rise .7s .46s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            {thumbs.map((th, i) => (
              <Fragment key={i}>
                <div
                  className="relative flex-none cursor-pointer overflow-hidden"
                  onClick={th.pick}
                  style={{
                    width: '104px',
                    height: '72px',
                    borderRadius: '10px',
                    background: `${th.bg}`,
                    boxShadow: `${th.ring}`,
                    transition: 'background .16s,box-shadow .16s',
                  }}
                >
                  <div
                    className="absolute inset-0"
                    style={{
                      background:
                        'radial-gradient(circle at 52% 28%, rgba(255,218,224,.14), rgba(255,214,232,0) 66%)',
                    }}
                  ></div>
                </div>
              </Fragment>
            ))}
            <div
              style={{
                width: '104px',
                height: '72px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.05)',
                boxShadow: 'inset 0 0 0 1px rgba(255,255,255,.16)',
                gap: '3px',
              }}
              className="flex flex-none cursor-pointer flex-col items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.12)]"
            >
              <div
                className="text-[15px]"
                style={{ fontWeight: '300', color: 'rgba(255,255,255,.62)' }}
              >
                +
              </div>
              <div className="text-[11px]" style={{ color: 'rgba(255,240,247,.55)' }}>
                사진 추가
              </div>
            </div>
          </div>

          <div
            className="absolute text-[16px] font-semibold tracking-[-.01em] text-white"
            style={{
              left: '272px',
              top: '1268px',
              animation: 'rise .7s .5s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            실행 예시
          </div>
          <div
            className="absolute box-border"
            style={{
              left: '272px',
              top: '1300px',
              width: '896px',
              padding: '24px',
              borderRadius: '14px',
              background: 'rgba(255,255,255,.07)',
              animation: 'unfold .8s .52s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <div className="flex flex-col" style={{ gap: '12px' }}>
              <div
                className="text-[13.5px] tracking-[-.01em]"
                style={{ color: 'rgba(255,240,247,.82)' }}
              >
                입력 · 다운로드 폴더에 있는 문서 42개
              </div>
              <div
                className="text-[13.5px] tracking-[-.01em]"
                style={{ color: 'rgba(255,240,247,.66)' }}
              >
                1. 문서를 읽고 주제를 뽑습니다 — 계약, 회의록, 영수증, 기타
              </div>
              <div
                className="text-[13.5px] tracking-[-.01em]"
                style={{ color: 'rgba(255,240,247,.66)' }}
              >
                2. 주제별 폴더를 만들어 파일을 옮깁니다
              </div>
              <div
                className="text-[13.5px] tracking-[-.01em]"
                style={{ color: 'rgba(255,240,247,.82)' }}
              >
                출력 · 4개 폴더, 42개 파일 이동 · 12초
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
