package com.ssafy.upcycle.common.storage;

import java.io.InputStream;

/**
 * 파일 저장의 공통 계약. Material 증빙, Processing 증빙, Product 이미지, 분쟁 첨부가 함께 쓴다.
 *
 * <h2>왜 인터페이스를 먼저 두는가</h2>
 *
 * <p>지금 구현체는 로컬 파일시스템({@link LocalFileStorageService})이고, MinIO/S3 는
 * 뒤에 온다. 도메인이 저장 방식을 알면 백엔드를 바꿀 때 도메인마다 고쳐야 하고,
 * 무엇보다 <b>도메인마다 경로 규칙과 검증이 따로 생긴다.</b> 같은 사진이 한 화면에서는
 * 통과하고 다른 화면에서는 거부되는 것이 그렇게 시작된다.
 *
 * <h2>경로를 인자로 받지 않는다</h2>
 *
 * <p>호출부는 <b>무엇에 딸린 파일인지</b>만 말하고 경로는 구현체가 만든다
 * ({@code 2026-09-07-file-upload-policy.md}).
 *
 * <pre>
 *   {reference_type}/{reference_id}/{yyyy}/{MM}/{uuid}.{ext}
 * </pre>
 *
 * <p>클라이언트가 경로에 관여하면 {@code ..} 나 선행 {@code /} 로 디렉터리를 벗어날 수 있고,
 * 그 검사는 틀리기 쉽다. 전체 경로를 파생시키면 <b>막는 것이 아니라 불가능해진다.</b>
 *
 * <h2>해시는 저장하면서 계산한다</h2>
 *
 * <p>{@link StoredFile#hash()} 는 실제로 기록된 바이트의 SHA-256 이다
 * ({@code 0x} + 64자, {@code evidence_file.file_hash CHAR(66)} 과 온체인
 * {@code MaterialToken.verifyEvidence} 가 비교하는 형식). 저장 후 다시 읽어 계산하면
 * 그 사이 내용이 달라질 여지가 생기고, 무엇보다 <b>기록된 것과 다른 해시</b>가 나올 수 있다.
 */
public interface FileStorageService {

    /**
     * 파일을 저장하고 저장 결과를 돌려준다.
     *
     * <p>{@code originalFilename} 은 <b>메타데이터로만</b> 쓴다. 저장 이름은 UUID 다 —
     * 원본 이름은 충돌하고({@code IMG_0001.jpg}), 한글·공백·따옴표를 파일시스템·HTTP 헤더·
     * S3 키가 각각 다르게 escape 한다.
     *
     * @param reference 무엇에 딸린 파일인지 (경로 파생에 쓰인다)
     * @param originalFilename 사용자가 올린 이름. 확장자 검증과 표시용
     * @param contentType 선언된 미디어 타입. 확장자와 일치해야 한다
     * @param content 파일 내용. 이 메서드가 닫지 않는다 — 연 쪽이 닫는다
     * @param size 바이트 수. 크기 상한 검증용
     * @throws com.ssafy.upcycle.common.exception.BusinessException 정책 위반 또는 저장 실패
     */
    StoredFile store(
            FileReference reference,
            String originalFilename,
            String contentType,
            InputStream content,
            long size);

    /**
     * 저장된 파일을 읽는다. S15P21E104-162.
     *
     * <h2>왜 presigned URL 이 아닌가</h2>
     *
     * <p>S3/MinIO 는 만료 시간이 붙은 서명 URL 을 발급할 수 있다. 쓰지 않았다.
     *
     * <ul>
     *   <li>{@link LocalFileStorageService} 에 <b>대응물이 없다.</b> presigned 로 가면
     *       {@code app.storage.type=local} 배포에서 조회가 통째로 사라진다. 저장소 전환은
     *       설정 한 줄이라 그 차이가 배포 시점까지 드러나지 않는다
     *   <li>발급된 URL 은 만료 전까지 <b>토큰 없이 누구에게든 통한다.</b> 증빙은 남의 소재
     *       자료라 링크가 새는 순간 인가가 없어진다 — 채팅에 붙여 넣거나 리퍼러로 새는 것을
     *       우리가 막을 방법이 없다
     *   <li>인가 판정을 저장소에 넘기면 <b>우리가 못 한다.</b> 누가 무엇을 볼 수 있는지는
     *       소재 등록자·보유자 관계라 도메인 지식이고, 저장소는 그것을 모른다
     * </ul>
     *
     * <p>그래서 바이트를 우리 서버로 끌어와 인가를 통과한 요청에만 흘린다
     * ({@code GET /api/evidence/{id}/content}). 두 구현이 같은 방식으로 동작한다.
     *
     * @param storageKey {@code store} 가 돌려준 키. DB 에 저장된 값을 그대로 받는다
     * @return 스트림·크기·미디어 타입. <b>스트림은 호출부가 닫는다</b>
     * @throws com.ssafy.upcycle.common.exception.BusinessException 객체가 없으면
     *     {@link FileStorageErrorCode#FILE_NOT_FOUND}, 저장소 자체가 실패하면
     *     {@link FileStorageErrorCode#FILE_STORAGE_FAILED}
     */
    FileContent read(String storageKey);

    /**
     * 저장된 파일을 지운다.
     *
     * <p>없는 키를 지우는 것은 <b>오류가 아니다.</b> 호출부는 트랜잭션이 롤백될 때 이 메서드를
     * 부르는데, 그 시점에 파일이 이미 없을 수 있고(저장 자체가 실패한 경우) 그때 예외가 나면
     * 원래 실패 원인을 덮어쓴다.
     *
     * @return 실제로 지웠으면 {@code true}, 없어서 지울 것이 없었으면 {@code false}
     */
    boolean delete(String storageKey);

    /**
     * 저장된 파일이 있는지.
     *
     * <p><b>키가 저장소가 만들 수 없는 모양이면 "없다" 로 답한다</b>
     * ({@link #isWellFormedStorageKey}). 예외를 던지지 않는 이유는 이 물음이
     * 클라이언트 입력으로도 불린다는 데 있다 — 상품 등록이 썸네일 키를 받아 저장 여부를
     * 확인하는 자리가 그렇다. 거기서 잘못된 키에 저장소 오류(500)를 내면 "입력이 틀렸다"
     * 를 "서버가 고장났다" 로 답하는 것이 된다.
     */
    boolean exists(String storageKey);

    /**
     * 이 키가 {@code store} 가 만들어 낼 수 있는 모양인가.
     *
     * <p>두 구현이 같은 답을 내야 해서 여기 둔다. 저장 키는 언제나
     * {@code {type}/{id}/{yyyy}/{MM}/{uuid}.{ext}} 처럼 <b>루트 아래 상대 경로</b>라
     * 절대 경로·상위 참조·역슬래시·제어문자는 우리가 만든 키일 수 없다.
     *
     * <p>모양만 본다. 실제로 있는지는 저장소가 답한다 — 여기서 "있음직한 키" 까지
     * 판정하려 들면 과거에 다른 규칙으로 저장된 키를 못 읽게 된다.
     */
    static boolean isWellFormedStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        if (storageKey.startsWith("/") || storageKey.contains("\\")) {
            return false;
        }
        for (String segment : storageKey.split("/", -1)) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return storageKey.chars().noneMatch(Character::isISOControl);
    }
}
