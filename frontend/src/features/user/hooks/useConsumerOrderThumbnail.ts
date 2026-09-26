import { useQuery } from '@tanstack/react-query'
import { useObjectUrl } from '../../../hooks/useObjectUrl.ts'
import type { AppError } from '../../../utils/errors.ts'
import { consumerOrderKeys } from '../api/consumerOrderKeys.ts'
import { getConsumerOrderThumbnail } from '../api/getConsumerOrderThumbnail.ts'

/** 인증이 필요한 주문상품 이미지를 `<img>`에서 사용할 Blob URL로 변환한다. */
export function useConsumerOrderThumbnail(imageUrl: string) {
  const query = useQuery<Blob, AppError>({
    queryKey: [...consumerOrderKeys.details(), 'thumbnail', imageUrl],
    queryFn: () => getConsumerOrderThumbnail(imageUrl),
    retry: false,
  })
  const url = useObjectUrl(query.data)

  return { url, isPending: query.isPending, isError: query.isError }
}
