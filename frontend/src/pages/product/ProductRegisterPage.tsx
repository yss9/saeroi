import { useState } from 'react'
import { ErrorMessage, Modal } from '../../components/common/index.ts'
import { ProductionBatchChooser } from '../../features/production/components/ProductionBatchChooser.tsx'
import { useAssignProductModel } from '../../features/production/hooks/useAssignProductModel.ts'
import { useChangeProductModelStatus } from '../../features/product/hooks/useChangeProductModelStatus.ts'
import type { ProductionBatch } from '../../features/production/types/production.types.ts'
import {
  ProductCreatedSummary,
  ProductForm,
} from '../../features/product/components/ProductForm.tsx'
import type { ProductModel } from '../../features/product/types/product.types.ts'

/**
 * 제품 등록 (Brand).
 *
 * **생산 목록이 입구다.** 제품 등록만으로는 팔 물건이 없고 개체를 만드는 것은 생산이다 —
 * 생산을 고르고 "이걸로 무엇을 만들었나" 를 적는 순서가 실제 일의 순서다. 생산 없이 제품만
 * 올리는 길은 두지 않는다. 그렇게 만든 제품은 재고가 0 이라 팔 수 없고, 어차피 생산으로
 * 돌아와 이어야 한다.
 *
 * 폼은 팝업으로 연다. 목록과 폼을 한 화면에 쌓으면 배치를 고르고도 어느 배치에 적는지가
 * 위로 밀려 올라가 버린다.
 *
 * 연결은 등록이 **끝난 뒤에야** 할 수 있다. 배치에 적어 넣을 제품 번호가 그때 생기기
 * 때문이다 — 그래서 고르기만 해 두었다가 등록 응답을 받는 즉시 잇는다.
 *
 * 두 기능(product · production)을 이 화면이 조합한다. 기능끼리는 서로를 모른다.
 */
export function ProductRegisterPage() {
  // 지금 제품을 적고 있는 생산. `null` 이면 창이 닫혀 있다.
  const [batch, setBatch] = useState<ProductionBatch | null>(null)
  // 방금 등록한 제품. 창을 닫은 뒤에도 결과가 목록 위에 남는다.
  const [created, setCreated] = useState<ProductModel | null>(null)
  const assign = useAssignProductModel()
  const putOnSale = useChangeProductModelStatus()

  function open(next: ProductionBatch) {
    setCreated(null)
    assign.reset()
    putOnSale.reset()
    setBatch(next)
  }

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 등록</h1>

      {created && <ProductCreatedSummary product={created} />}

      {/*
        연결 실패는 등록 실패가 아니다. 제품은 이미 저장됐고 생산만 비어 있으므로, 사실만
        알린다 — 생산은 나중에 다시 이을 수 있다. 판매 시작 실패도 같다.
      */}
      {assign.isError && <ErrorMessage error={assign.error} />}
      {putOnSale.isError && <ErrorMessage error={putOnSale.error} />}

      <ProductionBatchChooser onChoose={open} />

      {/* 열렸을 때만 그린다 — 닫아 둔 폼에 지난번 입력이 남는다. */}
      {batch !== null && (
        <Modal open title="제품 등록" subtitle={batch.batchName} onClose={() => setBatch(null)}>
          <ProductForm
            onCreated={(product) => {
              setCreated(product)
              setBatch(null)
              assign.mutate(
                {
                  batchId: batch.productionBatchId,
                  productModelId: product.productModelId,
                },
                {
                  onSuccess: (linked) => {
                    // **확정된 생산에 붙였으면 곧바로 판매를 시작한다.** 그 생산의 개체가
                    // 이 제품의 재고가 되므로 팔 수 있고, 등록하자마자 파는 것이 브랜드가
                    // 하려던 일이다. 작성 중인 생산이면 재고가 0 이라 서버가
                    // PRODUCT_MODEL_HAS_NO_STOCK 으로 거절한다 — 보내지 않는다.
                    if (linked.status !== 'CONFIRMED') return
                    putOnSale.mutate(
                      { productModelId: product.productModelId, action: 'ON_SALE' },
                      { onSuccess: setCreated },
                    )
                  },
                },
              )
            }}
          />
        </Modal>
      )}
    </main>
  )
}
