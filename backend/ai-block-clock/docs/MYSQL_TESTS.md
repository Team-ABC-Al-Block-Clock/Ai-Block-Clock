# 실제 MySQL 테스트

## 실행

Docker Desktop의 Linux 엔진을 실행하고 `docker version`에서 Client와 Server를 확인한다.
백엔드 폴더에서 다음 명령을 실행한다.

```powershell
.\gradlew.bat containerTest --console=plain --rerun-tasks
```

첫 실행에는 MySQL 8.4 · RabbitMQ 4 이미지 다운로드가 필요하다. Docker를 사용할 수 없으면 실패하며,
컨테이너 검증을 건너뛴 결과를 통과로 취급하지 않는다. `containerTest` 는 MySQL 테스트와 RabbitMQ 테스트를 함께 돌린다.

## 테스트 분리와 DB 보호

- `test`: integration 및 container 태그 제외.
- `integrationTest`: integration 태그 선택, container 태그 제외.
- `containerTest`: container 태그만 선택. build에는 포함하지 않는다.
- `PaymentMySqlTest` · `NodeMySqlTest`는 테스트 공통 애노테이션이며 container 태그와 Spring 설정을 제공한다.
- MySQL 컨테이너 설정은 `testsupport.MySqlTestConfiguration` 하나뿐이다. 도메인마다 두면 컨텍스트가 갈려
  MySQL 이 그 수만큼 뜬다.
- RabbitMQ 컨테이너 설정은 `core-api` 의 `testsupport.RabbitTestConfiguration` 과 `:client:ai` 의
  `RabbitTestConfiguration` 이다. 메모리 경보로 발행을 막는 테스트(`RunQueueConfirmTimeoutContainerTest`)만 제 브로커를 띄운다.
- Testcontainers가 새 MySQL을 생성한다. 서비스 DB 주소나 기존 Compose 볼륨은 사용하지 않는다.
- Spring 테스트는 ServiceConnection으로 H2 설정보다 테스트 MySQL 접속 정보를 우선 적용한다.
- 컨테이너는 Spring 컨텍스트 종료 시 정리된다. 마이그레이션 전용 컨테이너는 테스트 종료 시 정리된다.
- 기존 H2 테스트의 중복 생성 및 원장 경합 시나리오는 MySQL 테스트 클래스에서 상속해 재사용한다.
- 충전 수량 테스트는 외부 트랜잭션 없이 Service를 호출한다.

## 검증 범위

### 결제

- 실제 MySQL 8.4, REPEATABLE READ 및 InnoDB 환경 확인.
- 같은 거래 동시 생성의 동일 ID·한 행 저장 및 다른 사용자 거래 재사용 거부.
- 중복 삽입 롤백 후 재조회, 실제 DB 중복 오류의 도메인 오류 변환.
- 오래된 요청의 상태 덮어쓰기 거부 및 확정·실패의 동시 조건부 갱신.
- SYSTEM 계정 생성 경합과 여러 사용자·같은 사용자의 잔액 누락 방지, 원장 실패의 전체 롤백.
- 소수 6자리 요청·확정·원장·잔액의 커밋 후 일치.
- 변환 정책의 소수 7자리 거부와 DB에 직접 쓴 소수 7자리의 반올림 동작 구분.
- 결제 금액 칸의 정수부 용량·소수 자리·양수 CHECK 검증.
- Flyway validate와 Spring 시작 시 JPA ddl-auto=validate.

### 노드

- 삭제와 배치가 동시에 들어와도 지워진 노드를 가리키는 배치가 남지 않는다.
- 한 라운드만 돌리면 커넥션 풀 초기화 · JIT 때문에 두 스레드가 겹치지 않아 락을 지워도 통과한다.
  그래서 30 라운드를 반복한다.

### 실행 메시지 (RabbitMQ)

- 계약의 exchange · queue 를 계약의 타입 · 인자 그대로 선언하고, 런타임이 같은 인자로 다시 선언해도 받아들인다.
  숫자 인자를 문자열로 보낸 선언은 같은 큐로 받아들이지 않는다.
- SubmitRun · CancelRun 은 confirm 을 받은 뒤에만 돌아온다. nack · confirm 기한 초과는 `REQUEST_FAILED` 다.
- 큐에서 만료된 SubmitRun 은 사유 `expired`, 두 번 배달하고도 끝내지 못한 것은 `delivery_limit` 을 달고 `abc.runs.dead` 로 간다.
- 처리에 실패한 이벤트는 세 번 더 시도한 뒤, 읽을 수 없는 이벤트는 곧바로 DLQ 로 간다.

## GitLab

MR 파이프라인의 `backend:test:mysql`을 수동 실행한다. 선택 작업이므로 미실행 상태의
전체 파이프라인 통과는 MySQL 테스트 통과를 뜻하지 않는다. JUnit 결과는 containerTest 경로에 보관한다.

현재 CI는 deploy·shell 태그의 Linux shell 러너에서 JDK 컨테이너를 실행하는 구조다.
MySQL 작업은 Docker 소켓과 동일 절대 경로의 프로젝트 마운트를 사용한다.
러너에서 Docker 소켓 접근 및 테스트 컨테이너의 공개 포트 접근이 가능해야 한다.
Docker 소켓 접근은 호스트 제어 권한을 제공하므로 신뢰할 수 있는 작업만 실행한다.
이 설정 추가만으로 원격 러너 실행을 검증한 것은 아니다. 최초 수동 실행 결과를 별도로 확인한다.
