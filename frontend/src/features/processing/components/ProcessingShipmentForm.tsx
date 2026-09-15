import { Package } from '@phosphor-icons/react'
import { useState } from 'react'
import { isAppError } from '../../../utils/errors.ts'
import './processing-jobs.css'

interface Props {
  isPending: boolean
  error?: unknown
  onSubmit: (input: { carrier?: string; trackingNumber?: string }) => void
}

const errorMessage = (error: unknown) => isAppError(error) || error instanceof Error
  ? error.message
  : '소재 발송을 처리하지 못했어요.'

export function ProcessingShipmentForm({ isPending, error, onSubmit }: Props) {
  const [carrier, setCarrier] = useState('')
  const [trackingNumber, setTrackingNumber] = useState('')

  return (
    <section className="pj-panel">
      <h2><Package aria-hidden="true" />소재 발송</h2>
      <div className="pj-fields">
        <label>택배사(선택)<input value={carrier} onChange={event => setCarrier(event.target.value)} /></label>
        <label>운송장 번호(선택)<input value={trackingNumber} onChange={event => setTrackingNumber(event.target.value)} /></label>
      </div>
      <button disabled={isPending} onClick={() => onSubmit({ carrier: carrier.trim(), trackingNumber: trackingNumber.trim() })}>
        {isPending ? '발송 처리 중…' : '발송 처리'}
      </button>
      {error !== undefined && error !== null && <p className="pj-error" role="alert">{errorMessage(error)}</p>}
    </section>
  )
}
