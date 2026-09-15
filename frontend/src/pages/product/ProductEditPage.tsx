import { useNavigate, useParams } from 'react-router-dom'
import { BackLink, ErrorMessage, Loading } from '../../components/common/index.ts'
import { ProductForm } from '../../features/product/components/ProductForm.tsx'
import { useProductModel } from '../../features/product/hooks/useProductModel.ts'

/**
 * 제품 수정 (Brand).
 *
 * **오타 하나를 고칠 방법이 없었다.** 가격을 잘못 넣으면 결제가 통째로 실패하는데도
 * 제품을 새로 등록하는 것 말고는 손쓸 수가 없었고, 새로 등록하면 이미 만든 개체가 옛
 * 제품에 붙는다.
 *
 * 등록과 같은 폼을 쓴다 — 필드 구성과 검증 규칙이 같아서, 화면을 나누면 판매가 상한 같은
 * 규칙이 한쪽에만 붙는 일이 생긴다.
 *
 * **단종된 제품은 서버가 수정을 거절한다**(`PRODUCT_MODEL_DISCONTINUED`). 목록에서 수정
 * 링크를 감추지만, 주소를 직접 열었을 때도 이유를 말해 준다.
 */
export function ProductEditPage() {
  const { productModelId } = useParams<{ productModelId: string }>()
  const navigate = useNavigate()
  const parsed = Number(productModelId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { data, isPending, isError, error, refetch } = useProductModel(id)

  return (
    <main className="page">
      <BackLink to="/brand/products" label="내 제품" />

      <header className="page__header">
        <h1>제품 수정</h1>
        <p className="page__lead">
          제품명·판매가·설명·대표 이미지를 고칠 수 있습니다. 카테고리와 판매 상태는 바꿀 수
          없습니다.
        </p>
      </header>

      {id === null ? (
        <ErrorMessage error="잘못된 주소입니다." />
      ) : isPending ? (
        <Loading variant="skeleton" rows={4} label="제품을 불러오는 중" />
      ) : isError ? (
        <ErrorMessage error={error} onRetry={() => void refetch()} />
      ) : data.status === 'DISCONTINUED' ? (
        <ErrorMessage error="단종된 제품은 수정할 수 없습니다." />
      ) : (
        <ProductForm
          product={data}
          // 고치고 나면 목록으로 돌려보낸다. 폼에 남겨 두면 저장됐는지 알 수 없다.
          onUpdated={() => navigate('/brand/products')}
        />
      )}
    </main>
  )
}
