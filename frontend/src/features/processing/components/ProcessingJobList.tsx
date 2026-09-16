import { ArrowRight, Factory, Package } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { useProcessingJobs } from '../hooks/useProcessingJobs.ts'
import './processing-jobs.css'

const labels = { CREATED:'결제 대기', PAID:'발송 대기', SHIPPED:'수령 대기', RECEIVED:'가공 시작 대기', PROCESSING:'가공 중', RESULT_SUBMITTED:'검토 대기', CONFIRMED:'완료', REJECTED:'보완 요청', EXPIRED:'만료' } as const

export function ProcessingJobList() {
  const jobs = useProcessingJobs()
  if (jobs.isPending) return <section className="pj-state">가공 작업을 불러오는 중이에요…</section>
  if (jobs.isError) return <section className="pj-state pj-state--error">작업 목록을 불러오지 못했어요.<button onClick={() => void jobs.refetch()}>다시 시도</button></section>
  return <section className="pj-list">
    <header><h1 className="ui-visually-hidden">가공 작업</h1><Factory size={30} /></header>
    {!jobs.data?.length ? <div className="pj-state"><Package size={36}/><strong>진행 중인 가공 작업이 없어요</strong></div> :
      <div className="pj-grid">{jobs.data.map(job => <Link className="pj-card" to={`/processing/jobs/${job.jobId}`} key={job.jobId}>
        <div><span>{job.jobCode}</span><b className={`is-${job.status.toLowerCase()}`}>{labels[job.status]}</b></div>
        <strong>투입 소재 #{job.inputMaterialId}</strong>
        <p>{job.inputQuantity.toLocaleString('ko-KR')} g · {job.fee.toLocaleString('ko-KR')}원</p>
        <small>작업 상세 및 처리 <ArrowRight /></small>
      </Link>)}</div>}
  </section>
}
