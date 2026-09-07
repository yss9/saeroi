import { EmptyState, ErrorMessage, Loading } from '../../../components/common/index.ts'
import { useListings } from '../hooks/useListings.ts'
import type { GetListingsParams } from '../api/getListings.ts'
import { ListingCard } from './ListingCard.tsx'
import { Pagination } from './Pagination.tsx'

export interface ListingListProps {
  params?: GetListingsParams
  /** 판매자 화면에서 카드마다 붙일 동작 (취소 버튼 등) */
  renderActions?: (listingId: number) => React.ReactNode
  /**
   * 페이지 이동. 주지 않으면 페이지 UI 가 나오지 않는다 —
   * 페이지 번호는 조회 조건이라 이 컴포넌트가 아니라 화면이 들고 있어야 한다
   * (뒤로 가기·URL 공유 같은 것이 전부 화면의 관심사다).
   */
  onPageChange?: (page: number) => void
  emptyTitle?: string
  emptyDescription?: string
}

/** 목록 조회 + 로딩·에러·빈 결과 세 상태 처리. */
export function ListingList({
  params,
  renderActions,
  onPageChange,
  emptyTitle = '판매 중인 소재가 없습니다',
  emptyDescription = '조건을 바꾸면 다른 소재를 찾을 수 있습니다.',
}: ListingListProps) {
  const { data, isPending, isError, error, refetch } = useListings(params)

  if (isPending) return <Loading variant="skeleton" rows={4} label="판매 목록을 불러오는 중" />
  if (isError) return <ErrorMessage error={error} onRetry={() => void refetch()} />
  if (data.content.length === 0)
    return <EmptyState title={emptyTitle} description={emptyDescription} />

  return (
    <>
      <ul className="listing-list">
        {data.content.map((listing) => (
          <li key={listing.listingId}>
            <ListingCard listing={listing} actions={renderActions?.(listing.listingId)} />
          </li>
        ))}
      </ul>

      {onPageChange && <Pagination page={data} onChange={onPageChange} />}
    </>
  )
}
