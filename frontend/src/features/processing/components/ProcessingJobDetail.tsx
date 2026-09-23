import { ArrowLeft, CheckCircle, Factory, Package, Plus, Trash, X } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '../../../stores/authStore.ts'
import { EvidenceUpload } from '../../evidence/components/EvidenceUpload.tsx'
import { EVIDENCE_TYPE_LABEL } from '../../evidence/types/evidence.types.ts'
import { GRADE_LABEL, type MaterialGrade } from '../../material/types/material.types.ts'
import { MATERIAL_TYPE_LABEL } from '../types/processorProfile.types.ts'
import { PROCESS_TYPE_LABEL } from '../types/processing.types.ts'
import { useProcessingJob } from '../hooks/useProcessingJobs.ts'
import { PROCESSING_CHAIN_STATUS } from '../services/processingContractService.ts'
import type { ProcessingJobDetail as ProcessingJob } from '../api/processingJobApi.ts'
import type { ProcessingRequestDetail } from '../api/processingRequestApi.ts'
import { ProcessingShipmentForm } from './ProcessingShipmentForm.tsx'
import { formatDateTime } from '../utils/processingTime.ts'
import './processing-jobs.css'


const materialGrades = Object.keys(GRADE_LABEL) as MaterialGrade[]
const statusLabel = { CREATED:'결제 대기', PAID:'수락 대기', ACCEPTED:'발송 대기', DECLINED:'가공사 거절', SHIPPED:'배송 중', RECEIVED:'가공 시작 대기', PROCESSING:'가공 중', RESULT_SUBMITTED:'결과 반환 대기', RETURN_SHIPPED:'반환 배송 중', CONFIRMED:'가공 완료', REJECTED:'결과 보완', EXPIRED:'만료', CANCELED:'반환 종료' } as const
/**
 * 진행 막대의 칸. **순서가 곧 상태의 순서다** — 지금 상태가 몇 번째인지로 어디까지 칠할지
 * 정한다.
 *
 * 이름은 과정을 적는다(누가 하는지가 아니라). 같은 막대를 브랜드와 가공사가 함께 보고,
 * 무엇을 기다리는 중인지는 아래 칸들이 각자 말한다.
 */
const PROGRESS_STEPS = [
  { status: 'PAID', label: '결제' },
  { status: 'ACCEPTED', label: '가공사 수락' },
  { status: 'SHIPPED', label: '소재 발송' },
  { status: 'RECEIVED', label: '수령' },
  { status: 'PROCESSING', label: '가공' },
  { status: 'RESULT_SUBMITTED', label: '결과 제출' },
  { status: 'RETURN_SHIPPED', label: '반환 배송' },
  { status: 'CONFIRMED', label: '수령 확인' },
] as const

// 산출 소재 종류는 여기 없다. 투입 소재에서 그대로 따라오므로 폼이 담을 값이 아니다.
type Row = { grade: MaterialGrade; gradeReason: string; name: string; quantity: string }
const emptyRow = (): Row => ({ grade:'A', gradeReason:'', name:'', quantity:'' })

export function ProcessingJobDetail({ jobId }: { jobId:number }) {
  const role = useAuthStore(state => state.user?.role)
  const flow = useProcessingJob(jobId)
  if (flow.detail.isPending) return <section className="pj-state">작업 상세를 불러오는 중이에요…</section>
  if (flow.detail.isError || !flow.detail.data) return <section className="pj-state pj-state--error">작업 상세를 불러오지 못했어요.<button onClick={() => void flow.detail.refetch()}>다시 시도</button></section>
  const job = flow.detail.data
  return <ProcessingJobDetailContent key={`${job.jobId}-${job.resultRevision}-${job.status}`} jobId={jobId} job={job} request={flow.request.data} role={role} flow={flow}/>
}

function ProcessingJobDetailContent({ jobId, job, request, role, flow }: {
  jobId: number
  job: ProcessingJob
  request: ProcessingRequestDetail | undefined
  role: string | undefined
  flow: ReturnType<typeof useProcessingJob>
}) {
  const [rows,setRows] = useState<Row[]>(() => job.outputs.length > 0
    ? job.outputs.map(output => ({ grade:output.grade, gradeReason:output.gradeReason, name:output.name, quantity:String(output.quantity) }))
    : [emptyRow()])
  const [reason,setReason] = useState('')
  // 거절은 사유가 필수라 한 번 더 묻는다 — 눌러서 바로 끝나는 일이 아니다.
  const [declining,setDeclining] = useState(false)
  const [declineReason,setDeclineReason] = useState('')
  const evidenceOptions = (flow.evidence?.data?.files ?? []).map(file => ({
    id: file.evidenceFileId,
    hash: file.fileHash,
    label: `${EVIDENCE_TYPE_LABEL[file.evidenceType]} · ${file.storageKey.split('/').at(-1) ?? file.storageKey}`,
  }))
  const pending=Boolean(flow.initializeChain?.isPending||flow.accept?.isPending||flow.acceptJob?.isPending||flow.declineJob?.isPending||flow.ship.isPending||flow.receive.isPending||flow.start.isPending||flow.saveResult.isPending||flow.submit.isPending||flow.shipReturn?.isPending||flow.approve.isPending||flow.reject.isPending||flow.returnRejected?.isPending)
  const error=flow.initializeChain?.error||flow.accept?.error||flow.acceptJob?.error||flow.declineJob?.error||flow.ship.error||flow.receive.error||flow.start.error||flow.saveResult.error||flow.submit.error||flow.shipReturn?.error||flow.approve.error||flow.reject.error||flow.returnRejected?.error
  const outputSum=rows.reduce((sum,row)=>sum+(Number.isSafeInteger(Number(row.quantity))&&Number(row.quantity)>0?Number(row.quantity):0),0)
  const lossQuantity=Math.max(job.inputQuantity-outputSum,0)
  const excessQuantity=Math.max(outputSum-job.inputQuantity,0)
  const hasInvalidOutput=rows.some(row=>!row.name.trim()||!row.gradeReason.trim()||!Number.isSafeInteger(Number(row.quantity))||Number(row.quantity)<=0)
  const cannotSave=pending||excessQuantity>0||hasInvalidOutput
  const save=()=>flow.saveResult.mutate({outputs:rows.map(row=>({...row,name:row.name.trim(),gradeReason:row.gradeReason.trim(),quantity:Number(row.quantity)}))})
  const counterparty = role === 'PROCESSOR' ? request?.brandName : request?.processorName
  return <section className="pj-detail">
    <Link to={role==='PROCESSOR'?'/processor/jobs':'/brand/processing/jobs'}><ArrowLeft/>목록으로</Link>
    <header><div><h1>{request ? `${request.materialName} 가공` : '가공 작업 상세'}</h1>{request&&<><p><strong>{counterparty}</strong> · {request.quantity.toLocaleString()} g</p><div className="pj-request-processes">{request.processTypes.map(type=><b key={type}>{PROCESS_TYPE_LABEL[type]}</b>)}</div></>}</div><b className={`pj-status is-${job.status.toLowerCase()}`}>{statusLabel[job.status]}</b></header>
    {/*
      **숫자만 두면 무슨 과정인지 알 수 없다.** 브랜드는 이 화면에 "지금 어디까지 왔나" 를
      보러 오는데, 동그라미 여섯 개로는 3번이 수령인지 가공인지 알 길이 없었다.

      반환 배송을 칸으로 넣은 이유 — 그 상태는 단계 목록에 없어서 `indexOf` 가 -1 이 되고,
      다 차 있던 표시가 통째로 회색으로 풀렸다. 정작 브랜드가 수령 확인을 해야 하는 구간이다.
    */}
    <ol className="pj-progress">{PROGRESS_STEPS.map((step,index)=>{
      const current=PROGRESS_STEPS.findIndex(s=>s.status===job.status)
      return <li key={step.status} className={current>=index?'is-done':''} aria-current={current===index?'step':undefined}>
        <span className="pj-progress__mark">{index+1}</span>
        <span className="pj-progress__label">{step.label}</span>
      </li>
    })}</ol>
    <section className="pj-panel"><h2><Package/>투입 정보</h2><dl><div><dt>소재</dt><dd>{request?<><strong>{request.materialName}</strong><small>{request.materialCode}</small></>:'소재 정보 확인 중'}</dd></div><div><dt>투입 수량</dt><dd>{job.inputQuantity.toLocaleString()} g</dd></div><div><dt>가공비</dt><dd>{job.fee.toLocaleString()}원</dd></div><div><dt>Loss</dt><dd>{job.lossQuantity.toLocaleString()} g</dd></div></dl></section>

    {/*
      **결제 다음은 가공사의 대답이다.** 맡을지 말지를 정하기 전에 브랜드가 소재를 보내면,
      거절당했을 때 물건이 남의 창고에 가 있다 — 발송은 수락 뒤에만 열린다(서버도 409 로 막는다).
    */}
    {role==='PROCESSOR'&&job.status==='PAID'&&<section className="pj-panel pj-action"><div><p>결제가 끝난 요청입니다. 맡으시면 브랜드가 소재를 발송합니다.</p>{job.acceptExpiresAt&&<p className="pj-help">{formatDateTime(job.acceptExpiresAt)}까지 답하지 않으면 자동으로 거절되고 가공비가 환불됩니다.</p>}</div><div className="pj-decision"><button className="pj-secondary" disabled={pending} onClick={()=>setDeclining(true)}>거절</button><button disabled={pending} onClick={()=>flow.acceptJob.mutate()}>요청 수락</button></div></section>}

    {role==='PROCESSOR'&&job.status==='PAID'&&declining&&<section className="pj-panel"><h2><X/>요청 거절</h2><p>사유는 브랜드에게 그대로 보입니다. 거절하면 가공비가 환불되고 예약된 소재 수량을 다시 사용할 수 있습니다.</p><div className="pj-review"><input aria-label="거절 사유" maxLength={500} placeholder="예: 설비 점검 중입니다" value={declineReason} onChange={e=>setDeclineReason(e.target.value)}/><button className="is-danger" disabled={pending||!declineReason.trim()} onClick={()=>flow.declineJob.mutate(declineReason.trim())}>거절하기</button></div></section>}

    {role==='BRAND'&&job.status==='PAID'&&<section className="pj-panel pj-action"><p>가공업체의 수락을 기다리고 있습니다.{job.acceptExpiresAt?` ${formatDateTime(job.acceptExpiresAt)}까지 답이 없으면 자동으로 환불됩니다.`:''}</p></section>}

    {job.status==='DECLINED'&&<section className="pj-panel"><h2><X/>가공사가 거절했습니다</h2><p className="pj-warning">{job.declineReason??'가공업체가 기한 안에 답하지 않아 자동으로 종료되었습니다.'}</p>{job.refundedAt&&<p className="pj-help">{formatDateTime(job.refundedAt)}에 가공비가 환불되었습니다. 잠긴 소재 수량도 풀렸습니다.</p>}</section>}

    {role==='BRAND'&&job.status==='ACCEPTED'&&!job.chainJobId&&<section className="pj-panel pj-action"><p>가공업체가 요청을 수락했습니다. 발송 전에 투입 소재와 작업 정보를 기록해 주세요.</p><button disabled={pending} onClick={()=>flow.initializeChain.mutate()}>가공 작업 등록</button></section>}
    {role==='PROCESSOR'&&job.status==='ACCEPTED'&&job.chainJobId&&flow.chainStatusCode===PROCESSING_CHAIN_STATUS.REQUESTED&&<section className="pj-panel pj-action"><p>가공 조건과 투입 소재를 확인한 뒤 작업을 수락해 주세요.</p><button disabled={pending} onClick={()=>flow.accept.mutate()}>가공 작업 수락</button></section>}
    {role==='BRAND'&&job.status==='ACCEPTED'&&job.chainJobId&&flow.chainStatusCode===PROCESSING_CHAIN_STATUS.REQUESTED&&<section className="pj-panel pj-action"><p>가공업체의 작업 수락을 기다리고 있습니다.</p></section>}
    {/*
      기록이 ACCEPTED 일 때뿐 아니라 **이미 SHIPPED 일 때도 띄운다.** 서명이 체인에 들어간 뒤
      서버 호출이 실패하면(네트워크 끊김, 토큰 만료) 체인만 SHIPPED 가 되는데, ACCEPTED 에서만
      띄우면 그 순간 폼이 사라져 담당자가 DB 를 따라잡게 할 방법이 없어진다. 작업은 영원히
      ACCEPTED 로 남고 가공업체는 수령을 누를 수 없다.

      이때 훅은 서명을 건너뛰고 서버만 부른다(useProcessingJobs.ship).
    */}
    {role==='BRAND'&&job.status==='ACCEPTED'&&job.chainJobId&&(flow.chainStatusCode===PROCESSING_CHAIN_STATUS.ACCEPTED||flow.chainStatusCode===PROCESSING_CHAIN_STATUS.SHIPPED)&&<ProcessingShipmentForm isPending={flow.ship.isPending} error={flow.ship.error} submitLabel={flow.chainStatusCode===PROCESSING_CHAIN_STATUS.SHIPPED?'발송 처리 마저 끝내기':'소재 발송 처리'} onSubmit={input=>flow.ship.mutate(input)}/>}
    {role==='PROCESSOR'&&job.status==='SHIPPED'&&<section className="pj-panel pj-action"><p>소재가 도착했다면 수령을 확인해 주세요.</p><button disabled={pending} onClick={()=>flow.receive.mutate()}>소재 수령 처리</button></section>}
    {role==='PROCESSOR'&&job.status==='RECEIVED'&&<section className="pj-panel pj-action"><p>투입 소재와 작업 조건을 확인한 뒤 가공을 시작합니다.</p><button disabled={pending} onClick={()=>flow.start.mutate()}>가공 시작</button></section>}

    {role==='PROCESSOR'&&(job.status==='PROCESSING'||job.status==='REJECTED')&&<section className="pj-panel"><h2><Factory/>가공 결과 작성</h2>{job.rejectionReason&&<p className="pj-warning">브랜드 수정 요청 사유: {job.rejectionReason}</p>}
      {rows.map((row,index)=><fieldset className="pj-output" key={index}>
        <legend>산출물 {index+1}</legend>
        <div className="pj-output__grid">
          {/* 고르게 하지 않는다. 가공은 섬유를 정제하고 모양을 바꿀 뿐 종류를 바꾸지 않아,
              산출물의 종류는 언제나 투입 소재의 종류다. 서버도 같은 값을 파생해 기록한다. */}
          <label>산출 소재 종류<output aria-label={`산출물 ${index+1} 소재 종류`}>{request?MATERIAL_TYPE_LABEL[request.materialType]:'투입 소재 확인 중'}</output><small className="pj-help">투입한 소재와 같습니다</small></label>
          <label>산출 소재명<input aria-label={`산출물 ${index+1} 소재명`} placeholder="예: 재생 데님 원단" value={row.name} onChange={e=>setRows(v=>v.map((x,i)=>i===index?{...x,name:e.target.value}:x))}/></label>
          <label>품질 등급<select aria-label={`산출물 ${index+1} 품질 등급`} value={row.grade} onChange={e=>setRows(v=>v.map((x,i)=>i===index?{...x,grade:e.target.value as MaterialGrade}:x))}>
            {materialGrades.map(grade=><option value={grade} key={grade}>{GRADE_LABEL[grade]}</option>)}
          </select></label>
          <label>산출 수량(g)<input aria-label={`산출물 ${index+1} 산출 수량(g)`} type="number" min="1" step="1" placeholder="예: 650" value={row.quantity} onChange={e=>setRows(v=>v.map((x,i)=>i===index?{...x,quantity:e.target.value}:x))}/></label>
        </div>
        <label className="pj-output__reason">등급 판정 사유<textarea aria-label={`산출물 ${index+1} 등급 판정 사유`} maxLength={500} placeholder="오염이나 손상 상태 등 해당 등급으로 판단한 근거를 입력하세요." value={row.gradeReason} onChange={e=>setRows(v=>v.map((x,i)=>i===index?{...x,gradeReason:e.target.value}:x))}/></label>
        {rows.length>1&&<button className="pj-output__remove" aria-label={`산출물 ${index+1} 삭제`} onClick={()=>setRows(v=>v.filter((_,i)=>i!==index))}><Trash/>산출물 삭제</button>}
      </fieldset>)}
      <button className="pj-secondary" onClick={()=>setRows(v=>[...v,emptyRow()])}><Plus/>산출물 추가</button>
      <div className="pj-loss-summary">
        <div><span>투입 수량</span><strong>{job.inputQuantity.toLocaleString()} g</strong></div>
        <div><span>산출 수량 합계</span><output aria-label="산출 수량 합계">{outputSum.toLocaleString()} g</output></div>
        <div><span>자동 계산 Loss</span><output aria-label="자동 계산 Loss">{lossQuantity.toLocaleString()} g</output></div>
      </div>
      {excessQuantity>0&&<p className="pj-warning" role="alert">산출 수량 합계가 투입량을 {excessQuantity.toLocaleString()} g 초과했습니다.</p>}
      <button disabled={cannotSave} onClick={save}>결과 임시 저장</button>
      <div className="pj-evidence"><h3>가공 증빙</h3><EvidenceUpload referenceType="PROCESSING_JOB" referenceId={jobId} uploadMode="type-grid"/></div>
      {job.resultRevision>0&&<button className="pj-sign" disabled={pending} onClick={()=>flow.submit.mutate()}>가공 결과 제출</button>}
    </section>}

    {job.outputs.length>0&&<section className="pj-panel"><h2>제출 산출물</h2><ul className="pj-results">{job.outputs.map(row=><li key={row.outputId}><strong>{row.name}</strong><span>{MATERIAL_TYPE_LABEL[row.materialType]} · {GRADE_LABEL[row.grade]}</span><b>{row.quantity.toLocaleString()} g</b><p>{row.gradeReason}</p>{row.materialId&&<small>생성 소재 #{row.materialId}</small>}</li>)}</ul></section>}
    {role==='PROCESSOR'&&job.status==='RESULT_SUBMITTED'&&<section className="pj-panel"><div className="pj-evidence"><h3>반환 발송 증빙 등록</h3><EvidenceUpload referenceType="PROCESSING_JOB" referenceId={jobId} uploadMode="type-grid"/></div><ProcessingShipmentForm embedded title="가공 결과물 반환 발송" submitLabel="결과물 반환 발송" isPending={flow.shipReturn.isPending} error={flow.shipReturn.error} evidenceRequired evidenceOptions={evidenceOptions} onSubmit={input=>{
      if (input.evidenceFileId && input.evidenceHash) flow.shipReturn.mutate({...input,evidenceFileId:input.evidenceFileId,evidenceHash:input.evidenceHash})
    }}/></section>}
    {role==='PROCESSOR'&&job.status==='REJECTED'&&<section className="pj-panel"><h2><Package/>원소재 반환 후 작업 종료</h2><div className="pj-evidence"><h3>반환 발송 증빙 등록</h3><EvidenceUpload referenceType="PROCESSING_JOB" referenceId={jobId} uploadMode="type-grid"/></div><ProcessingShipmentForm embedded title="수정 요청된 작업의 원소재 반환" submitLabel="반환 및 작업 종료" isPending={flow.returnRejected.isPending} error={flow.returnRejected.error} evidenceRequired evidenceOptions={evidenceOptions} onSubmit={input=>{
      if (input.evidenceFileId && input.evidenceHash) flow.returnRejected.mutate({...input,evidenceFileId:input.evidenceFileId,evidenceHash:input.evidenceHash})
    }}/></section>}
    {role==='BRAND'&&job.status==='RESULT_SUBMITTED'&&<section className="pj-panel"><h2><CheckCircle/>결과 검토</h2><p>보완이 필요하면 결과 수정을 요청할 수 있습니다. 승인 처리는 결과물을 수령한 뒤 진행합니다.</p><div className="pj-review"><input placeholder="수정 요청 사유" value={reason} onChange={e=>setReason(e.target.value)}/><button className="is-danger" disabled={pending||!reason.trim()} onClick={()=>flow.reject.mutate(reason)}>결과 수정 요청</button></div></section>}
    {role==='BRAND'&&job.status==='RETURN_SHIPPED'&&<section className="pj-panel"><h2><CheckCircle/>결과물 수령 및 승인</h2><p>실물 결과물을 확인한 뒤 승인하면 투입 소재가 사용 처리되고 새 소재가 브랜드의 보유 목록에 등록됩니다.</p><button disabled={pending} onClick={()=>flow.approve.mutate()}>수령 확인하고 승인</button></section>}
    {error&&<p className="pj-error" role="alert">{error.message}</p>}
  </section>
}
