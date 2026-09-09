import { useQuery } from '@tanstack/react-query'
import { getMyWallets } from '../api/walletRegistrationApi.ts'
import { walletKeys } from '../api/walletKeys.ts'

export function useMyWallets(enabled = true) {
  const query = useQuery({
    queryKey: walletKeys.lists(),
    queryFn: getMyWallets,
    enabled,
  })

  const activeWallet = query.data?.find((wallet) => wallet.active) ?? null

  return { ...query, activeWallet }
}
