import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../hooks/useMyMaterials.ts'

/**
 * 내 보유 소재 카드 목록.
 *
 * 한 행에 4장씩 — 소재 / 종류 / 판매 가능량 / 상태만 보여준다. 나머지(보유 수량 ·
 * 잠긴 수량 · 출처 · 생성 유형 · 기준 시각)는 표 한 줄에 다 욱여넣으면 읽기 힘들어져
 * 뺐다. 그 값들은 카드를 눌러 들어가는 소재 상세(`/materials/:id`)와 보유자별
 * 잔량(`MaterialBalanceTable`)에 이미 있다 — 여기서 지우는 게 아니라 옮기는 것.
 */
export function InventoryList() {
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
    <ul className="inventory-grid">
      {data.map((item) => (
        <li key={item.materialId} className="inventory-card">
          <Link to={`/materials/${item.materialId}`} className="inventory-card__link">
            <strong className="inventory-card__name">{item.name}</strong>
            <span className="inventory-card__code">{item.materialCode}</span>
            <span className="inventory-card__type">
              {MATERIAL_TYPE_LABEL[item.materialType as keyof typeof MATERIAL_TYPE_LABEL] ??
                item.materialType}
            </span>
            <div className="inventory-card__footer">
              {/* 판매 가능량이 이 카드를 보는 이유다. 음수가 와도 0으로 깎지 않는다 —
                  잠금 합계가 보유량을 넘었다는 데이터 파손 신호이고 숨기면 원인을 못 찾는다. */}
              <span className="inventory-card__available">{formatKg(item.available)}</span>
              <StatusBadge tone={item.status === 'ACTIVE' ? 'success' : 'neutral'}>
                {item.status === 'ACTIVE' ? '보유 중' : '소진'}
              </StatusBadge>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}
