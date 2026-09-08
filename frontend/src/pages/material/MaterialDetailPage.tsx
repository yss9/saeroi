import { useParams } from 'react-router-dom'
import { EmptyState, ErrorMessage, Loading, StatusBadge } from '../../components/common/index.ts'
import { MaterialBalanceTable } from '../../features/material/components/MaterialBalanceTable.tsx'
import { EvidenceUpload } from '../../features/evidence/components/EvidenceUpload.tsx'
import { MaterialLineageTree } from '../../features/material/components/MaterialLineageTree.tsx'
import { MaterialMintPanel } from '../../features/material/components/MaterialMintPanel.tsx'
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
