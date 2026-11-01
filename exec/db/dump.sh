#!/bin/sh
# 운영 DB 덤프를 뜬다.
#
#   배포 서버(j15a403) 에서 :  sh dump.sh
#   로컬 Docker 에서        :  SUDO= sh dump.sh
#
# 비밀번호를 밖에서 넘길 필요가 없다. MySQL 컨테이너가 MYSQL_ROOT_PASSWORD ·
# MYSQL_DATABASE 를 이미 환경변수로 들고 있으므로 컨테이너 안에서 그것만 쓴다.
# 값이 셸 히스토리나 프로세스 목록에 남지 않는다.
#
# --single-transaction  InnoDB 를 잠그지 않고 일관된 스냅샷을 뜬다 (서비스 중에도 안전)
# --routines --triggers --events  프로시저 · 트리거 · 이벤트까지 포함
# --set-gtid-purged=OFF GTID 가 켜진 서버의 덤프를 GTID 없는 서버에 넣을 수 있게 한다
#
# flyway_schema_history 테이블도 함께 들어간다. 빼면 복원 후 백엔드가 기동할 때
# Flyway 가 마이그레이션을 처음부터 다시 돌리려 해서 "table already exists" 로 죽는다.

set -e

CONTAINER="${CONTAINER:-ai-block-clock-mysql}"
OUT="${OUT:-$(dirname "$0")/ai_block_clock_dump.sql}"
SUDO="${SUDO-sudo -n}"   # docker 그룹에 속해 있으면 SUDO= 로 비운다

$SUDO docker exec "$CONTAINER" sh -c '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -u root \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 \
    "$MYSQL_DATABASE"
' > "$OUT"

echo "저장: $OUT ($(wc -c < "$OUT") bytes, 테이블 $(grep -c '^CREATE TABLE' "$OUT")개)"
