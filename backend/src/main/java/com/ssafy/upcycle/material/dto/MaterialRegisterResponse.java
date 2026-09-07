package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.Material;

/**
 * 등록 결과. 상세 조회로 이어갈 {@code materialId} 와 사람이 부를 {@code materialCode} 만 준다.
 *
 * <p>전체 필드를 여기서 내려주지 않는 것은 의도적이다. 등록 직후 화면이 필요한 것은
 * "무엇이 만들어졌는가" 이고, 나머지는 상세 조회({@code GET /api/materials/{materialId}})가
 * 한 곳에서 책임진다. 두 곳에서 같은 표현을 만들면 필드가 갈라진다.
 */
public record MaterialRegisterResponse(Long materialId, String materialCode) {

    public static MaterialRegisterResponse from(Material material) {
        return new MaterialRegisterResponse(material.getId(), material.getMaterialCode());
    }
}
