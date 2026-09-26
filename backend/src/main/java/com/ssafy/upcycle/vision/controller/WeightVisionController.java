package com.ssafy.upcycle.vision.controller;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.vision.dto.WeightRecognitionResponse;
import com.ssafy.upcycle.vision.service.WeightVisionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 인증된 화면이 내부 AI 서비스를 호출할 수 있게 하는 저울 사진 인식 프록시. */
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class WeightVisionController {

    private final WeightVisionClient weightVisionClient;

    @PostMapping(value = "/recognize-weight", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WeightRecognitionResponse> recognizeWeight(
            @RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            throw new BusinessException(FileStorageErrorCode.FILE_EMPTY);
        }
        return ApiResponse.success(weightVisionClient.recognize(image));
    }
}
