import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import {
  GENERATION_TYPE_LABEL,
  GRADE_LABEL,
  MATERIAL_TYPE_LABEL,
  ROOT_ORIGIN_LABEL,
} from '../../material/types/material.types.ts'
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
    // 표는 자기 안에서만 가로 스크롤한다 — 컬럼이 여섯 개라 좁은 화면에서
    // 페이지 전체가 밀리면 안 된다.
    <div className="ui-table-scroll">
      <table className="ui-table inventory-table">
        <caption className="ui-visually-hidden">보유 소재</caption>
        <thead>
          <tr>
            <th scope="col">소재</th>
            <th scope="col" className="is-tight">종류</th>
            {/* 출처와 생성 유형을 한 칸에 둔다 — 둘은 함께 읽어야 뜻이 생긴다.
                "폐기물 · 최초 등록" 은 원물이고 "폐기물 · 가공 산출물" 은 이미 한 번
                손이 간 소재다 (화면 설계서 S05). */}
            <th scope="col" className="is-tight">출처 · 생성</th>
            <th scope="col" className="is-numeric">보유 수량</th>
            <th scope="col" className="is-numeric">잠긴 수량</th>
            <th scope="col" className="is-numeric">판매 가능량</th>
            <th scope="col" className="is-tight">상태</th>
            <th scope="col" className="is-tight">기준 시각</th>
          </tr>
        </thead>
        <tbody>
          {data.map((item) => (
            <tr key={item.materialId}>
              <td>
                <Link to={`/materials/${item.materialId}`}>{item.name}</Link>
                <span className="inventory-table__code">{item.materialCode}</span>
              </td>
              <td className="is-tight">
                {MATERIAL_TYPE_LABEL[item.materialType as keyof typeof MATERIAL_TYPE_LABEL] ??
                  item.materialType}
                {/* 등급은 Supplier 의 자가 신고다 — 검증된 값처럼 보이지 않게 작게 둔다. */}
                {item.grade !== null && (
                  <span className="inventory-table__grade" title={GRADE_LABEL[item.grade as 'A']}>
                    {item.grade}등급
                  </span>
                )}
              </td>
              <td className="is-tight inventory-table__origin">
                {ROOT_ORIGIN_LABEL[item.rootOrigin as keyof typeof ROOT_ORIGIN_LABEL] ??
                  item.rootOrigin}
                {' · '}
                {GENERATION_TYPE_LABEL[
                  item.generationType as keyof typeof GENERATION_TYPE_LABEL
                ] ?? item.generationType}
              </td>
              <td className="is-numeric">{formatKg(item.quantity)}</td>
              <td className="is-numeric">{formatKg(item.lockedQuantity)}</td>
              {/* 음수를 그대로 보여준다. 잠금 합계가 보유량을 넘었다는 파손 신호다.
                  판매 가능량은 이 표에서 사용자가 가장 자주 보는 값이라 강조한다. */}
              <td className="is-numeric inventory-table__available">
                {formatKg(item.available)}
              </td>
              <td className="is-tight">
                <StatusBadge tone={item.status === 'ACTIVE' ? 'success' : 'neutral'}>
                  {item.status === 'ACTIVE' ? '보유 중' : '소진'}
                </StatusBadge>
              </td>
              <td className="is-tight inventory-table__synced">
                <time dateTime={item.syncedAt}>{formatSyncedAt(item.syncedAt)}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
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
