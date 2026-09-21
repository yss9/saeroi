import { ArrowLeft, CalendarBlank, Clock, Factory, Package, Receipt } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { useBrandProcessingRequestDetail } from '../hooks/useBrandProcessingRequestDetail.ts'
import { PROCESS_TYPE_LABEL } from '../types/processing.types.ts'
import { progressLabel, progressTone } from '../utils/processingProgress.ts'
import { MATERIAL_TYPE_LABEL } from '../types/processorProfile.types.ts'
import { BrandProcessingJobPanel } from './BrandProcessingJobPanel.tsx'
import { ProcessingPaymentEntry } from './ProcessingPaymentEntry.tsx'
import { formatDateTime } from '../utils/processingTime.ts'
import './brand-processing-detail.css'

const won = (value: number) => `${value.toLocaleString('ko-KR')}원`
const grams = (value: number) => `${value.toLocaleString('ko-KR')} g`
const date = formatDateTime
const message = (error: unknown) => isAppError(error) || error instanceof Error
  ? error.message
  : '정보를 불러오지 못했어요.'

interface Props { requestId: number }

/**
 * 가공 요청 상세.
 *
 * **견적 단계가 없어졌다.** 브랜드가 검색 결과에서 가격과 소요일을 보고 바로 고르므로,
 * 요청이 만들어지는 순간 작업과 금액이 함께 정해진다 — 이 화면이 하던 "견적을 받아 고르는"
 * 일은 사라지고 남은 것은 셋이다.
 *
 *   1) 무엇을 누구에게 맡겼는가 (요청 정보)
 *   2) 결제 — 아직 안 했으면 여기서 한다
 *   3) 그 뒤의 진행과 수령 확인 (가공 진행 칸)
 */
export function BrandProcessingRequestDetail({ requestId }: Props) {
  const { detail, job } = useBrandProcessingRequestDetail(requestId)

  if (detail.isPending) {
    return <div className="br-detail__state" role="status">요청을 불러오는 중이에요…</div>
  }
  if (detail.isError) {
    return (
      <div className="br-detail__state br-detail__state--error" role="alert">
        <strong>상세 정보를 불러오지 못했어요</strong><p>{message(detail.error)}</p>
        <button type="button" onClick={() => { void detail.refetch() }}>다시 시도</button>
      </div>
    )
  }
  if (!detail.data) return null

  const request = detail.data
  // 결제는 작업에 걸려 있다. 요청이 만들어질 때 함께 생긴 작업이 아직 CREATED(결제 전)면
  // 여기서 결제한다 — 결제하면 가공사가 수락할 차례로 넘어간다.
  const paymentJob = job?.status === 'CREATED' ? job : null

  return (
    <section className="br-detail">
      <Link className="br-detail__back" to="/brand/processing/requests"><ArrowLeft aria-hidden="true" />요청 내역</Link>
      <header className="br-detail__heading">
        {/* 요청 번호(`PR` + UUID 28자리)는 제목이 될 수 없다 — 읽어서 얻을 것이 없다.
            어느 요청인지는 소재 이름이 말한다. */}
        <div><span>PROCESSING REQUEST DETAIL</span><h1>{request.materialName}</h1></div>
        {/* 요청은 만들어진 뒤 움직이지 않는다 — 그 뒤의 일은 작업이 말한다. */}
        <span className={`br-detail__request-status is-${progressTone(request.status, job?.status)}`}>
          {progressLabel(request.status, job?.status, job?.chainJobId != null)}
        </span>
      </header>

      <section className="br-detail__request-card" aria-labelledby="request-information-title">
        <div className="br-detail__section-title">
          <div><Package aria-hidden="true" /><h2 id="request-information-title">요청 정보</h2></div>
          {/* 잠긴 수량은 아래 "요청 수량" 이 이미 말한다. 이 자리에는 언제 보낸 요청인지를 둔다. */}
          <span><Clock aria-hidden="true" />{date(request.createdAt)} 요청</span>
        </div>
        <dl className="br-detail__request-grid">
          {/* 소재 이름은 제목이 이미 말한다 — 종류와 코드만 둔다. */}
          <div><dt>소재</dt><dd><strong>{MATERIAL_TYPE_LABEL[request.materialType]}</strong><span>{request.materialCode}</span></dd></div>
          <div><dt>가공업체</dt><dd><strong>{request.processorName}</strong></dd></div>
          <div><dt>요청 수량</dt><dd><strong>{grams(request.quantity)}</strong></dd></div>
          <div><dt>가공비</dt><dd><strong>{job ? won(job.fee) : '확인 중'}</strong></dd></div>
        </dl>
        <div className="br-detail__processes"><span>요청 가공</span><div>{request.processTypes.map(type => <strong key={type}>{PROCESS_TYPE_LABEL[type]}</strong>)}</div></div>
      </section>

      {/*
        **견적서 자리에 "무엇을 얼마에 맡겼는가" 를 둔다.**

        견적 단계가 사라져 받을 견적서가 없다. 금액은 브랜드가 검색 결과에서 고른 순간
        서버가 확정해 작업에 적어 두므로, 결제 버튼 앞에서 "이 금액이 어느 업체의
        것인가" 를 확인할 근거는 요청(업체·공정·수량)과 작업(금액)에 모두 있다.
      */}
      {job && (
        <section className="br-detail__quotes" aria-labelledby="processing-terms-title">
          <div className="br-detail__section-title">
            <div><Receipt aria-hidden="true" /><h2 id="processing-terms-title">가공 조건</h2></div>
          </div>

          <section className="br-detail__chosen" aria-label="맡긴 가공업체">
            <div className="br-detail__chosen-head">
              <Factory aria-hidden="true" />
              <div>
                <strong>{request.processorName}</strong>
                <span>{request.processTypes.map(type => PROCESS_TYPE_LABEL[type]).join(' · ')}</span>
              </div>
            </div>
            <dl>
              {/* 결제 전에는 바로 아래 결제 칸이 같은 금액을 이미 말한다 — 두 줄에 적지 않는다.
                  결제가 끝나 그 칸이 사라진 뒤에는 여기가 유일하게 금액을 남기는 자리다. */}
              {!paymentJob && <div><dt>가공 비용</dt><dd>{won(job.fee)}</dd></div>}
              <div><dt>요청 수량</dt><dd>{grams(request.quantity)}</dd></div>
            </dl>
          </section>

          {paymentJob && <ProcessingPaymentEntry job={paymentJob} />}
        </section>
      )}

      {/*
        **결제 다음 차례는 브랜드다.** 가공사의 수락이 곧 온체인 서명이므로, 서명할
        대상(job)이 체인에 먼저 있어야 한다 — 그 등록은 브랜드 지갑만 할 수 있다.
        여기서 "수락을 기다리세요" 로 끝내면 양쪽이 서로를 기다리다 `acceptExpiresAt`
        이 지나 자동 거절되고 환불된다.

        발송은 그 뒤다. 수락 전에 보낸 소재는 가공사가 거절하면 갈 곳이 없다.
      */}
      {job?.status === 'PAID' && job.chainJobId === null && (
        <div className="br-detail__job-link">
          <span>결제가 끝났습니다. 소재를 온체인 가공 작업에 잠가야 가공사가 수락할 수 있습니다.</span>
          <Link to={`/processing/jobs/${job.jobId}`}>온체인 작업 등록하기</Link>
        </div>
      )}

      {job?.status === 'ACCEPTED' && (
        <div className="br-detail__job-link">
          {/* 소재 발송은 발송 서명까지 거친다. 그 단계는 작업 상세(useProcessingJobs)에만
              있다 — 여기서 다시 만들면 온체인 서명 없이 상태만 SHIPPED 로 바뀌는 두 번째
              발송 경로가 생긴다. */}
          <span>가공사가 요청을 수락했습니다. 소재 발송은 작업 상세에서 온체인으로 진행합니다.</span>
          <Link to={`/processing/jobs/${job.jobId}`}>소재 발송하기</Link>
        </div>
      )}

      {/*
        진행 상황과 수령 확인은 이 화면 안에서 끝난다.

        **결제 전과 만료된 작업에는 두지 않는다.** 앞은 아직 시작하지 않은 일이고, 뒤는
        기한이 지나 소재 잠금까지 풀린 건이라 진행할 것도 받을 것도 없다.
      */}
      {job && !['CREATED', 'EXPIRED'].includes(job.status) && (
        <BrandProcessingJobPanel jobId={job.jobId} />
      )}

      {/*
        **결제를 기다리는 동안만 둔다.** 기한이 지나면 요청이 만료되고 잠긴 소재가 풀린다 —
        이미 결제한 요청에서는 지난 일을 다시 알리는 칸이 되고, 만료된 화면에서는 아직
        유효한 것처럼 읽히기까지 한다.
      */}
      {paymentJob && (
        <aside className="br-detail__notice"><CalendarBlank aria-hidden="true" /><p>{date(paymentJob.paymentExpiresAt)}까지 결제하지 않으면 요청이 만료되고 잠긴 소재 수량이 풀립니다.</p></aside>
      )}
    </section>
  )
}
