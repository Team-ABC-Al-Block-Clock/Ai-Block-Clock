import { Fragment, useState } from 'react'
import { paths } from '@/routes/paths'

export default function RegisterForm() {
  const [done, setDone] = useState(false)

  const handleSubmit = () => setDone(true)
  const shots = [0, 1, 2, 3, 4].map((i) => ({ i }))

  return (
    <>
      <div
        className="scroll-quiet absolute top-0 left-0 overflow-x-hidden"
        id="formScroll"
        style={{
          width: '1440px',
          height: '1024px',
        }}
      >
        <div className="relative" style={{ width: '1440px', height: '1690px' }}>
          <div
            className="absolute"
            style={{
              left: '236px',
              top: '56px',
              width: '968px',
              height: '1594px',
              borderRadius: '24px',
              background: 'rgba(18,9,32,.44)',
              backdropFilter: 'blur(24px) saturate(118%)',
              WebkitBackdropFilter: 'blur(24px) saturate(118%)',
              animation: 'rise .8s cubic-bezier(.2,.8,.25,1) both',
            }}
          ></div>

          <a
            href={paths.edit.nodesSeller.withoutId}
            style={{
              left: '272px',
              top: '94px',
              color: 'rgba(255,236,246,.66)',
              transition: 'color .16s',
              animation: 'rise .7s .04s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute text-[13px] tracking-[-.01em] hover:text-[#fff]"
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
            className="absolute text-[14px] tracking-[-.01em]"
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
            className="absolute text-[14px] font-semibold tracking-[-.01em]"
            style={{
              left: '272px',
              top: '262px',
              color: 'rgba(255,255,255,.86)',
              animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            에이전트 아이콘
          </div>
          <div
            style={{
              left: '272px',
              top: '292px',
              width: '100px',
              height: '100px',
              borderRadius: '20px',
              background: 'rgba(255,255,255,.07)',
              animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
            }}
            className="absolute flex cursor-pointer items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.13)]"
          >
            <div
              className="text-[26px]"
              style={{ fontWeight: '300', color: 'rgba(255,255,255,.62)', lineHeight: '1' }}
            >
              +
            </div>
          </div>
          <div
            className="absolute text-[11.5px] tracking-[-.01em]"
            style={{ left: '272px', top: '400px', color: 'rgba(255,236,246,.5)' }}
          >
            권장 크기: 256×256px
          </div>

          <div
            className="absolute text-[14px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '436px', color: 'rgba(255,255,255,.86)' }}
          >
            온보딩 웰컴 메시지
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
              className="absolute border-0 bg-transparent font-[inherit] text-[14px] tracking-[-.01em] text-white outline-0"
              type="text"
              placeholder="에이전트에 어떤 프롬프트를 입력해야할지 사용자에게 알려주세요"
              style={{ left: '18px', top: '15px', width: '860px', height: '22px' }}
            />
          </div>

          <div
            className="absolute text-[14px] font-semibold tracking-[-.01em]"
            style={{
              left: '272px',
              top: '530px',
              color: 'rgba(255,255,255,.86)',
              animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            에이전트 이름
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '560px',
              width: '896px',
              height: '52px',
              borderRadius: '12px',
              animation: 'rise .7s .16s cubic-bezier(.2,.8,.25,1) both',
            }}
          >
            <input
              className="absolute border-0 bg-transparent font-[inherit] text-[14px] tracking-[-.01em] text-white outline-0"
              type="text"
              defaultValue="문서 정리"
              style={{ left: '18px', top: '15px', width: '860px', height: '22px' }}
            />
          </div>

          <div
            className="absolute text-[14px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '644px', color: 'rgba(255,255,255,.86)' }}
          >
            한 줄 설명
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '674px',
              width: '896px',
              height: '52px',
              borderRadius: '12px',
            }}
          >
            <input
              className="absolute border-0 bg-transparent font-[inherit] text-[14px] tracking-[-.01em] text-white outline-0"
              type="text"
              placeholder="지정한 폴더의 문서를 읽고 주제별 폴더로 분류합니다"
              style={{ left: '18px', top: '15px', width: '860px', height: '22px' }}
            />
          </div>

          <div
            className="absolute text-[14px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '758px', color: 'rgba(255,255,255,.86)' }}
          >
            상세 설명
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '788px',
              width: '896px',
              height: '124px',
              borderRadius: '12px',
            }}
          >
            <textarea
              className="absolute border-0 bg-transparent font-[inherit] text-[14px] tracking-[-.01em] text-white outline-0"
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
            className="absolute text-[14px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '944px', color: 'rgba(255,255,255,.86)' }}
          >
            스크린샷
          </div>

          <div
            className="absolute text-[12px] tracking-[-.01em]"
            style={{ left: '272px', top: '976px', color: 'rgba(255,236,246,.6)' }}
          >
            입력 · 에이전트 실행 전 화면
          </div>
          <div
            className="absolute flex items-start"
            style={{ left: '272px', top: '1002px', width: '896px', gap: '12px' }}
          >
            {shots.map((_s, i) => (
              <Fragment key={i}>
                <div className="flex flex-none flex-col" style={{ width: '128px', gap: '7px' }}>
                  <div
                    style={{ width: '128px', height: '84px', borderRadius: '10px' }}
                    className="relative flex-none cursor-pointer overflow-hidden bg-[rgba(255,255,255,.09)] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.16)]"
                  >
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
                  </div>
                  <input
                    className="box-border border-0 font-[inherit] text-[11.5px] tracking-[-.01em] text-white outline-0"
                    type="text"
                    placeholder="예: 실행 전 폴더 상태"
                    style={{
                      width: '128px',
                      height: '32px',
                      padding: '0 10px',
                      borderRadius: '9px',
                      background: 'rgba(255,255,255,.06)',
                    }}
                  />
                </div>
              </Fragment>
            ))}
            <div
              style={{
                width: '128px',
                height: '123px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.05)',
                gap: '5px',
              }}
              className="flex flex-none cursor-pointer flex-col items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.14)]"
            >
              <div
                className="text-[17px] font-semibold"
                style={{ color: 'rgba(255,255,255,.72)', lineHeight: '1' }}
              >
                +
              </div>
              <div className="text-[11px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                사진 추가
              </div>
            </div>
          </div>

          <div
            className="absolute text-[12px] tracking-[-.01em]"
            style={{ left: '272px', top: '1157px', color: 'rgba(255,236,246,.6)' }}
          >
            출력 · 에이전트 실행 후 화면
          </div>
          <div
            className="absolute flex items-start"
            style={{ left: '272px', top: '1183px', width: '896px', gap: '12px' }}
          >
            {shots.map((_s, i) => (
              <Fragment key={i}>
                <div className="flex flex-none flex-col" style={{ width: '128px', gap: '7px' }}>
                  <div
                    style={{ width: '128px', height: '84px', borderRadius: '10px' }}
                    className="relative flex-none cursor-pointer overflow-hidden bg-[rgba(255,255,255,.09)] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.16)]"
                  >
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
                  </div>
                  <input
                    className="box-border border-0 font-[inherit] text-[11.5px] tracking-[-.01em] text-white outline-0"
                    type="text"
                    placeholder="예: 분류 완료된 결과"
                    style={{
                      width: '128px',
                      height: '32px',
                      padding: '0 10px',
                      borderRadius: '9px',
                      background: 'rgba(255,255,255,.06)',
                    }}
                  />
                </div>
              </Fragment>
            ))}
            <div
              style={{
                width: '128px',
                height: '123px',
                borderRadius: '10px',
                background: 'rgba(255,255,255,.05)',
                gap: '5px',
              }}
              className="flex flex-none cursor-pointer flex-col items-center justify-center transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.14)]"
            >
              <div
                className="text-[17px] font-semibold"
                style={{ color: 'rgba(255,255,255,.72)', lineHeight: '1' }}
              >
                +
              </div>
              <div className="text-[11px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                사진 추가
              </div>
            </div>
          </div>

          <div
            className="absolute text-[14px] font-semibold tracking-[-.01em]"
            style={{ left: '272px', top: '1350px', color: 'rgba(255,255,255,.86)' }}
          >
            실행 예시
          </div>
          <div
            className="bg-surface-card absolute"
            style={{
              left: '272px',
              top: '1380px',
              width: '896px',
              height: '124px',
              borderRadius: '12px',
            }}
          >
            <textarea
              className="absolute border-0 bg-transparent font-[inherit] text-[14px] tracking-[-.01em] text-white outline-0"
              placeholder="에이전트 실행 결과 예시를 입력해주세요"
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
            style={{ left: '272px', top: '1540px', width: '896px', height: '1px' }}
          ></div>

          <a
            href={paths.edit.nodesSeller.withoutId}
            style={{
              left: '832px',
              top: '1580px',
              width: '160px',
              height: '52px',
              borderRadius: '26px',
              color: 'rgba(255,255,255,.9)',
            }}
            className="absolute flex items-center justify-center bg-[rgba(255,255,255,.10)] text-[15px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
          >
            취소하기
          </a>
          <div
            onClick={handleSubmit}
            style={{
              left: '1008px',
              top: '1580px',
              width: '160px',
              height: '52px',
              borderRadius: '26px',
              background: '#b98da9',
              boxShadow: '0 10px 26px rgba(140,100,130,.24)',
              color: '#2f1527',
              transition: 'transform .18s',
            }}
            className="absolute flex cursor-pointer items-center justify-center text-[15px] font-bold tracking-[-.01em] hover:-translate-y-[2px]"
          >
            등록 완료
          </div>
        </div>
      </div>

      {done && (
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
            <div className="text-[19px] font-semibold tracking-[-.01em] text-white">
              등록이 완료되었습니다
            </div>
            <div
              className="text-[13px]"
              style={{ marginTop: '10px', lineHeight: '1.6', color: 'rgba(255,236,246,.68)' }}
            >
              에이전트 올려줘서 ㄱㅅㄱㅅ!!
              <br />
            </div>
            <div className="flex" style={{ marginTop: '26px', gap: '12px' }}>
              <a
                href={paths.my.agents}
                style={{ height: '50px', borderRadius: '25px', color: 'rgba(255,255,255,.9)' }}
                className="flex flex-1 items-center justify-center bg-[rgba(255,255,255,.09)] text-[14px] font-semibold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.18)] hover:text-[#fff]"
              >
                마이페이지 가기
              </a>
              <a
                className="flex flex-1 items-center justify-center text-[14px] font-bold"
                href={paths.agentDetail.withoutId}
                style={{
                  height: '50px',
                  borderRadius: '25px',
                  background: '#b98da9',
                  color: '#2f1527',
                }}
              >
                상세 페이지 보기
              </a>
            </div>
          </div>
        </>
      )}
    </>
  )
}
