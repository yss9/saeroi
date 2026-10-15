import { MaterialProcessingBadge } from '../../features/material/components/MaterialProcessingBadge.tsx'
import { useNavigate, useParams } from 'react-router-dom'
import {
  BackLink,
  Button,
  EmptyState,
  ErrorMessage,
  Loading,
  StatusBadge,
} from '../../components/common/index.ts'
import { canAccess, getRouteAccess } from '../../app/router/routeAccess.ts'
import { useAuth } from '../../features/auth/hooks/useAuth.ts'
import { MaterialBalanceTable } from '../../features/material/components/MaterialBalanceTable.tsx'
import { EvidenceUpload } from '../../features/evidence/components/EvidenceUpload.tsx'
import { ListingList } from '../../features/marketplace/components/ListingList.tsx'
import { useMaterial } from '../../features/material/hooks/useMaterial.ts'
import { useMaterialBalances } from '../../features/material/hooks/useMaterialBalances.ts'
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

  // 판매 등록은 공급업체의 화면이다. 역할을 여기서 나열하지 않고 라우터가 정한 권한을 묻는다.
  const { user, isAuthenticated } = useAuth()
  const sellAccess = getRouteAccess('/supplier/listings')
  const canSell =
    sellAccess !== undefined &&
    canAccess(sellAccess, { isAuthenticated, role: user?.role ?? null })

  const { data, isPending, isError, error, refetch } = useMaterial(id)

  // **총 수량은 내 수량이 아니다.** 거래는 소재 행을 복제하지 않고 잔량만 옮기므로,
  // 0.5kg 을 산 사람도 이 화면에서는 소재 전체의 발행량을 본다. 내가 가진 양을 따로
  // 적지 않으면 산 것이 사라진 것처럼 읽힌다.
  //
  // 아래 보유자 표와 같은 쿼리 키라 요청이 한 번 더 나가지는 않는다.
  const balances = useMaterialBalances(id)
  const myBalance =
    user === null ? undefined : balances.data?.find((balance) => balance.ownerId === user.id)
  const navigate = useNavigate()

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

      <header className="material-detail__header">
        <h1>{data.name}</h1>
        <p className="material-detail__code">{data.materialCode}</p>
        <MaterialProcessingBadge completedProcessTypes={data.completedProcessTypes} generationType={data.generationType}/>
        <StatusBadge tone={data.status === 'ACTIVE' ? 'success' : 'neutral'}>
          {data.status === 'ACTIVE' ? '보유 중' : '소진'}
        </StatusBadge>
        {/*
          **팔 수 있는 사람에게만 낸다.** 판매 등록(/supplier/listings)은 공급업체의
          화면이라 브랜드가 누르면 /403 으로 튕긴다 — 브랜드는 소재를 사서 쓰는 쪽이다.
          역할을 여기서 나열하지 않고 라우터가 정한 접근 권한을 그대로 묻는다.
        */}
        {canSell && (
          <Button size="sm" onClick={() => navigate(`/supplier/listings?materialId=${id}`)}>
            보유 소재 팔기
          </Button>
        )}
      </header>

      <div className="material-detail__columns">
      <section aria-labelledby="material-info">
        <h2 id="material-info">소재 정보</h2>
        {data.description && <p className="material-detail__description">{data.description}</p>}
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
          {/*
            가지지 않은 소재에는 줄을 그리지 않는다. "0 kg" 으로 적으면 가졌다가 다 쓴
            것처럼 읽히고, 그건 빈칸보다 나쁜 거짓이다.
          */}
          {myBalance && (
            <div>
              <dt>내 보유량</dt>
              <dd>
                {formatKg(myBalance.quantity)}
                {/* 잠긴 몫은 지금 쓸 수 없다. 합계만 보여주면 쓸 수 있는 양을 오해한다. */}
                {myBalance.lockedQuantity > 0 && (
                  <small> · 잠김 {formatKg(myBalance.lockedQuantity)}</small>
                )}
              </dd>
            </div>
          )}
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

      <section aria-labelledby="material-composition">
        <h2 id="material-composition">구성</h2>
        {data.composition.length === 0 ? (
          <EmptyState
            title="구성 정보가 없습니다"
            description="등록 시 혼방 비율을 입력하면 여기에 표시됩니다."
          />
        ) : (
          <>
            {/* 비율 막대. 숫자만 늘어놓으면 어느 섬유가 주된 것인지 한 번에 읽히지 않는다. */}
            <div className="material-detail__mix" role="img" aria-label={compositionLabel(data)}>
              {data.composition.map((row) => (
                <span key={row.fiberType} style={{ width: `${row.percentage}%` }} />
              ))}
            </div>

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
              <p className="material-detail__note">
                나머지 {100 - compositionTotal(data.composition)}% 는 확인되지 않았습니다.
              </p>
            )}
          </>
        )}
      </section>
      </div>

      <section aria-labelledby="material-owners">
        <h2 id="material-owners">보유 현황</h2>
        <MaterialBalanceTable materialId={id} />
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

/** 막대가 읽어 주는 문장. 색만으로는 무엇이 얼마인지 알 수 없다. */
function compositionLabel(data: MaterialDetail): string {
  return data.composition.map((row) => `${fiberLabel(row.fiberType)} ${row.percentage}%`).join(', ')
}

function compositionTotal(composition: MaterialCompositionItem[]): number {
  return composition.reduce((sum, row) => sum + row.percentage, 0)
}

function formatDate(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleDateString('ko-KR', { dateStyle: 'long' })
}
