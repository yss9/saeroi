import { useParams } from 'react-router-dom'
import { ProcessingJobDetail } from '../../features/processing/components/ProcessingJobDetail.tsx'

export function ProcessingJobDetailPage() {
  const value=Number(useParams().jobId)
  return Number.isSafeInteger(value)&&value>0?<ProcessingJobDetail jobId={value}/>:<section className="pj-state pj-state--error">올바르지 않은 작업 번호입니다.</section>
}
