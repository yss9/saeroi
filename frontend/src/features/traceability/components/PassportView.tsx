import { ShieldCheck } from '@phosphor-icons/react'
import { ErrorMessage, Loading } from '../../../components/common/index.ts'
import { getPassportThumbnailUrl } from '../api/getPassport.ts'
import { usePassport } from '../hooks/usePassport.ts'
import type { ProductPassport } from '../types/traceability.types.ts'
import { JourneyExplorer } from './JourneyExplorer.tsx'
import logoUrl from '../../../assets/saeroi-logo-vector.svg'
import '../../../pages/landing/landing.css'
import './passport.css'

export interface PassportViewProps {
  qrToken: string
}

const CATEGORY_LABEL: Record<ProductPassport['product']['category'], string> = {
  BAG: '가방',
  WALLET: '지갑',
  CLOTHING: '의류',
  ACCESSORY: '액세서리',
  HOME: '홈·리빙',
  ETC: '기타',
}

function formatIssuedAt(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}

/** 로그인 없이 QR 토큰만으로 확인하는 제품별 공개 Journey 화면. */
export function PassportView({ qrToken }: PassportViewProps) {
  const { data, isPending, isError, error, refetch } = usePassport(qrToken)

  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  return (
    <article className="landing-page passport">
      <header className="passport-brandbar">
        <a className="passport-brandbar__logo" href="/" aria-label="SAEROI 홈">
          <img src={logoUrl} alt="SAEROI" />
        </a>
        <span className="passport-brandbar__title">PRODUCT JOURNEY</span>
      </header>

      <div className="passport__content">
        <section className="passport-product" aria-labelledby="passport-product-title">
          <img
            className="passport-product__image"
            src={getPassportThumbnailUrl(qrToken)}
            alt={`${data.product.name} 대표 이미지`}
          />

          <div className="passport-product__identity">
            <h1 id="passport-product-title">{data.product.name}</h1>
            <p className="passport-product__brand">
              {data.product.brandName ?? CATEGORY_LABEL[data.product.category]}
            </p>
          </div>

          <dl className="passport-product__meta">
            <div>
              <dt>제품 ID</dt>
              <dd>{data.itemCode}</dd>
            </div>
            <div>
              <dt>시리얼 번호</dt>
              <dd>#{String(data.serialNumber).padStart(4, '0')}</dd>
            </div>
            <div>
              <dt>기록 발행일</dt>
              <dd>{formatIssuedAt(data.issuedAt)}</dd>
            </div>
          </dl>

          <span className="passport-product__verified">
            <ShieldCheck size={18} weight="fill" aria-hidden="true" />
            Verified Upcycled Product
          </span>
        </section>

        {data.traceabilityAvailable && data.journey ? (
          <>
            {data.journey.truncated ? (
              <p className="passport__notice" role="status">
                {data.journey.message ?? '표시 가능한 범위의 여정 기록만 제공됩니다.'}
              </p>
            ) : null}
            <JourneyExplorer journey={data.journey} />
          </>
        ) : (
          <section className="passport-empty" aria-label="여정 준비 상태">
            <ShieldCheck size={38} weight="duotone" aria-hidden="true" />
            <div>
              <h2>제품 여정을 준비하고 있습니다.</h2>
              <p>제품 정보는 확인되었으며, 연결된 Journey 기록은 아직 제공되지 않습니다.</p>
            </div>
          </section>
        )}
      </div>
      <footer className="passport-footer">
        <a href="/" aria-label="SAEROI 홈"><img src={logoUrl} alt="SAEROI" /></a>
        <p>쓰임을 다한 소재에 새로운 가치를 더합니다.</p>
        <a href="/about">새로이 알아보기</a>
      </footer>
    </article>
  )
}
