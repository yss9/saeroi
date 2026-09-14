# Refresh Token API

로그인 상태를 길게 유지하기 위해 Access Token과 별도로 Refresh Token을 발급한다.
Access Token 기본 수명은 30분이고, Refresh Token 기본 수명은 14일이다.

## 로그인 응답

이메일 로그인 `POST /api/v1/auth/login`과 지갑 로그인
`POST /api/v1/auth/wallet/verify`가 성공하면 Access Token은 응답 본문으로, Refresh Token은
JavaScript에서 읽을 수 없는 HttpOnly 쿠키로 전달한다.

```json
{
  "success": true,
  "data": {
    "accessToken": "<JWT>",
    "user": {}
  }
}
```

```http
Set-Cookie: refresh_token=<opaque token>; Path=/api/v1/auth; Max-Age=1209600; Secure; HttpOnly; SameSite=Strict
```

서버는 Refresh Token의 SHA-256 해시만 `refresh_token.token_hash`에 저장하므로 DB에서
원문을 복구할 수 없다. 운영 쿠키는 HTTPS에서만 전송하며 프론트는 원문을 저장하지 않는다.

## Access Token 재발급

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=<opaque token>
```

성공하면 기존 Refresh Token은 즉시 폐기되고 새 토큰 쌍이 발급된다.
새 Refresh Token은 회전된 HttpOnly 쿠키로 덮어쓴다.

```json
{
  "success": true,
  "data": {
    "accessToken": "<new JWT>"
  }
}
```

이미 재발급에 사용한 토큰, 존재하지 않는 토큰 또는 위조된 토큰은
`401 INVALID_REFRESH_TOKEN`, 만료된 토큰은 `401 REFRESH_TOKEN_EXPIRED`로 거부한다.
정지된 계정은 `403 USER_SUSPENDED`로 거부한다. 명시적인 교차 Origin 배치에서는 브라우저가
쿠키를 주고받도록 클라이언트 요청에 credentials 옵션이 필요하다.

## 설정

```dotenv
JWT_ACCESS_TOKEN_TTL=PT30M
JWT_REFRESH_TOKEN_TTL=P14D
REFRESH_TOKEN_COOKIE_SECURE=true
REFRESH_TOKEN_COOKIE_SAME_SITE=Strict
```

TTL은 ISO-8601 Duration 형식이며 양수여야 한다. 운영에서는 cookie-secure를 반드시 true로
유지한다. HTTP 로컬 개발에서는 false로 덮어쓸 수 있다. Refresh Token을 이용한 로그아웃·전체
무효화는 별도 기능 범위다.
