package com.ssafy.upcycle.production.dto;

import java.util.List;

/**
 * 온체인에 올릴 생산 manifest 와 그 해시.
 *
 * <h2>왜 서버가 만드는가</h2>
 *
 * <p>화면이 만들면 <b>같은 배치에서 브라우저마다 다른 해시</b>가 나올 수 있다 — 필드 순서,
 * 공백, 숫자 표기, 문자 인코딩이 조금만 달라도 해시는 완전히 달라진다. 그러면 나중에 그
 * 해시를 재현해 대조할 수 없고, 추적성의 마지막 한 칸이 그 대조에 걸려 있다.
 *
 * <p>내용 자체도 서버가 이미 전부 갖고 있다 — 투입 소재·수량·개체 목록·업사이클 비율은
 * 확정 시점에 DB 에 굳은 값이다. 화면이 그것을 다시 조립할 이유가 없다.
 *
 * <h2>직렬화 규칙</h2>
 *
 * <p>{@code evidenceHash} 는 <b>이 응답의 {@code manifest} 를 서버가 직렬화한 바이트</b>의
 * SHA-256 이다 ({@code 0x} + 64 hex). 규칙은 셋이다.
 *
 * <ol>
 *   <li>필드 순서는 이 레코드의 선언 순서. Jackson 이 그대로 쓴다</li>
 *   <li>공백 없음 (pretty print 하지 않는다)</li>
 *   <li>UTF-8</li>
 * </ol>
 *
 * <p><b>화면은 이 해시를 그대로 서명에 넣으면 된다.</b> 직접 계산하지 않는다 — 계산 주체가
 * 둘이면 언젠가 갈라지고, 갈라진 사실은 대조할 때만 드러난다.
 *
 * <p>해시가 SHA-256 인 것은 이 프로젝트의 기존 증빙 해시와 같은 방식이기 때문이다
 * ({@code evidence_file.file_hash}, {@code FileStorageService} 가 저장하며 계산하는 값).
 */
public record ProductionManifestResponse(Manifest manifest, String evidenceHash) {

    /**
     * 해시 대상이 되는 본문. <b>필드를 더하거나 순서를 바꾸면 같은 배치의 해시가 달라진다</b> —
     * 이미 체인에 올라간 배치를 검증할 수 없게 되므로, 바꿔야 한다면 버전 필드를 두고
     * 옛 방식도 계산할 수 있게 남겨야 한다.
     */
    public record Manifest(
            String batchCode,
            Long productModelId,
            int producedQuantity,
            String producerWallet,
            String upcycleRatio,
            long totalUsedQuantity,
            long virginUsedQuantity,
            long upcycledUsedQuantity,
            List<ManifestInput> inputs,
            List<String> itemCodes) {
    }

    /**
     * 투입 한 건. 소재를 {@code materialCode} 로 적는 이유는 DB {@code id} 가 이 서비스
     * 안에서만 의미 있는 순번이기 때문이다 — manifest 는 체인에 남아 나중에 다른 시스템이
     * 읽을 수도 있다.
     */
    public record ManifestInput(
            String materialCode,
            long inputQuantity,
            long productUsedQuantity,
            long reusableScrapQuantity,
            long wasteQuantity) {
    }
}
