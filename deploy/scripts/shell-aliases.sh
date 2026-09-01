#!/usr/bin/env bash
# 로그인 셸에서 쓰는 단축 명령 모음.
#
# EC2 의 ~/.bashrc 가 이 파일을 source 한다:
#   [ -f "$HOME/deploy/scripts/shell-aliases.sh" ] && . "$HOME/deploy/scripts/shell-aliases.sh"
#
# 리포에 두는 이유: ~/.bashrc 에 직접 적으면 서버를 재구축할 때 사라진다.
# 여기 있으면 git clone 만으로 따라온다 (docs/EC2_DAY1.md 참고).

# dc() — compose 호출의 단일 경로
[ -f "$HOME/deploy/scripts/compose.sh" ] && . "$HOME/deploy/scripts/compose.sh"

# cfps — 컨테이너 목록
cfps() { dc ps; }

# cfhealth — 전체(또는 특정 서비스) 정상 여부
#   cfhealth          전체
#   cfhealth backend  backend 만
cfhealth() { bash "$HOME/deploy/scripts/health-check.sh" "$@"; }

# cflog <서비스> [줄수] — 로그 보기 (기본 50줄)
#   cflog backend
#   cflog nginx 200
cflog() {
    local svc="${1:?사용법: cflog <backend|frontend|ai|mysql|nginx|jenkins> [줄수]}"
    docker logs --tail "${2:-50}" "cf-${svc}"
}

# cftail <서비스> — 로그 실시간 (Ctrl+C 로 종료)
cftail() {
    local svc="${1:?사용법: cftail <서비스>}"
    docker logs -f --tail 20 "cf-${svc}"
}

# cfsh <서비스> — 컨테이너 안으로 (나갈 때 exit)
cfsh() {
    local svc="${1:?사용법: cfsh <서비스>}"
    docker exec -it "cf-${svc}" sh
}

# cfdb — MySQL 접속. 비밀번호를 입력하지 않는다.
#   컨테이너가 이미 MYSQL_ROOT_PASSWORD 를 환경변수로 갖고 있으므로 그대로 쓴다.
#   MYSQL_PWD 로 넘기는 이유: -p<암호> 는 컨테이너 프로세스 목록(ps)에 그대로 보이고
#   "password on the command line is insecure" 경고도 뜬다.
#   비밀번호를 약하게 바꾸는 대신 "칠 일이 없게" 만드는 쪽이 안전하다.
cfdb() {
    docker exec -it cf-mysql sh -c \
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"'
}

# cfpull — MR 머지 후 서버 코드 갱신 + 변경분 재기동
cfpull() {
    ( cd "$HOME/deploy" && git pull && dc up -d )
}

# cfhelp — 뭐가 있는지 잊었을 때
cfhelp() {
    cat <<'HELP'
cfps              컨테이너 목록
cfhealth [서비스]  정상 여부 (엔드포인트까지 확인)
cflog <서비스> [N] 로그 마지막 N줄 (기본 50)
cftail <서비스>    로그 실시간 (Ctrl+C 종료)
cfsh <서비스>      컨테이너 안으로 (exit 로 나옴)
cfdb              MySQL 접속 (비밀번호 입력 불필요)
cfpull            git pull + 변경분 재기동
dc <compose명령>   예: dc restart backend

서비스 이름: backend frontend ai mysql nginx jenkins
HELP
}
