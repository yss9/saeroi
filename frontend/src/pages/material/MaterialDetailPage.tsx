import { useParams } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../components/common/index.ts'
import { MaterialBalanceTable } from '../../features/material/components/MaterialBalanceTable.tsx'
import { MaterialLineageTree } from '../../features/material/components/MaterialLineageTree.tsx'
import { useMaterial } from '../../features/material/hooks/useMaterial.ts'
import {
  GRADE_LABEL,
  ROOT_ORIGIN_LABEL,
  type MaterialDetail,
} from '../../features/material/types/material.types.ts'
import { formatKg } from '../../utils/quantity.ts'

/**
 * 소재 상세. 티켓(S15P21E104-180)이 요구한 7블록을 조합한다.
 *
 * | 티켓 항목 | 이 화면 |
 * | --- | --- |
 * | 소재 정보 | 기본 정보 목록 |
 * | 이미지 | ⏳ 증빙 업로드 경로가 없어 자리표시자 |
 * | 수량 | 총 수량 (gram → kg) |
 * | Owner | `MaterialBalanceTable` (MR !111) |
 * | Verification | ⚠️ 서버가 항상 REGISTERED — 배지를 그리지 않는다 |
 * | Marketplace 정보 | ⏳ 소재별 Listing 조회 API 가 없어 자리표시자 |
 * | Material History | `MaterialLineageTree` (MR !131) |
 *
 * <b>Page 는 조합만 한다.</b> API 호출·가공은 Feature 컴포넌트가 각자 한다 —
 * Owner 와 History 는 별도 엔드포인트라 각자 자기 로딩·에러를 다룬다.
 * 한 곳에서 모아 부르면 하나가 실패했을 때 화면 전체가 에러가 된다.
 */
export function MaterialDetailPage() {
  const { materialId } = useParams<{ materialId: string }>()

  // 라우트 파라미터는 항상 문자열이고 사용자가 URL 을 직접 고칠 수 있다.
  // 숫자로 파싱되지 않으면 null 을 넘겨 조회 자체를 막는다 (useMaterial 의 enabled).
  const parsed = Number(materialId)
  const id = Number.isInteger(parsed) && parsed > 0 ? parsed : null

  const { data, isPending, isError, error, refetch } = useMaterial(id)

  if (id === null) return <ErrorMessage error="잘못된 소재 주소입니다." />
  if (isPending) return <Loading variant="skeleton" rows={5} label="소재 정보를 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  return (
    <main className="page material-detail">
      <header className="material-detail__header">
        <h1>{data.name}</h1>
        <p className="material-detail__code">{data.materialCode}</p>
        <StatusBadge tone={data.status === 'ACTIVE' ? 'success' : 'neutral'}>
          {data.status === 'ACTIVE' ? '보유 중' : '소진'}
        </StatusBadge>
      </header>

      <section aria-labelledby="material-info">
        <h2 id="material-info">소재 정보</h2>
        {data.description && <p>{data.description}</p>}
        <dl className="material-detail__info">
          <div>
            <dt>출처</dt>
            <dd>{ROOT_ORIGIN_LABEL[data.rootOrigin]}</dd>
          </div>
          <div>
            <dt>등급</dt>
            <dd>{data.grade ? GRADE_LABEL[data.grade] : '미지정'}</dd>
          </div>
          <div>
            <dt>총 수량</dt>
            {/* gram 을 단위 없이 보여주면 1.2kg 인지 1200kg 인지 알 수 없다. */}
            <dd>{formatKg(data.totalQuantity)}</dd>
          </div>
          <div>
            <dt>규격</dt>
            <dd>{formatDimensions(data)}</dd>
          </div>
          <div>
            <dt>등록일</dt>
            <dd>{formatDate(data.createdAt)}</dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="material-onchain">
        <h2 id="material-onchain">온체인</h2>
        {data.tokenId === null ? (
          // null tokenId 가 "등록됐지만 온체인에 없음" 의 유일한 신호다.
          // confirm 엔드포인트가 아직 없어 지금은 모든 소재가 이 상태다.
          <EmptyState
            title="온체인 미확정"
            description="소재가 등록됐지만 아직 블록체인에 발행되지 않았습니다."
          />
        ) : (
          <dl className="material-detail__info">
            <div>
              <dt>Token ID</dt>
              <dd>{data.tokenId}</dd>
            </div>
            <div>
              <dt>Chain ID</dt>
              <dd>{data.chainId}</dd>
            </div>
          </dl>
        )}
      </section>

      <section aria-labelledby="material-owners">
        <h2 id="material-owners">보유 현황</h2>
        <MaterialBalanceTable materialId={id} />
      </section>

      <section aria-labelledby="material-history">
        <h2 id="material-history">소재 이력</h2>
        <MaterialLineageTree materialId={id} />
      </section>

      <section aria-labelledby="material-evidence">
        <h2 id="material-evidence">증빙</h2>
        {data.evidenceHash === null ? (
          <EmptyState
            title="등록된 증빙이 없습니다"
            description="증빙 업로드 기능은 준비 중입니다."
          />
        ) : (
          <dl className="material-detail__info">
            <div>
              <dt>증빙 해시</dt>
              <dd className="material-detail__hash">{data.evidenceHash}</dd>
            </div>
          </dl>
        )}
      </section>

      {/*
        티켓의 나머지 두 항목은 자리표시자다. 화면에 자리를 만들어 두는 이유는
        나중에 레이아웃이 밀리지 않게 하려는 것이고, "왜 비어 있는가" 를 적어 두는
        이유는 사용자가 고장으로 읽지 않게 하려는 것이다.

        ⚠️ Verification 배지를 일부러 그리지 않았다. 서버가 항상 REGISTERED 를
        내려준다 — material 에 컬럼이 없고 파생 판정 근거(signed_at)도 스키마에
        없어서다. 배지를 그리면 실제로 검증된 소재도 "등록만 됨" 으로 보이는데,
        그건 추적성 화면에서 가장 나쁜 거짓이다. 판정 근거가 확보되면 붙인다.
      */}
      <section aria-labelledby="material-verification">
        <h2 id="material-verification">검증 상태</h2>
        <EmptyState
          title="검증 단계 표시 준비 중"
          description="온체인 검증 기록과 연결되면 이 소재가 어디까지 검증됐는지 표시됩니다."
        />
      </section>

      <section aria-labelledby="material-marketplace">
        <h2 id="material-marketplace">판매 정보</h2>
        <EmptyState
          title="판매 정보 준비 중"
          description="이 소재의 판매 등록 현황을 조회하는 기능이 준비 중입니다."
        />
      </section>
    </main>
  )
}

/** 규격은 둘 다 있을 때만 의미가 있다. 하나만 있으면 그것만 보여준다. */
function formatDimensions(material: MaterialDetail): string {
  const { lengthMm, widthMm } = material
  if (lengthMm === null && widthMm === null) return '미지정'
  if (lengthMm !== null && widthMm !== null) {
    return `${lengthMm.toLocaleString('ko-KR')} × ${widthMm.toLocaleString('ko-KR')} mm`
  }
  return `${(lengthMm ?? widthMm)!.toLocaleString('ko-KR')} mm`
}

function formatDate(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleDateString('ko-KR', { dateStyle: 'long' })
}
