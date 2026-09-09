import { ArrowLeft, ArrowRight, Check, CheckCircle, ClipboardText, Clock, Factory, Info, Leaf, MapPin, Package } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { PROCESS_TYPE_LABEL, type ProcessType } from '../types/processing.types.ts'
import { useBrandProcessingRequest } from '../hooks/useBrandProcessingRequest.ts'
import './brand-processing.css'

// Backend LocalDateTime values represent UTC even when the offset is omitted.
const requestDate = (value: string) => new Date(/(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : value + 'Z').toLocaleString('ko-KR')
const gram = (value: number) => `${value.toLocaleString('ko-KR')} g`
const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error ? error.message : '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.'
const materialLabel: Record<string, string> = { DENIM: '데님', COTTON: '면', LEATHER: '가죽', LINING: '안감', METAL: '금속', MIXED: '혼합', ETC: '기타' }

export function BrandProcessingRequest() {
  const flow = useBrandProcessingRequest()
  const selectedLabels = flow.processTypes.map(type => PROCESS_TYPE_LABEL[type]).join(' · ')
  const canceled = flow.created?.status === 'CANCELED'
  const summary = <dl className="bp-summary-list">
    <div><dt>선택 소재</dt><dd>{flow.material?.name ?? '소재를 선택해 주세요'}</dd></div>
    <div><dt>가공 수량</dt><dd>{flow.created ? gram(flow.created.quantity) : flow.quantityText && !flow.quantityError ? gram(flow.quantity) : '—'}</dd></div>
    <div><dt>가공 목적</dt><dd>{selectedLabels || '—'}</dd></div>
    <div><dt>선택 가공사</dt><dd>{flow.processor?.name ?? '—'}</dd></div>
  </dl>
  return <div className="bp-shell bp-shell--embedded">
      <div className="bp-content">
        <div className="bp-page-heading"><div><span className="bp-eyebrow">PROCESSING REQUEST</span><h1>소재 가공 신청</h1><p>보유 소재에 맞는 가공사를 찾고, 새로운 가능성을 연결하세요.</p></div><Link className="bp-heading-action" to="/brand/processing/requests"><ClipboardText size={18} />요청 내역 보기</Link></div>
        <ol className="bp-steps" aria-label="신청 단계">{['소재 및 가공 조건', '가공사 선택', '신청 확인'].map((label, index) => <li key={label} className={flow.step === index + 1 ? 'is-active' : flow.step > index + 1 ? 'is-done' : ''} aria-current={flow.step === index + 1 ? 'step' : undefined}><span>{flow.step > index + 1 ? <Check weight="bold" size={17} /> : `0${index + 1}`}</span><strong>{label}</strong></li>)}</ol>
        {flow.created ? <section className="bp-card bp-success" aria-live="polite">
          <span className="bp-success-icon"><CheckCircle size={48} weight="duotone" /></span><span className="bp-eyebrow">{canceled ? 'REQUEST CANCELED' : 'REQUEST COMPLETE'}</span>
          <h2>{canceled ? '가공 신청이 취소되었어요' : '가공 신청이 완료되었어요'}</h2>
          <p>{canceled ? '예약된 소재 수량이 해제되었어요. 새로운 조건으로 다시 신청할 수 있어요.' : '가공사에 요청을 전달했어요. 견적 유효기간 안에 다음 단계를 진행해 주세요.'}</p>
          <div className="bp-success-detail"><span>신청 번호 <strong>{flow.created.requestCode}</strong></span><span>상태 <strong>{canceled ? '취소 완료' : '견적 대기'}</strong></span>{summary}</div>
          {!canceled && <div className="bp-info"><Clock size={21} /><span>신청 후 7일간 유효해요.<br /><strong>만료 일시: {requestDate(flow.created.expiresAt)}</strong></span></div>}
          {flow.cancel.isError && <p role="alert" className="bp-error">{errorMessage(flow.cancel.error)}</p>}
          <div className="bp-actions"><button className="bp-button bp-primary" onClick={flow.restart}>새 가공 신청</button>{!canceled && flow.created.status === 'QUOTING' && <button className="bp-button" disabled={flow.cancel.isPending} onClick={flow.cancelRequest}>{flow.cancel.isPending ? '취소 중…' : '신청 취소'}</button>}</div>
        </section> : <div className="bp-grid"><section className="bp-card bp-form">
          {flow.step === 1 && <>
            <div className="bp-section-title"><span className="bp-section-icon"><Package size={24} /></span><div><h2>어떤 소재를 가공할까요?</h2><p>소재와 수량, 필요한 가공 목적을 선택해 주세요.</p></div></div>
            {flow.materials.isPending ? <p role="status" className="bp-state">보유 소재를 불러오는 중이에요…</p> : flow.materials.isError ? <div role="alert" className="bp-state bp-error">{errorMessage(flow.materials.error)}<button className="bp-button" onClick={() => void flow.materials.refetch()}>다시 불러오기</button></div> : !flow.materials.data?.length ? <div className="bp-state"><Package size={36} /><h3>아직 보유한 소재가 없어요</h3><p>마켓플레이스에서 소재를 확보한 뒤 가공을 신청해 주세요.</p><Link className="bp-button" to="/marketplace">소재 둘러보기</Link></div> : <>
              <label className="bp-field-label" htmlFor="processing-material">보유 소재</label><select id="processing-material" value={flow.materialId} onChange={event => flow.changeMaterial(event.target.value)}><option value="">가공할 소재를 선택하세요</option>{flow.materials.data.map(material => <option key={material.materialId} value={material.materialId} disabled={material.availableQuantity <= 0}>{material.name} · {material.materialCode} · 가용 {gram(material.availableQuantity)}</option>)}</select>
              {flow.material && <div className="bp-material"><span className="bp-material-icon"><Package size={25} weight="duotone" /></span><div><strong>{flow.material.name}</strong><small>{flow.material.materialCode} · {materialLabel[flow.material.materialType] ?? flow.material.materialType}</small></div><div className="bp-material-amount"><small>가용 수량</small><strong>{gram(flow.material.availableQuantity)}</strong><small>예약 {gram(flow.material.lockedQuantity)}</small></div></div>}
              <label className="bp-field-label" htmlFor="processing-quantity">가공 수량 (g)</label><div className="bp-input-unit"><input id="processing-quantity" type="text" inputMode="numeric" placeholder="가공할 수량을 입력하세요" value={flow.quantityText} aria-invalid={Boolean(flow.quantityError)} aria-describedby="processing-quantity-help" onChange={event => flow.changeQuantity(event.target.value)} /><span>g</span></div><p id="processing-quantity-help" className={flow.quantityError ? 'bp-error' : 'bp-helper'} role={flow.quantityError ? 'alert' : undefined}>{flow.quantityError || '예약된 수량을 제외한 가용 수량 안에서, 1 g 단위로 입력해 주세요.'}</p>
              <fieldset className="bp-purposes"><legend>가공 목적 <span>복수 선택 가능</span></legend><p>선택한 모든 가공 목적을 처리할 수 있는 가공사를 찾아드려요.</p><div>{(Object.entries(PROCESS_TYPE_LABEL) as [ProcessType, string][]).map(([type, label]) => <label key={type} className={flow.processTypes.includes(type) ? 'is-selected' : ''}><input type="checkbox" checked={flow.processTypes.includes(type)} onChange={() => flow.toggleType(type)} /><span>{label}</span></label>)}</div></fieldset>
              <div className="bp-form-footer"><span>소재 · 수량 · 가공 목적을 선택해 주세요</span><button className="bp-button bp-primary" disabled={!flow.valid} onClick={flow.findProcessors}>가공사 찾기 <ArrowRight size={18} /></button></div>
            </>}
          </>}
          {flow.step === 2 && <>
            <div className="bp-section-title"><span className="bp-section-icon"><Factory size={24} /></span><div><h2>함께할 가공사를 선택하세요</h2><p>선택한 소재, 수량과 모든 가공 목적에 맞는 파트너예요.</p></div></div>
            <div className="bp-search-conditions"><span>{flow.material?.name} · {gram(flow.quantity)}</span><span>{selectedLabels}</span><button onClick={flow.edit}>조건 수정</button></div>
            {flow.search.isFetching ? <p className="bp-state" role="status">조건에 맞는 가공사를 찾고 있어요…</p> : flow.search.isError ? <div role="alert" className="bp-state bp-error">{errorMessage(flow.search.error)}<button className="bp-button" onClick={() => void flow.search.refetch()}>다시 검색</button></div> : !flow.search.data?.items.length ? <div className="bp-state"><Factory size={40} /><h3>조건에 맞는 가공사가 없어요</h3><p>수량이나 가공 목적을 조정해 다시 검색해 주세요.</p><button className="bp-button" onClick={flow.edit}>조건 다시 선택</button></div> : <>
              <p className="bp-results-count">검색 결과 <strong>{flow.search.data.total}개</strong><span>한 곳을 선택해 주세요</span></p><div className="bp-processor-list" role="radiogroup" aria-label="가공사 선택">{flow.search.data.items.map(processor => <label key={processor.processorId} className={`bp-processor ${flow.processor?.processorId === processor.processorId ? 'is-selected' : ''}`}><input type="radio" name="processor" checked={flow.processor?.processorId === processor.processorId} onChange={() => flow.selectProcessor(processor)} /><div className="bp-processor-body"><strong>{processor.name}</strong><span><MapPin size={16} />{processor.region || '지역 미등록'}</span><div><span>처리 수량 <b>{gram(processor.minQuantity)} ~ {gram(processor.maxQuantity)}</b></span><span>예상 소요일 <b>{processor.estimatedLeadDays == null ? '가공사 협의' : `${processor.estimatedLeadDays}일`}</b></span></div></div><span className="bp-match-tag">조건 일치</span></label>)}</div>
              {flow.search.data.total > flow.search.data.size && <div className="bp-pagination"><button className="bp-button" disabled={flow.search.data.page === 0} onClick={() => flow.changePage(flow.search.data!.page - 1)}>이전 페이지</button><span>{flow.search.data.page + 1} / {Math.ceil(flow.search.data.total / flow.search.data.size)}</span><button className="bp-button" disabled={(flow.search.data.page + 1) * flow.search.data.size >= flow.search.data.total} onClick={() => flow.changePage(flow.search.data!.page + 1)}>다음 페이지</button></div>}
            </>}
            <div className="bp-form-footer"><button className="bp-button" onClick={flow.edit}><ArrowLeft size={17} />이전</button><button className="bp-button bp-primary" disabled={!flow.processor || flow.search.isFetching || flow.search.isError} onClick={flow.confirm}>신청 내용 확인 <ArrowRight size={18} /></button></div>
          </>}
          {flow.step === 3 && <>
            <div className="bp-section-title"><span className="bp-section-icon"><CheckCircle size={24} /></span><div><h2>신청 내용을 확인해 주세요</h2><p>아래 내용으로 선택한 가공사에 견적을 요청해요.</p></div></div>
            <div className="bp-confirm-summary">{summary}</div><div className="bp-info"><Info size={22} /><div><strong>신청 전에 확인해 주세요</strong><p>신청한 수량은 가용 재고에서 예약돼요. 견적 요청은 7일간 유효하며, 신청 취소 또는 만료 시 예약이 해제돼요.</p></div></div>
            {flow.create.isError && <p role="alert" className="bp-error">{errorMessage(flow.create.error)}</p>}
            {!flow.valid && <p role="alert" className="bp-error">소재 가용 수량이 변경되었어요. 조건을 다시 확인해 주세요.</p>}
            <div className="bp-form-footer"><button className="bp-button" disabled={flow.create.isPending} onClick={flow.valid ? flow.backToSearch : flow.edit}><ArrowLeft size={17} />{flow.valid ? '이전' : '조건 수정'}</button><button className="bp-button bp-primary" disabled={flow.create.isPending || !flow.valid} onClick={() => void flow.submit()}>{flow.create.isPending ? '신청 중…' : '가공 신청하기'} <ArrowRight size={18} /></button></div>
          </>}
        </section><aside className="bp-summary"><section className="bp-card"><span className="bp-eyebrow">YOUR REQUEST</span><h2>신청 요약</h2>{summary}</section><div className="bp-tip"><Leaf size={24} /><h3>더 나은 소재의 시작</h3><p>소재의 특성에 맞는 가공을 통해<br />새로운 제품의 가능성을 발견하세요.</p></div><p className="bp-side-note"><Info size={17} />최종 비용과 일정은 가공사의 견적을 통해 확정됩니다.</p></aside></div>}
      </div>
  </div>
}

