# EC2 Day-1 런북 (2026-08-31 월요일)

> EC2 서버를 받은 날, 위에서 아래로 순서대로 실행하는 체크리스트.
> 목표: 퇴근 전까지 **"파트 master에 Merge하면 Jenkins가 EC2에 배포하는 상태"** 만들기.
> 배경 설명은 `INFRA_SETUP.md`, 구성 설명은 `README.md` 참고.

---

## 0. 시작 전 준비물 확인

- [ ] EC2 접속 정보: `J15E104T.pem` 키 (받으면 즉시 팀 비밀 저장소에 보관, **절대 리포에 커밋 금지**)
- [ ] GitLab Personal Access Token — 본인 계정 `Preferences → Access Tokens`에서 발급
  - scope: `api`, `read_repository` / 용도: Jenkins의 리포 clone + webhook
- [ ] 1번(Blockchain)에게 받을 값: `BESU_RPC_URL`, `BESU_CHAIN_ID` (없으면 일단 비워두고 진행 가능)
- [x] 도메인 확정: `j15e104.p.ssafy.io` (공지 기준)

## 1. SSH 접속

```bash
chmod 400 J15E104T.pem   # Windows면 파일 속성 → 보안에서 본인만 읽기
ssh -i J15E104T.pem ubuntu@j15e104.p.ssafy.io
```

- [ ] 접속 성공

## 2. 서버 최초 세팅 (타임존 / swap / Docker)

```bash
git clone -b deploy/master https://lab.ssafy.com/s15-blockchain-pred-sub1/S15P21E104.git ~/deploy
cd ~/deploy
bash scripts/init-ec2.sh
exit   # docker 그룹 권한 적용을 위해 재로그인
```

다시 SSH 접속 후:

```bash
docker version && docker compose version   # 둘 다 정상 출력되면 OK
```

- [ ] Docker 설치 및 권한 확인

## 3. .env 작성

```bash
cd ~/deploy
cp .env.example .env
vi .env
```

| 키 | 값 정하는 방법 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD` | 직접 생성 (`openssl rand -hex 16` 활용) |
| `JWT_SECRET` | `openssl rand -hex 32` |
| `BESU_*`, `BLOCKCHAIN_SERVICE_PRIVATE_KEY` | 1번에게 받은 값 (아직 없으면 비워둠) |
| `JENKINS_ADMIN_ID` / `JENKINS_ADMIN_PASSWORD` | 직접 생성 — **기본값 admin 금지** |
| `JENKINS_EXTERNAL_URL` | `http://j15e104.p.ssafy.io:8081/` |
| `GITLAB_USERNAME` | `oauth2` 그대로 |
| `GITLAB_TOKEN` | 0번에서 발급한 토큰 |
| `VITE_*` | `VITE_API_BASE_URL=http://j15e104.p.ssafy.io` (HTTPS 적용 시 https로 교체), 컨트랙트 주소는 1번 배포 후 |

- [ ] `.env` 작성 완료

## 4. 전체 스택 기동 (placeholder)

frontend/backend/ai 실제 이미지는 아직 없으므로 **첫 기동은 로컬 검증 때처럼 placeholder로** 올린다.
(각 파트 스켈레톤이 배포되기 시작하면 override 없이 `docker compose up -d`로 전환)

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
bash scripts/health-check.sh
```

기대 결과: `nginx → frontend`, `nginx → backend`, `jenkins` 모두 OK.

- [ ] health-check 3항목 OK

## 5. UFW 방화벽 확인

SSAFY EC2는 AWS 콘솔이 없고 **UFW로만 방화벽을 관리**한다 (기본: enable + 22번만 허용).
`init-ec2.sh`의 [3/5] 단계가 80/443/8081을 열어주므로 여기서는 확인만 한다.

```bash
sudo ufw status verbose
```

| 포트 | 상태 | 비고 |
| --- | --- | --- |
| 22 | 허용 (기본) | SSH — **절대 deny/disable 금지**, ufw도 enable 유지 (잠기면 초기화만 가능) |
| 80, 443 | 허용 | 서비스 |
| 8081 | 허용 | Jenkins — GitLab webhook 수신을 위해 공개 필요. 방어는 강력한 admin 비밀번호로 |
| 3306 | 열지 않음 | 아래 주의 참고 |

> **주의: Docker는 UFW를 우회한다.** compose의 `ports:`로 publish된 포트는 Docker가 iptables를
> 직접 조작해 UFW 규칙과 무관하게 외부에 노출된다. UFW에서 3306을 막아도 `3306:3306`으로 열면 뚫린다.
> 그래서 `docker-compose.local.yml`은 `127.0.0.1:3306:3306`(루프백)으로 바인딩한다.

- [ ] `sudo ufw status`에서 22/80/443/8081 허용 + `Status: active` 확인
- [ ] 3306이 외부에서 접근 안 되는지 확인 (로컬 PC에서 `nc -zv j15e104.p.ssafy.io 3306` 이 실패해야 정상)

## 6. Jenkins 확인 (자동 세팅 검증)

브라우저에서 `http://j15e104.p.ssafy.io:8081` 접속:

- [ ] `.env`의 admin 계정으로 로그인 성공 (설치 마법사가 뜨면 안 됨)
- [ ] Job 4개(`backend`, `frontend`, `ai`, `blockchain`)가 이미 등록되어 있음
- [ ] `Manage Jenkins → Credentials`에 `gitlab-token` 존재

전부 JCasC 자동 세팅이므로 안 되어 있으면 `docker logs cf-jenkins`에서 SEVERE 로그 확인.

## 7. GitLab Webhook 연결 (유일하게 손이 가는 부분)

Job마다 반복 (backend 예시):

1. Jenkins에서 `backend` Job → `Configure` → Build Triggers의 GitLab 항목 → `Advanced` → **Secret token `Generate`** → 복사
2. GitLab 리포 → `Settings → Webhooks → Add new webhook`
   - URL: `http://j15e104.p.ssafy.io:8081/project/backend`
   - Secret token: 방금 복사한 값
   - Trigger: **Push events**만 체크
3. 생성 후 `Test → Push events` → HTTP 200 확인

- [ ] backend / frontend / ai / blockchain 4개 webhook 등록 + 테스트 200

## 8. 파이프라인 트리거 검증

```bash
# 로컬 PC에서 (아무 파트나, 예: backend)
git checkout backend/master && git pull
git commit --allow-empty -m "chore: Jenkins 트리거 테스트"
git push origin backend/master   # 보호 브랜치면 MR로 병합해서 테스트
```

- [ ] push 직후 Jenkins의 `backend` Job이 자동 실행됨 (아직 gradlew가 없으면 빌드 실패는 정상 — **트리거 동작 확인이 목적**)

여기까지 되면 **"Merge하면 Jenkins가 돈다"** 달성. 팀에 공지:
서비스 `http://j15e104.p.ssafy.io`, Jenkins `http://j15e104.p.ssafy.io:8081`, 이미지 규약 `circular-fashion/<part>:latest`.

## 9. (여유 있으면) HTTPS

```bash
sudo apt-get install -y certbot
sudo certbot certonly --webroot -w ~/deploy/certbot/www -d j15e104.p.ssafy.io \
  --non-interactive --agree-tos -m <이메일> --no-eff-email
```

인증서는 certbot 기본 경로(`/etc/letsencrypt`)에 발급되고, compose가 이 경로를 nginx에 그대로 마운트한다
(systemd `certbot.timer`가 자동 갱신). 갱신 후 nginx가 새 인증서를 읽도록 deploy hook 등록:

```bash
sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh >/dev/null <<'HOOK'
#!/bin/bash
docker exec cf-nginx nginx -s reload
HOOK
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
```

발급 후 (nginx 443 설정은 `deploy/master`에 이미 반영됨):
1. `cd ~/deploy && git pull`
2. `.env`의 `VITE_API_BASE_URL`을 `https://j15e104.p.ssafy.io`로 교체 (Jenkins는 8081 http 유지)
3. `docker compose up -d nginx` (마운트 추가로 컨테이너 재생성됨)
4. 검증: `curl -I https://j15e104.p.ssafy.io` 200, `curl -I http://j15e104.p.ssafy.io` 301

> 변경은 EC2에서 직접 수정하지 말고 `deploy/feat/*` 브랜치 → MR → EC2에서 `git pull` 순서로.

- [ ] `https://j15e104.p.ssafy.io` 접속 확인

## 트러블슈팅

| 증상 | 조치 |
| --- | --- |
| `permission denied ... docker.sock` | 2번의 재로그인 안 한 것 — `exit` 후 재접속 |
| Jenkins가 안 뜸 / 재시작 반복 | `docker logs cf-jenkins 2>&1 \| grep -i severe` — 대부분 casc.yaml 환경변수 누락 |
| webhook 테스트 403 | Secret token 불일치 — Job 설정에서 재생성 후 GitLab에 다시 입력 |
| webhook 테스트 타임아웃 | `sudo ufw status`로 8081 허용 확인 (GitLab 서버가 접근 가능해야 함) |
| 80/443/8081 접근 안 됨 | `sudo ufw status` 확인 — 누락 시 `init-ec2.sh`의 [3/5] UFW 단계 재실행 |
| 컨테이너는 뜨는데 페이지 안 열림 | `sudo ufw status`로 80 허용 여부, `docker compose ps`로 nginx 상태 확인 |
