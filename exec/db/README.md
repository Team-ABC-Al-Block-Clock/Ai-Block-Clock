# 3. DB 덤프

| 파일 | 내용 |
| --- | --- |
| `ai_block_clock_dump.sql` | **운영 DB 덤프 최신본** (스키마 + 데이터). 2026-09-28 배포 서버에서 추출, 31개 테이블 · 196KB |
| `schema.sql` | 스키마만. `master` 의 Flyway 마이그레이션 V1~V7 을 이어 붙인 것 |
| `seed-agent-grade-demo.sql` | 에이전트 등급 시연용 시드 데이터 |
| `dump.sh` | 덤프 생성 스크립트 |

## DB 정보

운영 서버에서 확인한 값이다 (2026-09-28).

| 항목 | 값 |
| --- | --- |
| 엔진 | MySQL **8.4.11** (`mysql:8.4` 이미지) |
| 컨테이너 | `ai-block-clock-mysql` |
| 데이터베이스 | **`ai-block-clock`** (하이픈) |
| 문자셋 | `utf8mb4` |
| 애플리케이션 계정 | **`admin@%`** (`MYSQL_USER` / `MYSQL_PASSWORD`) |
| 관리 계정 | `root@%`, `root@localhost` (`MYSQL_ROOT_PASSWORD`) |
| 접속 URL | `jdbc:mysql://mysql:3306/ai-block-clock?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul` |
| 스키마 관리 | **Flyway** (`ddl-auto: validate` — JPA 가 스키마를 만들지 않는다) |
| 적용된 마이그레이션 | V1 ~ V7 (2026-09-26 적용) |
| 마이그레이션 위치 | `backend/ai-block-clock/storage/src/main/resources/db/migration/` |
| 규모 | 31개 테이블 (V1~V7 의 30개 + `flyway_schema_history`), 약 1MB |

> ⚠️ **DB 이름과 계정이 `compose.yaml` 기본값과 다르다.**
> compose 기본값은 `ai_block_clock` / `ai_block_clock` (밑줄)이고, 운영은
> `ai-block-clock` / `admin` (하이픈)이다. GitLab 변수 `MYSQL_DATABASE` · `MYSQL_USER` 가
> 그렇게 설정돼 있기 때문이다. **새 서버에 올릴 때 이 두 변수를 같이 옮기지 않으면
> 기본값으로 빈 DB 가 만들어지고, 덤프를 넣을 곳이 달라진다.**

## 덤프 뜨기

배포 서버(`j15a403`)에서 실행한다. 비밀번호를 넘길 필요가 없다 — MySQL 컨테이너가 이미
환경변수로 들고 있다.

```bash
sh exec/db/dump.sh
```

또는 손으로:

```bash
sudo docker exec ai-block-clock-mysql sh -c '
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -u root \
    --single-transaction --routines --triggers --events \
    --set-gtid-purged=OFF --default-character-set=utf8mb4 \
    "$MYSQL_DATABASE"
' > exec/db/ai_block_clock_dump.sql
```

> **`flyway_schema_history` 를 빼지 않는다.** 빼면 복원한 DB 로 백엔드를 띄울 때 Flyway 가
> 마이그레이션을 처음부터 다시 돌리려 해서 `table already exists` 로 기동이 실패한다.

## 복원

### 새 서버에 데이터까지 그대로

```bash
# 1) MySQL 컨테이너만 먼저 띄운다 (MYSQL_DATABASE=ai-block-clock 로 맞춘다)
cd backend/ai-block-clock
export MYSQL_DATABASE=ai-block-clock MYSQL_USER=admin MYSQL_PASSWORD=... MYSQL_ROOT_PASSWORD=...
docker compose up -d mysql

# 2) 덤프를 넣는다 (DB 이름에 하이픈이 있어 따옴표로 감싼다)
docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" ai-block-clock-mysql \
  mysql -u root --default-character-set=utf8mb4 'ai-block-clock' \
  < exec/db/ai_block_clock_dump.sql

# 3) 백엔드를 띄운다 — Flyway 는 이미 적용된 버전을 건너뛴다
docker compose up -d backend
```

### 빈 DB 로 시작 (덤프 불필요)

**덤프를 넣지 않아도 된다.** 백엔드가 처음 뜰 때 Flyway 가 마이그레이션을 전부 돌려
스키마를 만든다. `schema.sql` 은 그 결과를 미리 보기 위한 참고용이다.

시연용 등급 데이터가 필요하면 시드만 넣는다.

```bash
docker exec -i -e MYSQL_PWD="$MYSQL_PASSWORD" ai-block-clock-mysql \
  mysql -u admin 'ai-block-clock' < exec/db/seed-agent-grade-demo.sql
```

## 테이블 구성 (V1~V7)

| 마이그레이션 | 테이블 |
| --- | --- |
| `V1__create_account` | `app_user` · `wallet_auth_message` · `identity_verification` |
| `V2__create_spring_session` | `SPRING_SESSION` · `SPRING_SESSION_ATTRIBUTES` |
| `V3__create_payment` | `ledger_account` · `ledger_transaction` · `ledger_entry` · `charge_request` · `withdrawal` |
| `V4__create_node` | `node_definition` · `node_ref_file` · `node_test_run` |
| `V5__create_workflow` | `graph_node` · `graph_edge` · `workflow` |
| `V6__create_agent` | `agent` · `agent_version` · `agent_version_node` · `agent_version_node_ref_file` · `agent_grade_metric` · `agent_grade_history` · `agent_tier_change` · `agent_provenance` · `agent_favorite` |
| `V7__create_execution` | `execution` · `execution_result` · `execution_charge_rate` · `execution_failure` · `execution_settlement` |

돈은 전부 `ledger_*` 복식부기 테이블이 센다. 충전 · 선차감 · 정산 · 환불이 모두 여기에 기입된다.

## 주의

- 덤프에는 **사용자 지갑 주소 · 본인 확인 정보 · 로그인 세션(`SPRING_SESSION`)** 이 들어 있다.
  외부에 공유하기 전에 확인한다.
- **개인키는 DB 에 저장하지 않는다.** 환경 변수로만 주입된다
  (→ [02-외부서비스 § 6](../02-외부서비스.md#6-지갑--개인키-정리)).
- 업로드 파일은 DB 가 아니라 파일 시스템(`FILE_STORE_ROOT=/var/lib/ai-block-clock/files`,
  compose 볼륨 `ai-block-clock_file-store`)에 있다. **덤프만으로는 파일이 복원되지 않는다.**
  필요하면 볼륨도 함께 백업한다.

```bash
sudo docker run --rm -v ai-block-clock_file-store:/data -v "$PWD":/backup alpine \
  tar czf /backup/file-store.tar.gz -C /data .
```
