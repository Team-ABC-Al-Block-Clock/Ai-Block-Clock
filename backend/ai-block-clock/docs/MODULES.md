# 백엔드 모듈 구조

본 문서는 Gradle 멀티모듈 구성과 **모듈 간 의존 관계**를 정의한다.

- 코드를 어느 모듈에 둘지 판단 기준을 통일한다
- 의존 방향을 고정해 모듈 간 순환 참조를 막는다
- 설정과 의존성을 모듈 단위로 가두어 책임 경계를 유지한다
- 모듈을 추가·분리할 때 따라야 할 절차를 남긴다

---

## 1. 모듈 구성

### 1.1 전체 트리

```
backend/ai-block-clock/
├── settings.gradle          모듈 등록
├── build.gradle             leaf 모듈 공통 설정
├── core/                    wrapper
│   ├── core-api/            실행 모듈 (진입점) — application.yaml
│   └── core-enum/           공통 enum
├── storage/                 영속성 — db.yml, db/migration
└── support/                 wrapper
    ├── logging/             로깅 — logback-spring.xml
    └── monitoring/          모니터링 — monitoring.yml
```

각 모듈은 자기 관심사의 설정 파일을 직접 소유한다 (4장).

### 1.2 모듈 종류

모듈은 두 종류로 나뉜다.

| 종류 | 설명 | 해당 모듈 |
| --- | --- | --- |
| wrapper | 하위 모듈만 묶는 상자. 소스도 `build.gradle` 도 갖지 않는다 | `:core`, `:support` |
| leaf | 실제 소스·리소스를 갖는 모듈. 빌드 산출물이 나온다 | 그 외 전부 |

루트 `build.gradle` 의 공통 설정은 leaf 모듈에만 적용된다. wrapper 는 Java 플러그인조차 적용되지 않으므로 빈 jar 이 생기지 않는다.

```groovy
// 하위 모듈을 가진 프로젝트(= wrapper)는 설정 대상에서 제외된다
configure(subprojects.findAll { it.subprojects.isEmpty() }) { ... }
```

### 1.3 모듈 목록

| 모듈 | 종류 | 역할 | 산출물 |
| --- | --- | --- | --- |
| `:core` | wrapper | `core-api`, `core-enum` 을 묶는다 | 없음 |
| `:core:core-api` | leaf | 애플리케이션 진입점, 웹 계층 | `bootJar` (실행 가능) |
| `:core:core-enum` | leaf | 모듈 공통 enum | jar |
| `:storage` | leaf | 영속성 (JPA, 마이그레이션) | jar |
| `:support` | wrapper | `logging`, `monitoring` 을 묶는다 | 없음 |
| `:support:logging` | leaf | 프로파일별 logback 설정 | jar (리소스만) |
| `:support:monitoring` | leaf | 액추에이터·메트릭 설정 | jar |

그룹·버전은 전 모듈 공통으로 `com.aiblockclock:0.0.1-SNAPSHOT` 이다.

---

## 2. 모듈 간 의존 관계

### 2.1 의존 그래프

```mermaid
graph TD
    api[":core:core-api"]
    enums[":core:core-enum"]
    storage[":storage"]
    logging[":support:logging"]
    monitoring[":support:monitoring"]
    jpa["spring-boot-starter-data-jpa"]

    api -->|implementation| storage
    api -->|implementation| enums
    api -.->|runtimeOnly| logging
    api -.->|runtimeOnly| monitoring
    storage -->|implementation| enums
    storage ==>|api| jpa
```

의존은 **항상 한 방향**이다. `core-api` 는 모든 모듈을 알고, `core-enum` 은 아무도 모른다.

### 2.2 의존성 선언

| 모듈 | 의존 대상 | configuration | 이유 |
| --- | --- | --- | --- |
| `:core:core-api` | `:storage` | `implementation` | 영속성 계층을 호출한다 |
| `:core:core-api` | `spring-tx` | `implementation` | Service가 유스케이스 트랜잭션 경계를 잡고 implementation은 이에 참여한다 |
| `:core:core-api` | `:core:core-enum` | `implementation` | 공통 enum 을 참조한다 |
| `:core:core-api` | `:support:logging` | `runtimeOnly` | 설정 리소스만 클래스패스에 올린다 |
| `:core:core-api` | `:support:monitoring` | `runtimeOnly` | 설정 리소스만 클래스패스에 올린다 |
| `:storage` | `:core:core-enum` | `implementation` | 엔티티에서 공통 enum 을 쓴다 |
| `:storage` | `spring-boot-starter-data-jpa` | `api` | implementation 계층이 `JpaRepository` 와 `Pageable` 을 직접 다룬다 (3.3 참고) |
| `:core:core-enum` | 없음 | - | 외부 라이브러리도 추가하지 않는다 |
| `:support:logging` | 없음 | - | 리소스만 갖는다 |
| `:support:monitoring` | 없음 (프로젝트 기준) | - | 액추에이터 라이브러리만 갖는다 |

`support:*` 를 `runtimeOnly` 로 붙이는 이유는, 이 모듈들이 **설정만 제공**하고 애플리케이션 코드가 이들의 클래스를 직접 참조할 일이 없기 때문이다. `implementation` 으로 붙이면 컴파일 의존이 생겨 나중에 지우기 어려워진다.

### 2.3 의존 규칙

지켜야 할 규칙은 다음과 같다.

1. **역방향 금지** — `storage` 나 `support:*` 가 `core-api` 를 의존해서는 안 된다
2. **형제 모듈 금지** — `storage` 와 `support:*` 는 서로 의존하지 않는다
3. **`core-enum` 은 순수하게 유지** — 어떤 모듈도, 어떤 외부 라이브러리도 의존하지 않는다. 다른 모듈이 공유해야 하는 enum 만 둔다
4. **wrapper 에 직접 의존 금지** — `project(':support')` 가 아니라 `project(':support:logging')` 처럼 leaf 를 지정한다
5. **Spring Boot 플러그인은 `core-api` 에만** — 실행 가능한 산출물은 하나뿐이다. 나머지 모듈은 일반 라이브러리 jar 로 남는다
6. **설정도 모듈에 가둔다** — 특정 모듈에서만 쓰는 설정을 `core-api` 의 `application.yaml` 에 적지 않는다 (4장)

---

## 3. 모듈별 상세

### 3.1 `:core:core-api`

애플리케이션 진입점이자 웹 계층을 담당한다. 유일하게 실행 가능한 모듈이다.

```
core/core-api/src/main/
├── java/com/aiblockclock/
│   ├── AiBlockClockApplication.java   진입점
│   └── api/                           컨트롤러, 요청·응답 DTO
└── resources/application.yaml
```

- 진입점 클래스는 베이스 패키지 `com.aiblockclock` 에 둔다. 컴포넌트 스캔이 다른 모듈의 `com.aiblockclock.*` 패키지까지 닿아야 하기 때문이다
- `core-api` 자체 코드는 `com.aiblockclock.api` 아래에 둔다
- 의존성: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`
- `application.yaml` 에는 **애플리케이션 조립**(이름, 기본 프로파일, `config.import`)과 `core-api` 자신의 설정(`server.*`)만 둔다. 다른 모듈의 설정을 여기에 적지 않는다
- `bootJar` 활성화, 일반 `jar` 비활성화

### 3.2 `:core:core-enum`

여러 모듈이 공유하는 enum 만 둔다. 패키지는 `com.aiblockclock.enums` 다. (`enum` 은 예약어라 패키지명으로 쓸 수 없다.)

- 외부 라이브러리를 추가하지 않는다. 여기에 의존성이 생기면 그 의존성이 전 모듈로 퍼진다
- 한 모듈에서만 쓰는 enum 은 해당 모듈에 둔다

### 3.3 `:storage`

영속성 계층이다. 패키지는 `com.aiblockclock.storage` 다. **DB 설정을 이 모듈이 소유한다.**

```
storage/src/main/resources/
├── db.yml            공통 (JPA, Flyway)
├── db-local.yml
├── db-dev.yml
├── db-prod.yml
└── db/migration/     Flyway 마이그레이션 스크립트
```

- JPA 엔티티, 리포지토리, 마이그레이션 스크립트를 둔다
- **JPA 를 `api` 로 노출한다.** implementation 계층(`Reader`/`Writer`)이 `JpaRepository` 를 직접 주입받고
  `Pageable` 을 직접 조립한다. 엔티티도 이미 `storage` 밖으로 나가고 있으므로, Spring Data 타입만 가리는
  래퍼 클래스를 두는 것은 값을 못 한다 — 감추려면 엔티티까지 감춰야 하고, 그것은 `domain` 모듈이 생길 때 한다
  (ARCHITECTURE.md 5장 미해결 항목)
- 의존성: `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-mysql`, 드라이버(`mysql-connector-j`, `h2`)
- 시각 컬럼은 **`Instant`** 로 매핑한다. 생성·수정 시각은 `audit/BaseCreatedAtEntity`,
  `audit/BaseTimeEntity` 를 상속해 JPA Auditing 이 채우고, 엔티티가 직접 필드를 갖지 않는다.
  `LocalDateTime` 은 오프셋이 없어 API 응답과 DB 사이에서 타임존이 유실되므로 쓰지 않는다
- `ddl-auto` 는 전 프로파일 `validate` 다. 스키마 변경은 마이그레이션 스크립트로만 한다
- `open-in-view` 는 끈다. 영속성 컨텍스트가 웹 계층까지 열려 있으면 모듈 경계가 무너진다

| 프로파일 | 데이터소스 | 비고 |
| --- | --- | --- |
| `local` | 인메모리 H2 (MySQL 모드) | 별도 DB 설치 불필요, `show-sql` 활성 |
| `dev` | `${DB_URL}` 등 환경 변수 | 풀 크기 기본 10 |
| `prod` | `${DB_URL}` 등 환경 변수 | 풀 크기 기본 20, 기본값 없음 |

`dev` · `prod` 의 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로 주입한다. 기본값을 두지 않아 값이 없으면 기동 단계에서 실패한다. 자격 증명은 저장소에 두지 않는다.

### 3.4 `:support:logging`

프로파일별 logback 설정만 갖는다. 자바 소스가 없다.

```
support/logging/src/main/resources/
├── logback-spring.xml           공통 + 프로파일 분기
└── logback/
    ├── logback-local.xml
    ├── logback-dev.xml
    └── logback-prod.xml
```

세 프로파일 모두 **stdout 으로만** 출력한다. 로그 수집은 배포 환경의 수집기가 담당하므로 파일 appender 를 쓰지 않는다.

| 프로파일 | 포맷 | 레벨 |
| --- | --- | --- |
| `local` | 평문 (색상) | `com.aiblockclock`=DEBUG, root=INFO |
| `dev` | 평문 | `com.aiblockclock`=DEBUG, root=INFO |
| `prod` | 구조화 JSON (ECS) | INFO |

`prod` 의 출력 형식은 `logback-prod.xml` 의 `CONSOLE_LOG_STRUCTURED_FORMAT` 값으로 바꾼다 (`ecs` / `logstash` / `gelf`).

### 3.5 `:support:monitoring`

액추에이터·메트릭 설정을 갖는다.

```
support/monitoring/src/main/resources/
├── monitoring.yml           공통
├── monitoring-local.yml
├── monitoring-dev.yml
└── monitoring-prod.yml
```

- 의존성: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`
- 노출 엔드포인트는 프로파일별로 다르다. `local` 은 전체(`*`), `prod` 는 `health`, `info`, `prometheus` 만 연다
- 이 파일들은 자동으로 읽히지 않는다. `core-api` 의 `application.yaml` 이 명시적으로 가져온다

```yaml
spring:
  config:
    import:
      - classpath:monitoring.yml   # monitoring-{profile}.yml 은 함께 로드된다
```

---

## 4. 설정 소유 규칙

### 4.1 원칙

**설정은 그 설정을 필요로 하는 모듈이 소유한다.** DB 설정은 `storage` 가, 모니터링 설정은 `support:monitoring` 이 갖는다. `core-api` 는 이들을 가져와 조립할 뿐, 내용을 알지 않는다.

이렇게 두는 이유는 다음과 같다.

- 모듈을 떼어내거나 교체할 때 설정이 함께 따라간다. `core-api` 를 뒤질 필요가 없다
- 설정을 바꿀 때 건드릴 파일이 한 곳으로 정해진다
- `application.yaml` 이 전 모듈의 설정을 모아둔 잡동사니가 되지 않는다

### 4.2 파일 규칙

| 규칙 | 내용 |
| --- | --- |
| 파일명 | 모듈의 관심사를 쓴다. `db.yml`, `monitoring.yml` |
| 위치 | 소유 모듈의 `src/main/resources` 바로 아래 |
| 프로파일 | `{이름}-{profile}.yml` 로 덮어쓴다. 공통값은 `{이름}.yml` 에 둔다 |
| 로드 | `core-api` 의 `application.yaml` 에 `spring.config.import` 를 추가한다 |

```yaml
# core/core-api/src/main/resources/application.yaml
spring:
  config:
    import:
      - classpath:db.yml           # :storage
      - classpath:monitoring.yml   # :support:monitoring
```

`spring.config.import` 로 가져온 파일은 프로파일 변형(`db-prod.yml`)도 함께 로드된다. 별도로 import 하지 않는다.

### 4.3 현재 설정 파일

| 파일 | 소유 모듈 | 로드 방식 |
| --- | --- | --- |
| `application.yaml` | `:core:core-api` | 기본 로드 |
| `db.yml`, `db-{profile}.yml` | `:storage` | `spring.config.import` |
| `monitoring.yml`, `monitoring-{profile}.yml` | `:support:monitoring` | `spring.config.import` |
| `logback-spring.xml`, `logback/*.xml` | `:support:logging` | Spring Boot 가 클래스패스에서 탐색 |

### 4.4 프로파일

프로파일은 `local`, `dev`, `prod` 를 쓴다. 지정하지 않으면 `local` 로 동작한다 (`spring.profiles.default`). logback 이 프로파일별로 분기하므로, 기본값이 없으면 appender 가 하나도 잡히지 않아 로그가 사라진다.

환경 변수로 주입하는 값은 다음과 같다.

| 변수 | 사용 프로파일 | 기본값 |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `dev`, `prod` | 없음 (누락 시 기동 실패) |
| `DB_POOL_SIZE` | `dev`, `prod` | 10 / 20 |
| `SERVER_PORT` | 전체 | 8080 |

---

## 5. 모듈 추가·분리

### 5.1 leaf 모듈 추가

1. `settings.gradle` 에 `include` 를 추가한다
2. 디렉터리와 `build.gradle` 을 만든다. 공통 설정(Java 25, Boot BOM, Lombok, JUnit)은 루트에서 상속되므로 모듈 고유 의존성만 적는다
3. 이 모듈을 쓰는 쪽에 `implementation project(':...')` 를 추가한다. 2.3 의 의존 규칙을 위반하지 않는지 확인한다
4. 설정이 필요하면 4.2 규칙대로 모듈 안에 `{이름}.yml` 을 만들고, `application.yaml` 에 `import` 한 줄을 추가한다. 설정 내용을 `application.yaml` 에 직접 쓰지 않는다

### 5.2 leaf 모듈을 wrapper 로 승격

`storage` 를 `storage/db-core` 로 쪼개는 경우처럼 하위 모듈이 생기면,

1. 하위 모듈 디렉터리를 만들고 기존 소스·리소스를 옮긴다
2. 상위 모듈의 `build.gradle` 과 `src` 를 **삭제**한다
3. `settings.gradle` 에 하위 모듈 `include` 를 추가한다
4. 이 모듈을 의존하던 쪽의 `project(':storage')` 를 `project(':storage:db-core')` 로 바꾼다

루트 `build.gradle` 은 손대지 않는다. 하위 모듈이 생기는 순간 자동으로 wrapper 로 취급된다.

### 5.3 빈 디렉터리

패키지 구조만 잡아둔 빈 디렉터리는 `.gitkeep` 으로 추적한다. Git 은 빈 디렉터리를 기록하지 않는다.

---

## 6. 빌드·실행

```bash
./gradlew build                    # 전체 빌드
./gradlew :core:core-api:bootRun   # 애플리케이션 실행 (local 프로파일)
./gradlew :storage:test            # 특정 모듈 테스트
```

실행 가능한 산출물은 `core/core-api/build/libs/core-api-0.0.1-SNAPSHOT.jar` 하나다.
