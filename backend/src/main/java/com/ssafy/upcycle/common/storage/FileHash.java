package com.ssafy.upcycle.common.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 파일 해시. {@code 0x} + SHA-256 64자.
 *
 * <p><b>형식을 한곳에 모은 이유.</b> 이 값은 온체인에 기록되고
 * {@code evidence_file.file_hash CHAR(66)} 에 저장된다. 저장 구현이 둘(로컬 · S3)이라
 * 각자 포맷하면 <b>같은 파일이 구현에 따라 다른 문자열</b>이 될 수 있다.
 * 그러면 온체인 값과 대조가 안 되고, 그때는 파일이 정말 그것인지 확인할 방법이 사라진다.
 */
public final class FileHash {

    /** {@code 0x} + 64자. {@code evidence_file.file_hash CHAR(66)} 과 같다. */
    public static final int LENGTH = 66;

    private FileHash() {}

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 제공한다. 없으면 환경이 깨진 것이라 복구 대상이 아니다.
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }

    /** 다이제스트를 온체인 비교 형식으로 만든다. 이 메서드가 유일한 문이다. */
    public static String format(MessageDigest digest) {
        return "0x" + HexFormat.of().formatHex(digest.digest());
    }
}
