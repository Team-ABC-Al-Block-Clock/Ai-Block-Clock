# 백엔드 규칙

본 문서는 백엔드의 테스트 규칙과 코드 규칙을 정의한다. 코드 리뷰의 판단 기준이 된다.

| 장 | 내용 |
| --- | --- |
| 1 ~ 6 | **테스트** — 단위·통합 분류, 태그, 파이프라인, 운영 코드와의 경계 |
| 7 ~ 12 | **코드** — 예외, 검증 위치, 값 객체, Lombok, 패키지, 미결 사항 |

계층 구조는 [ARCHITECTURE.md](ARCHITECTURE.md), 네이밍은 [NAMING.md](NAMING.md),
모듈 구성은 [MODULES.md](MODULES.md), 파이프라인은 [cicd.md](../../../docs/cicd.md) 를 따른다.
도메인 용어는 저장소 루트의 `GLOSSARY.md` 를 따른다.

---

## 1 ~ 6. 테스트

테스트를 단위와 통합으로 분류하는 기준을 정의한다.

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

## 6. 테스트 때문에 운영 코드를 바꾸지 않는다

테스트가 필요하다는 이유로 운영 코드에 무언가를 더하거나 열지 않는다. 그렇게 들어온 것은
아무도 쓰지 않는데 지울 수도 없다 — 지우면 테스트가 깨지니 근거가 있는 것처럼 보인다.

**하지 않는다**

- 테스트에서 값을 보려고 `toString` · `equals` · `hashCode` 를 붙인다
- 테스트에서만 부르는 조회 메서드를 도메인에 남긴다
- 테스트에서 조립하려고 `@Setter` · `@Builder` · 기본 생성자를 연다
- `public` 이어야 할 이유가 테스트뿐인데 `private` 을 푼다

**한다 — 협력자는 목으로 뜬다**

테스트를 위해 운영 코드에 인터페이스를 만들지 않는다. 구현이 하나뿐인 인터페이스는 설계가
요구한 것이 아니라 테스트가 요구한 것이고, 그것은 이 장이 금지하는 바로 그 일이다.
협력자가 클래스면 Mockito 로 목을 뜬다.

```java
NodeDefinitionReader nodeReader = mock(NodeDefinitionReader.class);
given(nodeReader.isOwnedBy(any(), any())).willReturn(false);
```

인터페이스가 정당한 경우는 **구현이 실제로 갈릴 때**다. `NodeSourceResolver` 는 `NODE` 와
`AGENT` 두 구현이 있고, `BaseCostCalculator` 는 지금 고정값을 내지만 AI 서버 구현으로 바뀐다.
그때는 인터페이스가 테스트 때문이 아니라 설계 때문에 있는 것이다.

`domain -> implementation` 은 허용된 방향이다(ARCHITECTURE 1.1). 정책이 Reader 를 직접 물어도
규칙을 깨지 않으므로, 물음 하나를 위해 인터페이스를 새로 만들지 않는다.

픽스처는 테스트 소스셋에 둔다 — `GraphFixtures` · `WorkflowFixtures` 가 그 예다.

**단정을 운영 코드에 맞추지 말고, 물음을 바꾼다**

값이 같은지 보려고 `equals` 를 붙이기 전에, 그 테스트가 실제로 무엇을 확인하려 했는지 본다.

```java
// 이것 때문에 WorkflowGraph 에 equals 를 붙였다
assertThat(version.getSnapshot()).isEqualTo(original).isNotEqualTo(edited);

// 확인하려던 것은 "원본과 분리됐는가" 였다. 그건 불변이면 참조 비교로 끝난다
assertThat(version.getSnapshot()).isSameAs(original);
```

`equals` 가 정말 필요해지면 그때 **운영 코드의 요구로** 들인다. 그리고 뜻이 갈리는 자리에서는
`equals` 가 아니라 이름 있는 메서드로 만든다 — `layout` 이 다르면 값으로는 다르고 실행
정의로는 같으므로, 물음은 `sameDefinitionAs` 여야 한다.

**리뷰에서 보는 것**

새로 생긴 `public` 멤버의 호출자가 테스트뿐이면, 그것이 운영 코드에 있어야 할 이유를 MR 본문에
적는다. 적을 수 없으면 지운다.

---

## 요약

| 물음 | 답 |
| --- | --- |
| 컨텍스트를 띄우는가 | 통합 → `@Tag("integration")` |
| DB·네트워크를 타는가 | 통합 → `@Tag("integration")` |
| 객체만 조립하는가 | 단위 → 태그 없음 |
| `build` 에 통합을 묶어도 되는가 | 안 된다. MR 이 느려진다 |
| 통합 테스트 게이트는 어디인가 | `master` 로 들어가는 MR |
| 테스트가 필요해서 운영 코드를 열어도 되는가 | 안 된다. 협력자는 목으로 뜬다 |
| 호출자가 테스트뿐인 `public` 멤버 | 근거를 MR 에 적거나 지운다 |

---

## 7 ~ 12. 코드

## 7. 예외

### 7.1 예외 클래스를 새로 만들지 않는다

`CoreException` 하나만 던진다. 상황마다 예외 클래스를 만들면 잡는 자리가 계속 늘고,
같은 상황에 대해 어떤 HTTP 상태와 코드로 나가는지가 클래스마다 흩어진다.

```java
// 이렇게 던진다
throw new CoreException(ErrorType.WORKFLOW_REVISION_CONFLICT,
        Map.of("expectedRevision", expected.value(), "currentRevision", revision.value()));

// 부가 정보가 없으면
throw new CoreException(ErrorType.WORKFLOW_GRAPH_CYCLE);
```

```
CoreException          던지는 유일한 예외. ErrorType 과 data 를 나른다
ErrorType              상황 하나 = 상수 하나. HTTP 상태·에러 코드·메시지·로그 레벨을 함께 갖는다
ErrorCode              API 명세서에 적힌 코드. 클라이언트가 분기 기준으로 삼는 값
ErrorMessage           실패 응답의 error 필드
ApiResult<T>           성공·실패 공통 응답 봉투
```

### 7.2 새 실패 상황이 생기면

1. Notion API 명세서에서 **에러 코드를 먼저 정한다** (`WORKFLOW_009` 같은).
2. `ErrorCode` 에 그 코드를 추가한다.
3. `ErrorType` 에 상수를 하나 추가한다 — HTTP 상태, 방금 만든 `ErrorCode`, 메시지, 로그 레벨.
4. 던지는 쪽에서는 `new CoreException(ErrorType.X, data)` 만 쓴다.

`ErrorType` 이름은 **무엇이 잘못됐는지**를 말한다(`WORKFLOW_REVISION_CONFLICT`).
`ErrorCode` 는 **클라이언트와 약속한 문자열**이다(`WORKFLOW_005`). 둘을 섞지 않는다.

**여러 `ErrorType` 이 같은 `ErrorCode` 를 가리켜도 된다.** 계약을 늘리지 않고도 상황을 정확히
나눌 수 있으므로, 사유가 다르면 메시지가 맞지 않는 상수를 재사용하지 말고 새로 만든다.

```java
// 셋 다 WORKFLOW_013 으로 나가지만, 사용자가 고쳐야 할 곳은 저마다 다르다
WORKFLOW_DUPLICATE_NODE_KEY     (BAD_REQUEST, ErrorCode.WORKFLOW_013, "노드 키가 중복되었습니다.", WARN)
WORKFLOW_DANGLING_EDGE          (BAD_REQUEST, ErrorCode.WORKFLOW_013, "연결선이 존재하지 않는 노드를 가리킵니다.", WARN)
WORKFLOW_NODE_REFERENCE_MISMATCH(BAD_REQUEST, ErrorCode.WORKFLOW_013, "노드 종류와 참조 정보가 맞지 않습니다.", WARN)
```

### 7.3 로그 레벨

4xx 는 `WARN`, 5xx 는 `ERROR`. 공통 예외 핸들러는 `api.support.ApiControllerAdvice` 다.
핸들러가 `errorType.getLogLevel()` 을 보고 기록하며, 던지는 자리에서는 로그를 남기지 않는다.

핸들러는 `LogLevel` 상수를 **전부** switch 로 받는다. 상수가 늘면 컴파일이 먼저 막히므로,
새 레벨을 쓰기로 하고 기록을 빠뜨리는 일이 생기지 않는다. `OFF` 는 남기지 않는다.

**스택 트레이스는 `ERROR`·`FATAL` 에만 붙인다.** 4xx 는 요청을 고치면 되는 상황이라
트레이스가 로그를 덮을 뿐이다. 어디서 났는지는 `data` 가 말한다(7.4).

### 7.4 data 에 무엇을 담는가

> **무엇이 잘못됐는지는 `ErrorType` 이 말한다. `data` 는 그것이 <u>어디서</u> 났는지만 가리킨다.**

`data` 에는 **값**만 담는다. 문장을 담지 않는다.

```java
// 안 된다 — 메시지가 두 개가 된다. ErrorType 이 이미 말하고 있다
new CoreException(ErrorType.WORKFLOW_DUPLICATE_NODE_KEY, "노드 키가 중복됩니다: " + node.key());

// 된다 — 어느 노드인지만 가리킨다
new CoreException(ErrorType.WORKFLOW_DUPLICATE_NODE_KEY, Map.of("nodeKey", node.key().value()));

// 된다 — 클라이언트가 그대로 화면에 쓸 수 있는 값
new CoreException(ErrorType.WORKFLOW_NODE_LIMIT_EXCEEDED, Map.of("limit", 20, "actual", 23));
new CoreException(ErrorType.WORKFLOW_REVISION_CONFLICT,
        Map.of("expectedRevision", 7L, "currentRevision", 8L));

// 된다 — 가리킬 것이 없으면 생략한다. 리소스 식별자는 이미 요청 경로에 있다
new CoreException(ErrorType.WORKFLOW_FORBIDDEN);
```

**문장을 담고 싶어졌다면 `ErrorType` 을 잘못 고른 것이다.** 메시지가 상황과 맞지 않는 상수를
재사용하고 `data` 로 덧칠하는 일이 실제로 있었다 — 빈 그래프로 에이전트를 만들려는 요청에
"검증이 완료되지 않은 워크플로우입니다" 라는 메시지의 상수를 쓰고, `data` 에 진짜 사유를 적었다.
이럴 때는 `ErrorType` 을 새로 만든다. 7.2 를 보라.

내부 구조·프롬프트·타인 소유 리소스의 내용은 담지 않는다. `data` 는 그대로 응답으로 나간다.

---

## 8. 검증과 변경을 어디서 하는가

### 8.1 검증

> **도메인에 남기는 검증은 컨트롤러가 알 수 없는 것뿐이다.**

| 종류 | 어디서 | 예 |
| --- | --- | --- |
| 형식·길이·필수값 | **요청 DTO** 의 Bean Validation | 이름 100자, 설명 500자, `@NotNull`, 태그 개수 |
| 열거값 허용 목록 | **요청 DTO** | `visibility` 가 `PUBLIC`/`PRIVATE` 인지 |
| 객체들 사이의 관계 | **도메인** | 연결선이 실재하는 노드를 가리키는가, 노드 키가 겹치지 않는가 |
| 타입이 아는 규칙 | **도메인** | `AGENT` 노드는 버전까지 고정해야 한다 |
| 저장된 상태와의 대조 | **도메인** | 소유권, 리비전 충돌, 이미 공개된 버전인가 |
| 서비스 정책 | **도메인 정책 객체** | 최대 노드 수 |
| 실행 의미 | **AI Runtime** | 순환, 연결 깊이, 스키마 호환성, 실행 순서 |

컨트롤러가 이미 거른 것을 도메인에서 한 번 더 세지 않는다. 규칙이 두 곳에 흩어지면
한쪽만 고쳐지고 다른 쪽이 남는다.

반대로 **그래프 전체를 봐야 알 수 있는 것**은 컨트롤러가 할 수 없다. 요청 하나에는
"이 노드 키가 다른 노드와 겹치는가" 를 판단할 정보가 없다.

`Objects.requireNonNull` 은 쓰지 않는다. 널이면 어차피 그 자리에서 드러나고,
줄마다 붙으면 정작 중요한 규칙이 묻힌다.

---

### 8.2 변경 — 판정은 도메인이나 Validator 가, 쓰기는 Writer 가 한다

애그리거트는 자기 상태를 바꾸지 않는다. 규칙을 검사하고 다음 값을 돌려주면, 그 결과를 행에
반영하는 것은 Writer 가 한다. 규칙을 도메인에 두는 이유는 저장이 안 중요해서가 아니라
**규칙을 DB 없이 검증할 수 있어야 하기 때문**이다.

판정이 어디에 사는지는 **무엇을 읽어야 하는가**로 가른다.

| 판정이 보는 것 | 어디 | 예 |
| --- | --- | --- |
| 도메인 구조를 순회하거나 여러 객체를 엮는다 | **`*Policy`** — 애그리거트를 받는다 | `AgentVersionPolicy.ensureVersionable` |
| 컬럼 값 비교로 끝난다 | **`*Validator`** — id 로 잠그고 엔티티를 본다 | `WorkflowValidator.ensureReplaceable` |

**응답을 만들려고 도메인 객체가 필요한 것은 근거로 치지 않는다.** 그것은 계층 규칙이지 판정의
요구가 아니다. 컬럼 두 칸을 보려고 애그리거트를 조립하면 자식 테이블까지 읽고 버리게 된다.

```java
// Service — 흐름만 남는다
GraphRevision revised = validator.ensureReplaceable(id, requester, expectedRevision);
WorkflowGraph graph = workflowGraphAssembler.assembleWithSourceLock(nodes, edges);
workflowNodeWriter.replace(id, graph.getNodes());
workflowEdgeWriter.replace(id, graph.getEdges());

return workflowWriter.revise(id, name, revised, graph);
```

Writer 는 테이블 하나씩 맡고 `requester` 를 받지 않는다. 잠금과 판정은 Validator 가 하고, 무엇을
어느 순서로 쓰는지는 Service 가 정한다. 삭제 순서(자식 → 부모)도 Service 에 드러나야 한다.

**규칙과 그 규칙이 지키는 변경을 떼어놓지 않는다.** 전제조건을 Service 에 적고 대입만 다른 곳에
두면, 두 번째 호출 경로가 생길 때 검사를 복붙하게 되고 한쪽만 고치는 사고가 난다.

### 8.3 읽고 쓰는 흐름은 한 트랜잭션에 둔다

Reader 와 Writer 를 이어 부르는 Service · Manager 에 `@Transactional` 이 없으면 영속성 컨텍스트가
갈린다. Writer 의 `findById` 가 1차 캐시를 타지 못해 SELECT 가 두 번 나가고, 그 사이에 남이 끼어든다.

같은 행을 고치는 경로끼리는 **같은 락**을 쓴다. 한쪽만 `readForUpdate` 를 잡으면 막히지 않는다 —
노드 삭제와 배치가 그랬다.

**락을 거는 메서드는 이름으로 말한다.** `assembleWithSourceLock` 은 배치되는 원본 행에
`for share` 를 걸고 트랜잭션이 끝날 때까지 쥔다. 그런 메서드에는
`@Transactional(propagation = MANDATORY)` 를 걸어, 트랜잭션 없이 불렸을 때 락이 조용히 풀리는
대신 그 자리에서 터지게 한다.

낙관적 락으로 가는 것도 방법이다. `payment` 는 `@Version` 과 유니크 제약으로 경합을 다루고
동시성 테스트로 고정한다. 무엇을 쓰든 **두 경로가 같은 전략을 써야 한다.**

---

## 9. 값 객체를 언제 만드는가

기준은 하나다. **자리를 바꿔 넣는 실수를 막아주는가.**

**감싼다**

- 식별자 — `WorkflowId`, `AgentId`, `AgentVersionId`, `OwnerId`, `NodeKey`, `EdgeKey`
- 행동이 붙는 수치 — `Revision.next()`, `VersionNumber.next()`, `Money.multiply()`
- 짝으로만 의미가 있는 값 — `Position(x, y)`, `AgentPlacement(agentId, versionId)`

**감싸지 않는다**

- 검증을 요청 DTO 로 옮기고 나면 껍데기만 남는 것 — 이름, 설명, 카테고리, JSON Schema 문서
- 조회에 쓰이지 않는 자유 문자열 — 연결점 이름(`handle`)

값 객체는 불변으로 만들고, 컬렉션은 `List.copyOf` / `Map.copyOf` / `Set.copyOf` 로 받는다.
밖에서 넘긴 컬렉션을 그대로 들고 있으면 값이 아니게 된다.

**`toString` 을 붙이지 않는다.** 문자열이 필요한 자리에서 `value()` 를 꺼낸다.
직렬화가 우연히 `toString` 에 얹히면 그것을 고치는 순간 응답이 조용히 바뀐다.
읽기 편하려고 붙인 것은 대부분 아무도 부르지 않으면서 지울 근거만 흐린다 (6장).

---

### 9.1 같은 필드를 두 타입이 따로 선언하지 않는다

필드 하나를 바꾸는 데 두 곳 이상 고쳐야 하면 잘못 나눈 것이다. 공통을 값 객체로 빼서 양쪽이
그것을 품는다.

```java
public record NodeSpec(String name, AiModel model, AbcAmount baseCost) { }

NodeDefinition        — id · owner · timestamps · spec · prompt
NodeDefinitionSummary — id · spec · timestamps
```

같은 모양의 레코드를 도메인마다 복제하지 않는다. 주인이 없으면 `domain` 바로 밑에 하나만
둔다 (11.2). `Timestamps` 가 그 예다 — 워크플로우용과 노드용을 따로 만들지 않는다.

감사 컬럼이 아닌 것을 감사 묶음에 넣지 않는다. `lastExecutedAt` 은 워크플로우의 도메인 사실이고
정렬 키로도 쓰므로 `Timestamps` 밖에 둔다.

---

## 10. Lombok

| 쓴다 | 안 쓴다 |
| --- | --- |
| `@Getter` (클래스 단위) | `@Setter` — 변경은 8.2 대로 Writer 가 한다 |
| `@RequiredArgsConstructor` | `@Data` — 무엇이 생기는지 읽는 사람이 모른다 |
| `@EqualsAndHashCode` + `@EqualsAndHashCode.Exclude` | `@Builder` — 필수 값이 빠져도 컴파일된다 |
| | `@AllArgsConstructor` — 쓸 일이 없다 (아래) |

**애그리거트의 생성자를 손으로 쓰지 않는다.** 접근을 `PRIVATE` 로 닫고 `create` · `restore` 같은
정적 팩터리만 밖에 낸다.

애그리거트는 전부 불변이므로(8.2) 모든 필드가 `final` 이고,
`@RequiredArgsConstructor(access = AccessLevel.PRIVATE)` 하나면 된다. `@AllArgsConstructor` 는
변경되는 필드를 밖에서 받아야 할 때 쓰던 것인데, 그런 필드가 없어서 쓸 일이 없다.

`create` 는 새로 만들 때, `restore` 는 저장된 값을 되돌려 받을 때다. 감사 값처럼 저장 후에만
존재하는 것이 있으므로 둘 다 필요하다 — `Workflow` · `NodeDefinition` · `Agent` · `AgentVersion`
이 모두 그 모양이다.

`record` 는 게터가 이미 있으니 Lombok 을 붙이지 않는다.
그래서 접근자가 두 모양으로 나뉜다 — `record` 는 `value()`, 클래스는 `getValue()`.

> **주의**: `@EqualsAndHashCode(of = {...})` 와 `@EqualsAndHashCode.Exclude` 를 같이 쓰면
> Lombok 1.18.46 이 컴파일 중 `NullPointerException` 으로 죽는다. 둘 중 하나만 쓴다.

---

## 11. 계층과 패키지

계층 구조와 의존 규칙은 [ARCHITECTURE.md](ARCHITECTURE.md) 가 원본이다.
`api` → `domain` → `implementation` → `storage` 4계층을 따른다.
이 장은 그 안에서 **도메인 모델을 어느 패키지에 두는가**만 정한다.

```
core-api    com.aiblockclock
            ├── api            Controller · Request · Response
            ├── domain         Service · 도메인 모델      (아래가 이 장의 범위)
            │   ├── OwnerId    회원 도메인이 생기면 그쪽으로 갈 임시 거처
            │   ├── AbcAmount  ABC 수량. 어느 도메인 것도 아니라 계속 여기 있는다
            │   ├── node       `NodeDefinition` 애그리거트 — 프롬프트 · 모델 · 원가
            │   │              그래프에 놓인 배치(`BaseNode`)와 다른 것이다
            │   ├── workflow   워크플로우 애그리거트 — 그래프 · 배치 · 연결선
            │   │              Service · 정책 · Reader · Writer 가 함께 있다
            │   └── agent      에이전트 · 버전 애그리거트
            └── support        응답 봉투 · 예외 규칙

core-enum   com.aiblockclock.enums
            ├── node           AiModel
            ├── workflow       NodeType · SourceType · InputSource · ValidationStatus
            └── agent          Tier · PublicationState
```

**`implementation` 계층은 자기 패키지를 갖지 않고 도메인 패키지 안에 들어간다.**
`Reader` · `Writer` · `Manager` 는 `com.aiblockclock.implementation.workflow` 가 아니라
`com.aiblockclock.domain.workflow` 에 둔다. 계층은 패키지가 아니라 접미사로 판별하고
(NAMING.md 2.3), 패키지는 도메인으로 묶는다 (ARCHITECTURE.md 5장).

- 도메인 모델은 스프링 빈이 아니다. `@Service`·`@Component` 를 붙이지 않는다.
- 도메인 모델은 JPA 애노테이션을 갖지 않고, `storage` 의 엔티티를 참조하지 않는다 (ARCHITECTURE.md 2.2).
  같은 패키지의 `Reader` · `Writer` · `Manager` 는 implementation 계층이므로 엔티티를 만진다. 모델과 혼동하지 않는다.
- `support` 는 응답·예외 규약이라 계층 밖에 둔다. 어느 계층에서든 참조할 수 있다.

### 11.1 도메인 enum 은 `core-enum` 에 둔다

같은 enum 을 `core-api` 의 도메인과 `storage` 의 엔티티가 함께 쓴다. 한쪽에 두면 다른 쪽이
그 모듈을 통째로 끌어와야 하므로, 모듈 밖으로 나가는 값은 `core-enum` 에 모은다.

- **패키지는 도메인 쪽 구조를 그대로 따라간다.** `Tier`, `NodeType`, `InputSource` 처럼 이름만으로는
  어느 도메인 것인지 알 수 없는 것들이라, 한 패키지에 몰아 두면 모듈 자체가 바구니가 된다.
- **`core-enum` 에는 외부 의존을 추가하지 않는다.** Spring 도, Jackson 도 들어오면 안 된다.
  이 모듈은 어디서든 끌어다 쓸 수 있어야 의미가 있다.
- **enum 이 아는 규칙은 enum 이 갖는다.** `NodeType.isVersionRequired()`, `Tier.premiumRate()` 처럼
  값에 딸린 규칙은 바깥의 `switch` 나 상수 표가 아니라 enum 안에 둔다. 상수를 추가할 때
  빠뜨릴 자리가 없어진다.

예외인 것도 있다. `ErrorType` 은 `HttpStatus` 와 `LogLevel` 을 갖고 있어 `core-enum` 에 들어갈 수
없고, `ErrorCode` · `ResultType` 은 API 응답 규약이라 `support` 에 남는다.
**도메인 개념을 나타내는 enum 만 `core-enum` 으로 간다.**

### 11.2 `shared` · `common` 패키지를 만들지 않는다

이름이 무엇이든, 아무거나 들어갈 수 있는 자리는 결국 쓰레기통이 된다.
타입 하나를 둘 곳이 애매하면 아래 순서로 정한다.

1. **소유 애그리거트가 분명하면 그 패키지에 둔다.**
   `AgentId` 는 두 패키지가 쓰더라도 에이전트의 것이므로 `agent` 에 있다.
   `WorkflowGraph` 도 같다. `AgentVersion` 이 스냅샷으로 들고 있지만 워크플로우의 형상이므로
   `workflow` 에 있다. 쓰는 쪽이 둘이라는 것은 별도 패키지를 만들 근거가 못 된다.
2. **아직 주인이 없으면 `domain` 바로 밑에 둔다.**
   `OwnerId` 는 회원 도메인이 생기면 그쪽으로 간다.
   그때까지는 하위 패키지를 미리 만들지 않는다. 빈 패키지를 만들어 두는 것도 자리를 미리 잡는
   또 다른 방식일 뿐이다.
3. **두 패키지가 서로를 참조해야 할 것 같으면, 둘 곳이 아니라 모델을 의심한다.** (아래)

### 11.3 패키지 순환은 모델이 잘못됐다는 신호다

`workflow` 가 `AgentId` 를 알고 `agent` 가 `WorkflowGraph` 를 알면 순환이 생긴다.
이때 공용 패키지를 만들어 식별자를 빼내거나 그래프만 따로 떼어내면 순환은 사라지지만 원인은 남는다.

원인은 **그래프가 "에이전트" 라는 개념을 알고 있었다는 것**이다.
그래프는 노드가 어떤 식별자를 가리킨다는 것까지만 알면 되고, 그 식별자를 에이전트로 읽는 일은
에이전트 도메인의 몫이다.

```java
// workflow — 무엇을 가리키는지까지만 안다. 참조를 내어 줄 뿐 노드 타입은 내보내지 않는다
public record NodeReference(UUID referenceId, UUID versionId) { }
public List<NodeReference> agentReferences() { ... }

// agent — 그 참조를 에이전트로 읽는다
public record AgentPlacement(AgentId agentId, AgentVersionId versionId) {
    public static AgentPlacement from(NodeReference reference) { ... }
}
```

지금 의존 방향은 이렇다. 순환이 없다.

```
workflow   ->  domain
agent      ->  workflow, domain
```

순환을 막는 것은 패키지 경계가 아니라 **`NodeReference` 가 `AgentId` 를 모른다는 것**이다.
그래서 그래프를 `workflow` 안에 둬도 순환이 생기지 않는다.

**건너오는 타입을 세어서 본다.** `agent` 가 `workflow` 에서 아는 것은 `Workflow` · `WorkflowGraph` ·
`NodeReference` · `WorkflowId` · `Revision` 다섯뿐이다. `AgentNode` 같은 구체 노드 타입도,
`ValidationStatus` 같은 enum 도 건너오지 않는다 — 전자는 `agentReferences()` 가, 후자는
`Workflow.isValidated()` 가 막는다. **`instanceof` 로 남의 패키지 타입을 갈라 보고 있으면
그쪽이 물음에 답해 주지 않고 있다는 뜻이다.**

테스트 소스셋도 같다. `workflow` 의 픽스처가 `AgentId` 를 받으면 main 에서 막아 둔 방향이
테스트에서 뚫린다. 에이전트가 필요한 픽스처는 `agent` 쪽에 둔다 (`AgentGraphFixtures`).

### 11.4 애그리거트를 가로지르는 규칙

한 애그리거트가 다른 애그리거트를 알아야 하는 규칙은 **정책 객체**로 뺀다.
도메인이 필요로 하는 조회는 인터페이스로 선언하고 구현은 바깥에 맡긴다.

```java
public final class AgentVersionPolicy {        // 판정은 도메인에 남는다
    private final AgentOwnershipView agentOwnershipView;
    private final NodeDefinitionReader nodeReader;

    public void ensureVersionable(Workflow workflow, OwnerId requester) { ... }
}
```

협력자가 다른 애그리거트의 Reader 여도 된다. `domain -> implementation` 은 허용된 방향이므로
(ARCHITECTURE 1.1) 물음 하나를 위해 인터페이스를 새로 만들지 않는다 (6장). `AgentOwnershipView`
가 인터페이스로 남은 것은 `AgentReader` 가 아직 없기 때문이고, 생기면 걷는다.

애그리거트에 넣을 수도, 서비스로 흘릴 수도 없는 규칙이 여기로 온다.

**정책은 "더 많이 알아야 하는 쪽" 에 둔다.** `AgentVersionPolicy` 는 워크플로우를 판정하지만
판단에 에이전트 소유 관계가 필요하므로 `agent` 에 있다. 반대로 두면 `workflow` 가 `agent` 를
참조하게 되어 순환이 생긴다.

---

### 11.5 연관 매핑과 자식 컬렉션 쓰기

**매핑하지 않는다.** 애그리거트를 넘는 참조는 식별자로 든다. `workflow_node.reference_id` 는
`node_definition` 이나 `agent` 를 가리키지만 `@ManyToOne` 이 아니라 생 `UUID` 다. 다형 참조라
매핑할 수도 없고, 매핑하면 워크플로우 하나 읽는 데 노드 정의가 딸려 온다.

**한 애그리거트 안의 자식 컬렉션도 매핑하지 않는다.** `workflow_node` · `workflow_edge` 는
생명주기가 부모에 종속되지만 `@OneToMany` 로 들지 않는다. 자식은 전용 리포지토리로 직접 쓴다.

**`cascade` · `orphanRemoval` 을 쓰지 않는다.** 무엇이 언제 지워지는지 코드에서 보이지 않게 된다.
`orphanRemoval` 로 자식을 갈아끼우면 Hibernate 가 DELETE 보다 INSERT 를 먼저 내보내 유니크 키가
터져서, `clear()` → `flush()` → `addAll()` 순서를 손으로 챙겨야 하기도 한다.

**갈아끼울 때 지우고 다시 넣지 않는다.** 살아남는 행까지 지웠다 넣으면 바뀌지도 않은 행에 DELETE 와
INSERT 가 나간다. 벌크 UPDATE 로 제자리에서 고치고, 사라진 키만 지우고, 새 키만 넣는다.

```java
private void replaceNodes(UUID workflowId, List<WorkflowNodeEntity> nodes) {
    workflowNodeRepository.deleteMissing(
            workflowId, nodes.stream().map(WorkflowNodeEntity::getNodeKey).toList());

    for (WorkflowNodeEntity node : nodes) {
        int updated = workflowNodeRepository.updatePlacement(workflowId, node.getNodeKey(), ...);

        if (updated == 0) {
            workflowNodeRepository.save(node);
        }
    }
}
```

**무엇이 남았는지 알려고 조회하지 않는다.** `UPDATE` 의 영향 행 수가 그것을 말해 준다 — 0 이면
없던 키이니 넣는다. 기존 행을 `SELECT` 해서 키를 대조하는 코드는 그래서 필요 없다.

부모를 통째로 지울 때는 다르다. 남길 것이 없으므로 자식을 벌크로 지우고 부모를 지운다.

---

## 12. 아직 정하지 못한 것

구현하면서 임시로 정한 값들이다. 확정되면 이 문서와 코드를 함께 고친다.

| 항목 | 지금 | 정해야 할 것 |
| --- | --- | --- |
| `GraphLimits` 기본값 | 노드 20개 / 깊이 10 (API 명세서 값) | 에이전트 중첩 깊이 3 — 스냅샷을 펼쳐야 판정된다 |
| 그래프 모양 | 분기 없는 한 줄만 받는다 (`WorkflowGraph.chainLength`) | 제어 노드(분기)가 들어오면 한 줄 검증을 DAG 검증으로 되돌려야 한다 |
| 노드 수 / 깊이 한도 | 한 줄이라 둘을 같은 수(`chainLength`)로 잰다. 실효 한도는 10 하나뿐이고, 20 은 11개 이상일 때 어느 코드가 나가는지를 가르는 경계다 (11~20 은 깊이, 21 부터는 노드) | 명세서가 둘을 따로 둘 이유가 있는지 |
| 목록 커서 | 오프셋을 Base64 로 감싼 것. `size + 1` 을 읽어 다음 페이지 유무를 판정한다 | 정렬 키 기반 커서. 지금은 페이지를 넘기다 행이 끼면 밀린다 |
| `ApiResult` 와 명세서 | `{status: SUCCESS/FAIL, data, error{code,message,data}}` | 명세서는 `{status: success/error, code, message}` 평면형 |
| `AGENT_006`, `AGENT_007`, `AGENT_009` | 코드를 임의로 붙였다 | Notion API 명세서에 등록 |
| `ownerId` 출처 | 회원 도메인·인증이 없어 `X-Owner-Id` 헤더를 그대로 믿는다 (`CurrentOwnerArgumentResolver`). 클라이언트가 남을 가장할 수 있다 | 인증 붙은 뒤 현재 사용자 조회 방식 |
| 워크플로우 `usageType` | 응답에서 뺐다 | 타인 에이전트 포함 여부 판정. `AgentOwnershipView` 구현과 조립 위치가 함께 정해져야 한다 |
| 출처 워크플로우 삭제 금지 | `WorkflowDeletionPolicy` 가 막는다 | 스냅샷이 값 복사라 원본이 없어도 실행된다. 출처 추적이 이유라면 삭제가 아니라 보존 표시가 맞다 |
| `domain` → Spring 의존 | `ErrorType` 이 `HttpStatus`·`LogLevel` 을 쓴다 | 아래 참고 |

### `domain` 이 Spring 을 참조하는 문제

예외를 `CoreException` 하나로 모으면서 `domain` 이 `support.error.ErrorType` 을 참조하게 됐고,
`ErrorType` 은 `org.springframework.http.HttpStatus` 와 `LogLevel` 을 갖는다.
그래서 도메인 단위 테스트에도 Spring 클래스패스가 필요하다.

예외 규칙을 한 곳으로 모으는 이득을 택하고 이 결합을 받아들였다.
나중에 걸림돌이 되면 `ErrorType` 에서 HTTP 상태를 떼어 `api` 계층의 매핑 표로 옮기면 된다.

### `ErrorMessage` 가 내보내는 코드

`ErrorMessage` 는 `errorType.getCode().name()` 을 `code` 로 내보낸다.
예를 들어 `WORKFLOW_REVISION_CONFLICT` 는 `WORKFLOW_016` 으로 응답한다.
