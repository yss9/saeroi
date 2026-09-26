import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../../components/common/index.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'
import type { ProcessingJobStatus } from '../../processing/types/processing.types.ts'
import { useProcessingCustodyMaterials } from '../hooks/useProcessingCustodyMaterials.ts'
import './processing-custody.css'

const STATUS_LABEL: Partial<Record<ProcessingJobStatus, string>> = {
  SHIPPED: '배송 중',
  RECEIVED: '입고 완료',
  PROCESSING: '가공 중',
  RESULT_SUBMITTED: '결과 검토 중',
  REJECTED: '보완 작업 중',
}

export function ProcessingCustodyList() {
  const isProcessor = useAuthStore((state) => state.user?.role === 'PROCESSOR')
  const custody = useProcessingCustodyMaterials(isProcessor)

  if (!isProcessor) return null

  return (
    <section className="processing-custody" aria-labelledby="processing-custody-title">
      <header>
        <h2 id="processing-custody-title">가공 보관 소재</h2>
        <span>소유권은 Brand에 유지됩니다</span>
      </header>
      {custody.isPending ? (
        <Loading variant="skeleton" rows={2} label="가공 보관 소재를 불러오는 중" />
      ) : custody.isError ? (
        <ErrorMessage error={custody.error} onRetry={() => void custody.refetch()} />
      ) : custody.data.length === 0 ? (
        <EmptyState title="가공을 위해 보관 중인 소재가 없습니다" />
      ) : (
        <ul className="inventory-grid">
          {custody.data.map((item) => (
            <li key={item.jobId} className="inventory-card processing-custody__card">
              <Link to={`/processing/jobs/${item.jobId}`} className="inventory-card__link">
                <strong className="inventory-card__name">{item.materialName}</strong>
                <span className="inventory-card__code">{item.materialCode}</span>
                <span className="inventory-card__type">
                  {MATERIAL_TYPE_LABEL[item.materialType as keyof typeof MATERIAL_TYPE_LABEL] ?? item.materialType}
                </span>
                <div className="inventory-card__footer">
                  <span className="inventory-card__available">{formatKg(item.quantity)}</span>
                  <StatusBadge tone={item.status === 'SHIPPED' ? 'info' : 'success'}>
                    {STATUS_LABEL[item.status] ?? item.status}
                  </StatusBadge>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}