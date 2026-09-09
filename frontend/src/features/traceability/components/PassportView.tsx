import { ErrorMessage, Loading } from '../../../components/common/index.ts'
import { getPassportThumbnailUrl } from '../api/getPassport.ts'
import { usePassport } from '../hooks/usePassport.ts'
import type { PassportItemStatus, ProductPassport } from '../types/traceability.types.ts'
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

/**
 * 이 개체가 어떤 상태인지를 블록체인 용어 없이 말한다 (S15P21E104-109).
 * `verification_state` 같은 내부 상태 이름을 그대로 노출하지 않는다 — 소비자는
 * "이게 진짜 내가 받은 그 제품이 맞는가" 를 궁금해하지 트랜잭션 상태를 궁금해하지 않는다.
 */
const ITEM_STATUS_MESSAGE: Record<PassportItemStatus, string> = {
  IN_STOCK: '아직 판매되지 않은 제품입니다.',
  RESERVED: '구매가 진행 중인 제품입니다.',
  SOLD: '제품 사용 확인 완료',
}

/**
 * QR Passport 공개 화면.
 *
 * **소재 출처·거래·가공 확인은 아직 없다.** `traceabilityAvailable` 이 `false` 인 동안은
 * "제품 확인" 한 단계만 보여준다 — 없는 이력을 확인된 것처럼 그리면 위조 여부를
 * 가리는 이 화면의 존재 이유가 무너진다. Production 도메인이 생겨 값이 채워지면
 * 아래 `stages` 에 소재 출처·거래·가공 단계를 추가한다.
 */
export function PassportView({ qrToken }: PassportViewProps) {
  const { data, isPending, isError, error, refetch } = usePassport(qrToken)

  if (isPending) return <Loading variant="skeleton" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />

  const stages = [
    { key: 'PRODUCT', label: '제품 확인', confirmed: true },
    { key: 'MATERIAL_ORIGIN', label: '소재 출처 확인', confirmed: data.traceabilityAvailable },
    { key: 'TRADE', label: '거래 확인', confirmed: data.traceabilityAvailable },
    { key: 'PROCESSING', label: '가공 과정 확인', confirmed: data.traceabilityAvailable },
  ] as const

  return (
    <article className="passport">
      <img
        className="passport__thumbnail"
        src={getPassportThumbnailUrl(qrToken)}
        alt={`${data.product.name} 대표 이미지`}
      />

      <header className="passport__header">
        <h1>{data.product.name}</h1>
        <p className="passport__item-code">{data.itemCode}</p>
      </header>
      <p className="passport__category">{CATEGORY_LABEL[data.product.category]}</p>

      <p>{ITEM_STATUS_MESSAGE[data.itemStatus]}</p>

      <p className="passport__description">{data.product.description}</p>

      <ul className="passport__stages">
        {stages.map((stage) => (
          <li
            key={stage.key}
            className={`passport__stage${stage.confirmed ? '' : ' passport__stage--pending'}`}
          >
            {stage.confirmed ? `${stage.label} 완료` : `${stage.label} 준비 중`}
          </li>
        ))}
      </ul>
    </article>
  )
}
