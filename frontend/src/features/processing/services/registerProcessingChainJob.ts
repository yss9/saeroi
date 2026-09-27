import { processingJobApi } from '../api/processingJobApi.ts'
import {
  createProcessingJob,
  getProcessingBrandLockId,
  readProcessingEventValues,
} from './processingContractService.ts'
import {
  createOrResumeProcessingChainJob,
  forgetProcessingChainCreate,
} from './processingChainJobCreation.ts'
import { assertCanRegisterProcessingJob } from './processingRegistrationPreflight.ts'

/**
 * 가공 작업을 <b>블록체인에 등록한다.</b> 인계보다 앞선 단계다.
 *
 * 등록하면 투입 소재가 온체인에 잠기고 작업이 REQUESTED 가 된다. 그다음은 업체 차례다 —
 * 업체가 체인에서 수락해야 인계 서명을 할 수 있다.
 *
 * 서명에 필요한 값을 모델에게서 받지 않고 서버에서 다시 읽는다(`chainPreparation`). 모델이
 * 준 것은 jobId 뿐이고, 토큰·수량·업체 지갑은 조회해 얻는다 — 숫자를 지어내도 엉뚱한 작업이
 * 체인에 올라가지 않는다.
 */
export async function registerProcessingChainJob(jobId: number): Promise<string> {
  const preparation = await processingJobApi.chainPreparation(jobId)
  if (preparation.chainJobId != null) {
    forgetProcessingChainCreate(jobId)
    throw Object.assign(new Error('이 작업은 이미 블록체인에 등록돼 있습니다.'), {
      code: 'CHAIN_ALREADY_DONE',
    })
  }

  // **앞서 보낸 등록이 있으면 새로 만들지 않고 그 거래에서 이어 간다.** 서명이 체인에 들어간
  // 뒤 서버 연결(linkChain)이 실패하는 창이 있다(네트워크 끊김, 로그인 만료). 그때 다시 누르면
  // 서버는 아직 체인 작업을 모르니 한 번 더 만들게 되고, 앞의 작업은 소재를 잠근 채 버려진다.
  //
  // **새로 만들 때만 사전 확인을 한다.** 되돌려질 것이 확실하면(다른 지갑, 권한 없음, 체인 잔량
  // 부족) 서명 창을 열지 않고 할 일을 알린다. 이어받는 거래는 이미 소재를 잠갔으므로 여기서
  // 잔량을 보면 모자라다고 잘못 막는다 — 그래서 send 안에서만 부른다.
  const receipt = await createOrResumeProcessingChainJob(jobId, async () => {
    await assertCanRegisterProcessingJob(preparation)
    return createProcessingJob(
      preparation.processorWallet,
      preparation.inputTokenId,
      preparation.inputQuantity,
      preparation.processTypes,
    )
  })
  const values = readProcessingEventValues(receipt, 'ProcessingJobCreated', 'jobId')
  if (values.length !== 1) throw new Error('온체인 작업 번호를 확인하지 못했습니다.')
  const onChainId = Number(values[0])

  const chainLockId = await getProcessingBrandLockId(onChainId)
  await processingJobApi.linkChain(jobId, {
    onChainId,
    chainLockId,
    txHash: receipt.hash,
    blockNumber: receipt.blockNumber,
  })
  forgetProcessingChainCreate(jobId)
  return receipt.hash
}
