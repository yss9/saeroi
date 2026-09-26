package com.ssafy.upcycle.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 생산 배치의 온체인 기록 요청.
 *
 * <p>공용 {@code ChainLinkRequest} 를 쓰지 않는다. 그쪽은 {@code onChainId} 가 필수인데,
 * 소재·판매글·주문은 체인이 번호를 채번해 주고 <b>생산 배치는 그럴 자리가 없다</b> —
 * {@code production_batch} 에 온체인 번호를 담을 컬럼이 없다. 필수 필드에 아무 값이나 넣어
 * 통과시키면 그 숫자가 무엇인지 아무도 모르게 된다.
 *
 * <p>대신 <b>배치가 이미 갖고 있는 두 칸</b>을 채운다. {@code evidence_hash} 와
 * {@code manifest_url} 이며, 그것이 "이 제품이 이 소재로 만들어졌다" 를 체인에서 대조할 수
 * 있게 하는 값이다.
 *
 * <p><b>체인 ID 와 컨트랙트 주소는 받지 않는다.</b> 서버가 설정으로 들고 있다 — 화면이
 * 보내면 자기가 배포한 컨트랙트를 가리키는 기록을 만들 수 있다.
 *
 * @param txHash 확정된 트랜잭션 해시. {@code 0x} + 64 hex
 * @param blockNumber 채굴된 블록. 없어도 기록은 성립하므로 선택이다
 * @param evidenceHash 온체인에 올린 manifest 의 해시. {@code 0x} + 64 hex
 * @param manifestUrl manifest 원본 주소. 해시만으로는 무엇을 해시했는지 볼 수 없다
 * @param lockedMaterials 이 트랜잭션이 만든 온체인 잠금들. 영수증에서만 나오는 값이라
 *     여기서 받아 두지 않으면 3단계까지 화면이 붙들고 있어야 한다
 */
public record ProductionChainLinkRequest(

        @NotBlank(message = "트랜잭션 해시는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "트랜잭션 해시 형식이 올바르지 않습니다.")
        String txHash,

        Long blockNumber,

        @NotBlank(message = "증빙 해시는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "증빙 해시 형식이 올바르지 않습니다.")
        String evidenceHash,

        @Size(max = 500, message = "manifest 주소는 500자를 넘을 수 없습니다.")
        String manifestUrl,

        @NotEmpty(message = "잠긴 소재를 하나 이상 지정해야 합니다.")
        @Valid
        List<LockedMaterial> lockedMaterials) {

    /**
     * 투입 하나와 {@code submitPlanAndLock} 이 만든 온체인 잠금 번호.
     *
     * <p><b>여기서 받는 이유.</b> 잠금 번호는 이 트랜잭션의 영수증에서만 나온다
     * ({@code MaterialLockCreated} 이벤트). 화면이 들고 있다가 3단계 보고에 넣게 하면
     * 그 사이에 새로고침 한 번으로 잃어버린다 — {@code chainBatchId} 를 서버가 보관하기로
     * 한 것과 정확히 같은 문제다.
     *
     * @param productionInputId DB 투입 id. 어느 잠금인지는 서버가 그 행에서 꺼낸다 —
     *     잠금 id 를 직접 받으면 남의 잠금을 이 배치의 것으로 보고할 수 있다
     * @param chainLockId 온체인 {@code MaterialLock} 번호
     */
    public record LockedMaterial(
            @NotNull(message = "투입 id는 필수입니다.")
            @Positive(message = "투입 id는 양수여야 합니다.")
            Long productionInputId,

            @NotNull(message = "온체인 잠금 번호는 필수입니다.")
            @Positive(message = "온체인 잠금 번호는 양수여야 합니다.")
            Long chainLockId) {
    }
}
