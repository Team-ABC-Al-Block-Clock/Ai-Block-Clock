import { Fragment, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Sidebar from '../components/Sidebar'
import { shortAddress } from '@/lib/format'
import { paths } from '@/routes/paths'
import { useAuthStore } from '@/stores/authStore'

export default function MyPageEdit() {
  const navigate = useNavigate()
  const [saved, setSaved] = useState(false)
  const profile = useAuthStore((s) => s.profile)

  // 렌더마다 새로 만들어지는 지역 변수로는 정리 함수가 엉뚱한 핸들을 지운다
  const saveTimer = useRef<ReturnType<typeof setTimeout>>(undefined)

  const handleSave = () => {
    setSaved(true)
    clearTimeout(saveTimer.current)
    saveTimer.current = setTimeout(() => {
      navigate(paths.my.agents)
    }, 1100)
  }
  const tags = ['AI', '데이터 분석', '스마트 컨트랙트', '블록체인'].map((name) => ({ name }))
  const payments = [
    {
      icon: 'WALLET',
      label: '연결된 지갑',
      brand: 'MetaMask',
      number: shortAddress(profile?.walletAddress ?? ''),
      subLabel: '네트워크',
      subValue: 'Polygon Mainnet',
      action: '다시 연결',
    },
  ]

  useEffect(() => {
    return () => {
      clearTimeout(saveTimer.current)
    }
  }, [])

  return (
    <>
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            'linear-gradient(180deg, rgba(20,9,34,.42) 0%, rgba(20,9,34,.22) 30%, rgba(20,9,34,.30) 100%)',
        }}
      ></div>
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
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: '600px',
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        프로필과 계정 정보를 수정할 수 있습니다.
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '442px',
          top: '248px',
          width: '900px',
          height: '248px',
          padding: '24px',
          borderRadius: '16px',
          animation: 'rise .7s .14s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[14.5px] font-semibold tracking-[-.01em] text-white">프로필 정보</div>
        <div className="flex" style={{ marginTop: '22px', gap: '28px' }}>
          <div className="relative flex-none" style={{ width: '104px', height: '104px' }}>
            <div
              className="relative overflow-hidden rounded-full bg-[rgba(255,255,255,.10)]"
              style={{ width: '104px', height: '104px' }}
            >
              <div
                className="absolute inset-0"
                style={{
                  background:
                    'radial-gradient(circle at 52% 28%, rgba(255,218,224,.20), rgba(255,214,232,0) 62%)',
                }}
              ></div>
            </div>
            <div
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
            </div>
          </div>

          <div className="flex flex-1 flex-col" style={{ gap: '20px' }}>
            <div className="flex" style={{ gap: '24px' }}>
              <div className="flex-1">
                <div className="text-[12.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
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
                    className="border-0 bg-transparent font-[inherit] text-[13.5px] text-white outline-0"
                    type="text"
                    defaultValue={profile?.nickname ?? ''}
                    style={{ width: '100%' }}
                  />
                </div>
              </div>
              <div className="flex-1">
                <div className="text-[12.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
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
                    className="border-0 bg-transparent font-[inherit] text-[13.5px] text-white outline-0"
                    type="text"
                    defaultValue={profile?.introduction ?? ''}
                    style={{ width: '100%' }}
                  />
                </div>
              </div>
            </div>
            <div>
              <div className="text-[12.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                전문 분야
              </div>
              <div className="flex" style={{ marginTop: '12px', gap: '10px' }}>
                {tags.map((t, i) => (
                  <Fragment key={i}>
                    <div
                      style={{
                        height: '31px',
                        padding: '0 13px',
                        borderRadius: '9px',
                        gap: '8px',
                        color: 'rgba(255,240,247,.86)',
                      }}
                      className="flex cursor-pointer items-center bg-[rgba(255,255,255,.10)] text-[12px] whitespace-nowrap transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)]"
                    >
                      <span>{t.name}</span>
                      <span className="text-[12px]" style={{ color: 'rgba(255,255,255,.5)' }}>
                        ×
                      </span>
                    </div>
                  </Fragment>
                ))}
                <div
                  style={{
                    height: '31px',
                    padding: '0 13px',
                    borderRadius: '9px',
                    background: 'rgba(255,255,255,.05)',
                    color: 'rgba(255,240,247,.6)',
                  }}
                  className="flex cursor-pointer items-center text-[12px] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.14)]"
                >
                  + 추가
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
          top: '516px',
          width: '900px',
          height: '146px',
          padding: '24px',
          borderRadius: '16px',
          animation: 'rise .7s .18s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[14.5px] font-semibold tracking-[-.01em] text-white">계정 정보</div>
        <div className="flex" style={{ marginTop: '20px', gap: '24px' }}>
          <div className="flex-1">
            <div className="text-[12.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
              이메일 · 변경 불가
            </div>
            <div
              className="flex items-center text-[13.5px]"
              style={{
                marginTop: '8px',
                height: '42px',
                borderRadius: '11px',
                background: 'rgba(255,255,255,.04)',
                padding: '0 14px',
                color: 'rgba(255,240,247,.42)',
              }}
            >
              sunmi.kim@example.com
            </div>
          </div>
          <div className="flex-1">
            <div className="text-[12.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
              연락처
            </div>
            <div
              className="bg-surface-card flex items-center"
              style={{ marginTop: '8px', height: '42px', borderRadius: '11px', padding: '0 14px' }}
            >
              <input
                className="border-0 bg-transparent font-[inherit] text-[13.5px] text-white outline-0"
                type="text"
                defaultValue="010-1234-5678"
                style={{ width: '100%' }}
              />
            </div>
          </div>
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '442px',
          top: '682px',
          width: '900px',
          height: '166px',
          padding: '24px',
          borderRadius: '16px',
          animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[14.5px] font-semibold tracking-[-.01em] text-white">지갑 연결</div>
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
                  className="flex flex-none items-center justify-center text-[11px] font-semibold"
                  style={{
                    width: '44px',
                    height: '44px',
                    borderRadius: '11px',
                    background: 'rgba(255,220,235,.10)',
                    color: 'rgba(255,226,238,.8)',
                  }}
                >
                  {p.icon}
                </div>
                <div className="flex-1">
                  <div className="text-[12px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                    {p.label}
                  </div>
                  <div
                    className="flex"
                    style={{ marginTop: '5px', gap: '9px', alignItems: 'baseline' }}
                  >
                    <div className="text-[13.5px] font-semibold text-white">{p.brand}</div>
                    <div className="text-[13.5px]" style={{ color: 'rgba(255,255,255,.86)' }}>
                      {p.number}
                    </div>
                  </div>
                  <div className="flex" style={{ marginTop: '4px', gap: '7px' }}>
                    <div className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.58)' }}>
                      {p.subLabel}
                    </div>
                    <div className="text-[11.5px]" style={{ color: 'rgba(255,255,255,.8)' }}>
                      {p.subValue}
                    </div>
                  </div>
                </div>
                <div
                  style={{
                    height: '34px',
                    padding: '0 14px',
                    borderRadius: '10px',
                    color: 'rgba(255,255,255,.9)',
                  }}
                  className="flex flex-none cursor-pointer items-center bg-[rgba(255,255,255,.10)] text-[12.5px] font-semibold whitespace-nowrap transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.22)]"
                >
                  {p.action}
                </div>
              </div>
            </Fragment>
          ))}
        </div>
      </div>

      <div
        className="absolute bg-[rgba(255,255,255,.12)]"
        style={{ left: '442px', top: '876px', width: '900px', height: '1px' }}
      ></div>

      <div
        className="absolute flex"
        style={{
          left: '1104px',
          top: '898px',
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
          className="flex items-center justify-center bg-[rgba(255,255,255,.09)] text-[14px] font-semibold transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
        >
          취소
        </a>
        <div
          onClick={handleSave}
          style={{
            width: '142px',
            height: '44px',
            borderRadius: '22px',
            background: '#b98da9',
            boxShadow: '0 8px 22px rgba(140,100,130,.24)',
            color: '#2f1527',
            transition: 'transform .18s',
          }}
          className="flex cursor-pointer items-center justify-center text-[14px] font-bold hover:-translate-y-[2px]"
        >
          변경사항 저장
        </div>
      </div>

      {saved && (
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
              <div
                className="rounded-full"
                style={{ width: '7px', height: '7px', background: '#84bf9f' }}
              ></div>
              <div className="text-[13.5px] font-semibold tracking-[-.01em] text-white">
                변경사항이 저장되었습니다
              </div>
            </div>
          </div>
        </>
      )}
    </>
  )
}
