// Contract 인스턴스를 만드는 유일한 지점.
//
// Component 에서 `new ethers.Contract(...)` 를 직접 쓰지 않는다. 그렇게 하면
// 주소·ABI·signer 획득이 화면 코드에 흩어지고, 컨트랙트 재배포 때 전부 찾아 고쳐야 한다.
//
// 호출 경로: Page → Feature Hook → Contract Service → 여기 → walletService → ethers → MetaMask

import { Contract, type InterfaceAbi } from 'ethers'
import { abis, contracts, type ContractName } from '../../config/contracts.ts'
import { getBrowserProvider, getSigner } from '../../features/wallet/services/walletService.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'

/**
 * 읽기 전용 Contract. 서명이 필요 없는 view 호출에 쓴다.
 * 지갑이 연결되어 있지 않아도 provider 만 있으면 동작한다.
 */
export async function getReadContract(name: ContractName): Promise<Contract> {
  const provider = getBrowserProvider()
  if (!provider) throw createAppError(ERROR_KIND.WALLET_NOT_FOUND)
  return new Contract(requireAddress(name), requireAbi(name), provider)
}

/**
 * 쓰기용 Contract. signer 가 붙어 있어 트랜잭션을 보낼 수 있다.
 * 호출 시 MetaMask 서명 팝업이 뜬다.
 */
export async function getWriteContract(name: ContractName): Promise<Contract> {
  const signer = await getSigner()
  return new Contract(requireAddress(name), requireAbi(name), signer)
}

function requireAddress(name: ContractName): string {
  const address = contracts[name]
  if (!address) {
    // 설정 누락은 사용자가 해결할 수 없는 문제다. 배포 담당이 볼 수 있게 이름을 남긴다.
    throw createAppError(ERROR_KIND.UNKNOWN, {
      message: `${name} 컨트랙트 주소가 설정되지 않았습니다.`,
    })
  }
  return address
}

function requireAbi(name: ContractName): InterfaceAbi {
  const abi = abis[name]
  if (abi.length === 0) {
    throw createAppError(ERROR_KIND.UNKNOWN, {
      message: `${name} 컨트랙트 ABI가 아직 연결되지 않았습니다.`,
    })
  }
  return abi as InterfaceAbi
}
