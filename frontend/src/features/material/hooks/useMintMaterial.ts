import { useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useBlockchainTransaction } from '../../../hooks/useBlockchainTransaction.ts'
import { readEventArg } from '../../../services/blockchain/contractFactory.ts'
import { materialKeys } from '../api/materialKeys.ts'
import { mintMaterial, type MintMaterialParams } from '../services/materialContractService.ts'

/**
 * Material Token 발행 — 공통 Blockchain 모듈을 재사용하는 표준 예시.
 *
 * 이 훅이 보여주려는 것
 *   1) 트랜잭션 상태 기계는 공통 훅(useBlockchainTransaction)이 관리한다.
 *      SIGNING / PENDING / CONFIRMED / FAILED 를 도메인마다 다시 짜지 않는다.
 *   2) 도메인은 "무엇을 보낼지"(contractService 호출)만 넘긴다.
 *      Provider·Signer·Contract 생성은 전부 공통 모듈 안에서 끝난다.
 *   3) 새로 채번된 tokenId 는 **이벤트에서** 꺼낸다.
 *      `mintMaterial` 이 tokenId 를 반환하도록 선언돼 있어도, 트랜잭션으로 부르면
 *      그 반환값은 받을 수 없다. 영수증에 남는 건 로그뿐이다.
 *   4) 서버 조회 무효화는 CONFIRMED 이후에만 한다.
 *      Backend 가 이벤트를 수집해 DB 를 갱신하므로, 확정 전에 조회하면 옛 값이 온다.
 */
export function useMintMaterial(materialId: number) {
  const queryClient = useQueryClient()

  const transaction = useBlockchainTransaction({
    onConfirmed: (receipt) => {
      // 발행된 tokenId. 서버에 알려 오프체인 레코드와 연결할 때 쓴다.
      const tokenId = readEventArg<bigint>(receipt, 'materialToken', 'MaterialMinted', 'tokenId')

      // TODO(2번/Material 담당): tokenId 를 Backend 에 전달하는 API 가 정해지면 연결한다.
      //   POST /materials/{materialId}/confirm  ← API 목록에 이미 있는 "서명 결과 기록"
      void tokenId

      void queryClient.invalidateQueries({ queryKey: materialKeys.detail(materialId) })
      // 지금은 이 키를 읽는 쿼리가 없다 — 목 데이터로 돌던 소재 목록 화면을 걷어내면서
      // useMaterials 가 함께 사라졌다. 무효화는 no-op 이지만 지우지 않는다. Backend 에
      // `GET /api/materials` 가 생겨 목록이 부활할 때 다시 넣어야 하고, 그때 빠뜨리면
      // 소재를 등록해도 목록이 갱신되지 않는 조용한 버그가 된다.
      void queryClient.invalidateQueries({ queryKey: materialKeys.lists() })
    },
  })

  const mint = useCallback(
    (params: MintMaterialParams) => transaction.execute(() => mintMaterial(params)),
    [transaction],
  )

  return { ...transaction, mint }
}
