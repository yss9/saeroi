package com.ssafy.upcycle.common.health;

import com.ssafy.upcycle.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 파이프라인이 "이 컨테이너가 요청을 받을 준비가 되었는가"를 판정하는 지점.
 *
 * <p>deploy/master 의 scripts/health-check.sh 가 배포 성공 조건으로 {@code GET /api/health} 를 호출한다
 * (nginx 가 /api/ 를 경로 그대로 backend 로 넘기므로 매핑도 /api/health 다). 여기서 2xx 가 나오지 않으면
 * 배포가 실패로 처리된다.
 *
 * <p>DB 등 외부 의존성은 일부러 확인하지 않는다 — 이 엔드포인트의 질문은 "애플리케이션 컨텍스트가 떴는가"이고,
 * 의존성 장애로 배포 자체가 실패 처리되면 롤백 판단이 흐려진다.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("UP");
    }
}
