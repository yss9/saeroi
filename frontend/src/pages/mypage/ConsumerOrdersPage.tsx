import { useNavigate } from 'react-router-dom'
import { ErrorMessage, Loading } from '../../components/common/index.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { useLogout } from '../../features/auth/hooks/useLogout.ts'
import { ConsumerAccountLayout } from '../../features/user/components/ConsumerAccountLayout.tsx'
import { ConsumerOrderList } from '../../features/user/components/ConsumerOrderList.tsx'
import { ConsumerOrderSummary } from '../../features/user/components/ConsumerOrderSummary.tsx'
import { useConsumerMyPage } from '../../features/user/hooks/useConsumerMyPage.ts'

/**
 * 주문 내역.
 *
 * **마이페이지 홈에서 떼어 낸 화면이다.** 홈에 주문을 통째로 쌓아 두면 주문이 늘어날수록
 * 배송지처럼 짧은 정보가 목록 아래로 밀려 내려간다.
 *
 * 주문 현황(상태별 건수)도 여기 있다. 목록 바로 아래에서 이어 보는 값이라, 홈에 두면
 * "몇 건이 배송 중인가" 를 확인한 뒤 다시 이 화면으로 건너와야 한다.
 *
 * 홈과 같은 훅을 쓴다. 요청이 늘지 않는다 — 같은 Query 키를 보므로 홈에서 이미 받아 둔
 * 응답을 그대로 쓴다.
 */
export function ConsumerOrdersPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const logout = useLogout()
  const profile = user ? { id: user.id, name: user.name, email: user.email } : null
  const myPage = useConsumerMyPage(profile)

  return (
    <ConsumerAccountLayout
      current="orders"
      profile={profile}
      onSignOut={() =>
        logout.mutate(undefined, { onSuccess: () => navigate('/', { replace: true }) })
      }
      signOutPending={logout.isPending}
      signOutError={logout.error}
    >
      {myPage.isPending ? (
        <div className="consumer-mypage-status">
          <Loading label="주문 내역을 불러오는 중" variant="skeleton" rows={6} />
        </div>
      ) : myPage.isError || !myPage.data ? (
        <div className="consumer-mypage-status">
          <ErrorMessage
            error={myPage.error ?? new Error('주문 정보를 확인할 수 없습니다.')}
            onRetry={() => void myPage.refetch()}
          />
        </div>
      ) : (
        <>
          {/*
            "지금 몇 건이 어디까지 왔나" 를 먼저 보고, 그 아래에서 "무엇을 샀나" 를 하나씩
            훑는다(2026-09-16 순서 변경) — 목록을 다 지나야 현황이 나오면 주문이 늘수록
            현황을 보려고 목록 전체를 지나쳐야 했다.
          */}
          <ConsumerOrderSummary stages={myPage.data.orderStages} />
          <ConsumerOrderList orders={myPage.data.recentOrders} />
        </>
      )}
    </ConsumerAccountLayout>
  )
}
