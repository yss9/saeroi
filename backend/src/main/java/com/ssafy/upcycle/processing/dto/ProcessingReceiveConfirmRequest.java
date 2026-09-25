package com.ssafy.upcycle.processing.dto;

/**
 * 가공업체의 수령 확인.
 *
 * @param txHash 수령 서명 거래. <b>비워 보내면 서버가 체인에서 찾는다</b> — 서명은 체인에 들어갔는데
 *     서버 호출이 실패해 화면이 해시를 잃은 경우다. 다시 서명하면 체인이 이미 수령된 작업이라
 *     되돌리므로, 해시 없이 다시 보내 기록을 따라가게 한다. 어느 쪽이든 서버는 영수증을 검증한다
 */
public record ProcessingReceiveConfirmRequest(String txHash) {}
