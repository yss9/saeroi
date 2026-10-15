# [공지] 운영 로그인이 CORS로 막혀 있던 문제 수정 (2026-09-11)

작성: 6번(인프라) · 대상: 전 파트, 특히 로그인/인증을 다루는 프론트·백엔드

## 무엇이 문제였나

`CORS_ALLOWED_ORIGINS` 환경변수가 운영에 한 번도 설정된 적이 없었습니다. 백엔드
(`app.cors.allowed-origins`)의 기본값이 `http://localhost:5173,http://127.0.0.1:5173`뿐이라,
**운영 도메인(`https://j15e104.p.ssafy.io`)에서의 요청은 전부 CORS 거부 대상**이었습니다.

최신 브라우저는 same-origin POST 요청에도 `Origin` 헤더를 실어 보내는데, 백엔드의
`CorsConfigurationSource`가 그 헤더를 검사해서 허용 목록에 없으면 **403 "Invalid CORS
request"**로 거부합니다. 로그인이 대표적인 POST 요청이라 정면으로 걸렸습니다.

`/test/` 스택에서 로그인을 테스트하다가 발견했고, curl로 운영 로그인 API에도 동일하게
재현되는 것을 확인했습니다:

```bash
curl -X POST https://j15e104.p.ssafy.io/api/v1/auth/login \
  -H "Origin: https://j15e104.p.ssafy.io" -H "Content-Type: application/json" \
  -d '{"email":"a@a.com","password":"x"}'
# → 403 "Invalid CORS request" (수정 전)
```

## 수정 내용

`docker-compose.yml`(운영)·`docker-compose.test.yml`(test) 모두에 `CORS_ALLOWED_ORIGINS`를
전달하도록 추가하고, 운영/test EC2의 `.env`/`.env.test`에 `https://j15e104.p.ssafy.io`
(운영·test 공용 — Origin 헤더엔 경로가 안 들어가서 도메인만 같으면 됩니다)를 채웠습니다.

## 확인해 주세요

- 로그인 관련 코드에서 이 응답(403 "Invalid CORS request")을 다른 원인으로 오인해 처리한
  로직이 있다면 재검토 바랍니다 — 이제는 나지 않아야 합니다.
- 새 origin(예: 다른 서브도메인, 앱 내 웹뷰 등)에서 API를 호출할 계획이 있다면 미리
  알려주세요 — `CORS_ALLOWED_ORIGINS`에 콤마로 추가하면 됩니다.
