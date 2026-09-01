#!/usr/bin/env bash
# EC2 의 MySQL 백업을 작업용 PC 로 내려받는다.
# ⚠️ EC2 에서 실행하는 스크립트가 아니다 — 팀원 로컬에서 실행한다.
#
# 왜 필요한가: backup-mysql.sh 가 만드는 덤프는 EC2 의 같은 디스크에 있다.
# 컨테이너 사고에는 대응되지만 인스턴스 자체가 사라지면 백업도 같이 사라진다.
# 정기적으로 EC2 밖으로 빼두는 것이 이 스크립트의 목적이다.
#
# 사용법:
#   bash scripts/pull-backups.sh
#   BACKUP_DEST=/d/backups bash scripts/pull-backups.sh
set -euo pipefail

HOST="${BACKUP_SRC_HOST:-ubuntu@j15e104.p.ssafy.io}"
KEY="${BACKUP_SSH_KEY:-$HOME/.ssh/J15E104T.pem}"
SRC_DIR="${BACKUP_SRC_DIR:-~/backups/mysql}"
DEST="${BACKUP_DEST:-$HOME/Desktop/S15P21E104-backups/mysql}"

[ -f "$KEY" ] || { echo "FAIL: SSH 키 없음: $KEY  (BACKUP_SSH_KEY 로 지정 가능)"; exit 1; }
mkdir -p "$DEST"

echo "── 원격 백업 목록 ──"
REMOTE="$(ssh -i "$KEY" -o ConnectTimeout=15 "$HOST" "ls -1 $SRC_DIR/*.sql.gz 2>/dev/null" || true)"
[ -n "$REMOTE" ] || { echo "원격에 백업이 없습니다."; exit 1; }
echo "$REMOTE" | wc -l | xargs printf "원격 %s 개\n"

NEW=0
while IFS= read -r path; do
    name="$(basename "$path")"
    if [ -f "$DEST/$name" ]; then
        continue   # 이미 받은 것은 건너뛴다 (덤프는 불변 파일이다)
    fi
    echo "받는 중: $name"
    scp -q -i "$KEY" "$HOST:$path" "$DEST/$name"

    # 받은 파일이 온전한지 여기서 확인한다. 전송 중 잘린 백업은 백업이 아니다.
    if ! gzip -t "$DEST/$name" 2>/dev/null; then
        echo "FAIL: gzip 무결성 검사 실패 — $name (삭제함)"; rm -f "$DEST/$name"; exit 1
    fi
    if ! gunzip -c "$DEST/$name" | tail -5 | grep -q "Dump completed"; then
        echo "FAIL: 'Dump completed' 마커 없음 — $name (삭제함)"; rm -f "$DEST/$name"; exit 1
    fi
    NEW=$((NEW+1))
done <<< "$REMOTE"

echo
echo "새로 받은 파일: ${NEW}개"
echo "보관 위치: $DEST ($(ls -1 "$DEST"/*.sql.gz 2>/dev/null | wc -l | tr -d ' ')개)"
echo
echo "※ 이 디렉터리는 리포 바깥에 두세요. 덤프에는 실제 데이터가 들어 있습니다."
