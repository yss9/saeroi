package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductImageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 상품 갤러리 교체 요청. <b>보낸 목록이 곧 갤러리 전체다.</b>
 *
 * <p>추가·삭제·순서변경을 각각 열지 않은 이유가 순서다. 갤러리는 자리가 곧 의미라 세 가지
 * 요청이 섞이면 <b>중간 상태에서 자리가 겹치거나 비는</b> 구간이 생기고, 그것을 서버가
 * 되돌려 놔야 한다. 화면도 "지금 이 순서" 를 들고 있으므로 통째로 보내는 편이 맞다.
 *
 * <p>순서는 <b>배열의 순서</b>다. 사진마다 번호를 받으면 같은 번호가 둘이거나 중간이 비는
 * 요청이 만들어지는데, 배열은 그런 상태를 표현할 수 없다.
 *
 * <p>빈 목록을 보내면 갤러리가 비워진다. 교체 요청이므로 "아무것도 없는 상태로 교체" 는
 * 유효한 뜻이다 — 상세 화면은 그때 {@code thumbnailUrl} 한 장으로 돌아간다.
 *
 * <p><b>장수 상한은 여기서 보지 않는다.</b> 몇 장까지 걸 수 있는지는 형식이 아니라 갤러리의
 * 규칙이라 {@code PRODUCT_IMAGE_TOO_MANY} 로 답한다 — 중복 저장 키나 대표 사진 중복과 같은
 * 자리에서 같은 모양의 오류로 나가야 화면이 한 곳만 보면 된다.
 */
public record ProductModelImagesReplaceRequest(

        @NotNull(message = "이미지 목록은 필수입니다.")
        @Valid
        List<Image> images) {

    /** 갤러리에 들어갈 사진 한 장. */
    public record Image(

            @NotBlank(message = "이미지 저장 키는 필수입니다.")
            @Size(max = 500, message = "이미지 저장 키는 500자를 넘을 수 없습니다.")
            String storageKey,

            @NotNull(message = "이미지 종류는 필수입니다.")
            ProductImageType type) {
    }
}
