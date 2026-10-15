import { useQuery } from '@tanstack/react-query'
import { getMyWallets } from '../api/walletRegistrationApi.ts'
import { walletKeys } from '../api/walletKeys.ts'

export function useMyWallets(enabled = true) {
  const query = useQuery({
    queryKey: walletKeys.lists(),
    queryFn: getMyWallets,
    enabled,
  })

  // 공용 레이아웃은 여러 화면 테스트에서도 함께 렌더된다. 잘못된 응답이나 광범위한
  // API mock이 배열이 아닌 값을 돌려줘도 헤더 전체가 중단되지 않게 안전하게 다룬다.
  const wallets = Array.isArray(query.data) ? query.data : []
  const activeWallet = wallets.find((wallet) => wallet.active) ?? null

  return { ...query, activeWallet }
}
