import { useParams } from 'react-router-dom'
import { ErrorMessage, Loading, VerificationBadge } from '../../components/common/index.ts'
import { useMaterial } from '../../features/material/hooks/useMaterial.ts'

export function MaterialDetailPage() {
  const { materialId } = useParams<{ materialId: string }>()

  // 라우트 파라미터는 항상 문자열이고, 사용자가 URL 을 직접 고칠 수 있다.
  // 숫자로 파싱되지 않으면 null 을 넘겨 조회 자체를 막는다 (useMaterial 의 enabled).
  const parsed = Number(materialId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { data, isPending, isError, error, refetch } = useMaterial(id)

  if (id === null) return <ErrorMessage error="잘못된 소재 주소입니다." />
  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  return (
    <main className="page">
      <header>
        <h1>{data.name}</h1>
        <VerificationBadge state={data.verificationState} />
      </header>
      <p>{data.description}</p>
      <dl>
        <dt>소재 코드</dt>
        <dd>{data.materialCode}</dd>
        <dt>총 수량</dt>
        <dd>{data.totalQuantity.toLocaleString('ko-KR')}</dd>
      </dl>
    </main>
  )
}
