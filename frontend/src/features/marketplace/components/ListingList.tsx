import { EmptyState, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useListings } from '../hooks/useListings.ts'
import type { GetListingsParams } from '../api/getListings.ts'
import { ListingCard } from './ListingCard.tsx'

export interface ListingListProps {
  params?: GetListingsParams
  /** 판매자 화면에서 카드마다 붙일 동작 (취소 버튼 등) */
  renderActions?: (listingId: number) => React.ReactNode
  emptyTitle?: string
  emptyDescription?: string
}

/** 목록 조회 + 로딩·에러·빈 결과 세 상태 처리. */
export function ListingList({
  params,
  renderActions,
  emptyTitle = '판매 중인 소재가 없습니다',
  emptyDescription = '조건을 바꾸면 다른 소재를 찾을 수 있습니다.',
}: ListingListProps) {
  const { data, isPending, isError, error, refetch } = useListings(params)

  if (isPending) return <Loading variant="skeleton" rows={4} label="판매 목록을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.length === 0) return <EmptyState title={emptyTitle} description={emptyDescription} />

  return (
    <ul className="listing-list">
      {data.map((listing) => (
        <li key={listing.id}>
          <ListingCard listing={listing} actions={renderActions?.(listing.id)} />
        </li>
      ))}
    </ul>
  )
}
