package com.ssafy.upcycle.processing.dto;

/**
 * 브랜드의 가공 결과 승인(반환 수령).
 *
 * @param txHash 승인 서명 거래. <b>비워 보내면 서버가 체인에서 찾는다</b> — 서명은 체인에 들어갔는데
 *     서버 호출이 실패해 화면이 해시를 잃은 경우다. 승인은 새 소재를 발행하는 자리라 그 번호가
 *     영수증에만 있고, 다시 서명하면 체인이 이미 승인된 작업이라 되돌린다. 어느 쪽이든 서버는
 *     영수증을 검증한다
 */
public record ProcessingReturnReceiveRequest(String txHash) {}
