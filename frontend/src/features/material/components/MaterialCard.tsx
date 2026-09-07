import { Link } from 'react-router-dom'
import { StatusBadge, VerificationBadge } from '../../../components/common/index.ts'
import { formatKg } from '../../../utils/quantity.ts'
import { ROOT_ORIGIN_LABEL, type MaterialSummary } from '../types/material.types.ts'

export interface MaterialCardProps {
  material: MaterialSummary
}

/**
 * 소재 목록 카드.
 *
 * Material 도메인에서만 쓰이므로 components/common 이 아니라 여기에 둔다 (§14).
 * 다른 도메인에서 필요해지는 순간 common 으로 올릴지 논의한다 — 미리 올리지 않는다.
 */
export function MaterialCard({ material }: MaterialCardProps) {
  return (
    <article className="material-card">
      <header className="material-card__header">
        <h3 className="material-card__name">
          <Link to={`/materials/${material.id}`}>{material.name}</Link>
        </h3>
        <VerificationBadge state={material.verificationState} />
      </header>

      <dl className="material-card__meta">
        <div>
          <dt>소재 코드</dt>
          <dd>{material.materialCode}</dd>
        </div>
        <div>
          <dt>출처</dt>
          <dd>{ROOT_ORIGIN_LABEL[material.rootOrigin]}</dd>
        </div>
        <div>
          {/*
            라벨이 "보유 수량" 이었는데 틀렸다. totalQuantity 는 그 소재의
            **총 발행량**이고 보유 수량이 아니다 — 거래가 일어나면 둘이 달라진다.
            내 보유량은 GET /api/materials/me 가 quantity 로 준다.

            그리고 값이 gram 인데 단위 없이 "1,200" 으로만 보여, 1.2kg 인지
            1200kg 인지 알 수 없었다. formatKg 로 변환한다.
          */}
          <dt>총 수량</dt>
          <dd>{formatKg(material.totalQuantity)}</dd>
        </div>
      </dl>

      <footer>
        <StatusBadge tone={material.status === 'ACTIVE' ? 'success' : 'neutral'}>
          {material.status === 'ACTIVE' ? '보유 중' : '소진'}
        </StatusBadge>
      </footer>
    </article>
  )
}
