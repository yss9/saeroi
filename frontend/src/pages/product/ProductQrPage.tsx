import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  BackLink,
  EmptyState,
  ErrorMessage,
  Loading,
  Select,
} from '../../components/common/index.ts'
import { ProductionItemQrGrid } from '../../features/production/components/ProductionItemQrGrid.tsx'
import { useMyProductionBatches } from '../../features/production/hooks/useMyProductionBatches.ts'
import type { ProductionBatch } from '../../features/production/types/production.types.ts'
import { useProductModel } from '../../features/product/hooks/useProductModel.ts'

/**
 * 제품의 QR 라벨 미리보기.
 *
 * **제품 관리 화면에서 오는 입구다.** QR 은 개체마다 붙고 개체는 생산 배치가 만들지만,
 * 브랜드의 일은 "이 제품 라벨을 뽑는다" 로 시작한다 — 그 제품이 어느 배치에서 생산됐는지
 * 기억해야 라벨에 닿는 것은 순서가 뒤바뀐 것이다. 여기서 제품을 기준으로 배치를 찾아 준다.
 *
 * **두 도메인을 잇는 화면이라 페이지가 조립한다** (ARCHITECTURE §12.1). 제품 이름은
 * `product` 에서, 배치와 개체는 `production` 에서 온다 — 둘 중 한쪽 컴포넌트가 다른 쪽
 * 훅을 직접 부르면 feature 경계가 무너진다.
 *
 * **`/brand` 안에 둔다.** 개체의 `qrToken` 은 비회원 공개 경로의 열쇠라 서버도 배치
 * 소유자에게만 준다. 업무 상세(`/business/products/:id`)는 로그인한 누구나 보는 화면이므로 QR 을
 * 그쪽에 두지 않는다.
 */
export function ProductQrPage() {
  const { productModelId } = useParams<{ productModelId: string }>()
  const parsed = Number(productModelId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const product = useProductModel(id)
  const batches = useMyProductionBatches()

  // 확정된 배치만 개체를 갖는다. 작성 중인 배치는 아직 라벨이 없다.
  //
  // **내 배치 목록에서 고른다.** 서버가 남의 배치를 주지 않으므로, 다른 브랜드의 제품
  // 번호를 주소에 넣어도 "라벨 없음" 이 되고 개체나 토큰은 새지 않는다.
  const confirmed = useMemo(
    () => selectConfirmed(batches.data, id),
    [batches.data, id],
  )

  const [chosen, setChosen] = useState<number | null>(null)
  // 고르기 전에는 가장 최근 배치를 본다. effect 로 초기값을 넣으면 데이터가 도착한 뒤
  // 한 프레임 동안 아무 배치도 선택되지 않은 화면이 스친다.
  const selected = confirmed.find((batch) => batch.productionBatchId === chosen) ?? confirmed[0]

  return (
    <main className="page">
      <BackLink to="/brand/products" label="내 제품" />

      <header className="page__header">
        <h1>QR 라벨{product.data ? ` · ${product.data.name}` : ''}</h1>
      </header>

      {id === null ? (
        <ErrorMessage error="잘못된 주소입니다." />
      ) : (
        <Content
          batches={confirmed}
          isPending={batches.isPending}
          isError={batches.isError}
          error={batches.error}
          onRetry={() => void batches.refetch()}
          selected={selected}
          onSelect={setChosen}
        />
      )}
    </main>
  )
}

/**
 * 확정된 내 배치 중 이 제품의 것.
 *
 * 확정이 최근인 순서로 둔다 — 라벨을 뽑는 대상은 거의 항상 방금 생산한 배치다.
 */
function selectConfirmed(
  batches: readonly ProductionBatch[] | undefined,
  productModelId: number | null,
): ProductionBatch[] {
  if (!batches || productModelId === null) return []
  return batches
    .filter((batch) => batch.productModelId === productModelId && batch.status === 'CONFIRMED')
    .sort((left, right) => (right.confirmedAt ?? '').localeCompare(left.confirmedAt ?? ''))
}

function Content({
  batches,
  isPending,
  isError,
  error,
  onRetry,
  selected,
  onSelect,
}: {
  batches: readonly ProductionBatch[]
  isPending: boolean
  isError: boolean
  error: unknown
  onRetry: () => void
  selected: ProductionBatch | undefined
  onSelect: (batchId: number) => void
}) {
  if (isPending) return <Loading variant="skeleton" rows={3} label="생산 배치를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={onRetry} />

  // 결과 없음과 에러를 구분한다 (ARCHITECTURE §8). 여기는 "아직 생산하지 않았다" 이고,
  // 사용자가 할 수 있는 일이 있다 — 제품 생산으로 보낸다.
  if (selected === undefined) {
    return (
      <EmptyState
        title="아직 만들어진 QR 이 없습니다"
        description="생산 배치를 확정하면 개체마다 QR 이 생성됩니다. 작성 중인 배치는 개체가 없어 라벨도 없습니다."
        action={<Link to="/brand/production">제품 생산으로 이동</Link>}
      />
    )
  }

  return (
    <>
      {/*
        배치가 하나면 고를 것이 없다. 그래도 어느 배치인지는 적는다 — 같은 제품을 여러 번
        생산하면 라벨은 배치마다 다른 개체를 가리키고, 잘못된 배치의 라벨을 인쇄하면
        물건과 이력이 어긋난다.
      */}
      {batches.length > 1 ? (
        <Select
          label="생산 배치"
          options={batches.map((batch) => ({
            value: String(batch.productionBatchId),
            label: batchLabel(batch),
          }))}
          value={String(selected.productionBatchId)}
          onChange={(event) => onSelect(Number(event.target.value))}
        />
      ) : (
        <p className="page__lead">생산 배치 · {batchLabel(selected)}</p>
      )}

      <ProductionItemQrGrid batchId={selected.productionBatchId} />
    </>
  )
}

/** 배치를 사람이 알아볼 수 있게. 코드만으로는 어느 생산이었는지 떠오르지 않는다. */
function batchLabel(batch: ProductionBatch): string {
  const confirmedOn = batch.confirmedAt?.slice(0, 10) ?? '확정일 미상'
  return `${batch.batchName} (${batch.batchCode}) · ${confirmedOn} 확정 · ${batch.producedQuantity}개`
}
