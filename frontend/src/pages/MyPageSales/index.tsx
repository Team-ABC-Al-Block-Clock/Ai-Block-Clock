import { useState } from 'react'

import ListHeader from '@/components/mypage/ListHeader'
import ListMessage from '@/components/mypage/ListMessage'
import ListRow from '@/components/mypage/ListRow'
import ListTabs from '@/components/mypage/ListTabs'
import MoreButton from '@/components/mypage/MoreButton'
import MyPageFrame from '@/components/mypage/MyPageFrame'
import MyPageHeader from '@/components/mypage/MyPageHeader'
import PeriodMenu from '@/components/mypage/PeriodMenu'
import Sidebar from '@/components/mypage/Sidebar'
import { PERIODS } from '@/lib/stats/period'

import { TABS, type SalesTab } from './config'
import MetricCard from './MetricCard'
import SalesTrendCard from './SalesTrendCard'
import ShareCard from './ShareCard'
import { useSalesData } from './useSalesData'

export default function MyPageSales() {
  const [tab, setTab] = useState<SalesTab>('entry')
  const [periodAt, setPeriodAt] = useState(1)

  const period = PERIODS[periodAt]
  const sales = useSalesData({ days: period.days, tab })

  return (
    <MyPageFrame>
      <Sidebar active="sales" />

      <MyPageHeader
        title="판매 내역"
        description="내 에이전트를 판매한 정산금과 출금할 수 있는 금액을 확인합니다."
        descriptionWidth={700}
      />

      <PeriodMenu at={periodAt} onPick={setPeriodAt} />

      <div
        className="absolute flex"
        style={{ left: '442px', top: '246px', width: '900px', gap: '16px' }}
      >
        {sales.metrics.map((m, i) => (
          <MetricCard key={i} {...m} index={i} />
        ))}
      </div>

      <SalesTrendCard
        line={sales.line}
        ticks={sales.ticks}
        periodLabel={period.label}
        noSales={sales.noSales}
        isError={sales.statisticsError}
      />

      <ShareCard topAmount={sales.topAmount} topName={sales.topName} shares={sales.shares} />

      <div
        className="bg-surface-card absolute box-border overflow-hidden"
        style={{
          left: '442px',
          top: '665px',
          width: '900px',
          height: '330px',
          padding: '16px 20px',
          borderRadius: '16px',
          animation: 'rise .7s .3s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <div className="flex items-center justify-between" style={{ height: '30px' }}>
          <ListTabs tabs={TABS} active={tab} onPick={setTab} />
          <div className="text-[13px]" style={{ color: 'rgba(255,236,246,.55)' }}>
            {period.label}
          </div>
        </div>

        <ListHeader>
          {sales.columns.map((c, i) => (
            <div key={i} style={{ width: c.w }}>
              {c.label}
            </div>
          ))}
        </ListHeader>

        <div
          className="flex flex-col overflow-y-auto"
          style={{ marginTop: '8px', height: '196px' }}
        >
          {sales.rows.map((r, i) => (
            <ListRow key={i}>
              {r.cells.map((c, j) => (
                <div
                  key={j}
                  className="overflow-hidden whitespace-nowrap"
                  style={{
                    width: c.w,
                    color: c.color,
                    fontWeight: c.weight,
                    textOverflow: 'ellipsis',
                  }}
                >
                  {c.text}
                </div>
              ))}
            </ListRow>
          ))}
          {sales.isPending && <ListMessage>정산 내역을 불러오는 중입니다.</ListMessage>}
          {sales.isError && <ListMessage error>정산 내역을 불러오지 못했습니다.</ListMessage>}
          {sales.isEmpty && <ListMessage>{period.label}에 받은 정산금이 없습니다.</ListMessage>}
        </div>

        <MoreButton
          show={sales.hasMore}
          isFetching={sales.isFetchingMore}
          onMore={sales.fetchMore}
        />
      </div>
    </MyPageFrame>
  )
}
