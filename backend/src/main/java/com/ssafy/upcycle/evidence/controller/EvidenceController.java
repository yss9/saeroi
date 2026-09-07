package com.ssafy.upcycle.evidence.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.service.EvidenceUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 증빙 업로드·조회. <b>인증이 필요하다.</b>
 *
 * <p>경로에 {@code /v1} 이 없다 — 소재 계열과 같은 관례를 따른다
 * ({@code MaterialController} 주석 참고). 증빙은 소재·가공·생산에 딸리는 자료라
 * 그쪽 표면과 갈라 두면 프론트가 두 관례를 동시에 다뤄야 한다.
 *
 * <p>{@code SecurityConfig} 는 고치지 않았다. permitAll 목록에 없으므로
 * {@code anyRequest().authenticated()} 로 떨어져 <b>아무것도 하지 않아도 토큰이 필요하다.</b>
 */
@Validated
@RestController
@RequestMapping("/api/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceUploadService evidenceUploadService;

    /**
     * 증빙 파일을 올린다. 한 요청에 최대 10개, 파일당 10MB.
     *
     * <p><b>{@code multipart/form-data} 로만 받는다.</b> 메타데이터를 JSON 본문으로 받고
     * 파일을 따로 올리는 방식이면, 파일은 올라갔는데 메타데이터가 실패한 상태가 생기고
     * 그 파일은 아무도 가리키지 않는 고아가 된다.
     *
     * <p>업로더는 JWT {@code sub} 에서 얻는다 — 요청으로 받으면 남의 이름으로 증빙을
     * 올릴 수 있다. 증빙은 온체인에 해시가 남는 되돌릴 수 없는 기록이다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<EvidenceFileResponse>> upload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute EvidenceUploadRequest request,
            @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.success(
                evidenceUploadService.upload(userId(jwt), request, files));
    }

    /**
     * 한 대상의 증빙 목록.
     *
     * <p>대상이 없으면 <b>404 가 아니라 빈 목록</b>이다. 증빙이 아직 없는 것과 대상이
     * 없는 것은 다른 상황이지만, 이 엔드포인트는 목록을 묻는 것이라 빈 목록이 정확한 답이다.
     * 대상 존재는 업로드할 때 확인한다.
     */
    @GetMapping
    public ApiResponse<List<EvidenceFileResponse>> findAll(
            @RequestParam EvidenceReferenceType referenceType,
            @RequestParam @Positive(message = "증빙 대상 id는 양수여야 합니다.") Long referenceId) {
        return ApiResponse.success(
                evidenceUploadService.findAll(referenceType, referenceId));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
