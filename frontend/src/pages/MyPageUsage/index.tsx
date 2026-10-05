import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { getCreditBalance } from '@/api/credits'
import ListHeader from '@/components/mypage/ListHeader'
import ListMessage from '@/components/mypage/ListMessage'
import ListTabs from '@/components/mypage/ListTabs'
import MoreButton from '@/components/mypage/MoreButton'
import MyPageFrame from '@/components/mypage/MyPageFrame'
import MyPageHeader from '@/components/mypage/MyPageHeader'
import PeriodMenu from '@/components/mypage/PeriodMenu'
import Sidebar from '@/components/mypage/Sidebar'
import { queryKeys } from '@/lib/queryKeys'
import { PERIODS, rangeOf } from '@/lib/stats/period'

import BalanceCard from './BalanceCard'
import { LIST_TABS, type ListTab } from './config'
import TransactionRow from './TransactionRow'
import UsageTrendCard from './UsageTrendCard'
import { useUsageLists } from './useUsageLists'
import { useUsageTrend } from './useUsageTrend'
import WalletRow from './WalletRow'

export default function MyPageUsage() {
  const [periodAt, setPeriodAt] = useState(1)
  const [listTab, setListTab] = useState<ListTab>('usage')

  const period = PERIODS[periodAt]
  const range = rangeOf(period.days)

  const balance = useQuery({
    queryKey: queryKeys.credits.balance(),
    queryFn: getCreditBalance,
  })
  const trend = useUsageTrend(range)
  const lists = useUsageLists({ range, listTab })
  const walletTab = listTab === 'wallet'

  return (
    <MyPageFrame>
      <Sidebar active="usage" />

      <MyPageHeader
        title="사용 내역"
        description="ABC 사용량과 잔액, 충전·출금 내역을 확인합니다."
        descriptionWidth={620}
      />

      <PeriodMenu at={periodAt} onPick={setPeriodAt} />

      <UsageTrendCard trend={trend} periodLabel={period.label} />

      <BalanceCard
        available={balance.data?.availableAmount}
        reserved={balance.data?.reservedAmount}
      />

      <div
        className="bg-surface-card absolute box-border overflow-hidden"
        style={{
          left: '442px',
          top: '570px',
          width: '900px',
          height: '425px',
          padding: '16px 20px',
          borderRadius: '16px',
          animation: 'rise .7s .34s cubic-bezier(.2,.8,.25,1) both',
        }}
      >
        <ListTabs tabs={LIST_TABS} active={listTab} onPick={setListTab} />
        <ListHeader>
          <div style={{ width: '170px' }}>날짜/시간</div>
          <div style={{ width: '120px' }}>{walletTab ? '구분' : '유형'}</div>
          <div style={{ width: '190px' }}>금액</div>
          <div style={{ width: '220px' }}>{walletTab ? '상태' : '실행'}</div>
          <div style={{ width: '160px' }}>트랜잭션</div>
        </ListHeader>
        <div
          className="flex flex-col overflow-y-auto"
          style={{ marginTop: '8px', height: '292px' }}
        >
          {walletTab && lists.walletRows.map((h) => <WalletRow key={h.type + h.id} item={h} />)}
          {!walletTab &&
            lists.history.map((h) => <TransactionRow key={h.transactionId} item={h} />)}

          {lists.isPending && <ListMessage>내역을 불러오는 중입니다.</ListMessage>}
          {lists.isError && <ListMessage error>내역을 불러오지 못했습니다.</ListMessage>}
          {lists.isEmpty && (
            <ListMessage>
              {walletTab
                ? '아직 충전 · 출금 내역이 없습니다.'
                : `${period.label}에 사용 내역이 없습니다.`}
            </ListMessage>
          )}
        </div>

        <MoreButton
          show={lists.hasMore}
          isFetching={lists.isFetchingMore}
          onMore={lists.fetchMore}
        />
      </div>
    </MyPageFrame>
  )
}
