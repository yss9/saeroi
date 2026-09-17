import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button, EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { useMyMaterials } from '../hooks/useMyMaterials.ts'
import type { BalanceStatus, InventoryItem } from '../types/inventory.types.ts'

/**
 * 보유 상태 필터.
 *
 * 서버에 상태별로 필터링해 달라고 요청하는 파라미터가 없다 (`getMyMaterials()` 는
 * 전체를 받아온다) — 여기서는 이미 받아온 배열을 클라이언트에서만 거른다.
 */
const FILTERS: readonly { label: string; status?: BalanceStatus }[] = [
  { label: '전체' },
  { label: '보유 중', status: 'ACTIVE' },
  { label: '소진', status: 'DEPLETED' },
]

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
  // 상태 필터는 이 컴포넌트 안에서만 쓰는 값이라 useState 다.
  const [status, setStatus] = useState<BalanceStatus | undefined>(undefined)

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

  // 필터도 같은 잣대로 고른다. 서버 status 로 거르면 "소진" 을 눌러도 0g 짜리가 안 나온다.
  const filtered = status
    ? data.filter((item) => (status === 'DEPLETED' ? isDepleted(item) : !isDepleted(item)))
    : data

  return (
    <>
      <div className="inventory-filters" role="group" aria-label="보유 상태 필터">
        {FILTERS.map((filter) => {
          const selected = filter.status === status
          return (
            <Button
              key={filter.label}
              size="sm"
              variant={selected ? 'primary' : 'secondary'}
              aria-pressed={selected}
              onClick={() => setStatus(filter.status)}
            >
              {filter.label}
            </Button>
          )
        })}
      </div>

      {filtered.length === 0 ? (
        <EmptyState title="조건에 맞는 소재가 없습니다" />
      ) : (
        <ul className="inventory-grid">
          {filtered.map((item) => (
            <li
              key={item.materialId}
              // 소진된 소재는 뒤로 물린다. 목록에서 찾는 것은 지금 쓸 수 있는 소재다.
              className={`inventory-card${isDepleted(item) ? ' inventory-card--used-up' : ''}`}
            >
              <Link to={`/materials/${item.materialId}`} className="inventory-card__link">
                {/* 상태는 맨 위 왼쪽. 카드 아래에 있으면 여러 장을 훑을 때마다 시선이
                    끝까지 내려갔다 올라와야 한다. */}
                <div className="inventory-card__status">
                  <StatusBadge tone={isDepleted(item) ? 'neutral' : 'success'}>
                    {isDepleted(item) ? '소진' : '보유 중'}
                  </StatusBadge>
                </div>
                <strong className="inventory-card__name">{item.name}</strong>
                {/*
                  소재 코드는 적지 않는다. 카드에서 고르는 기준은 이름이고, 코드가
                  이름 바로 아래 붙어 있으면 이름보다 먼저 눈에 걸린다. 상세에는 있다.
                */}
                <span className="inventory-card__type">
                  {MATERIAL_TYPE_LABEL[item.materialType as keyof typeof MATERIAL_TYPE_LABEL] ??
                    item.materialType}
                </span>
                <div className="inventory-card__footer">
                  {/* 판매 가능량이 이 카드를 보는 이유다. 음수가 와도 0으로 깎지 않는다 —
                      잠금 합계가 보유량을 넘었다는 데이터 파손 신호이고 숨기면 원인을 못 찾는다. */}
                  <span className="inventory-card__available">{formatKg(item.available)}</span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}

/**
 * 다 쓴 소재인가.
 *
 * **보유량으로 판정한다.** 서버의 `status` 는 아직 `DEPLETED` 로 넘어가지 않는다 —
 * 전환 메서드가 없어(`MaterialStatus` 주석) 0g 이 된 소재도 `ACTIVE` 로 온다. 그 값을
 * 그대로 적으면 0kg 짜리 소재가 "보유 중" 으로 보인다.
 *
 * **`available` 이 아니라 `quantity` 를 본다.** 판매글이나 가공에 전량이 잠긴 소재는
 * `available` 이 0 이지만 없어진 것이 아니다 — 잠금이 풀리면 돌아온다. 소진이라고 적으면
 * 되찾을 수 있는 소재를 잃은 것으로 읽는다.
 *
 * 서버가 `DEPLETED` 를 보내기 시작해도 그 판정을 존중한다.
 */
function isDepleted(item: InventoryItem): boolean {
  return item.status === 'DEPLETED' || item.quantity <= 0
}
