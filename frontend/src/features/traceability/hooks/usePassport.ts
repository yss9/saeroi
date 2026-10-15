import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getPassport } from '../api/getPassport.ts'
import { traceabilityKeys } from '../api/traceabilityKeys.ts'
import type { ProductPassport } from '../types/traceability.types.ts'

/** QR Passport. qrToken 이 없으면(주소가 잘못됨) 요청하지 않는다. */
export function usePassport(qrToken: string | null) {
  return useQuery<ProductPassport, AppError>({
    queryKey: traceabilityKeys.detail(qrToken ?? ''),
    queryFn: () => getPassport(qrToken as string),
    enabled: qrToken !== null && qrToken !== '',
    // 로그인 여부와 무관한 공개 데이터이고 QR 스캔 한 번에 한 번만 보면 되는 화면이라
    // 재시도로 서버를 다시 두드릴 이유가 적다. 실패하면 ErrorMessage 의 "다시 시도" 로 넘긴다.
    retry: false,
  })
}
