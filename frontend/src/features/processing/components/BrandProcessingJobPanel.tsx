import { CheckCircle, Factory } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { isAppError } from '../../../utils/errors.ts'
import { GRADE_LABEL } from '../../material/types/material.types.ts'
import { useProcessingJob } from '../hooks/useProcessingJobs.ts'
import { MATERIAL_TYPE_LABEL } from '../types/processorProfile.types.ts'
import { canConfirmReceipt, jobLabel } from '../utils/processingProgress.ts'

const grams = (value: number) => `${value.toLocaleString('ko-KR')} g`
const message = (error: unknown) =>
  isAppError(error) || error instanceof Error ? error.message : '처리하지 못했어요.'

/**
 * 요청 상세 안의 가공 진행.
 *
 * **가공 진행 화면을 없애고 이 자리로 옮겼다.** 요청 하나가 곧 작업 하나라 목록이 둘일
 * 이유가 없었고, 브랜드는 자기가 보낸 요청으로만 그 일을 기억한다 — 진행 상황을 보려고
 * 다른 메뉴로 건너가면 어느 요청의 작업인지 다시 맞춰 봐야 했다.
 *
 * **여기서 하는 일은 수령 확인 하나다.** 가공사가 결과물을 보내면(`RETURN_SHIPPED`) 브랜드가
 * 실물을 받고 확인해야 산출 소재가 발행된다 — 그 전까지는 소재가 없고, 그래서 제품 생산에
 * 투입할 수도 없다. 확인은 온체인 승인(MetaMask)과 서버 기록을 한 번에 한다.
 *
 * 반려처럼 드문 일은 작업 상세(`/processing/jobs/:jobId`)가 계속 맡는다.
 */
export function BrandProcessingJobPanel({ jobId }: { jobId: number }) {
  const flow = useProcessingJob(jobId)
  const job = flow.detail.data
  if (!job) return null

  return (
    <section className="br-detail__job" aria-labelledby="job-progress-title">
      <div className="br-detail__section-title">
        <div>
          <Factory aria-hidden="true" />
          <h2 id="job-progress-title">가공 진행</h2>
        </div>
        <span className={`br-detail__request-status is-${job.status.toLowerCase()}`}>
          {jobLabel(job.status)}
        </span>
      </div>

      {/* 무엇을 돌려받는지 먼저 적는다. 수령 확인은 이 목록을 눈으로 대조한 뒤에 누르는 일이다. */}
      {job.outputs.length > 0 && (
        <ul className="br-detail__outputs">
          {job.outputs.map((output) => (
            <li key={output.outputId}>
              <strong>{output.name}</strong>
              <span>
                {MATERIAL_TYPE_LABEL[output.materialType]} · {GRADE_LABEL[output.grade]}
              </span>
              <b>{grams(output.quantity)}</b>
            </li>
          ))}
        </ul>
      )}

      {canConfirmReceipt(job.status) ? (
        <div className="br-detail__receive">
          <p>
            결과물을 받으셨다면 수령을 확인해 주세요. 확인하면 위 산출 소재가 보유 소재로
            들어오고, 제품 생산에서 투입할 수 있게 됩니다.
          </p>
          <button type="button" disabled={flow.approve.isPending} onClick={() => flow.approve.mutate()}>
            <CheckCircle aria-hidden="true" />
            {flow.approve.isPending ? '확인 중…' : '소재 수령 확인'}
          </button>
        </div>
      ) : job.status === 'CONFIRMED' ? (
        <p className="br-detail__job-note">
          수령을 확인했습니다. 받은 소재는 <Link to="/inventory">보유 소재</Link>에 있고{' '}
          <Link to="/brand/production">제품 생산</Link>에서 투입할 수 있습니다.
        </p>
      ) : (
        <p className="br-detail__job-note">
          가공사가 결과물을 보내면 이 자리에서 수령을 확인할 수 있습니다.{' '}
          <Link to={`/processing/jobs/${jobId}`}>작업 상세</Link>
        </p>
      )}

      {flow.approve.isError && (
        <p className="br-detail__job-error" role="alert">
          {message(flow.approve.error)}
        </p>
      )}
    </section>
  )
}
