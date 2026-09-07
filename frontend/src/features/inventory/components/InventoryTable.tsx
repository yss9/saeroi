import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../hooks/useMyMaterials.ts'

/**
 * 내 보유 소재 표.
 *
 * 티켓(S15P21E104-195)이 요구한 표시 항목 5개에 1:1 대응한다 —
 * Material / 보유 수량 / 단위 / 상태 / 판매 가능량.
 *
 * <b>"단위" 는 별도 컬럼이 아니라 수량에 붙는다.</b> 서버에 단위 필드가 없고
 * (소재 수량은 gram 정수가 원칙이다) 값마다 단위가 달라질 수 없으므로,
 * `kg` 를 따로 한 칸 차지하게 두면 모든 행에 같은 글자가 반복될 뿐이다.
 * `formatKg()` 가 `20.5 kg` 로 함께 내보낸다.
 *
 * <b>보유 수량은 `quantity` 다.</b> `totalQuantity`(총 발행량)를 쓰면
 * "1200kg 을 갖고 있다" 는 거짓이 되고, 그게 판매 등록으로 이어지면 초과 판매다.
 * 총 발행량은 소재 상세에서 보면 되므로 이 표에 넣지 않는다.
 */
export function InventoryTable() {
  const { data, isPending, isError, error, refetch } = useMyMaterials()

  if (isPending) return <Loading variant="skeleton" rows={4} label="보유 소재를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.length === 0) {
    return (
      <EmptyState
        title="보유한 소재가 없습니다"
        description="소재를 등록하면 Material Token이 발행되고 보유 수량이 여기 표시됩니다."
        action={<Link to="/supplier/materials/new">소재 등록하기</Link>}
      />
    )
  }

  return (
    <table className="inventory-table">
      <caption>보유 소재</caption>
      <thead>
        <tr>
          <th scope="col">소재</th>
          <th scope="col">보유 수량</th>
          <th scope="col">잠긴 수량</th>
          <th scope="col">판매 가능량</th>
          <th scope="col">상태</th>
          <th scope="col">기준 시각</th>
        </tr>
      </thead>
      <tbody>
        {data.map((item) => (
          <tr key={item.materialId}>
            <td>
              <Link to={`/materials/${item.materialId}`}>{item.name}</Link>
              <span className="inventory-table__code">{item.materialCode}</span>
            </td>
            <td>{formatKg(item.quantity)}</td>
            <td>{formatKg(item.lockedQuantity)}</td>
            {/* 음수를 그대로 보여준다. 잠금 합계가 보유량을 넘었다는 파손 신호다. */}
            <td>{formatKg(item.available)}</td>
            <td>
              <StatusBadge tone={item.status === 'ACTIVE' ? 'success' : 'neutral'}>
                {item.status === 'ACTIVE' ? '보유 중' : '소진'}
              </StatusBadge>
            </td>
            <td>
              <time dateTime={item.syncedAt}>{formatSyncedAt(item.syncedAt)}</time>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/**
 * 기준 시각.
 *
 * 이 값을 빼면 "보유 소재가 없다" 와 "동기화가 한 번도 돌지 않았다" 를 구분할 수 없다.
 * `material_balance` 를 채우는 동기화(-376)가 아직 막혀 있어 지금 특히 중요하다.
 */
function formatSyncedAt(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
}
