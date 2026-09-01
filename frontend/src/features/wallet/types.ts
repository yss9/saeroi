// MetaMask(EIP-1193) 연동에 필요한 최소 타입 정의.
//
// ethers 의 타입을 그대로 쓰지 않고 직접 정의하는 이유:
// 테스트에서 가짜 provider 객체를 끼워 넣기 위해서다. 이 인터페이스만 만족하면
// 실제 확장 프로그램 없이도 연결/거부/계정변경 시나리오를 전부 재현할 수 있다.

/** EIP-1193 provider 중 우리가 실제로 쓰는 부분만 추린 것. `window.ethereum` 이 이 모양이다. */
export interface EthereumProvider {
  request(args: { method: string; params?: unknown[] }): Promise<unknown>
  on(event: string, handler: (...args: never[]) => void): void
  removeListener(event: string, handler: (...args: never[]) => void): void
  /** MetaMask 가 붙이는 비표준 플래그. 다른 지갑과 구분할 때만 참고용으로 쓴다. */
  isMetaMask?: boolean
}

/**
 * 지갑 연결 상태.
 *
 * TS `enum` 을 쓰지 않는 이유: tsconfig.app.json 에 `erasableSyntaxOnly: true` 가 켜져 있어
 * 런타임 코드를 만들어내는 enum 은 컴파일 에러가 된다. 문자열 유니온으로 대신한다.
 */
export type WalletStatus =
  /** 확장 프로그램이 설치되지 않음 (`window.ethereum` 부재) */
  | 'unavailable'
  /** 설치는 됐지만 이 사이트에 연결된 계정이 없음 */
  | 'disconnected'
  /** 연결 요청 진행 중 (MetaMask 팝업이 떠 있는 상태) */
  | 'connecting'
  /** 연결됨 */
  | 'connected'

/**
 * 사용자에게 보여줄 안내 문구의 종류.
 * 문자열을 직접 들고 다니지 않고 코드로 분류해, UI 문구 변경이 로직에 영향을 주지 않게 한다.
 */
export type WalletNotice =
  /** 사용자가 MetaMask 팝업에서 "취소" 를 눌렀다 (에러가 아니라 정상 흐름) */
  | 'rejected'
  /** 이미 팝업이 떠 있는데 또 요청했다 */
  | 'pending-in-wallet'
  /** 위 둘로 분류되지 않은 실패 */
  | 'failed'

export interface WalletState {
  status: WalletStatus
  /** 체크섬(EIP-55) 정규화된 주소. 연결 상태가 아니면 null */
  address: string | null
  /** 16진수 문자열 그대로 (예: '0x1'). 아직 조회 전이면 null */
  chainId: string | null
  notice: WalletNotice | null
}

export const INITIAL_WALLET_STATE: WalletState = {
  status: 'unavailable',
  address: null,
  chainId: null,
  notice: null,
}

/** EIP-1193 표준 에러 코드 중 실제로 분기가 필요한 것들. */
export const RPC_ERROR = {
  /** 사용자가 요청을 거부함 */
  USER_REJECTED: 4001,
  /** 요청이 이미 처리 중 (MetaMask 팝업이 열려 있음) */
  ALREADY_PENDING: -32002,
  /** 해당 체인이 지갑에 등록되어 있지 않음 — wallet_addEthereumChain 으로 폴백해야 한다 */
  UNRECOGNIZED_CHAIN: 4902,
} as const
