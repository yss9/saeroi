package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소재 상세. 티켓이 요구한 6블록 — 기본정보 · 수량 · Owner · Evidence · Verification · 상태.
 *
 * <h2>{@code verificationState} 를 믿지 말 것</h2>
 *
 * <p><b>이 값은 항상 {@link MaterialVerificationState#REGISTERED} 다.</b> 소재의 사실이 아니라
 * 현재 구현의 한계다.
 *
 * <p>{@link MaterialVerificationState} enum 은 5단계를 갖고 있지만 {@code material} 테이블에
 * 그걸 담을 컬럼이 없고, 파생 판정의 근거로 설계된 {@code signed_at}·{@code signer_wallet} 도
 * {@code schema.sql} 에 없다. 진짜 판정에는 온체인
 * {@code MaterialToken.verificationRecord(tokenId, SOURCE_VERIFIED)} 조회나
 * {@code blockchain_transaction} 의 {@code verifySource} 성공 Tx 검색이 필요하고,
 * 둘 다 이 티켓 범위 밖이다.
 *
 * <p>그래서 <b>이 필드로 검증 배지를 그리면 화면이 조용히 거짓말을 한다.</b>
 * 판정 근거가 확보되면 이 주석과 함께 사라질 제약이다.
 *
 * <h2>null 이 정상인 필드들</h2>
 *
 * <p>{@code tokenId}·{@code chainId}·{@code contractAddress} 는 mint 결과이고, 그 결과를
 * 기록하는 confirm 엔드포인트가 아직 없다. <b>null 이 "등록됐지만 온체인에 없음" 의 유일한
 * 신호</b>이므로 별도 boolean 을 만들지 않고 그대로 내려준다.
 *
 * <p>{@code evidenceHash}·{@code manifestUrl} 도 증빙 등록 경로가 없어 당분간 null 이다.
 * {@code evidence_file} 테이블의 파일 목록은 그 엔티티가 만들어진 뒤 이 응답에 덧붙는다.
 *
 * <p>{@code owners} 는 온체인 잔액 Projection 이 채워지기 전까지 빈 목록이다.
 * 각 항목의 {@code syncedAt} 이 "언제 기준 값인가" 를 말해 준다.
 */
public record MaterialDetailResponse(
        // 기본정보
        Long materialId,
        String materialCode,
        MaterialType materialType,
        MaterialRootOrigin rootOrigin,
        MaterialGenerationType generationType,
        String name,
        String description,
        String grade,
        Long lengthMm,
        Long widthMm,
        Long registrantId,
        LocalDateTime createdAt,

        // 수량 (gram 정수)
        long totalQuantity,

        // Owner
        List<MaterialOwnerResponse> owners,

        // Evidence
        String evidenceHash,
        String manifestUrl,

        // Verification — 위 주석 참고. 항상 REGISTERED 다
        MaterialVerificationState verificationState,

        // 상태
        MaterialStatus status,

        // 온체인 매핑. confirm 전까지 전부 null
        Long tokenId,
        Long chainId,
        String contractAddress) {

    public static MaterialDetailResponse of(Material material, List<MaterialBalance> balances) {
        return new MaterialDetailResponse(
                material.getId(),
                material.getMaterialCode(),
                material.getMaterialType(),
                material.getRootOrigin(),
                material.getGenerationType(),
                material.getName(),
                material.getDescription(),
                material.getGrade(),
                material.getLengthMm(),
                material.getWidthMm(),
                material.getRegistrantId(),
                material.getCreatedAt(),
                material.getTotalQuantity(),
                balances.stream().map(MaterialOwnerResponse::from).toList(),
                material.getEvidenceHash(),
                material.getManifestUrl(),
                MaterialVerificationState.REGISTERED,
                material.getStatus(),
                material.getTokenId(),
                material.getChainId(),
                material.getContractAddress());
    }
}
