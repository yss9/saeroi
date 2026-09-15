import { ArrowLeft, CheckCircle, Factory, Package, Plus, Trash } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '../../../stores/authStore.ts'
import { EvidenceUpload } from '../../evidence/components/EvidenceUpload.tsx'
import { GRADE_LABEL, type MaterialGrade } from '../../material/types/material.types.ts'
import type { MaterialType } from '../types/processorProfile.types.ts'
import { MATERIAL_TYPE_LABEL } from '../types/processorProfile.types.ts'
import { PROCESS_TYPE_LABEL } from '../types/processing.types.ts'
import { useProcessingJob } from '../hooks/useProcessingJobs.ts'
import type { ProcessingJobDetail as ProcessingJob } from '../api/processingJobApi.ts'
import type { ProcessingRequestDetail } from '../api/processingRequestApi.ts'
import { ProcessingShipmentForm } from './ProcessingShipmentForm.tsx'
import './processing-jobs.css'

const materialTypes = Object.keys(MATERIAL_TYPE_LABEL) as MaterialType[]
const materialGrades = Object.keys(GRADE_LABEL) as MaterialGrade[]
const statusLabel = { CREATED:'결제 대기', PAID:'발송 대기', SHIPPED:'배송 중', RECEIVED:'가공 시작 대기', PROCESSING:'가공 중', RESULT_SUBMITTED:'결과 검토 대기', CONFIRMED:'가공 완료', REJECTED:'결과 보완', EXPIRED:'만료' } as const
type Row = { materialType: MaterialType; grade: MaterialGrade; gradeReason: string; name: string; quantity: string }
const emptyRow = (): Row => ({ materialType:'DENIM', grade:'A', gradeReason:'', name:'', quantity:'' })

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
    ? job.outputs.map(output => ({ materialType:output.materialType, grade:output.grade, gradeReason:output.gradeReason, name:output.name, quantity:String(output.quantity) }))
    : [emptyRow()])
  const [reason,setReason] = useState('')
  const pending=flow.ship.isPending||flow.receive.isPending||flow.start.isPending||flow.saveResult.isPending||flow.submit.isPending||flow.approve.isPending||flow.reject.isPending
  const error=flow.ship.error||flow.receive.error||flow.start.error||flow.saveResult.error||flow.submit.error||flow.approve.error||flow.reject.error
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
    <div className="pj-progress">{['PAID','SHIPPED','RECEIVED','PROCESSING','RESULT_SUBMITTED','CONFIRMED'].map((value,index)=><span className={['PAID','SHIPPED','RECEIVED','PROCESSING','RESULT_SUBMITTED','CONFIRMED'].indexOf(job.status)>=index?'is-done':''} key={value}>{index+1}</span>)}</div>
    <section className="pj-panel"><h2><Package/>투입 정보</h2><dl><div><dt>소재</dt><dd>{request?<><strong>{request.materialName}</strong><small>{request.materialCode}</small></>:'소재 정보 확인 중'}</dd></div><div><dt>투입 수량</dt><dd>{job.inputQuantity.toLocaleString()} g</dd></div><div><dt>가공비</dt><dd>{job.fee.toLocaleString()}원</dd></div><div><dt>Loss</dt><dd>{job.lossQuantity.toLocaleString()} g</dd></div></dl></section>

    {role==='BRAND'&&job.status==='PAID'&&<ProcessingShipmentForm isPending={flow.ship.isPending} error={flow.ship.error} onSubmit={input=>flow.ship.mutate(input)}/>}
    {role==='PROCESSOR'&&job.status==='SHIPPED'&&<section className="pj-panel pj-action"><p>소재가 도착했다면 수령을 확인해 주세요.</p><button disabled={pending} onClick={()=>flow.receive.mutate()}>소재 수령 처리</button></section>}
    {role==='PROCESSOR'&&job.status==='RECEIVED'&&<section className="pj-panel pj-action"><p>투입 소재와 작업 조건을 확인한 뒤 가공을 시작합니다.</p><button disabled={pending} onClick={()=>flow.start.mutate()}>가공 시작</button></section>}

    {role==='PROCESSOR'&&(job.status==='PROCESSING'||job.status==='REJECTED')&&<section className="pj-panel"><h2><Factory/>가공 결과 작성</h2>{job.rejectionReason&&<p className="pj-warning">Brand 반려 사유: {job.rejectionReason}</p>}
      {rows.map((row,index)=><fieldset className="pj-output" key={index}>
        <legend>산출물 {index+1}</legend>
        <div className="pj-output__grid">
          <label>산출 소재 종류<select aria-label={`산출물 ${index+1} 소재 종류`} value={row.materialType} onChange={e=>setRows(v=>v.map((x,i)=>i===index?{...x,materialType:e.target.value as MaterialType}:x))}>
            {materialTypes.map(type=><option value={type} key={type}>{MATERIAL_TYPE_LABEL[type]}</option>)}
          </select></label>
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
      <div className="pj-evidence"><h3>가공 증빙</h3><EvidenceUpload referenceType="PROCESSING_JOB" referenceId={jobId}/></div>
      {job.resultRevision>0&&<button className="pj-sign" disabled={pending} onClick={()=>flow.submit.mutate()}>MetaMask 서명 후 결과 제출</button>}
    </section>}

    {job.outputs.length>0&&<section className="pj-panel"><h2>제출 산출물</h2><ul className="pj-results">{job.outputs.map(row=><li key={row.outputId}><strong>{row.name}</strong><span>{MATERIAL_TYPE_LABEL[row.materialType]} · {GRADE_LABEL[row.grade]}</span><b>{row.quantity.toLocaleString()} g</b><p>{row.gradeReason}</p>{row.materialId&&<small>생성 소재 #{row.materialId}</small>}</li>)}</ul></section>}
    {role==='BRAND'&&job.status==='RESULT_SUBMITTED'&&<section className="pj-panel"><h2><CheckCircle/>결과 검토</h2><p>승인하면 투입 Lock이 소비되고 산출 소재가 보유 소재에 생성됩니다.</p><div className="pj-review"><button disabled={pending} onClick={()=>flow.approve.mutate()}>MetaMask 서명 후 승인</button><input placeholder="반려 사유" value={reason} onChange={e=>setReason(e.target.value)}/><button className="is-danger" disabled={pending||!reason.trim()} onClick={()=>flow.reject.mutate(reason)}>서명 후 반려</button></div></section>}
    {error&&<p className="pj-error" role="alert">{error.message}</p>}
  </section>
}
