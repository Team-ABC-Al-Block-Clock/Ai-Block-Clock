# 백엔드 테스트 규칙

본 문서는 테스트를 단위와 통합으로 분류하는 기준을 정의한다. 코드 리뷰의 판단 기준이 된다. 파이프라인 구성은 [cicd.md](../../../docs/cicd.md) 를 따른다.

- MR 에서 도는 테스트를 빠르게 유지해 리뷰 회전을 늦추지 않는다
- 분류 기준을 이름이나 위치가 아니라 **의존 대상**으로 고정해 판단이 갈리지 않게 한다
- `master` 에 들어가기 전에 통합 테스트를 통과하도록 게이트를 고정한다

---

## 1. 두 갈래

테스트는 Gradle 태스크 두 개로 나뉜다.

| 태스크 | 대상 | 실행 시점 | 목표 시간 |
| --- | --- | --- | --- |
| `test` | `@Tag("integration")` 이 **없는** 것 | MR·`feat/*`·`develop-*`·`master` 전부 | 수십 초 |
| `integrationTest` | `@Tag("integration")` 이 **있는** 것 | `master` 로 들어가는 MR, `master` push (그 외 MR·`develop-BE` 는 수동) | 제한 없음 |

```bash
./gradlew test              # 단위
./gradlew integrationTest   # 통합
./gradlew build             # 컴파일 + 단위 + jar (integrationTest 는 포함되지 않는다)
```

`build` 에 `integrationTest` 를 묶지 않는다. 묶으면 MR 마다 통합 테스트가 돌아 분리한 의미가 없어진다.

---

## 2. 분류 기준

### 2.1 하나라도 걸리면 통합이다

- 스프링 컨텍스트를 띄운다 (`@SpringBootTest`, `@DataJpaTest`, `@WebMvcTest` 등 슬라이스 테스트 포함)
- DB 에 접근한다 (H2 인메모리도 포함)
- 네트워크를 탄다 (외부 API, 컨테이너, 메시지 브로커)
- Flyway 마이그레이션이 돌아야 한다

### 2.2 그 밖은 단위다

- 객체를 `new` 로 만들어 메서드를 호출한다
- 협력 객체는 스텁·페이크·목으로 대체한다
- JVM 밖으로 나가지 않는다

### 2.3 판단이 애매할 때

**"이 테스트가 CI 러너에서 아무 것도 설치되지 않은 상태로 도는가"** 를 묻는다. 무언가 떠 있어야 한다면 통합이다.

```java
// ✅ 단위 — 컨텍스트 없이 객체만 조립한다
class MemberValidatorTest {
    private final MemberValidator sut = new MemberValidator(new FakeMemberReader());

    @Test
    void 중복된_이메일이면_예외를_던진다() { ... }
}

// ✅ 통합 — 컨텍스트를 띄운다
@Tag("integration")
@SpringBootTest
class MemberRegisterIntegrationTest { ... }

// ✅ 통합 — 슬라이스 테스트도 컨텍스트를 띄운다
@Tag("integration")
@DataJpaTest
class MemberRepositoryTest { ... }

// ❌ 태그 누락 — @DataJpaTest 인데 단위로 분류돼 MR 마다 돈다
@DataJpaTest
class MemberRepositoryTest { ... }
```

---

## 3. `@Tag("integration")` 을 붙인다

```java
import org.junit.jupiter.api.Tag;

@Tag("integration")
@SpringBootTest
class AiBlockClockApplicationTests {
```

문자열 `"integration"` 은 `build.gradle` 의 `includeTags` / `excludeTags` 와 정확히 일치해야 한다. 오타가 나면 **에러 없이 단위 테스트로 분류된다.**

### 3.1 왜 태그인가

소스셋(`src/integrationTest/java`)을 나누는 방법도 있으나 태그를 쓴다.

| | 태그 | 소스셋 분리 |
| --- | --- | --- |
| 의존성 선언 | `test` 것을 그대로 쓴다 | 모듈마다 다시 선언해야 한다 |
| 분류 변경 | 애노테이션 한 줄 | 파일을 옮겨야 한다 |
| 강제성 | 태그를 안 붙이면 단위로 분류된다 | 위치가 곧 분류다 |

멀티모듈에서 소스셋을 나누면 leaf 모듈마다 테스트 의존성을 중복 선언해야 한다. 그 비용이 태그를 붙이는 규율보다 크다고 판단했다.

### 3.2 태그 누락은 조용히 실패한다

이 방식의 유일한 약점이다. 태그를 빠뜨리면 컨텍스트를 띄우는 테스트가 단위 테스트에 섞여 MR 파이프라인이 느려진다. **에러가 나지 않으므로 리뷰에서 잡아야 한다.**

리뷰 체크리스트:

- 새로 추가된 테스트 중 `@SpringBootTest` · `@DataJpaTest` · `@WebMvcTest` 가 붙은 것에 `@Tag("integration")` 이 있는가
- `./gradlew test` 실행 시간이 눈에 띄게 늘지 않았는가

---

## 4. 파이프라인에서의 흐름

```
feat/* push            : 단위만              (통합 잡은 생성되지 않는다)
feat/* → develop-BE MR : 단위 + 통합(수동)
develop-BE push        : 단위 + 통합(수동)
develop-BE → master MR : 단위 + 통합         ← 게이트
master push            : 단위 + 통합         (안전망)
```

통합 테스트는 `master` **push** 가 아니라 **`master` 로 들어가는 MR** 에서 돈다. push 에서 돌리면 실패해도 이미 머지된 뒤라 `master` 가 깨진 채 남는다. MR 에서 돌려야 실패가 머지 자체를 막는다.

`master` 외의 MR 과 `develop-BE` push 에서는 `backend:test:integration` 이 **수동 실행 잡**으로 생성된다. 파이프라인 화면에서 직접 눌러 확인할 수 있고, 누르지 않아도 파이프라인은 통과한다.

**`feat/*` push 에서는 통합 테스트 잡이 아예 생성되지 않는다.** 수동으로도 돌릴 수 없다. `feat/*` 에서 통합 테스트를 확인하려면 MR 을 열어야 한다.

---

## 5. 로컬에서 돌리기

### 5.1 테스트

```bash
./gradlew test                                   # 단위
./gradlew integrationTest                        # 통합
./gradlew :core:core-api:integrationTest         # 특정 모듈만
```

단위 테스트는 사전 준비가 없어야 한다. DB 를 띄워야 돌아가는 테스트를 단위에 두면 안 된다.

### 5.2 애플리케이션

`compose.yaml` 의 `local` target 은 컨테이너 안에서 소스를 컴파일한다. 사전 준비 없이 뜬다.

```bash
docker compose up -d --build
```

프로파일 기준은 다음과 같다.

| 프로파일 | DB | 쓰는 곳 |
| --- | --- | --- |
| `local` (기본) | H2 인메모리 | 로컬 실행, `@SpringBootTest` 기본값 |
| `dev` | 환경 변수로 주입 | `compose.yaml` |
| `prod` | 환경 변수로 주입 | `compose.prod.yaml` — 배포 서버 |

자격 증명은 저장소에 두지 않는다. 배포용 값은 GitLab CI/CD Variables 로 관리한다 ([cicd.md 2-4](../../../docs/cicd.md)).

---

## 6. 요약

| 물음 | 답 |
| --- | --- |
| 컨텍스트를 띄우는가 | 통합 → `@Tag("integration")` |
| DB·네트워크를 타는가 | 통합 → `@Tag("integration")` |
| 객체만 조립하는가 | 단위 → 태그 없음 |
| `build` 에 통합을 묶어도 되는가 | 안 된다. MR 이 느려진다 |
| 통합 테스트 게이트는 어디인가 | `master` 로 들어가는 MR |
