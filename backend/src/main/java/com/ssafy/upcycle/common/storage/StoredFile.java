package com.ssafy.upcycle.common.storage;

/**
 * 저장 결과. 호출 도메인이 DB 에 그대로 적어 넣는 값이다.
 *
 * @param storageKey 저장소 안의 키. 삭제·조회에 쓰고 {@code evidence_file.file_url} 에 들어간다
 * @param originalFilename 사용자가 올린 이름. 표시용이며 경로에는 쓰이지 않는다
 * @param contentType 검증을 통과한 미디어 타입
 * @param size 실제로 기록된 바이트 수. 선언값이 아니라 쓰인 값이다
 * @param hash 기록된 바이트의 SHA-256. {@code 0x} + 64자 —
 *     {@code evidence_file.file_hash CHAR(66)} 과 온체인 비교 형식에 맞춘다
 */
public record StoredFile(
        String storageKey, String originalFilename, String contentType, long size, String hash) {
}
