// 공통 Blockchain Client — Contract 인스턴스를 만드는 유일한 지점.
//
// Component 에서 `new ethers.Contract(...)` 를 직접 쓰지 않는다. 그렇게 하면
// 주소·ABI·signer 획득이 화면 코드에 흩어지고, 컨트랙트 재배포 때 전부 찾아 고쳐야 한다.
//
// 호출 경로
//   Page → Feature Hook → Contract Service → 여기 → walletService → ethers → MetaMask
//
// ⚠️ Contract 인스턴스를 캐시하지 않는다.
// Contract 는 생성 시점의 signer(=그때의 계정)를 붙들고 있다. 사용자가 MetaMask 에서
// 계정을 바꿔도 캐시된 인스턴스는 이전 계정으로 서명한다. 매번 새로 만든다.
// (같은 이유로 Provider·Signer 도 스토어에 저장하지 않는다 — walletService.ts 참고)

import { Contract, Interface, type ContractRunner } from 'ethers'
import { abis, contracts, type ContractName } from '../../config/contracts.ts'
import { env } from '../../config/env.ts'
import { getBrowserProvider, getSigner } from '../../features/wallet/services/walletService.ts'
import { useAuthStore } from '../../stores/authStore.ts'
import { createAppError, ERROR_KIND } from '../../utils/errors.ts'

/**
 * 읽기 전용 Contract. 서명이 필요 없는 view 호출에 쓴다.
 *
 * 지갑이 설치되어 있으면 연결(계정 승인) 전에도 동작한다 — MetaMask 가 노출하는
 * RPC 를 그대로 쓰기 때문이다. 그래서 잔량 조회 같은 건 팝업 없이 가능하다.
 */
export function getReadContract(name: ContractName): Contract {
  const provider = getBrowserProvider()
  if (!provider) throw createAppError(ERROR_KIND.WALLET_NOT_FOUND)
  return createContract(name, provider)
}

/**
 * 쓰기용 Contract. signer 가 붙어 있어 트랜잭션을 보낼 수 있다.
 *
 * 계정이 연결되어 있지 않으면 ethers 가 `eth_requestAccounts` 를 호출해 팝업을 띄운다.
 * 즉 렌더링 중이 아니라 **이벤트 핸들러 안에서** 불러야 한다.
 */
export async function getWriteContract(
  name: ContractName,
  options: { enforceRegisteredWallet?: boolean } = {},
): Promise<Contract> {
  const provider = getBrowserProvider()
  if (!provider) throw createAppError(ERROR_KIND.WALLET_NOT_FOUND)

  // 쓰기 호출은 반드시 배포된 SSAFY 체인에서만 보낸다. 헤더의 네트워크 경고만 믿으면
  // 사용자가 경고를 보지 못한 채 다른 체인에 같은 주소로 서명할 수 있다.
  if (!env.besuChainIdHex) {
    throw createAppError(ERROR_KIND.WRONG_NETWORK, {
      message: 'SSAFY 네트워크 Chain ID가 설정되지 않았습니다.',
    })
  }
  const currentChainId = `0x${(await provider.getNetwork()).chainId.toString(16)}`
  if (currentChainId !== env.besuChainIdHex) {
    throw createAppError(ERROR_KIND.WRONG_NETWORK, {
      message: `SSAFY 네트워크(${env.besuChainIdHex})로 전환해 주세요.`,
    })
  }

  const signer = await getSigner()

  // **서명하려는 계정이 이 사용자의 등록 지갑인가.**
  //
  // MetaMask 는 계정을 여러 개 들고 있고, 사용자는 아무 때나 바꿀 수 있다. 화면은
  // 전환을 따라가지만(walletClient 가 accountsChanged 를 구독한다) "지금 연결된 계정"
  // 과 "이 계정으로 서명해야 하는가" 는 다른 질문이다.
  //
  // 어긋난 채로 서명하면 체인에는 다른 주소가 남는다. 서버는 거래 시점의 지갑 주소를
  // 스냅샷으로 들고 있으므로 두 기록이 서로 다른 사람을 가리키게 되고, 그 뒤의
  // markShipped·confirmReceipt 는 컨트랙트가 order.seller/buyer 를 대조하다 되돌린다
  // ("caller is not seller"). 소재 발행이라면 더 나쁘다 — 토큰이 등록 지갑이 아닌
  // 곳으로 발행되고 되돌릴 수 없다.
  //
  // 여기서 막는 이유는 체인 검사와 같다. 패널마다 두면 새 패널이 생길 때 빠진다.
  const registered = useAuthStore.getState().user?.walletAddress
  if (options.enforceRegisteredWallet !== false && registered) {
    const signing = await signer.getAddress()
    if (signing.toLowerCase() !== registered.toLowerCase()) {
      throw createAppError(ERROR_KIND.WALLET_DISCONNECTED, {
        message:
          `MetaMask에서 선택된 계정(${shortAddress(signing)})이 이 계정에 등록된 지갑` +
          `(${shortAddress(registered)})과 다릅니다. 등록된 지갑으로 전환한 뒤 다시 시도해 주세요.`,
      })
    }
  }

  return createContract(name, signer)
}

function shortAddress(value: string): string {
  return `${value.slice(0, 8)}…${value.slice(-6)}`
}

function createContract(name: ContractName, runner: ContractRunner): Contract {
  const address = contracts[name]
  if (!address) {
    // 설정 누락은 사용자가 해결할 수 없는 문제다. 배포 담당이 볼 수 있게 키 이름을 남긴다.
    throw createAppError(ERROR_KIND.UNKNOWN, {
      message: `${name} 컨트랙트 주소가 설정되지 않았습니다. (VITE_*_CONTRACT_ADDRESS)`,
    })
  }
  return new Contract(address, abis[name], runner)
}

/**
 * 트랜잭션 영수증에서 이벤트 인자를 꺼낸다.
 *
 * 왜 필요한가 — `mintMaterial` 은 `tokenId` 를 반환하지만 **그 반환값은 트랜잭션으로
 * 부를 때 읽을 수 없다.** 반환값은 eth_call(시뮬레이션)에서만 나오고, 실제 전송에서는
 * 영수증에 로그만 남는다. 그래서 새로 생긴 id 는 이벤트에서 꺼내야 한다.
 *
 * 사용:
 *   const tokenId = readEventArg<bigint>(receipt, 'materialToken', 'MaterialMinted', 'tokenId')
 *
 * 해당 이벤트가 없으면 null (다른 컨트랙트의 로그가 섞여 있어도 조용히 건너뛴다).
 */
export function readEventArg<T>(
  receipt: { logs: readonly { topics: readonly string[]; data: string }[] },
  name: ContractName,
  eventName: string,
  argName: string,
): T | null {
  // Interface 는 ABI 만으로 만들 수 있다. 로그 해석에는 지갑도 주소도 필요 없으므로
  // provider 가 없는 상황(지갑 미설치)에서도 동작해야 한다.
  const iface = new Interface(abis[name])

  for (const log of receipt.logs) {
    let parsed
    try {
      parsed = iface.parseLog({ topics: [...log.topics], data: log.data })
    } catch {
      // 이 컨트랙트의 로그가 아니다. 한 트랜잭션에 여러 컨트랙트 로그가 섞이는 건 정상이다.
      continue
    }
    if (parsed?.name === eventName) return parsed.args[argName] as T
  }
  return null
}

/**
 * 같은 이벤트가 **여러 번** 실린 영수증에서 전부 꺼낸다.
 *
 * `readEventArg` 는 첫 번째 하나만 준다. 한 트랜잭션이 투입 수만큼 잠금을 만드는
 * `submitPlanAndLock` 처럼, 이벤트가 목록으로 나오는 경우에는 그것으로 부족하다 —
 * 어느 소재가 어느 잠금 번호를 받았는지 짝을 지으려면 전부 필요하다.
 *
 * 사용:
 *   const locks = readEvents(receipt, 'materialToken', 'MaterialLockCreated')
 *   locks[0].lockId / locks[0].tokenId
 */
export function readEvents(
  receipt: { logs: readonly { topics: readonly string[]; data: string }[] },
  name: ContractName,
  eventName: string,
): Record<string, unknown>[] {
  const iface = new Interface(abis[name])
  const found: Record<string, unknown>[] = []

  for (const log of receipt.logs) {
    let parsed
    try {
      parsed = iface.parseLog({ topics: [...log.topics], data: log.data })
    } catch {
      // 다른 컨트랙트의 로그다. 한 트랜잭션에 여러 컨트랙트 로그가 섞이는 건 정상이다.
      continue
    }
    if (parsed?.name !== eventName) continue

    // ethers 의 Result 는 인덱스와 이름을 함께 갖는 배열이다. 이름으로만 쓰도록 좁힌다.
    const args: Record<string, unknown> = {}
    for (const [index, input] of parsed.fragment.inputs.entries()) {
      args[input.name] = parsed.args[index]
    }
    found.push(args)
  }

  return found
}
