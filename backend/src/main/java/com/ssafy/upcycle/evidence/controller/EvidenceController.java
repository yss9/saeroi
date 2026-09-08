package com.ssafy.upcycle.evidence.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.evidence.EvidenceApiPaths;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.service.EvidenceContentService;
import com.ssafy.upcycle.evidence.service.EvidenceDeletionService;
import com.ssafy.upcycle.evidence.service.EvidenceUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 증빙 업로드·조회·삭제. <b>인증이 필요하다.</b>
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
@RequestMapping(EvidenceApiPaths.BASE)
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceUploadService evidenceUploadService;
    private final EvidenceContentService evidenceContentService;
    private final EvidenceDeletionService evidenceDeletionService;

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

    /**
     * 증빙 파일 바이트. S15P21E104-162.
     *
     * <p><b>여기만 {@code ApiResponse} 봉투가 아니다.</b> 본문이 JPEG·PDF 바이트라 JSON 으로
     * 감쌀 수 없다 — base64 로 감싸면 크기가 33% 늘고 {@code <img src>} 로 쓸 수 없다.
     * 실패는 그대로 봉투로 나간다 ({@code GlobalExceptionHandler} 가 처리하므로 404·403 은
     * 다른 API 와 같은 모양이다).
     *
     * <p>인가는 {@code EvidenceContentService} 가 본다 — 올린 사람, 소재 등록자, 그 소재의
     * 보유자만 볼 수 있다. presigned URL 을 쓰지 않은 이유가 이것이다
     * ({@code FileStorageService#read} 주석).
     *
     * <h2>헤더</h2>
     *
     * <ul>
     *   <li>{@code Content-Disposition: inline} — 사진은 화면에 그려야 한다. 원본 파일명을
     *       여기 싣지 않는다: 한글·따옴표·개행이 들어간 이름은 헤더 인젝션이 되고,
     *       그 이름은 사용자가 정한 값이다
     *   <li>{@code X-Content-Type-Options: nosniff} — 브라우저가 타입을 추측하지 못하게
     *       한다. 화이트리스트로 svg 를 막는 노력이 스니핑으로 무의미해질 수 있다
     *   <li>{@code Cache-Control: private} — 증빙은 인가가 걸린 자료다. 공용 프록시가
     *       캐시하면 다른 사용자에게 그대로 나간다
     * </ul>
     */
    @GetMapping("/{evidenceFileId}/content")
    public ResponseEntity<Resource> content(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "증빙 id는 양수여야 합니다.") Long evidenceFileId) {
        FileContent file = evidenceContentService.read(userId(jwt), evidenceFileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store")
                .header("X-Content-Type-Options", "nosniff")
                // InputStreamResource 는 한 번만 읽힌다. 그것이 맞다 — Spring 이 응답으로
                // 흘린 뒤 스트림을 닫아 준다.
                .body(new InputStreamResource(file.stream()));
    }

    /**
     * 증빙을 지운다. S15P21E104-162.
     *
     * <p>정책은 {@code EvidenceDeletionService} 주석에 표로 있다. 요약하면 <b>업로더나 소재
     * 등록자만</b> 지울 수 있고, <b>온체인에 고정된 증빙은 아무도</b> 지울 수 없다(409).
     *
     * <p>{@code 204} 가 아니라 지워진 증빙을 돌려준다. 응답의 {@code deletedAt} 이 삭제가
     * 반영됐다는 증거이고, 모든 응답이 같은 봉투를 쓰는 관례를 깨지 않는다.
     */
    @DeleteMapping("/{evidenceFileId}")
    public ApiResponse<EvidenceFileResponse> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "증빙 id는 양수여야 합니다.") Long evidenceFileId) {
        return ApiResponse.success(evidenceDeletionService.delete(userId(jwt), evidenceFileId));
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
