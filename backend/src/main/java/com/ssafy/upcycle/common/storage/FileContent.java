package com.ssafy.upcycle.common.storage;

import java.io.InputStream;

/**
 * 저장된 파일을 되돌려주는 형태. {@link FileStorageService#read} 의 반환값이다.
 *
 * <h2>바이트 배열이 아니라 스트림이다</h2>
 *
 * <p>{@code byte[]} 로 돌려주면 파일 하나가 최대 10MB 이고 요청당 10개까지 허용되므로
 * 동시 조회 몇 건만 겹쳐도 힙이 그만큼 늘어난다. 스트림으로 넘기면 서블릿 출력으로
 * 그대로 흘려보낼 수 있어 메모리가 파일 크기에 비례하지 않는다.
 *
 * <p><b>받은 쪽이 닫는다.</b> 여기서 열어 준 스트림을 저장 구현이 닫을 방법이 없다 —
 * 컨트롤러가 응답으로 흘린 뒤에야 닫히기 때문이다.
 *
 * @param stream 파일 내용. <b>사용한 쪽이 닫아야 한다</b>
 * @param size 바이트 수. {@code Content-Length} 로 나간다
 * @param contentType 미디어 타입. <b>저장 키의 확장자에서 파생한다</b> —
 *     저장소 메타데이터를 믿지 않는 이유는 {@link FileStorageService#read} 주석에 있다
 */
public record FileContent(InputStream stream, long size, String contentType) {}
