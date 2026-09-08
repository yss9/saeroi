package com.ssafy.upcycle.evidence.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code GET /api/evidence} 응답. S15P21E104-286 §6-1.
 *
 * <h2>왜 응답이 보는 사람에 따라 갈리는가</h2>
 *
 * <p>고치기 전에는 <b>인증만 하면 누구나</b> 남의 소재 증빙 목록을 통째로 봤다 —
 * 파일명({@code storageKey}) · 종류 · {@code contentPath} · {@code fileHash} · 업로더까지.
 * 바이트는 {@code EvidenceAccessPolicy} 가 막고 있었지만 <b>목록은 인가를 거치지 않았다.</b>
 * {@code 계근표_OO상사.pdf} 라는 이름 하나로 거래 상대가 드러나고, 계근표에는 중량·단가가
 * 들어 있다. 그러므로 목록도 관계를 봐야 한다.
 *
 * <h2>왜 이 모양인가 — 버린 두 안</h2>
 *
 * <ol>
 *   <li><b>같은 DTO 에 민감 필드만 {@code null}</b> — 가장 적게 고치는 방법이고 프론트가
 *       그대로 돈다. 버린 이유는 <b>"값이 없다" 와 "가려졌다" 가 구분되지 않는다</b>는 것이다.
 *       {@code fileHash: null} 을 받은 화면은 아직 해시가 계산되지 않은 증빙과 남의 증빙을
 *       같은 모양으로 그린다. 게다가 <b>다음에 추가되는 민감 필드는 기본값이 "노출"</b> 이라
 *       가리는 목록을 사람이 관리해야 하고, 빠뜨린 순간 조용히 새어 나간다
 *   <li><b>별도 엔드포인트 {@code GET /api/evidence/summary}</b> — 두 계약이 각자 명확해진다.
 *       버린 이유는 <b>프론트가 자기가 어느 쪽인지 미리 알 수 없다</b>는 것이다. 관계 판정은
 *       등록자 대조 + {@code material_balance} 조회이고 그건 서버만 안다. 결국 프론트는
 *       전체를 먼저 부르고 403 을 받으면 요약으로 재시도하게 되는데, <b>403 이 곧 "관계 없음"
 *       이라는 정보</b>라서 되레 관계 유무를 탐색할 수 있게 된다. 왕복도 두 번이 된다
 * </ol>
 *
 * <p>그래서 <b>같은 엔드포인트가 한 번에 답하고, 어느 범위인지를 {@code visibility} 로
 * 말한다.</b> 가려진 필드는 {@code null} 이 아니라 <b>키 자체가 없다</b>
 * ({@code @JsonInclude(NON_NULL)}) — 프론트가 필드마다 null 을 해석할 자리를 만들지 않는다.
 *
 * @param visibility 어느 범위까지 보여준 응답인가. <b>항상 있다</b>
 * @param files 증빙 목록. {@link EvidenceListVisibility#FULL} 일 때만 있고, 요약 응답에는
 *     <b>키가 없다</b>
 * @param summary 종류별 건수. <b>두 경우 모두 있다</b> — 전체를 보는 사람의 화면도 같은
 *     요약 줄을 쓰므로, 프론트가 {@code files} 를 세어 만들지 않아도 된다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvidenceListResponse(
        EvidenceListVisibility visibility,
        List<EvidenceFileResponse> files,
        List<EvidenceTypeCount> summary) {

    /** 업로더 · 소재 등록자 · 현재 보유자에게 주는 응답. */
    public static EvidenceListResponse full(List<EvidenceFile> evidence) {
        return new EvidenceListResponse(
                EvidenceListVisibility.FULL,
                evidence.stream().map(EvidenceFileResponse::from).toList(),
                summarize(evidence));
    }

    /**
     * 관계가 없는 인증 사용자에게 주는 응답.
     *
     * <p>{@code files} 를 빈 배열로 두지 않는다 — 빈 배열은 "증빙이 없다" 는 뜻이고,
     * 그건 사실이 아니다. 키가 없는 것이 "이 응답에는 목록이 들어 있지 않다" 다.
     */
    public static EvidenceListResponse summary(List<EvidenceFile> evidence) {
        return new EvidenceListResponse(
                EvidenceListVisibility.SUMMARY, null, summarize(evidence));
    }

    /**
     * 종류별 건수. 순서는 {@link EvidenceType} 열거 순서다 ({@code EnumMap}).
     *
     * <p>DB 에서 {@code GROUP BY} 로 세지 않는다. 목록 조회가 이미 같은 행들을 읽어 왔고,
     * 한 소재의 증빙은 사진 몇 장 규모다 (업로드 상한이 요청당 10개). 쿼리를 하나 더 두면
     * <b>목록과 요약이 서로 다른 시점을 볼 수 있다</b> — 같은 응답 안에서 개수가 어긋난다.
     */
    private static List<EvidenceTypeCount> summarize(List<EvidenceFile> evidence) {
        Map<EvidenceType, Long> counts = new EnumMap<>(EvidenceType.class);
        for (EvidenceFile file : evidence) {
            counts.merge(file.getEvidenceType(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new EvidenceTypeCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
