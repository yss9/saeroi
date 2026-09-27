# [공지] 운영 브랜드 AI 어시스턴트 503 — 운영 `.env` 에 `AGENT_LLM_API_KEY` 가 비어 있었음 (2026-09-27)

## 무슨 일이 있었나

운영(`https://j15e104.p.ssafy.io`)에서 브랜드 AI 어시스턴트가 "AI 응답을 받지 못했습니다"
(`AGENT_LLM_UNAVAILABLE`, 503)만 냈습니다. `/test` 스택(`cft-backend`)은 정상이었습니다.

원인은 코드가 아니라 **운영 `.env` 값 누락**입니다. `deploy/feat/brand-agent-llm-env`
(`d570d365`)로 `.env.example` · `docker-compose.yml` · `docker-compose.test.yml` 의 배선은
모두 들어왔지만, **실값은 `.env.test` 에만 채워졌고 운영 `.env` 에는 `AGENT_LLM_API_KEY`
줄 자체가 없었습니다.**

이 상태에서 backend 는 **정상 기동합니다.** 헬스체크도 통과합니다 — 에이전트 API 만
503 을 냅니다. 기동 로그의 아래 한 줄이 유일한 신호였습니다:

```
WARN  c.s.upcycle.agent.config.AgentConfig : Agent LLM NOT configured
      (app.agent.api-key is empty) — 대화형 에이전트는 503 을 낸다.
```

`QR_BASE_URL`(2026-09-18)과 **같은 유형의 세 번째 사고**입니다: 배선 MR 은 병합됐는데
운영 `.env` 의 실값은 사람이 채워야 하고, 그 사실이 어디에도 안 적혀 있으면 아무도 안 채웁니다.

## 무엇을 했나

1. 운영 `.env` 를 `.env.bak-202609272224` 로 백업
2. `.env.test` 의 `AGENT_LLM_API_KEY` 줄을 운영 `.env` 로 복사 (값은 터미널·로그에 출력하지
   않고 **길이 47 자 일치**로만 검증)
3. `. scripts/compose.sh && dc up -d --no-deps backend` 로 컨테이너 **재생성**
   (`restart` 는 `.env` 를 다시 읽지 않는다)
4. 확인: 컨테이너 내 키 길이 47, 로그에
   `Agent LLM configured: provider=anthropic baseUrl=https://gms.ssafy.io/gmsapi/api.anthropic.com model=claude-haiku-4-5-20251001 thinking=false maxToolIterations=6`,
   `health-check.sh` 전 항목 OK

`AGENT_LLM_API_KEY` 외 `AGENT_LLM_*` 키는 compose 에 기본값이 있어 운영 `.env` 에 없어도
무해합니다(GMS base URL / haiku-4-5 / thinking=false / 도구 6회).

## 다른 파트에 영향

- 운영·`/test` 가 **같은 팀 키 하나를 공유**합니다. ⚠️ **토큰당 크레딧이 차감되고, 대화
  한 턴에 LLM 이 여러 번 불립니다** — 운영에서 에이전트가 열린 지금부터는 테스트 반복이
  곧 비용입니다. 부하 테스트나 대량 반복 호출 전에 공유해 주세요.
- 키 자체는 이 공지 어디에도 없습니다. 필요하면 EC2 `.env` / `.env.test` 또는 팀 비밀
  저장소에서 확인하세요.

## 재발 방지 / 부탁

- `docs/INFRA_SETUP.md` §3 환경변수 표에 `AGENT_LLM_*` 행을 추가했습니다. 이 표에 없는
  키는 운영에 안 채워진다고 보면 됩니다.
- `docs/EC2_DAY1.md` 트러블슈팅에 "**`/test` 는 정상인데 운영만 503/에러**" 항목을
  추가했습니다 (진단 절차 포함).
- **부탁**: 새 필수 환경변수를 추가하는 MR 을 올릴 때 설명에 "운영 `.env` 실값 필요"를
  한 줄 남겨 주세요. `.env.example` 에 키를 넣는 것만으로는 운영에 값이 생기지 않습니다.
