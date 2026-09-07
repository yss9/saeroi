import { useState } from 'react'
import { Button, ConfirmModal } from '../../components/common/index.ts'
import { ListingForm } from '../../features/marketplace/components/ListingForm.tsx'
import { ListingList } from '../../features/marketplace/components/ListingList.tsx'
import { useCancelListing } from '../../features/marketplace/hooks/useCancelListing.ts'

/**
 * Supplier Market — 소재를 파는 쪽.
 *
 * 판매 등록과 내 판매글 관리를 한 화면에서 한다. 등록 직후 아래 목록에 바로 나타나야
 * "등록됐나?" 를 확인하러 다른 화면으로 갈 필요가 없다 —
 * useCreateListing 이 목록 캐시를 무효화하므로 자동으로 갱신된다.
 *
 * TODO(2번/Material 담당과 협의): 지금은 판매할 소재를 고르는 UI 가 없다.
 * 내 보유 소재 목록(`GET /materials/me`)이 나오면 선택 UI 를 붙이고
 * materialId·가용 수량을 거기서 가져온다. 그때까지는 고정값으로 폼을 보여준다.
 */
const PLACEHOLDER_MATERIAL = {
  id: 1,
  name: '세척된 폐데님',
  availableQuantity: 18_000,
} as const

export function SupplierMarketPage() {
  const [cancelTarget, setCancelTarget] = useState<number | null>(null)
  const { mutate: cancel, isPending: isCanceling } = useCancelListing()

  return (
    <main className="page">
      <header className="page__header">
        <h1>내 판매 관리</h1>
        <p className="page__lead">보유한 소재를 마켓플레이스에 등록하고 판매 상태를 관리합니다.</p>
      </header>

      <section className="page__section">
        <h2>판매 등록</h2>
        <ListingForm
          materialId={PLACEHOLDER_MATERIAL.id}
          materialName={PLACEHOLDER_MATERIAL.name}
          availableQuantity={PLACEHOLDER_MATERIAL.availableQuantity}
        />
      </section>

      <section className="page__section">
        <h2>등록한 판매글</h2>
        <ListingList
          emptyTitle="등록한 판매글이 없습니다"
          emptyDescription="위에서 보유 소재를 판매 등록할 수 있습니다."
          renderActions={(listingId) => (
            <Button variant="secondary" size="sm" onClick={() => setCancelTarget(listingId)}>
              판매 취소
            </Button>
          )}
        />
      </section>

      {/* 취소는 되돌릴 수 없다. 수정 기능이 없어 재등록해야 하므로 한 번 더 묻는다. */}
      <ConfirmModal
        open={cancelTarget !== null}
        title="판매를 취소할까요?"
        message="취소하면 남은 수량의 판매가 종료되고 다시 등록해야 합니다. 이미 진행 중인 거래는 영향을 받지 않습니다."
        confirmLabel="판매 취소"
        destructive
        loading={isCanceling}
        onConfirm={() => {
          if (cancelTarget === null) return
          cancel(
            { listingId: cancelTarget, reason: '판매자 취소' },
            { onSettled: () => setCancelTarget(null) },
          )
        }}
        onClose={() => setCancelTarget(null)}
      />
    </main>
  )
}
