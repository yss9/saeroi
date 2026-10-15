import { Package } from '@phosphor-icons/react'
import { useState } from 'react'
import { CARRIERS } from '../../../utils/carriers.ts'
import { isAppError } from '../../../utils/errors.ts'
import './processing-jobs.css'

interface Props {
  isPending: boolean
  error?: unknown
  onSubmit: (input: { carrier?: string; trackingNumber?: string; evidenceFileId?: number; evidenceHash?: string }) => void
  title?: string
  submitLabel?: string
  evidenceOptions?: Array<{ id: number; hash: string; label: string }>
  evidenceRequired?: boolean
  embedded?: boolean
}

const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error
  ? error.message
  : '소재 발송을 처리하지 못했어요.'

export function ProcessingShipmentForm({
  isPending,
  error,
  onSubmit,
  title = '소재 발송',
  submitLabel = '발송 처리',
  evidenceOptions = [],
  evidenceRequired = false,
  embedded = false,
}: Props) {
  const [carrier, setCarrier] = useState('')
  const [trackingNumber, setTrackingNumber] = useState('')
  const [evidenceId, setEvidenceId] = useState('')
  const selectedEvidence = evidenceOptions.find(option => String(option.id) === evidenceId)

  const content = <>
      <h2><Package aria-hidden="true" />{title}</h2>
      <div className="pj-fields">
        {/*
          **고르게 하되 가두지 않는다.** 거래 발송(공급사) 화면과 같은 목록·같은 방식이다 —
          자주 쓰는 업체를 제안하되 목록에 없는 화물 업체도 적을 수 있다.
        */}
        <label>
          택배사
          <input
            list="processing-carriers"
            value={carrier}
            onChange={event => setCarrier(event.target.value)}
          />
        </label>
        <datalist id="processing-carriers">
          {CARRIERS.map(name => <option value={name} key={name} />)}
        </datalist>
        <label>
          운송장 번호
          <input
            value={trackingNumber}
            onChange={event => setTrackingNumber(event.target.value)}
          />
        </label>
      </div>
      {evidenceRequired && <label>반환 발송 증빙
        <select value={evidenceId} onChange={event => setEvidenceId(event.target.value)}>
          <option value="">업로드한 증빙을 선택하세요</option>
          {evidenceOptions.map(option => <option value={option.id} key={option.id}>{option.label}</option>)}
        </select>
      </label>}
      <button disabled={isPending || (evidenceRequired && !selectedEvidence)} onClick={() => onSubmit({
        carrier: carrier.trim(), trackingNumber: trackingNumber.trim(),
        evidenceFileId: selectedEvidence?.id, evidenceHash: selectedEvidence?.hash,
      })}>
        {isPending ? '요청을 처리하는 중…' : submitLabel}
      </button>
      {error !== undefined && error !== null && <p className="pj-error" role="alert">{errorMessage(error)}</p>}
    </>

  return embedded
    ? <div className="pj-shipment-form">{content}</div>
    : <section className="pj-panel">{content}</section>
}
