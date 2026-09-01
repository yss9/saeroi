# 📢 인프라 공지 — EC2 배포 환경 오픈 (2026-08-31)

## 서비스 주소

| 구분 | 주소 | 비고 |
| --- | --- | --- |
| 서비스 | https://j15e104.p.ssafy.io | 현재 placeholder (각 파트 배포 시 교체됨), http는 https로 자동 리다이렉트 |
| Jenkins | http://j15e104.p.ssafy.io:8081 | 계정은 팀 비밀 저장소 참고 |

## CI/CD 동작 (Multibranch 전환 완료)

- **develop / feat 브랜치에 push하면 테스트가 자동으로 돕니다.** 새 feat 브랜치를 만들면
  Jenkins Job이 자동 생성되고, 브랜치를 지우면 자동으로 정리됩니다. 등록 요청 불필요.
- **각 파트 master에 merge하면 이미지 빌드 + 자동 배포됩니다.**
- 이미지 규약: `circular-fashion/<part>:latest`

| 파트 | CI (테스트) | CD (배포) |
| --- | --- | --- |
| backend / frontend / ai | 해당 파트의 모든 브랜치 | `<파트>/master` 만 |
| blockchain | `blockchain/*` (compile + test) | **없음** — 컨트랙트는 수동 배포 |

### 파이프라인은 이제 각 파트가 소유합니다

`Jenkinsfile`이 각 파트 브랜치 루트에 있습니다. **빌드 스텝은 직접 고치셔도 됩니다** —
인프라에 요청할 필요 없습니다. 지켜야 할 계약은 둘뿐입니다:
이미지 이름 `circular-fashion/<파트>:latest`, Deploy 스테이지는 `<파트>/master` 에서만.

> MR을 올렸을 때 GitLab 화면에 CI 결과가 표시되지는 않습니다. 다만 MR의 소스 브랜치는
> push 시점에 이미 테스트가 돌았으므로, Jenkins에서 해당 브랜치 결과를 확인하시면 됩니다.

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
