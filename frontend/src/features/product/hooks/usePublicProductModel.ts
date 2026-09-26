import { useQuery } from '@tanstack/react-query'
import { getPublicProductModel } from '../api/getPublicProductModel.ts'
import { publicProductKeys } from '../api/publicProductKeys.ts'
import { isAppError } from '../../../utils/errors.ts'

export function usePublicProductModel(id: number | null) {
  return useQuery({
    queryKey: [...publicProductKeys.all, 'detail', id],
    queryFn: () => getPublicProductModel(id!),
    enabled: id !== null,
    retry: (count, error) => !(isAppError(error) && error.status === 404) && count < 1,
  })
}
