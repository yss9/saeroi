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
     *
     * <p><b>{@code submitPlanAndLock} 의 인자가 이 목록 순서 그대로다.</b> 컨트랙트가 배열
     * 인덱스로 짝을 맞추므로, 화면이 서로 다른 응답을 조합해 배열을 만들면 순서가 어긋날
     * 여지가 생긴다. 한 응답에 함께 담아 그 여지를 없앤다.
     *
     * <p>{@code scrapEvidenceHash} 는 자투리가 있을 때만 값이 있고 없으면 32바이트 0 이다 —
     * 컨트랙트가 {@code scrap > 0} 인 경우에만 0 이 아닌 값을 요구한다. 자투리 해시는
     * 배치 코드·부모 소재 코드·수량에서 파생하므로 <b>언제 다시 계산해도 같은 값</b>이다.
     *
     * <p>{@code scrapMetadataUri} 는 지금 빈 문자열이다. 자투리 메타데이터를 올릴 저장소
     * 경로가 아직 없고, 컨트랙트도 URI 는 검사하지 않는다. 경로가 생기면 이 자리에 채운다.
     */
    public record ManifestInput(
            String materialCode,
            Long materialTokenId,
            long inputQuantity,
            long productUsedQuantity,
            long reusableScrapQuantity,
            long wasteQuantity,
            String scrapMetadataUri,
            String scrapEvidenceHash) {
    }
}
