import { useState } from 'react'
import { ErrorMessage } from '../../components/common/index.ts'
import { ProductionBatchSelect } from '../../features/production/components/ProductionBatchSelect.tsx'
import { useAssignProductModel } from '../../features/production/hooks/useAssignProductModel.ts'
import {
  ProductCreatedSummary,
  ProductForm,
} from '../../features/product/components/ProductForm.tsx'
import type { ProductModel } from '../../features/product/types/product.types.ts'

/**
 * 제품 등록 (Brand).
 *
 * 등록이 끝나면 폼을 비우고 결과를 위에 남긴다 — 브랜드는 제품을 여러 개 연달아 올리므로
 * 등록마다 다른 화면으로 보내면 매번 돌아와야 한다.
 *
 * **생산 배치를 함께 고른다.** 제품 등록만으로는 팔 물건이 없고, 개체를 만드는 것은
 * 배치다. 다만 연결은 등록이 **끝난 뒤에야** 할 수 있다 — 배치에 적어 넣을 제품 번호가
 * 그때 생기기 때문이다. 그래서 폼에서는 고르기만 하고, 등록 응답을 받는 즉시 잇는다.
 *
 * 두 기능(product · production)을 이 화면이 조합한다. 기능끼리는 서로를 모른다.
 */
export function ProductRegisterPage() {
  // 방금 등록한 제품. 여러 개를 올릴 수 있어 마지막 것만 보여준다.
  const [created, setCreated] = useState<ProductModel | null>(null)
  const [batchId, setBatchId] = useState<number | null>(null)
  const assign = useAssignProductModel()

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">제품 등록</h1>

      {created && <ProductCreatedSummary product={created} />}

      {/*
        연결 실패는 등록 실패가 아니다. 제품은 이미 저장됐고 배치만 비어 있으므로, 폼을
        되돌리지 않고 여기서 사실만 알린다 — 배치 상세에서 다시 이을 수 있다.
      */}
      {assign.isError && <ErrorMessage error={assign.error} />}
      {assign.isSuccess && (
        <p className="page__lead" role="status">
          {assign.data.batchName} 배치에 연결했습니다. 그 배치를 확정하면 제품 개체와 QR 이
          만들어집니다.
        </p>
      )}

      <ProductForm
        extraFields={<ProductionBatchSelect value={batchId} onChange={setBatchId} />}
        onCreated={(product) => {
          setCreated(product)
          if (batchId === null) return
          assign.mutate(
            { batchId, productModelId: product.productModelId },
            // 이은 배치는 후보에서 빠진다. 고른 값을 남겨 두면 다음 제품을 등록할 때
            // 이미 남의 것이 된 배치가 골라진 채로 보인다.
            { onSuccess: () => setBatchId(null) },
          )
        }}
      />
    </main>
  )
}
