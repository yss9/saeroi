import { Link } from 'react-router-dom'
import { StatusBadge, VerificationBadge } from '../../../components/common/index.ts'
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
          <dt>보유 수량</dt>
          {/* 숫자는 로케일 포맷을 거친다. 1200 → 1,200 */}
          <dd>{material.totalQuantity.toLocaleString('ko-KR')}</dd>
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
