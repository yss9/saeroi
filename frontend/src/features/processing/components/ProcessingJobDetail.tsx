import { ArrowLeft, CheckCircle, Factory, Package, Plus, Trash, X } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '../../../stores/authStore.ts'
import { EvidenceUpload } from '../../evidence/components/EvidenceUpload.tsx'
import { EVIDENCE_TYPE_LABEL } from '../../evidence/types/evidence.types.ts'
import { GRADE_LABEL, type MaterialGrade } from '../../material/types/material.types.ts'
import type { MaterialType } from '../types/processorProfile.types.ts'
import { MATERIAL_TYPE_LABEL } from '../types/processorProfile.types.ts'
import { PROCESS_TYPE_LABEL } from '../types/processing.types.ts'
import { useProcessingJob } from '../hooks/useProcessingJobs.ts'
import { PROCESSING_CHAIN_STATUS } from '../services/processingContractService.ts'
import type { ProcessingJobDetail as ProcessingJob } from '../api/processingJobApi.ts'
import type { ProcessingRequestDetail } from '../api/processingRequestApi.ts'
import { ProcessingShipmentForm } from './ProcessingShipmentForm.tsx'
import { formatDateTime } from '../utils/processingTime.ts'
import './processing-jobs.css'

const materialTypes = Object.keys(MATERIAL_TYPE_LABEL) as MaterialType[]
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
  // 거절은 사유가 필수라 한 번 더 묻는다 — 눌러서 바로 끝나는 일이 아니다.
  const [declining,setDeclining] = useState(false)
  const [declineReason,setDeclineReason] = useState('')
  const evidenceOptions = (flow.evidence?.data?.files ?? []).map(file => ({
    id: file.evidenceFileId,
    hash: file.fileHash,
    label: `${EVIDENCE_TYPE_LABEL[file.evidenceType]} · ${file.storageKey.split('/').at(-1) ?? file.storageKey}`,
  }))
  const pending=Boolean(flow.initializeChain?.isPending||flow.accept?.isPending||flow.declineJob?.isPending||flow.releaseChainLock?.isPending||flow.ship.isPending||flow.receive.isPending||flow.start.isPending||flow.saveResult.isPending||flow.submit.isPending||flow.shipReturn?.isPending||flow.approve.isPending||flow.reject.isPending||flow.returnRejected?.isPending)
  const error=flow.initializeChain?.error||flow.accept?.error||flow.declineJob?.error||flow.releaseChainLock?.error||flow.ship.error||flow.receive.error||flow.start.error||flow.saveResult.error||flow.submit.error||flow.shipReturn?.error||flow.approve.error||flow.reject.error||flow.returnRejected?.error
  const outputSum=rows.reduce((sum,row)=>sum+(Number.isSafeInteger(Number(row.quantity))&&Number(row.quantity)>0?Number(row.quantity):0),0)
  const lossQuantity=Math.max(job.inputQuantity-outputSum,0)
  const excessQuantity=Math.max(outputSum-job.inputQuantity,0)
  const hasInvalidOutput=rows.some(row=>!row.name.trim()||!row.gradeReason.trim()||!Number.isSafeInteger(Number(row.quantity))||Number(row.quantity)<=0)
  const cannotSave=pending||excessQuantity>0||hasInvalidOutput
  const save=()=>flow.saveResult.mutate({outputs:rows.map(row=>({...row,name:row.name.trim(),gradeReason:row.gradeReason.trim(),quantity:Number(row.quantity)}))})
  const counterparty = role === 'PROCESSOR' ? request?.brandName : request?.processorName
  /*
    **온체인 잠금이 아직 살아 있는가.** 해제 패널과 "환불되었습니다" 안내가 같은 답을
    써야 한다 — 하나는 "잠금이 남아 있습니다", 다른 하나는 "잠금도 풀렸습니다" 라고
    말하면 브랜드는 어느 쪽을 믿어야 할지 알 수 없다. 서버의 chainReleaseRequired 는
    필요조건일 뿐이라(해제를 기록하는 API 가 없다) 체인이 CANCELED 라고 확실히
    말할 때만 접는다.
  */
  const chainLockHeld=Boolean(job.chainReleaseRequired)&&flow.chainStatusCode!==PROCESSING_CHAIN_STATUS.CANCELED
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
      **결제가 끝나면 브랜드가 먼저 온체인에 올린다** — 가공사의 수락이 곧 온체인 서명이
      되려면 서명할 대상(job)이 체인에 먼저 있어야 한다. 그 다음이 가공사의 대답이다.
      발송은 수락 뒤에만 열린다(서버도 409 로 막는다).

      **ACCEPTED 도 함께 받는다.** 새 순서에서는 수락된 작업에 chainJobId 가 없을 수
      없으므로 이 절은 비용이 들지 않는다. 대신 구 순서(DB 수락이 먼저였던 시절)로
      만들어진 행을 위해 서버 linkJob 이 일부러 남겨 둔 ACCEPTED 허용이, 이것이
      없으면 눌러 볼 방법이 없는 규칙으로 남는다.
    */}
    {role==='BRAND'&&(job.status==='PAID'||job.status==='ACCEPTED')&&!job.chainJobId&&<section className="pj-panel pj-action"><div><p>결제가 끝났습니다. 가공업체가 수락할 수 있도록 입력 소재를 온체인 가공 작업에 잠급니다.</p><p className="pj-help">가공업체가 거절하거나 기한 내에 답하지 않으면 이 잠금을 직접 해제해야 합니다.</p></div><button disabled={pending} onClick={()=>flow.initializeChain.mutate()}>MetaMask로 온체인 작업 등록</button></section>}

    {role==='PROCESSOR'&&job.status==='PAID'&&!job.chainJobId&&<section className="pj-panel pj-action"><p>브랜드가 온체인 작업을 등록하면 수락할 수 있습니다.</p></section>}

    {/* 수락은 여기 한 번뿐이다. 이 서명이 곧 "가공사가 동의했다"는 온체인 증거다. */}
    {role==='PROCESSOR'&&job.status==='PAID'&&job.chainJobId&&<section className="pj-panel pj-action"><div><p>가공 조건과 투입 소재를 확인한 뒤 수락해 주세요.</p>{job.acceptExpiresAt&&<p className="pj-help">{formatDateTime(job.acceptExpiresAt)}까지 답하지 않으면 자동으로 거절되고 가공비가 환불됩니다.</p>}</div><div className="pj-decision"><button className="pj-secondary" disabled={pending} onClick={()=>setDeclining(true)}>거절</button><button disabled={pending} onClick={()=>flow.accept.mutate()}>MetaMask로 요청 수락</button></div></section>}

    {role==='PROCESSOR'&&job.status==='PAID'&&declining&&<section className="pj-panel"><h2><X/>요청 거절</h2><p>사유는 브랜드에게 그대로 보입니다. 거절하면 가공비가 환불되고 서버의 소재 잠금이 풀립니다. 온체인 잠금은 브랜드가 직접 해제해야 합니다.</p><div className="pj-review"><input aria-label="거절 사유" maxLength={500} placeholder="예: 설비 점검 중입니다" value={declineReason} onChange={e=>setDeclineReason(e.target.value)}/><button className="is-danger" disabled={pending||!declineReason.trim()} onClick={()=>flow.declineJob.mutate(declineReason.trim())}>거절하기</button></div></section>}

    {role==='BRAND'&&job.status==='PAID'&&job.chainJobId&&<section className="pj-panel pj-action"><p>가공업체의 수락을 기다리고 있습니다.{job.acceptExpiresAt?` ${formatDateTime(job.acceptExpiresAt)}까지 답이 없으면 자동으로 환불됩니다.`:''}</p></section>}

    {job.status==='DECLINED'&&<section className="pj-panel"><h2><X/>가공사가 거절했습니다</h2><p className="pj-warning">{job.declineReason??'가공업체가 기한 안에 답하지 않아 자동으로 종료되었습니다.'}</p>{job.refundedAt&&<p className="pj-help">{formatDateTime(job.refundedAt)}에 가공비가 환불되었습니다. {chainLockHeld?'온체인 잠금은 아직 남아 있습니다 — 아래에서 직접 해제해 주세요.':'잠긴 소재 수량도 풀렸습니다.'}</p>}</section>}

    {/*
      **발송에도 체인 게이트가 있다.** markShipped 는 체인이 ACCEPTED 일 때만 통한다 —
      구 순서로 수락된 행은 체인이 아직 REQUESTED 라 버튼을 누르면 되돌아간다(revert).

      그렇다고 "ACCEPTED 일 때만 연다" 로 두지는 않는다. 체인 조회가 아직 끝나지
      않았거나 실패하면 chainStatusCode 는 NONE 으로 남는데, 그걸로 감추면 정상적인
      새 순서 작업의 발송 폼이 조회가 도는 동안 사라졌다 나타난다. 해제 패널과 같은
      원칙 — **확실한 증거가 있을 때만 접는다.** DB 가 ACCEPTED 인 작업에서 체인이
      REQUESTED 라는 것은 "가공사가 아직 서명하지 않았다" 는 확실한 증거다.

      **체인에 아직 올라가지도 않은 행(chainJobId=null)은 둘 다 아니다.** 그 자리에서
      할 일은 바로 위 등록 패널이 말한다 — 함께 띄우면 서로 배타적인 다음 단계가
      두 개 보이고, 발송 쪽은 누를 작업이 체인에 없어 되돌아간다.
    */}
    {role==='BRAND'&&job.status==='ACCEPTED'&&job.chainJobId&&flow.chainStatusCode===PROCESSING_CHAIN_STATUS.REQUESTED&&<section className="pj-panel pj-action"><p>가공업체의 온체인 작업 수락을 기다리고 있습니다.</p></section>}
    {role==='BRAND'&&job.status==='ACCEPTED'&&job.chainJobId&&flow.chainStatusCode!==PROCESSING_CHAIN_STATUS.REQUESTED&&<ProcessingShipmentForm isPending={flow.ship.isPending} error={flow.ship.error} submitLabel="MetaMask 확인 후 발송 처리" onSubmit={input=>flow.ship.mutate(input)}/>}

    {/*
      서버는 DB 와 환불만 정리한다. 체인의 잠금은 브랜드 지갑만 풀 수 있다.
      **chainReleaseRequired 는 필요조건일 뿐이다** — 서버는 상태 + chainJobId 존재만으로
      계산하고, 해제를 기록하는 API 가 없어 cancelJob 에 성공해도 재조회 시 계속 true 로
      온다. 그래서 패널은 "이미 풀렸다는 확실한 증거(CANCELED)" 가 있을 때만 접는다 —
      로딩 중이거나 체인 조회가 실패해 NONE 으로 남아도(브랜드가 chainReleaseRequired
      를 받았다는 건 서버가 CANCELED 가 아니라고 봤다는 뜻이므로, 이때의 NONE 은 읽기
      실패로만 나올 수 있다) 패널을 그대로 둔다 — 잠금이 남아 있는데 버튼이 없는 것보다,
      이미 풀렸는데 눌러서 되돌아가는 쪽이 낫다.
    */}
    {role==='BRAND'&&chainLockHeld&&<section className="pj-panel pj-action"><div><p>이 작업은 끝났지만 온체인 잠금이 남아 있습니다. 해제하기 전까지 해당 수량은 다시 쓸 수 없습니다.</p>{(flow.chainStatus?.isError||flow.chainPreparation?.isError)&&<p className="pj-help">체인 상태를 불러오지 못했습니다. 이미 해제됐을 수도 있으니 먼저 새로고침해 보세요.</p>}</div><button disabled={pending} onClick={()=>flow.releaseChainLock.mutate()}>MetaMask로 온체인 잠금 해제</button></section>}
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
      <div className="pj-evidence"><h3>가공 증빙</h3><EvidenceUpload referenceType="PROCESSING_JOB" referenceId={jobId} uploadMode="type-grid"/></div>
      {job.resultRevision>0&&<button className="pj-sign" disabled={pending} onClick={()=>flow.submit.mutate()}>MetaMask 서명 후 결과 제출</button>}
    </section>}

    {job.outputs.length>0&&<section className="pj-panel"><h2>제출 산출물</h2><ul className="pj-results">{job.outputs.map(row=><li key={row.outputId}><strong>{row.name}</strong><span>{MATERIAL_TYPE_LABEL[row.materialType]} · {GRADE_LABEL[row.grade]}</span><b>{row.quantity.toLocaleString()} g</b><p>{row.gradeReason}</p>{row.materialId&&<small>생성 소재 #{row.materialId}</small>}</li>)}</ul></section>}
    {role==='PROCESSOR'&&job.status==='RESULT_SUBMITTED'&&<section className="pj-panel"><div className="pj-evidence"><h3>반환 발송 증빙 등록</h3><EvidenceUpload referenceType="PROCESSING_JOB" referenceId={jobId} uploadMode="type-grid"/></div><ProcessingShipmentForm embedded title="가공 결과물 반환 발송" submitLabel="MetaMask 확인 후 반환 발송" isPending={flow.shipReturn.isPending} error={flow.shipReturn.error} evidenceRequired evidenceOptions={evidenceOptions} onSubmit={input=>{
      if (input.evidenceFileId && input.evidenceHash) flow.shipReturn.mutate({...input,evidenceFileId:input.evidenceFileId,evidenceHash:input.evidenceHash})
    }}/></section>}
    {role==='PROCESSOR'&&job.status==='REJECTED'&&<section className="pj-panel"><h2><Package/>원소재 반환 후 작업 종료</h2><div className="pj-evidence"><h3>반환 발송 증빙 등록</h3><EvidenceUpload referenceType="PROCESSING_JOB" referenceId={jobId} uploadMode="type-grid"/></div><ProcessingShipmentForm embedded title="반려된 원소재 반환" submitLabel="MetaMask 확인 후 반환 및 종료" isPending={flow.returnRejected.isPending} error={flow.returnRejected.error} evidenceRequired evidenceOptions={evidenceOptions} onSubmit={input=>{
      if (input.evidenceFileId && input.evidenceHash) flow.returnRejected.mutate({...input,evidenceFileId:input.evidenceFileId,evidenceHash:input.evidenceHash})
    }}/></section>}
    {role==='BRAND'&&job.status==='RESULT_SUBMITTED'&&<section className="pj-panel"><h2><CheckCircle/>결과 검토</h2><p>보완이 필요하면 결과를 반려할 수 있습니다. 승인 처리는 결과물을 수령한 뒤 진행합니다.</p><div className="pj-review"><input placeholder="반려 사유" value={reason} onChange={e=>setReason(e.target.value)}/><button className="is-danger" disabled={pending||!reason.trim()} onClick={()=>flow.reject.mutate(reason)}>MetaMask 확인 후 반려</button></div></section>}
    {role==='BRAND'&&job.status==='RETURN_SHIPPED'&&<section className="pj-panel"><h2><CheckCircle/>결과물 수령 및 승인</h2><p>실물 결과물을 확인한 뒤 승인하면 Processor의 입력 토큰이 소비되고 Child Material 토큰이 Brand 지갑에 발행됩니다.</p><button disabled={pending} onClick={()=>flow.approve.mutate()}>MetaMask로 수령 확인 및 최종 승인</button></section>}
    {error&&<p className="pj-error" role="alert">{error.message}</p>}
  </section>
}
