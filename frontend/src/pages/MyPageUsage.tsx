import { Fragment } from 'react'
import Sidebar from '../components/Sidebar'
import { paths } from '@/routes/paths'

export default function MyPageUsage() {
  const history = [
    {
      time: '08.28 09:15',
      agent: '문서 분석',
      calls: '23',
      tokens: '128K / 64K',
      amount: '45.6 ABC',
      status: '정산 완료',
    },
    {
      time: '08.27 18:42',
      agent: '리서치 요약',
      calls: '18',
      tokens: '96K / 48K',
      amount: '36.8 ABC',
      status: '정산 완료',
    },
    {
      time: '08.27 14:10',
      agent: '계약 검토',
      calls: '31',
      tokens: '156K / 82K',
      amount: '59.2 ABC',
      status: '정산 완료',
    },
    {
      time: '08.26 21:33',
      agent: '데이터 추출',
      calls: '15',
      tokens: '72K / 36K',
      amount: '26.4 ABC',
      status: '정산 대기',
    },
    {
      time: '08.26 11:07',
      agent: '이미지 캡션',
      calls: '27',
      tokens: '134K / 70K',
      amount: '48.8 ABC',
      status: '정산 완료',
    },
    {
      time: '08.25 16:22',
      agent: '코드 리뷰',
      calls: '34',
      tokens: '168K / 88K',
      amount: '62.4 ABC',
      status: '정산 완료',
    },
    {
      time: '08.25 10:48',
      agent: '문서 번역',
      calls: '21',
      tokens: '112K / 58K',
      amount: '41.2 ABC',
      status: '정산 완료',
    },
    {
      time: '08.24 19:05',
      agent: '표 추출',
      calls: '12',
      tokens: '58K / 30K',
      amount: '22.8 ABC',
      status: '정산 대기',
    },
    {
      time: '08.24 08:31',
      agent: '회의 요약',
      calls: '29',
      tokens: '142K / 74K',
      amount: '52.6 ABC',
      status: '정산 완료',
    },
  ].map((h) => ({ ...h, statusFg: h.status === '정산 완료' ? '#9ad0b4' : '#ffb0cd' }))

  return (
    <>
      <Sidebar active="usage" />

      <div
        className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '442px',
          top: '158px',
          textShadow: '0 2px 20px rgba(35,8,45,.6)',
          animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        사용 내역
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: '620px',
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        에이전트 사용량, 토큰 소비량과 블록체인 결제 내역을 확인합니다.
      </div>

      <div
        style={{
          left: '1210px',
          top: '160px',
          width: '128px',
          height: '38px',
          borderRadius: '19px',
          padding: '0 15px',
          animation: 'rise .7s .12s cubic-bezier(.2,.8,.25,1) both',
        }}
        className="absolute box-border flex cursor-pointer items-center justify-between bg-[rgba(255,255,255,.09)] hover:bg-[rgba(255,255,255,.18)]"
      >
        <div className="text-[12px]" style={{ color: 'rgba(255,240,247,.84)' }}>
          최근 30일
        </div>
        <div
          style={{
            width: '0',
            height: '0',
            borderLeft: '4px solid transparent',
            borderRight: '4px solid transparent',
            borderTop: '5px solid rgba(255,255,255,.6)',
          }}
        ></div>
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '442px',
          top: '250px',
          width: '574px',
          height: '296px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">사용량 추이</div>
        <div
          className="text-[11.5px] tracking-[-.01em]"
          style={{ marginTop: '5px', width: '534px', color: 'rgba(255,236,246,.6)' }}
        >
          총 호출 수 1,248회 (+18.3%) · 가장 많이 사용 GPT-4o · 피크 14:00 ~ 16:00
        </div>
        <div className="flex" style={{ marginTop: '10px', gap: '18px' }}>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <div
              className="flex-none rounded-full"
              style={{ width: '7px', height: '7px', background: '#e9aec4' }}
            ></div>
            <div
              className="text-[11px] whitespace-nowrap"
              style={{ color: 'rgba(255,236,246,.66)' }}
            >
              사용 코인
            </div>
          </div>
          <div className="flex items-center" style={{ gap: '7px' }}>
            <div
              className="flex-none rounded-full"
              style={{ width: '7px', height: '7px', background: '#dba18b' }}
            ></div>
            <div
              className="text-[11px] whitespace-nowrap"
              style={{ color: 'rgba(255,236,246,.66)' }}
            >
              호출 수
            </div>
          </div>
        </div>

        <div className="relative" style={{ marginTop: '16px', width: '534px', height: '158px' }}>
          <div
            className="absolute top-0 left-0 bg-[rgba(255,255,255,.09)]"
            style={{ width: '534px', height: '1px' }}
          ></div>
          <div
            className="absolute left-0 bg-[rgba(255,255,255,.09)]"
            style={{ top: '40px', width: '534px', height: '1px' }}
          ></div>
          <div
            className="absolute left-0 bg-[rgba(255,255,255,.09)]"
            style={{ top: '80px', width: '534px', height: '1px' }}
          ></div>
          <div
            className="absolute left-0 bg-[rgba(255,255,255,.09)]"
            style={{ top: '120px', width: '534px', height: '1px' }}
          ></div>
          <svg
            className="absolute top-0 left-0"
            width="534"
            height="134"
            viewBox="0 0 534 146"
            preserveAspectRatio="none"
            fill="none"
            style={{ overflow: 'visible' }}
          >
            <defs>
              <linearGradient id="spendLine" x1="0" y1="1" x2="1" y2="0">
                <stop offset="0%" stopColor="#dba18b" />
                <stop offset="100%" stopColor="#e3b7c8" />
              </linearGradient>
              <linearGradient id="spendFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#ffb0cd" stopOpacity=".34" />
                <stop offset="100%" stopColor="#ffb0cd" stopOpacity="0" />
              </linearGradient>
            </defs>
            <path
              d="M0 118 L44 96 L89 108 L133 74 L178 92 L222 62 L267 78 L311 50 L356 66 L400 34 L445 52 L489 26 L534 40 L534 146 L0 146 Z"
              fill="url(#spendFill)"
            />
            <path
              d="M0 118 L44 96 L89 108 L133 74 L178 92 L222 62 L267 78 L311 50 L356 66 L400 34 L445 52 L489 26 L534 40"
              fill="none"
              stroke="url(#spendLine)"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray="1400"
              style={{ animation: 'drawUp 1.5s .35s cubic-bezier(.3,.7,.2,1) both' }}
            />
            <path
              d="M0 132 L44 124 L89 128 L133 110 L178 118 L222 100 L267 112 L311 92 L356 102 L400 80 L445 92 L489 70 L534 82"
              fill="none"
              stroke="#dba18b"
              strokeOpacity=".62"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray="1400"
              style={{ animation: 'drawUp 1.5s .5s cubic-bezier(.3,.7,.2,1) both' }}
            />
          </svg>
          <div
            className="absolute left-0 flex justify-between text-[10px]"
            style={{ top: '142px', width: '534px', color: 'rgba(255,236,246,.5)' }}
          >
            <span>08/01</span>
            <span>08/07</span>
            <span>08/13</span>
            <span>08/19</span>
            <span>08/25</span>
          </div>
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '1036px',
          top: '250px',
          width: '306px',
          height: '296px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .26s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="flex items-center justify-between">
          <div className="text-[17px] font-semibold tracking-[-.01em] text-white">코인 잔액</div>
          <div className="flex" style={{ gap: '8px' }}>
            <a
              href={paths.my.credit}
              style={{
                height: '30px',
                padding: '0 12px',
                borderRadius: '9px',
                background: 'rgba(185,141,169,.28)',
                color: '#f0dcea',
              }}
              className="flex items-center text-[11.5px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(185,141,169,.44)] hover:text-[#fff]"
            >
              예치
            </a>
            <a
              href={paths.my.credit}
              style={{
                height: '30px',
                padding: '0 12px',
                borderRadius: '9px',
                color: 'rgba(255,240,247,.86)',
              }}
              className="flex items-center bg-[rgba(255,255,255,.10)] text-[11.5px] font-semibold tracking-[-.01em] transition-colors duration-[160ms] hover:bg-[rgba(255,255,255,.2)] hover:text-[#fff]"
            >
              출금
            </a>
          </div>
        </div>
        <div className="relative" style={{ marginTop: '14px', width: '266px', height: '150px' }}>
          <svg
            className="absolute top-0 left-0"
            width="266"
            height="150"
            viewBox="0 0 266 150"
            fill="none"
          >
            <g transform="translate(133,75) rotate(-90) scale(0.76)">
              <circle r="72" fill="none" stroke="rgba(255,255,255,.12)" strokeWidth="12" />
              <circle
                r="72"
                fill="none"
                stroke="#c9a9dd"
                strokeWidth="12"
                strokeLinecap="round"
                strokeDasharray="115.81 452.39"
                style={{ animation: 'gaugeIn 1.1s .5s cubic-bezier(.3,.7,.2,1) both' }}
              />
            </g>
          </svg>
          <div className="absolute left-0 text-center" style={{ top: '56px', width: '266px' }}>
            <div
              className="text-[22px] font-semibold tracking-[-.02em] text-white"
              style={{ lineHeight: '1.1' }}
            >
              640
            </div>
            <div
              className="text-[10px]"
              style={{ marginTop: '1px', color: 'rgba(255,236,246,.6)', lineHeight: '1.2' }}
            >
              ABC 사용 가능
            </div>
          </div>
        </div>
        <div className="flex flex-col" style={{ marginTop: '14px', gap: '7px' }}>
          <div className="flex justify-between" style={{ alignItems: 'baseline' }}>
            <span className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
              예치금
            </span>
            <span className="text-[11.5px] font-semibold text-white">2,500 ABC</span>
          </div>
          <div className="flex justify-between" style={{ alignItems: 'baseline' }}>
            <span className="text-[11.5px]" style={{ color: 'rgba(255,236,246,.62)' }}>
              이번 달 사용
            </span>
            <span className="text-[11.5px] font-semibold" style={{ color: '#c9a9dd' }}>
              1,204 ABC
            </span>
          </div>
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border overflow-hidden"
        style={{
          left: '442px',
          top: '570px',
          width: '900px',
          height: '363px',
          padding: '16px 20px',
          borderRadius: '16px',
          animation: 'rise .7s .34s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[15px] font-semibold tracking-[-.01em] text-white">최근 사용 내역</div>
        <div
          className="flex text-[10.5px] font-semibold"
          style={{ marginTop: '14px', color: 'rgba(255,236,246,.55)' }}
        >
          <div style={{ width: '120px' }}>날짜/시간</div>
          <div style={{ width: '160px' }}>에이전트</div>
          <div style={{ width: '90px' }}>요청 수</div>
          <div style={{ width: '200px' }}>토큰(입력/출력)</div>
          <div style={{ width: '130px' }}>사용 코인</div>
          <div style={{ width: '80px' }}>상태</div>
        </div>
        <div className="flex flex-col" style={{ marginTop: '6px' }}>
          {history.map((h, i) => (
            <Fragment key={i}>
              <div
                style={{
                  height: '27px',
                  color: 'rgba(255,255,255,.88)',
                  fontVariantNumeric: 'tabular-nums',
                  borderRadius: '6px',
                  transition: 'background .14s',
                }}
                className="flex flex-none items-center text-[11.5px] hover:bg-[rgba(255,255,255,.07)]"
              >
                <div style={{ width: '120px' }}>{h.time}</div>
                <div style={{ width: '160px' }}>{h.agent}</div>
                <div style={{ width: '90px' }}>{h.calls}</div>
                <div style={{ width: '200px', color: 'rgba(255,255,255,.7)' }}>{h.tokens}</div>
                <div style={{ width: '130px' }}>{h.amount}</div>
                <div className="font-semibold" style={{ width: '80px', color: `${h.statusFg}` }}>
                  {h.status}
                </div>
              </div>
            </Fragment>
          ))}
        </div>

        <div className="flex" style={{ marginTop: '12px', justifyContent: 'flex-end' }}>
          <div
            style={{ gap: '6px', color: 'rgba(255,240,247,.78)', transition: 'color .16s' }}
            className="flex cursor-pointer items-center text-[11.5px] font-semibold hover:text-[#fff]"
          >
            전체 보기
            <span className="text-[12px]">→</span>
          </div>
        </div>
      </div>
    </>
  )
}
