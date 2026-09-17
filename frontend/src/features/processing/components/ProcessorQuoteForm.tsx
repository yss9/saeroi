import { CheckCircle, CurrencyKrw, PaperPlaneTilt, WarningCircle } from '@phosphor-icons/react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { toErrorMessage } from '../../../utils/errors.ts'
import { processingKeys } from '../api/processingKeys.ts'
import { processingRequestApi } from '../api/processingRequestApi.ts'
import './processor-quote-form.css'

interface ProcessorQuoteFormProps {
  requestId: number
  requestCode: string
}

const isUnsignedInteger = (value: string) => /^\d+$/.test(value) && Number.isSafeInteger(Number(value))

export function ProcessorQuoteForm({ requestId, requestCode }: ProcessorQuoteFormProps) {
  const queryClient = useQueryClient()
  const [price, setPrice] = useState('')
  const [leadDays, setLeadDays] = useState('')
  const [memo, setMemo] = useState('')
  const [validationError, setValidationError] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const refresh = () => queryClient.invalidateQueries({ queryKey: processingKeys.all })
  const submitQuote = useMutation({
    mutationFn: () => processingRequestApi.submitQuote(requestId, {
      price: Number(price),
      leadDays: Number(leadDays),
      ...(memo.trim() ? { memo: memo.trim() } : {}),
    }),
    onSuccess: () => {
      setSubmitted(true)
      void refresh()
    },
  })
  const rejectRequest = useMutation({
    mutationFn: () => processingRequestApi.reject(requestId),
    onSuccess: () => void refresh(),
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!isUnsignedInteger(price) || !isUnsignedInteger(leadDays) || Number(leadDays) <= 0) {
      setValidationError('견적 금액과 예상 처리 기간을 입력해 주세요.')
      return
    }
    setValidationError('')
    submitQuote.mutate()
  }

  if (submitted) {
    return <section className="pq-success" role="status">
      <CheckCircle size={27} weight="fill" />
      <div><strong>견적을 제출했어요</strong><p>{requestCode} 요청의 Brand가 견적을 검토할 예정입니다.</p></div>
    </section>
  }

  return <form className="pq-form" onSubmit={submit}>
    <div className="pq-heading">
      <span><CurrencyKrw size={22} weight="duotone" /></span>
      <div><strong>견적 작성</strong><small>요청 {requestCode}</small></div>
    </div>
    <div className="pq-fields">
      <label><span>견적 금액 <b>필수</b></span><div className="pq-input-unit"><input aria-label="견적 금액" type="text" inputMode="numeric" placeholder="예: 150000" value={price} onChange={event => setPrice(event.target.value.replace(/[^0-9]/g, ''))} /><em>원</em></div></label>
      <label><span>예상 처리 기간 <b>필수</b></span><div className="pq-input-unit"><input aria-label="예상 처리 기간" type="text" inputMode="numeric" placeholder="예: 5" value={leadDays} onChange={event => setLeadDays(event.target.value.replace(/[^0-9]/g, ''))} /><em>일</em></div></label>
      <label className="pq-memo"><span>견적 메모 <small>선택</small></span><textarea aria-label="견적 메모" maxLength={2000} placeholder="포함 작업, 배송 조건 등 Brand가 확인할 내용을 작성하세요." value={memo} onChange={event => setMemo(event.target.value)} /><small>{memo.length.toLocaleString()} / 2,000</small></label>
    </div>
    {validationError && <p className="pq-error" role="alert"><WarningCircle size={16} />{validationError}</p>}
    {submitQuote.isError && <p className="pq-error" role="alert"><WarningCircle size={16} />{toErrorMessage(submitQuote.error)}</p>}
    {rejectRequest.isError && <p className="pq-error" role="alert"><WarningCircle size={16} />{toErrorMessage(rejectRequest.error)}</p>}
    <div className="pq-actions">
      <button type="button" className="pq-reject" disabled={rejectRequest.isPending || submitQuote.isPending} onClick={() => rejectRequest.mutate()}>{rejectRequest.isPending ? '거절 처리 중…' : '요청 거절'}</button>
      <button type="submit" className="pq-submit" disabled={submitQuote.isPending || rejectRequest.isPending}><PaperPlaneTilt size={17} weight="fill" />{submitQuote.isPending ? '제출 중…' : '견적 제출'}</button>
    </div>
  </form>
}
