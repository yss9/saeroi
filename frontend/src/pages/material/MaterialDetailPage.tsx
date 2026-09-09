import { Link, useLocation, useParams } from 'react-router-dom'
import {
  BackLink,
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
  VerificationBadge,
} from '../../components/common/index.ts'
import { MaterialBalanceTable } from '../../features/material/components/MaterialBalanceTable.tsx'
import { EvidenceUpload } from '../../features/evidence/components/EvidenceUpload.tsx'
import { MaterialLineageTree } from '../../features/material/components/MaterialLineageTree.tsx'
import { MaterialMintPanel } from '../../features/material/components/MaterialMintPanel.tsx'
import { MaterialSourceVerificationPanel } from '../../features/material/components/MaterialSourceVerificationPanel.tsx'
import { ListingList } from '../../features/marketplace/components/ListingList.tsx'
import { useMaterial } from '../../features/material/hooks/useMaterial.ts'
import {
  FIBER_TYPE_LABEL,
  GRADE_LABEL,
  ORIGIN_REASON_LABEL,
  ROOT_ORIGIN_LABEL,
  type MaterialCompositionItem,
  type MaterialDetail,
  type MaterialFiberType,
} from '../../features/material/types/material.types.ts'
import { formatKg } from '../../utils/quantity.ts'
import './material-detail.css'

/**
 * 소재 상세. 티켓(S15P21E104-180)이 요구한 7블록을 조합한다.
 *
 * | 티켓 항목 | 이 화면 |
 * | --- | --- |
 * | 소재 정보 | 기본 정보 목록 |
 * | 이미지 | ⏳ 증빙 업로드 경로가 없어 자리표시자 |
 * | 수량 | 총 수량 (gram → kg) |
 * | Owner | `MaterialBalanceTable` (MR !111) |
 * | Verification | `VerificationBadge` + Blockchain Proof (S15P21E104-171/-182) |
 * | Marketplace 정보 | `ListingList` (소재별 필터, S15P21E104-438/439) |
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

  // 등록 폼이 심어 준 표시. 방금 만든 소재라면 다음에 할 일을 알려 준다 —
  // 등록만 끝내고 판매 등록을 못 찾아 화면에서 막히는 제보가 있었다.
  const justCreated = (useLocation().state as { justCreated?: boolean } | null)?.justCreated === true

  // **출구는 이른 반환보다 먼저 그려져야 한다.** 조회가 실패한 화면이야말로 사용자가
  // 나가고 싶은 화면인데, BackLink 를 성공 분기에만 두면 그때 갇힌다. 이 화면은
  // Public 라우트라 사이드바 셸도 없어서 브라우저 뒤로 가기 말고는 길이 없다.
  const back = <BackLink to="/inventory" label="보유 소재" />

  if (id === null) {
    return (
      <main className="page">
        {back}
        <ErrorMessage error="잘못된 소재 주소입니다." />
      </main>
    )
  }
  if (isPending) {
    return (
      <main className="page">
        {back}
        <Loading variant="skeleton" rows={5} label="소재 정보를 불러오는 중" />
      </main>
    )
  }
  if (isError) {
    return (
      <main className="page">
        {back}
        <ErrorMessage error={error} onRetry={() => void refetch()} />
      </main>
    )
  }

  return (
    <main className="page material-detail">
      {back}

      {justCreated && <MaterialCreatedNextStep />}

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
          {/*
            신소재면 발생 사유 자체가 없는 개념이라 줄을 그리지 않는다.
            "미지정" 으로 두면 입력을 빠뜨린 것처럼 보인다.
          */}
          {data.rootOrigin === 'WASTE' && (
            <div>
              <dt>발생 사유</dt>
              <dd>
                {data.originReason ? ORIGIN_REASON_LABEL[data.originReason] : '미지정'}
                {data.originReasonNote && ` · ${data.originReasonNote}`}
              </dd>
            </div>
          )}
          <div>
            <dt>등록일</dt>
            <dd>{formatDate(data.createdAt)}</dd>
          </div>
        </dl>
      </section>

      <section aria-labelledby="material-onchain">
        <h2 id="material-onchain">온체인</h2>
        {data.tokenId !== null && (
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
        {/* null tokenId 가 "등록됐지만 온체인에 없음" 의 유일한 신호다.
            패널이 그 상태에서 발행 버튼을, 발행 후에는 토큰 번호를 보여준다. */}
        <MaterialMintPanel material={data} />
      </section>

      <section aria-labelledby="material-composition">
        <h2 id="material-composition">구성</h2>
        {data.composition.length === 0 ? (
          <EmptyState
            title="구성 정보가 없습니다"
            description="등록 시 혼방 비율을 입력하면 여기에 표시됩니다."
          />
        ) : (
          <>
            <dl className="material-detail__info">
              {/* 서버가 비율 내림차순으로 정렬해 준다. 여기서 다시 정렬하지 않는다 —
                  양쪽에서 정렬하면 같은 소재의 줄 순서가 화면마다 달라 보인다. */}
              {data.composition.map((row) => (
                <div key={row.fiberType}>
                  <dt>{fiberLabel(row.fiberType)}</dt>
                  <dd>{row.percentage}%</dd>
                </div>
              ))}
            </dl>
            {/* 합계가 100 미만인 것은 정상이다. 모르는 부분을 지어내지 않은 결과이므로
                "나머지 미확인" 이라고 밝힌다 — 밝히지 않으면 데이터 누락으로 읽힌다. */}
            {compositionTotal(data.composition) < 100 && (
              <p>나머지 {100 - compositionTotal(data.composition)}% 는 확인되지 않았습니다.</p>
            )}
          </>
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
        {/*
          업로드와 목록을 EvidenceUpload 가 함께 맡는다. 페이지가 조회를 대신하지 않는
          이유는 다른 블록과 같다 — 증빙 조회가 실패해도 소재 정보는 보여야 한다.

          material.evidenceHash 는 여기서 쓰지 않는다. 그 컬럼은 **manifest 해시**
          (여러 증빙을 묶은 JSON 의 해시)를 담을 자리이고 개별 파일 해시가 아니다.
          아직 manifest 를 만드는 경로가 없어 늘 null 이며, 개별 파일 해시는
          evidence_file 이 갖는다. 둘을 섞으면 "증빙 없음" 인데 파일은 있는 상태가 된다.
        */}
        <EvidenceUpload referenceType="MATERIAL" referenceId={id} />
      </section>

      <section aria-labelledby="material-verification">
        <h2 id="material-verification">검증 상태</h2>
        <VerificationBadge state={data.verificationState} />
        {/* null tokenId 가 "온체인에 없음" 인 것처럼, verification 이 null 인 것은
            "출처 검증 근거 없음" 이다. 패널이 그 상태에서 서명 버튼을, 확정 후에는
            근거(지갑·Tx 해시·시각)를 보여준다. */}
        <MaterialSourceVerificationPanel material={data} />
        {data.verification && (
          <dl className="material-detail__info">
            <div>
              <dt>서명 지갑</dt>
              <dd>{data.verification.signerWallet}</dd>
            </div>
            <div>
              <dt>검증 시각</dt>
              <dd>{formatDate(data.verification.signedAt)}</dd>
            </div>
            <div>
              <dt>Tx 해시</dt>
              <dd title={data.verification.txHash}>{data.verification.txHash}</dd>
            </div>
          </dl>
        )}
      </section>

      {/*
        listingId 를 누르면 ListingCard 의 Link 가 /marketplace/:listingId 로 보낸다
        (완료 조건: "선택하면 marketplace 상세로 이동"). 판매 중인 글이 없으면
        ListingList 가 emptyTitle/emptyDescription 으로 자체 처리한다.
      */}
      <section aria-labelledby="material-marketplace">
        <h2 id="material-marketplace">판매 정보</h2>
        <ListingList
          params={{ materialId: id }}
          emptyTitle="판매 중이 아닙니다"
          emptyDescription="이 소재는 현재 Marketplace 에 등록되어 있지 않습니다."
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

/**
 * 서버가 주는 `fiberType` 은 문자열이다 — 컬럼이 `VARCHAR(50)` 이라 허용 목록 밖의 값이
 * 이미 저장돼 있을 수 있다. 라벨이 없으면 <b>원본을 그대로</b> 보여준다.
 * 빈칸으로 두면 성분이 하나 사라진 것처럼 보인다.
 */
function fiberLabel(fiberType: string): string {
  return FIBER_TYPE_LABEL[fiberType as MaterialFiberType] ?? fiberType
}

function compositionTotal(composition: MaterialCompositionItem[]): number {
  return composition.reduce((sum, row) => sum + row.percentage, 0)
}

function formatDate(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleDateString('ko-KR', { dateStyle: 'long' })
}

/**
 * 등록 직후에만 나오는 다음 단계 안내.
 *
 * <b>등록은 끝이 아니다.</b> 소재를 만들어도 판매 등록을 해야 마켓플레이스에 뜬다.
 * 그런데 등록 폼은 이 상세 화면으로 보내 놓고 여기서 갈 곳을 알려 주지 않았고,
 * 이 화면은 Public 라우트라 사이드바도 없어서 사용자가 브라우저 뒤로 가기로
 * 빠져나가야 했다. 그 자리를 메운다.
 *
 * 온체인 발행({@code MaterialMintPanel})은 아래에 그대로 있다 — 순서를 강제하지 않는
 * 이유는 발행이 실패해도 소재와 판매 등록은 유효하기 때문이다.
 */
function MaterialCreatedNextStep() {
  return (
    <section className="material-detail__next" role="status">
      <div>
        <h2>소재를 등록했습니다.</h2>
        <p>
          아직 마켓플레이스에는 보이지 않습니다. 판매 등록을 해야 다른 업체가 구매할 수
          있습니다.
        </p>
      </div>
      <Link className="material-detail__next-cta" to="/supplier/listings">
        판매 등록하러 가기
      </Link>
    </section>
  )
}
