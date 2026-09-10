import { Fragment, useState } from 'react'
import Sidebar from '../components/Sidebar'

export default function MyPageSales() {
  const [tab, setTab] = useState('customer')

  const metrics = [
    { label: '총 사용 횟수', value: '12,648회', delta: '+21.7%' },
    { label: '총매출', value: '2,456 ABC', delta: '정산 대기 52.8 ABC 포함' },
    { label: '출금 가능', value: '2,403.2 ABC', delta: '지금 바로 출금 가능' },
    { label: '활성 사용자 수', value: '1,248명', delta: '+16.2%' },
  ].map((m, i) => ({
    ...m,
    anim: 'rise .7s ' + (0.14 + i * 0.04).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
  }))
  const tokens = [
    { label: '입력 토큰', value: '10.2M (54.8%)', width: '55%', color: '#e9aec4' },
    { label: '출력 토큰', value: '6.7M (36.0%)', width: '36%', color: '#dba18b' },
    { label: '임베딩 토큰', value: '1.7M (9.2%)', width: '9%', color: '#a094c2' },
  ].map((t, i) => ({
    ...t,
    anim: 'growBar .8s ' + (0.4 + i * 0.12).toFixed(2) + 's cubic-bezier(.2,.8,.25,1) both',
  }))
  const fg = (s: string) => (s === '정산 완료' ? '#9ad0b4' : '#ffb0cd')
  const byCustomer = {
    columns: [
      { label: '고객', w: '300px' },
      { label: '사용 횟수', w: '130px' },
      { label: '토큰 사용량', w: '160px' },
      { label: '결제 코인', w: '160px' },
      { label: '상태', w: '110px' },
    ],
    data: [
      ['SmartBuild Inc.', '1,248', '1.93M', '256.8 ABC', '정산 완료'],
      ['AI Research Lab', '986', '1.42M', '189.4 ABC', '정산 완료'],
      ['DataFlow Systems', '754', '1.08M', '145.6 ABC', '정산 완료'],
      ['Nova Analytics', '689', '0.98M', '118.2 ABC', '정산 대기'],
      ['Orbit Labs', '542', '0.76M', '82.4 ABC', '정산 완료'],
    ],
  }
  const byEntry = {
    columns: [
      { label: '일시', w: '150px' },
      { label: '고객', w: '280px' },
      { label: '사용 횟수', w: '130px' },
      { label: '획득 코인', w: '190px' },
      { label: '상태', w: '110px' },
    ],
    data: [
      ['05.24 14:32', 'SmartBuild Inc.', '28회', '45.6 ABC', '정산 완료'],
      ['05.24 11:08', 'DataFlow Systems', '16회', '36.8 ABC', '정산 완료'],
      ['05.23 21:33', 'AI Research Lab', '31회', '59.2 ABC', '정산 완료'],
      ['05.23 15:47', 'Nova Analytics', '22회', '41.2 ABC', '정산 대기'],
      ['05.22 09:12', 'Orbit Labs', '18회', '33.4 ABC', '정산 완료'],
    ],
  }
  const byCustomerTab = tab === 'customer'
  const view = byCustomerTab ? byCustomer : byEntry
  const dim = byCustomerTab ? 2 : 0
  const rows = view.data.map((cells) => ({
    cells: cells.map((text, i) => ({
      text,
      w: view.columns[i].w,
      color:
        i === cells.length - 1
          ? fg(text)
          : i === dim
            ? 'rgba(255,255,255,.68)'
            : 'rgba(255,255,255,.88)',
      weight: i === cells.length - 1 || i === 3 ? '600' : '400',
    })),
  }))
  const LILAC = '#c9a9dd'
  const columns = view.columns
  const fgCustomer = byCustomerTab ? '#fff' : 'rgba(255,240,247,.6)'
  const fgEntry = byCustomerTab ? 'rgba(255,240,247,.6)' : '#fff'
  const barCustomer = byCustomerTab ? LILAC : 'transparent'
  const barEntry = byCustomerTab ? 'transparent' : LILAC
  const handlePickByCustomer = () => setTab('customer')
  const handlePickByEntry = () => setTab('entry')

  return (
    <>
      <Sidebar active="sales" />

      <div
        className="absolute text-[30px] font-semibold tracking-[-.02em] text-white"
        style={{
          left: '442px',
          top: '158px',
          textShadow: '0 2px 20px rgba(35,8,45,.6)',
          animation: 'rise .7s .06s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        판매 내역
      </div>
      <div
        className="absolute text-[13px] tracking-[-.01em]"
        style={{
          left: '442px',
          top: '206px',
          width: '700px',
          color: 'rgba(255,236,246,.7)',
          animation: 'rise .7s .1s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        내 에이전트가 사용된 만큼 적립된 ABC 코인과 사용량, 정산 상태를 확인합니다.
      </div>

      <div
        style={{
          left: '1214px',
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
        className="absolute flex"
        style={{ left: '442px', top: '246px', width: '900px', gap: '16px' }}
      >
        {metrics.map((m, i) => (
          <Fragment key={i}>
            <div
              className="bg-surface-card box-border flex flex-none flex-col justify-center"
              style={{
                width: '212px',
                height: '140px',
                padding: '18px',
                borderRadius: '16px',
                animation: `${m.anim}`,
              }}
            >
              <div>
                <div
                  className="text-[12.5px] tracking-[-.01em]"
                  style={{ color: 'rgba(255,236,246,.62)', lineHeight: '1.3' }}
                >
                  {m.label}
                </div>
                <div
                  className="text-[26px] font-semibold tracking-[-.02em] text-white"
                  style={{ marginTop: '10px', lineHeight: '1.1' }}
                >
                  {m.value}
                </div>
                <div
                  className="text-[11.5px]"
                  style={{ marginTop: '8px', color: '#dcaebf', lineHeight: '1.2' }}
                >
                  {m.delta}
                </div>
              </div>
            </div>
          </Fragment>
        ))}
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '442px',
          top: '400px',
          width: '574px',
          height: '244px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .22s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">사용량 추이</div>
        <div className="relative" style={{ marginTop: '18px', width: '534px', height: '158px' }}>
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
              <linearGradient id="salesLine" x1="0" y1="1" x2="1" y2="0">
                <stop offset="0%" stopColor="#dba18b" />
                <stop offset="100%" stopColor="#e3b7c8" />
              </linearGradient>
              <linearGradient id="salesFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#ffb0cd" stopOpacity=".34" />
                <stop offset="100%" stopColor="#ffb0cd" stopOpacity="0" />
              </linearGradient>
            </defs>
            <path
              d="M0 122 L48 100 L97 112 L145 76 L194 94 L242 58 L291 76 L339 48 L388 64 L436 30 L485 46 L534 20 L534 146 L0 146 Z"
              fill="url(#salesFill)"
            />
            <path
              d="M0 122 L48 100 L97 112 L145 76 L194 94 L242 58 L291 76 L339 48 L388 64 L436 30 L485 46 L534 20"
              fill="none"
              stroke="url(#salesLine)"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeDasharray="1400"
              style={{ animation: 'drawUp 1.5s .35s cubic-bezier(.3,.7,.2,1) both' }}
            />
          </svg>
          <div
            className="absolute left-0 flex justify-between text-[10px]"
            style={{ top: '142px', width: '534px', color: 'rgba(255,236,246,.5)' }}
          >
            <span>04/25</span>
            <span>05/03</span>
            <span>05/11</span>
            <span>05/19</span>
            <span>05/23</span>
          </div>
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border"
        style={{
          left: '1036px',
          top: '400px',
          width: '306px',
          height: '244px',
          padding: '20px',
          borderRadius: '16px',
          animation: 'rise .7s .26s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="text-[17px] font-semibold tracking-[-.01em] text-white">토큰 사용량</div>
        <div
          className="text-[26px] font-semibold tracking-[-.02em] text-white"
          style={{ marginTop: '10px', lineHeight: '1.2' }}
        >
          18.6M
        </div>
        <div className="text-[11px]" style={{ marginTop: '4px', color: 'rgba(255,236,246,.6)' }}>
          총 토큰
        </div>
        <div className="flex flex-col" style={{ marginTop: '14px', gap: '10px' }}>
          {tokens.map((t, i) => (
            <Fragment key={i}>
              <div>
                <div className="flex justify-between" style={{ alignItems: 'baseline' }}>
                  <div className="text-[11px]" style={{ color: 'rgba(255,236,246,.62)' }}>
                    {t.label}
                  </div>
                  <div className="text-[11px] font-semibold text-white">{t.value}</div>
                </div>
                <div
                  className="overflow-hidden bg-[rgba(255,255,255,.09)]"
                  style={{ marginTop: '5px', width: '266px', height: '7px', borderRadius: '4px' }}
                >
                  <div
                    style={{
                      width: `${t.width}`,
                      height: '7px',
                      borderRadius: '4px',
                      background: `${t.color}`,
                      transformOrigin: 'left center',
                      animation: `${t.anim}`,
                    }}
                  ></div>
                </div>
              </div>
            </Fragment>
          ))}
        </div>
      </div>

      <div
        className="bg-surface-card absolute box-border overflow-hidden"
        style={{
          left: '442px',
          top: '665px',
          width: '900px',
          height: '268px',
          padding: '16px 20px',
          borderRadius: '16px',
          animation: 'rise .7s .3s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="flex items-center justify-between" style={{ height: '30px' }}>
          <div className="flex" style={{ gap: '20px', height: '30px' }}>
            <div
              className="relative flex cursor-pointer items-start text-[14px] font-semibold tracking-[-.01em]"
              onClick={handlePickByCustomer}
              style={{ height: '30px', color: `${fgCustomer}`, transition: 'color .16s' }}
            >
              고객별
              <div
                className="absolute left-0"
                style={{
                  bottom: '0',
                  width: '100%',
                  height: '2px',
                  borderRadius: '1px',
                  background: `${barCustomer}`,
                }}
              ></div>
            </div>
            <div
              className="relative flex cursor-pointer items-start text-[14px] font-semibold tracking-[-.01em]"
              onClick={handlePickByEntry}
              style={{ height: '30px', color: `${fgEntry}`, transition: 'color .16s' }}
            >
              건별
              <div
                className="absolute left-0"
                style={{
                  bottom: '0',
                  width: '100%',
                  height: '2px',
                  borderRadius: '1px',
                  background: `${barEntry}`,
                }}
              ></div>
            </div>
          </div>
          <div className="flex items-center" style={{ gap: '14px' }}>
            <div className="flex items-center" style={{ gap: '7px' }}>
              <div
                className="rounded-full"
                style={{ width: '7px', height: '7px', background: '#84bf9f' }}
              ></div>
              <div
                className="text-[11.5px]"
                style={{ color: 'rgba(255,255,255,.86)', fontVariantNumeric: 'tabular-nums' }}
              >
                정산 완료 2,403.2 ABC
              </div>
            </div>
            <div className="flex items-center" style={{ gap: '7px' }}>
              <div
                className="rounded-full"
                style={{ width: '7px', height: '7px', background: '#e9aec4' }}
              ></div>
              <div
                className="text-[11.5px]"
                style={{ color: 'rgba(255,255,255,.86)', fontVariantNumeric: 'tabular-nums' }}
              >
                정산 대기 52.8 ABC
              </div>
            </div>
          </div>
        </div>

        <div
          className="flex text-[10.5px] font-semibold"
          style={{ marginTop: '14px', color: 'rgba(255,236,246,.55)' }}
        >
          {columns.map((c, i) => (
            <Fragment key={i}>
              <div style={{ width: `${c.w}` }}>{c.label}</div>
            </Fragment>
          ))}
        </div>

        <div className="flex flex-col" style={{ marginTop: '6px' }}>
          {rows.map((r, i) => (
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
                {r.cells.map((c, i) => (
                  <Fragment key={i}>
                    <div
                      className="overflow-hidden whitespace-nowrap"
                      style={{
                        width: `${c.w}`,
                        color: `${c.color}`,
                        fontWeight: `${c.weight}`,
                        textOverflow: 'ellipsis',
                      }}
                    >
                      {c.text}
                    </div>
                  </Fragment>
                ))}
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
