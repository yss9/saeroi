import { useParams } from 'react-router-dom'
import { BrandProcessingRequestDetail } from '../../features/processing/components/BrandProcessingRequestDetail.tsx'

export function BrandProcessingRequestDetailPage() {
  const { requestId } = useParams()
  const parsedRequestId = Number(requestId)
  const isValid = Number.isSafeInteger(parsedRequestId) && parsedRequestId > 0

  return isValid
    ? <BrandProcessingRequestDetail requestId={parsedRequestId} />
    : <div className="br-detail__state br-detail__state--error" role="alert">올바르지 않은 요청 번호입니다.</div>
}
