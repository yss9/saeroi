import { EmptyState, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { shortenAddress } from '../../../utils/address.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMaterialBalances } from '../hooks/useMaterialBalances.ts'

export interface MaterialBalanceTableProps {
  materialId: number | null
}

/**
 * 소재의 보유자별 잔량.
 *
 * Page 가 아니라 Feature Component 가 데이터를 가져온다 — Page 는 배치만 한다.
 * 로딩 / 에러 / 빈 결과를 각각 구분해 다룬다.
 *
 * <b>수량은 전부 kg 로 표시한다.</b> 서버는 gram 정수를 준다. 그대로 보여주면
 * `20,500` 이 20.5kg 인지 20500kg 인지 알 수 없다 — 1000배 오해가 나는 지점이다.
 * 변환은 `formatKg()` 한 곳에서만 한다 (부동소수점 방어가 들어 있다).
 *
 * <b>`available` 을 다시 계산하지 않는다.</b> 어떤 잠금 상태를 빼는지(ACTIVE 만)는
 * 서버 규칙이고, 양쪽에서 계산하면 값이 갈라진다. 음수가 와도 0 으로 깎지 않는다 —
 * 잠금 합계가 보유량을 넘었다는 데이터 파손 신호이고 숨기면 원인을 못 찾는다.
 */
export function MaterialBalanceTable({ materialId }: MaterialBalanceTableProps) {
  const { data, isPending, isError, error, refetch } = useMaterialBalances(materialId)

  if (isPending) return <Loading variant="skeleton" rows={2} label="보유 현황을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.length === 0) {
    return (
      <EmptyState
        title="보유자가 없습니다"
        // "아직 아무도 안 가졌다" 와 "동기화가 안 됐다" 는 다른 상황이다.
        // 지금은 후자일 가능성이 높아 그 사실을 숨기지 않는다.
        description="온체인 잔액 동기화가 아직 반영되지 않았을 수 있습니다."
      />
    )
  }

  return (
    <table className="material-balance-table">
      <caption>보유자별 잔량</caption>
      <thead>
        <tr>
          <th scope="col">보유자</th>
          <th scope="col">보유 수량</th>
          <th scope="col">잠긴 수량</th>
          <th scope="col">판매 가능량</th>
          <th scope="col">기준 시각</th>
        </tr>
      </thead>
      <tbody>
        {data.map((balance) => (
          <tr key={balance.ownerId}>
            <td>{shortenAddress(balance.walletAddress)}</td>
            <td>{formatKg(balance.quantity)}</td>
            <td>{formatKg(balance.lockedQuantity)}</td>
            {/* 음수를 그대로 보여준다. Math.max(0, ...) 로 감추지 않는다. */}
            <td>{formatKg(balance.available)}</td>
            <td>
              <time dateTime={balance.syncedAt}>{formatSyncedAt(balance.syncedAt)}</time>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/**
 * 기준 시각 표시.
 *
 * 이 값을 빼면 "보유자가 없다" 와 "동기화가 한 번도 돌지 않았다" 를 구분할 수 없다.
 * 서버는 `LocalDateTime` 을 오프셋 없이 준다 — 브라우저가 로컬 시간으로 해석하는데,
 * 지금은 팀·서버·사용자가 모두 KST 라 문제가 되지 않는다. 타임존이 갈리면 그때 다룬다.
 */
function formatSyncedAt(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
}
