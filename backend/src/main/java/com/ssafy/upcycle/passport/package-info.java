/**
 * QR Passport 공개 조회.
 *
 * <p>이 패키지의 모든 응답은 <b>로그인 없이</b> 나간다({@code SecurityConfig} permitAll).
 * 그래서 다른 도메인의 응답을 그대로 재사용하지 않는다 — {@code product} 패키지의 DTO 는
 * 인증된 사용자를 전제로 만들어졌고, 브랜드 id 같은 값이 그대로 들어 있다. 여기서는 공개
 * 응답 전용 DTO 로 다시 조립해서, 원본 DTO 에 필드가 추가돼도 이 패키지가 자동으로
 * 넓어지지 않게 한다.
 */
package com.ssafy.upcycle.passport;
