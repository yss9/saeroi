import { ArrowLeft, ArrowRight, Check, CheckCircle, Clock, Factory, MapPin, Package } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { MATERIAL_TYPE_LABEL } from '../../material/types/material.types.ts'
import { PROCESS_TYPE_LABEL, type ProcessType } from '../types/processing.types.ts'
import {
  REFINING_PROCESS_TYPES,
  missingRefiningTypes,
} from '../../production/types/production.types.ts'
import { useBrandProcessingRequest } from '../hooks/useBrandProcessingRequest.ts'
import { parseServerTime } from '../utils/processingTime.ts'
import { formatWon } from '../../../utils/quantity.ts'
import './brand-processing.css'

const requestDate = (value: string) => parseServerTime(value).toLocaleString('ko-KR')
const gram = (value: number) => `${value.toLocaleString('ko-KR')} g`
/*
  금액은 서버가 준 값을 그대로 적는다(formatWon). 단가 × 수량을 화면이 곱하면 올림이
  어긋나는 순간 목록 금액과 결제창 금액이 달라진다 — 요청 생성이 같은 식으로 fee 를 정한다.
*/
const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.'

export function BrandProcessingRequest() {
  const flow = useBrandProcessingRequest()
  const selectedLabels = flow.processTypes.map(type => PROCESS_TYPE_LABEL[type]).join(' · ')
  // 고른 소재가 이미 거친 가공. 소재 목록이 혈통을 따라 누적해 실어 준다.
  const completedTypes: string[] = flow.material?.completedProcessTypes ?? []
  const missingAfterThis = missingRefiningTypes([...completedTypes, ...flow.processTypes])
  const canceled = flow.created?.status === 'CANCELED'
  const summary = <dl className="bp-summary-list">
    <div><dt>선택 소재</dt><dd>{flow.material?.name ?? '소재를 선택해 주세요'}</dd></div>
    <div><dt>가공 수량</dt><dd>{flow.created ? gram(flow.created.quantity) : flow.quantityText && !flow.quantityError ? gram(flow.quantity) : '—'}</dd></div>
    <div><dt>가공 목적</dt><dd>{selectedLabels || '—'}</dd></div>
    <div><dt>선택 가공사</dt><dd>{flow.processor?.name ?? '—'}</dd></div>
  </dl>
  return <div className="bp-shell bp-shell--embedded">
      <div className="bp-content">
        <h1 className="ui-visually-hidden">소재 가공 요청</h1>
        <ol className="bp-steps" aria-label="신청 단계">{['가공 조건 및 업체 선택', '신청 확인'].map((label, index) => <li key={label} className={flow.step === index + 1 ? 'is-active' : flow.step > index + 1 ? 'is-done' : ''} aria-current={flow.step === index + 1 ? 'step' : undefined}><span>{flow.step > index + 1 ? <Check weight="bold" size={17} /> : `0${index + 1}`}</span><strong>{label}</strong></li>)}</ol>
        {flow.created ? <section className="bp-card bp-success" aria-live="polite">
          <span className="bp-success-icon"><CheckCircle size={48} weight="duotone" /></span><span className="bp-eyebrow">{canceled ? 'REQUEST CANCELED' : 'REQUEST COMPLETE'}</span>
          <h2>{canceled ? '가공 신청이 취소되었어요' : '가공 신청이 완료되었어요'}</h2>
          <p>{canceled ? '예약된 소재 수량이 해제되었어요. 새로운 조건으로 다시 신청할 수 있어요.' : '가공비를 결제해야 가공사에게 요청이 전달돼요. 기한이 지나면 신청이 만료되고 예약된 소재가 풀려요.'}</p>
          <div className="bp-success-detail"><span>신청 번호 <strong>{flow.created.requestCode}</strong></span><span>상태 <strong>{canceled ? '취소 완료' : '결제 대기'}</strong></span>{summary}</div>
          {!canceled && <div className="bp-info"><Clock size={21} /><span>기한 안에 결제해야 가공사에게 넘어가요.<br /><strong>만료 일시: {requestDate(flow.created.expiresAt)}</strong></span></div>}
          {flow.cancel.isError && <p role="alert" className="bp-error">{errorMessage(flow.cancel.error)}</p>}
          {/* 결제는 요청 상세가 맡는다 — 결제창·기한·진행 상황이 모두 그 화면에 있다. */}
          <div className="bp-actions">{!canceled && <Link className="bp-button bp-primary" to={`/brand/processing/requests/${flow.created.requestId}`}>결제하러 가기 <ArrowRight size={18} /></Link>}<button className={`bp-button${canceled ? ' bp-primary' : ''}`} onClick={flow.restart}>새 가공 신청</button>{!canceled && flow.created.status === 'REQUESTED' && <button className="bp-button" disabled={flow.cancel.isPending} onClick={flow.cancelRequest}>{flow.cancel.isPending ? '취소 중…' : '신청 취소'}</button>}</div>
        </section> : <div className="bp-grid"><section className="bp-card bp-form">
          {flow.step === 1 && <>
            <div className="bp-section-title"><span className="bp-section-icon"><Package size={24} /></span><div><h2>어떤 소재를 가공할까요?</h2></div></div>
            {flow.materials.isPending ? <p role="status" className="bp-state">보유 소재를 불러오는 중이에요…</p> : flow.materials.isError ? <div role="alert" className="bp-state bp-error">{errorMessage(flow.materials.error)}<button className="bp-button" onClick={() => void flow.materials.refetch()}>다시 불러오기</button></div> : !flow.materials.data?.length ? <div className="bp-state"><Package size={36} /><h3>아직 보유한 소재가 없어요</h3><p>마켓플레이스에서 소재를 확보한 뒤 가공을 신청해 주세요.</p><Link className="bp-button" to="/marketplace">소재 둘러보기</Link></div> : <>
              <label className="bp-field-label" htmlFor="processing-material">보유 소재</label><select id="processing-material" value={flow.materialId} onChange={event => flow.changeMaterial(event.target.value)}><option value="">가공할 소재를 선택하세요</option>{flow.materials.data.map(material => <option key={material.materialId} value={material.materialId} disabled={material.availableQuantity <= 0}>{material.name} · {material.materialCode} · 가용 {gram(material.availableQuantity)}</option>)}</select>
              {flow.material && <div className="bp-material"><span className="bp-material-icon"><Package size={25} weight="duotone" /></span><div><strong>{flow.material.name}</strong><small>{flow.material.materialCode} · {MATERIAL_TYPE_LABEL[flow.material.materialType] ?? flow.material.materialType}</small></div><div className="bp-material-amount"><small>가용 수량</small><strong>{gram(flow.material.availableQuantity)}</strong><small>예약 {gram(flow.material.lockedQuantity)}</small></div></div>}
              <label className="bp-field-label" htmlFor="processing-quantity">가공 수량 (g)</label><div className="bp-input-unit"><input id="processing-quantity" type="text" inputMode="numeric" placeholder="가공할 수량을 입력하세요" value={flow.quantityText} aria-invalid={Boolean(flow.quantityError)} aria-describedby="processing-quantity-help" onChange={event => flow.changeQuantity(event.target.value)} /><span>g</span></div><p id="processing-quantity-help" className={flow.quantityError ? 'bp-error' : 'bp-helper'} role={flow.quantityError ? 'alert' : undefined}>{flow.quantityError || '예약된 수량을 제외한 가용 수량 안에서, 1 g 단위로 입력해 주세요.'}</p>
              {/*
                **세척·해체·선별은 제품을 만들기 전에 반드시 거쳐야 한다.** 그 사실을 고르는
                자리에서 말한다 — 다 만들고 나서 "이 소재로는 생산할 수 없습니다" 를 보면
                가공을 한 번 더 맡기는 동안 시간이 통째로 날아간다.

                고른 소재가 이미 거친 것은 표시를 지운다. 남은 것만 붉게 남아, 이번에 무엇을
                맡겨야 하는지가 목록 자체로 드러난다.
              */}
              <fieldset className="bp-purposes">
                <legend>가공 목적 <span>복수 선택 가능</span></legend>
                <div>
                  {(Object.entries(PROCESS_TYPE_LABEL) as [ProcessType, string][]).map(([type, label]) => {
                    const done = completedTypes.includes(type)
                    const needed = REFINING_PROCESS_TYPES.includes(type) && !done
                    return (
                      <label
                        key={type}
                        className={[
                          flow.processTypes.includes(type) ? 'is-selected' : '',
                          needed ? 'is-required' : '',
                          done ? 'is-done' : '',
                        ].filter(Boolean).join(' ')}
                      >
                        <input type="checkbox" checked={flow.processTypes.includes(type)} onChange={() => flow.toggleType(type)} />
                        {/*
                          "필수"·"완료" 딱지는 CSS 가 그린다 (`.is-required` · `.is-done`).
                          글자를 넣으면 이 칸의 이름이 "세척 필수" 가 되어 무엇을 고르는
                          칸인지 흐려진다 — 뜻은 아래 문장이 말로 적는다.
                        */}
                        <span>{label}</span>
                      </label>
                    )
                  })}
                </div>
                <p className="bp-purposes-note">
                  {missingAfterThis.length === 0
                    ? '제품 생산에 필요한 가공(세척·해체·선별)이 이번 요청으로 모두 채워집니다.'
                    : `제품을 생산하려면 ${missingAfterThis.map(type => PROCESS_TYPE_LABEL[type as ProcessType]).join(' · ')} 이(가) 더 필요합니다.`}
                </p>
              </fieldset>
              <div className="bp-form-footer bp-form-footer--end"><button className="bp-button bp-primary" disabled={!flow.valid || flow.search.isFetching} onClick={flow.findProcessors}>검색 <ArrowRight size={18} /></button></div>
              <section className="bp-processor-directory" aria-labelledby="processor-directory-title">
                <div className="bp-directory-heading"><div className="bp-section-title"><span className="bp-section-icon"><Factory size={24} /></span><div><h2 id="processor-directory-title">가공업체 목록</h2></div></div>{flow.search.data && <strong>{flow.search.data.total}곳</strong>}</div>
                {flow.filters.materialType && flow.filters.quantity && flow.filters.processTypes && <div className="bp-active-filters" aria-label="적용된 필터"><span>{MATERIAL_TYPE_LABEL[flow.filters.materialType] ?? flow.filters.materialType}</span><span>{gram(flow.filters.quantity)}</span>{flow.filters.processTypes.map(type => <span key={type}>{PROCESS_TYPE_LABEL[type]}</span>)}<button type="button" onClick={flow.resetFilters}>전체 보기</button></div>}
                {flow.search.isFetching ? <p className="bp-state" role="status">조건에 맞는 가공업체를 찾고 있어요…</p> : flow.search.isError ? <div role="alert" className="bp-state bp-error">{errorMessage(flow.search.error)}<button className="bp-button" onClick={() => void flow.search.refetch()}>다시 검색</button></div> : !flow.search.data?.items.length ? <div className="bp-state"><Factory size={40} /><h3>조건에 맞는 가공업체가 없어요</h3><p>위 필터에서 수량이나 가공 목적을 조정해 주세요.</p><button className="bp-button" onClick={flow.edit}>조건 다시 선택</button></div> : <>
                  <p className="bp-results-count">{flow.filters.materialType ? '필터 결과' : '전체 업체'} <strong>{flow.search.data.total}개</strong></p><div className="bp-processor-list" role="radiogroup" aria-label="가공사 선택">{flow.search.data.items.map(processor => <label key={processor.processorId} className={`bp-processor ${flow.processor?.processorId === processor.processorId ? 'is-selected' : ''}`}><input type="radio" name="processor" checked={flow.processor?.processorId === processor.processorId} onChange={() => flow.selectProcessor(processor)} /><div className="bp-processor-body"><strong>{processor.name}</strong><span><MapPin size={16} />{processor.region || '주소 미등록'}</span><div>{processor.estimatedPrice === null || processor.estimatedDays === null ? <span className="bp-estimate-hint">수량과 가공 목적을 정하면 금액과 소요일을 계산해요</span> : <><span>예상 금액 <b>{formatWon(processor.estimatedPrice)}</b></span><span>예상 소요일 <b>{processor.estimatedDays}일</b></span><span className="bp-lead-times">공정별 소요일 <b>{processor.estimatedLeadTimes.map(estimate => <em key={estimate.processType}>{PROCESS_TYPE_LABEL[estimate.processType]} {estimate.leadDays}일</em>)}</b></span></>}</div></div><span className="bp-match-tag">{flow.filters.materialType ? '조건 일치' : '등록 업체'}</span></label>)}</div>
                  {flow.search.data.total > flow.search.data.size && <div className="bp-pagination"><button className="bp-button" disabled={flow.search.data.page === 0} onClick={() => flow.changePage(flow.search.data!.page - 1)}>이전 페이지</button><span>{flow.search.data.page + 1} / {Math.ceil(flow.search.data.total / flow.search.data.size)}</span><button className="bp-button" disabled={(flow.search.data.page + 1) * flow.search.data.size >= flow.search.data.total} onClick={() => flow.changePage(flow.search.data!.page + 1)}>다음 페이지</button></div>}
                </>}
                <div className="bp-form-footer bp-form-footer--end"><button className="bp-button bp-primary" disabled={!flow.processor || !flow.filtersApplied || flow.search.isFetching || flow.search.isError} onClick={flow.confirm}>신청 내용 확인 <ArrowRight size={18} /></button></div>
              </section>
            </>}
          </>}
          {flow.step === 2 && <>
            <div className="bp-section-title"><span className="bp-section-icon"><CheckCircle size={24} /></span><div><h2>신청 내용을 확인해 주세요</h2></div></div>
            <div className="bp-confirm-summary">{summary}</div>
            {flow.create.isError && <p role="alert" className="bp-error">{errorMessage(flow.create.error)}</p>}
            {!flow.valid && <p role="alert" className="bp-error">소재 가용 수량이 변경되었어요. 조건을 다시 확인해 주세요.</p>}
            <div className="bp-form-footer"><button className="bp-button" disabled={flow.create.isPending} onClick={flow.valid ? flow.backToSearch : flow.edit}><ArrowLeft size={17} />{flow.valid ? '이전' : '조건 수정'}</button><button className="bp-button bp-primary" disabled={flow.create.isPending || !flow.valid} onClick={() => void flow.submit()}>{flow.create.isPending ? '신청 중…' : '가공 신청하기'} <ArrowRight size={18} /></button></div>
          </>}
        </section></div>}
      </div>
  </div>
}

