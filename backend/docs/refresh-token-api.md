# Refresh Token API

S15P21E104-416에서 로그인 상태를 길게 유지하기 위해 Access Token과 별도로 Refresh Token을
발급한다. Access Token 기본 수명은 30분이고, Refresh Token 기본 수명은 14일이다.

## 로그인 응답

이메일 로그인 `POST /api/v1/auth/login`과 지갑 로그인
`POST /api/v1/auth/wallet/verify`의 성공 응답에 `refreshToken`이 추가된다.

```json
{
  "success": true,
  "data": {
    "accessToken": "<JWT>",
    "refreshToken": "<opaque token>",
    "user": {}
  }
}
```

Refresh Token 원문은 이 응답으로 한 번만 전달한다. 서버는 SHA-256 해시만
`refresh_token.token_hash`에 저장하므로 원문을 복구할 수 없다.

## Access Token 재발급

```http
POST /api/v1/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "<로그인 또는 직전 재발급에서 받은 토큰>"
}
```

성공하면 기존 Refresh Token은 즉시 폐기되고 새 토큰 쌍이 발급된다.

```json
{
  "success": true,
  "data": {
    "accessToken": "<new JWT>",
    "refreshToken": "<new opaque token>"
  }
}
```

클라이언트는 성공 응답의 두 토큰을 모두 원자적으로 교체해야 한다. 이미 재발급에 사용한 토큰,
존재하지 않는 토큰 또는 위조된 토큰은 `401 INVALID_REFRESH_TOKEN`, 만료된 토큰은
`401 REFRESH_TOKEN_EXPIRED`로 거부한다. 정지된 계정은 `403 USER_SUSPENDED`로 거부한다.

## 설정

```dotenv
JWT_ACCESS_TOKEN_TTL=PT30M
JWT_REFRESH_TOKEN_TTL=P14D
```

값은 ISO-8601 Duration 형식이며 양수여야 한다. Refresh Token을 이용한 로그아웃·전체 무효화는
S15P21E104-417의 범위다.
