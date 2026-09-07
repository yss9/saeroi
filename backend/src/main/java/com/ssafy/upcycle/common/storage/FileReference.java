package com.ssafy.upcycle.common.storage;

/**
 * 파일이 무엇에 딸려 있는지. 저장 경로의 앞 두 조각을 결정한다.
 *
 * <p>{@code type} 은 {@code evidence_file.reference_type} ENUM 과 문자열이 같아야 한다 —
 * 경로와 DB 가 갈라지면 파일을 참조로 되찾을 수 없다. ENUM 을 직접 쓰지 않는 이유는
 * {@code common} 이 도메인을 알면 안 되고, Product 이미지처럼 그 ENUM 에 없는 용도도
 * 같은 저장소를 쓰기 때문이다.
 */
public record FileReference(String type, Long id) {

    public FileReference {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("File reference type must not be blank");
        }
        // 경로 조각이 되므로 구분자와 상위 참조를 막는다. 여기서 막으면 경로 조립부는
        // 검사를 다시 하지 않아도 된다.
        if (!type.matches("[A-Z_]+")) {
            throw new IllegalArgumentException(
                    "File reference type must be upper snake case: " + type);
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("File reference id must be positive");
        }
    }
}
