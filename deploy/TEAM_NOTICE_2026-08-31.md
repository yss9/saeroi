# 📢 인프라 공지 — EC2 배포 환경 오픈 (2026-08-31)

## 서비스 주소

| 구분 | 주소 | 비고 |
| --- | --- | --- |
| 서비스 | https://j15e104.p.ssafy.io | 현재 placeholder (각 파트 배포 시 교체됨), http는 https로 자동 리다이렉트 |
| Jenkins | http://j15e104.p.ssafy.io:8081 | 계정은 팀 비밀 저장소 참고 |

## 배포 방식

- **각 파트 master에 merge하면 자동 배포됩니다** (blockchain만 `develop` 브랜치 트리거, 배포 없이 컴파일·테스트만)
- 이미지 규약: `circular-fashion/<part>:latest` — 각 파트 Dockerfile은 이 이름으로 빌드됨

| Job | 트리거 브랜치 |
| --- | --- |
| backend | `backend/master` |
| frontend | `frontend/master` |
| ai | `ai/master` |
| blockchain | `blockchain/develop` |

## 요청 사항

- **1번(Blockchain)**: `BESU_RPC_URL`, `BESU_CHAIN_ID`, 컨트랙트 주소 나오면 6번에게 전달 부탁드립니다 (EC2 `.env`에 반영 필요)

## 비밀 정보 보관 (팀 비밀 저장소)

아래 항목이 팀 비밀 저장소에 저장되어 있는지 확인해 주세요:

- Jenkins admin 계정
- MySQL 비밀번호들 (root / user)
- GitLab 토큰
- EC2 pem 키 (`J15E104T.pem`)

> ⚠️ `.env` 실값은 **EC2에만 존재**합니다. 서버 초기화 시 유실되므로 비밀 저장소 백업이 필수입니다.
> pem 키와 `.env`는 **절대 리포에 커밋 금지**.
