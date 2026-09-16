import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button, ConfirmModal, EmptyState } from '../../components/common/index.ts'
import { ListingForm } from '../../features/marketplace/components/ListingForm.tsx'
import { ListingList } from '../../features/marketplace/components/ListingList.tsx'
import { MaterialSelect } from '../../features/marketplace/components/MaterialSelect.tsx'
import { useCancelListing } from '../../features/marketplace/hooks/useCancelListing.ts'
import { useListingChainAction } from '../../features/marketplace/hooks/useListingChainAction.ts'
import { ListingChainCancelButton } from '../../features/marketplace/components/ListingChainCancelButton.tsx'
import { useMyMaterials } from '../../features/inventory/hooks/useMyMaterials.ts'
import type { Listing } from '../../features/marketplace/types/marketplace.types.ts'
import type { InventoryItem } from '../../features/inventory/types/inventory.types.ts'
import { useAuthStore } from '../../stores/authStore.ts'

/**
 * Supplier Market — 소재를 파는 쪽.
 *
 * 판매 등록과 내 판매글 관리를 한 화면에서 한다. 등록 직후 아래 목록에 바로 나타나야
 * "등록됐나?" 를 확인하러 다른 화면으로 갈 필요가 없다 —
 * useCreateListing 이 목록 캐시를 무효화하므로 자동으로 갱신된다.
 *
 * 소재 선택 상태를 페이지가 들고 있는 이유 — 고른 소재의 **가용 수량**이 폼의 최대값이
 * 되어야 하고, 등록에 성공하면 그 수량이 줄어들어 선택을 초기화해야 한다. 폼 안에 두면
 * 폼이 보유 목록까지 알아야 한다.
 */
export function SupplierMarketPage() {
  // 판매 글 전체를 든다 — 취소는 온체인 번호까지 알아야 체인의 잠금을 풀 수 있다.
  const [cancelTarget, setCancelTarget] = useState<Listing | null>(null)
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<InventoryItem | null>(null)
  const { mutate: cancel, isPending: isCanceling } = useCancelListing()
  const chain = useListingChainAction()

  // 내 판매글만 본다. 서버가 이 필터를 강제하지 않는 것은 의도적이다 — 판매자별 보기는
  // 권한이 아니라 필터이고, 어차피 전체 목록에 다 나와 있는 정보다.
  const sellerId = useAuthStore((state) => state.user?.id)

  // 소재 상세("보유 소재 팔기")에서 ?materialId= 로 넘어온 경우 그 소재를 미리 골라 둔다.
  // MaterialSelect 가 이미 같은 쿼리 키로 목록을 받으므로 여기서 또 불러도 요청은
  // 한 번만 나간다 — TanStack Query 캐시 공유.
  //
  // useEffect 로 하지 않는다. 데이터를 늦게 받아와 반영하는 것은 "렌더 중 상태 조정"이지
  // 외부 시스템 동기화가 아니다 — 이펙트에서 setState를 부르면 불필요한 리렌더가
  // 한 번 더 낀다 (react-hooks/set-state-in-effect). 대신 목록이 도착한 렌더에서
  // 바로 처리하고, 처리했다는 사실만 다음 렌더를 위해 남긴다.
  const [searchParams] = useSearchParams()
  const initialMaterialId = Number(searchParams.get('materialId'))
  const { data: myMaterials } = useMyMaterials()
  const [appliedMaterialId, setAppliedMaterialId] = useState<number | null>(null)

  if (
    Number.isInteger(initialMaterialId) &&
    initialMaterialId > 0 &&
    myMaterials !== undefined &&
    appliedMaterialId !== initialMaterialId
  ) {
    setAppliedMaterialId(initialMaterialId)
    const match = myMaterials.find((item) => item.materialId === initialMaterialId)
    if (match) setSelected(match)
  }

  return (
    <main className="page">
        <h1 className="ui-visually-hidden">내 판매 관리</h1>

      <section className="page__section">
        <h2>판매 등록</h2>
        <MaterialSelect value={selected?.materialId ?? null} onChange={setSelected} />

        {/* 소재를 고르기 전에는 폼을 띄우지 않는다. 수량 입력의 최대값이 고른 소재의
            가용량이라, 소재 없이 먼저 입력하게 하면 그 값을 검증할 기준이 없다. */}
        {selected === null ? (
          <EmptyState
            title="판매할 소재를 먼저 선택하세요"
            description="선택한 소재의 판매 가능 수량 안에서 등록할 수 있습니다."
          />
        ) : (
          <ListingForm
            // key 로 소재를 넣어 소재를 바꾸면 폼이 새로 마운트된다. 그러지 않으면
            // 이전 소재에 맞춰 입력한 수량이 남아 새 소재의 가용량을 넘길 수 있다.
            key={selected.materialId}
            materialId={selected.materialId}
            materialName={selected.name}
            availableQuantity={selected.available}
            // 등록하면 그 소재의 가용량이 줄어든다. 선택을 비워 사용자가 갱신된 목록에서
            // 다시 고르게 한다 — 낡은 가용량으로 연속 등록하면 두 번째가 서버에서 막힌다.
            onCreated={() => setSelected(null)}
          />
        )}
      </section>

      <section className="page__section">
        <h2>등록한 판매글</h2>
        <ListingList
          params={{ sellerId, page }}
          onPageChange={setPage}
          viewer="seller"
          emptyTitle="등록한 판매글이 없습니다"
          emptyDescription="위에서 보유 소재를 판매 등록할 수 있습니다."
          renderActions={(listing) =>
            // 이미 내린 글에는 취소 버튼 대신, 체인에 잠금이 남아 있으면 그것을 푸는
            // 자리를 준다. 서명을 놓친 글은 이 자리가 없으면 영영 정리되지 않고,
            // 그 소재는 다시 팔 수 없다.
            listing.status === 'CANCELED' ? (
              <ListingChainCancelButton listing={listing} />
            ) : (
              <Button variant="secondary" size="sm" onClick={() => setCancelTarget(listing)}>
                판매 취소
              </Button>
            )
          }
        />
      </section>

      {/* 취소는 되돌릴 수 없다. 수정 기능이 없어 재등록해야 하므로 한 번 더 묻는다. */}
      <ConfirmModal
        open={cancelTarget !== null}
        title="판매를 취소할까요?"
        message="취소하면 남은 수량의 판매가 종료되고 다시 등록해야 합니다. 이미 진행 중인 거래는 영향을 받지 않습니다."
        confirmLabel="판매 취소"
        destructive
        loading={isCanceling || chain.isBusy}
        onConfirm={() => {
          if (cancelTarget === null) return
          const chainListingId = cancelTarget.chainListingId
          // 취소 사유를 보내지 않는다 — material_listing 에 사유 컬럼이 없다.
          cancel(cancelTarget.listingId, {
            // **서버 취소가 끝나면 체인의 잠금도 풀어야 한다.**
            //
            // 판매 등록은 체인에서 소재 잔액을 잠갔고, 그것을 푸는 것은 cancelListing
            // 뿐이다. 여기서 놓치면 DB 는 풀렸는데 체인은 잠긴 채라 같은 소재를 다시
            // 팔 수 없다 — Marketplace: insufficient available balance 로 막힌다.
            onSuccess: () => {
              if (chainListingId !== null) void chain.cancel(chainListingId)
            },
            onSettled: () => setCancelTarget(null),
          })
        }}
        onClose={() => setCancelTarget(null)}
      />
    </main>
  )
}
