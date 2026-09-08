package com.ssafy.upcycle.evidence;

/**
 * 증빙 API 경로. <b>문자열을 한곳에서만 만든다.</b>
 *
 * <p>응답에 파일 조회 경로({@code contentPath})를 실어 보내므로, 컨트롤러의
 * {@code @RequestMapping} 과 그 문자열이 갈라지면 <b>프론트가 404 를 받는다.</b>
 * 양쪽 다 컴파일되고 테스트도 각각 통과하기 때문에 배포 후에야 드러난다.
 */
public final class EvidenceApiPaths {

    /** {@code /v1} 이 없는 것은 소재 계열과 같은 관례다 ({@code EvidenceController} 주석). */
    public static final String BASE = "/api/evidence";

    private EvidenceApiPaths() {}

    /** 파일 바이트를 받는 경로. 프론트가 {@code <img src>} 에 그대로 쓸 수 있다. */
    public static String content(Long evidenceFileId) {
        return BASE + "/" + evidenceFileId + "/content";
    }
}
