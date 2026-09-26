# 백엔드 문서

`backend/ai-block-clock` 의 설계 문서와 그림을 둔다.

| 문서 | 내용 |
| --- | --- |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 4계층(`api` → `domain` → `implementation` → `storage`) 구조와 의존 규칙 |
| [`MODULES.md`](MODULES.md) | Gradle 모듈 구성, 모듈 간 의존, 설정 소유 규칙 |
| [`NAMING.md`](NAMING.md) | 클래스·메서드 네이밍, 계층별 접미사, 어휘 사전 |
| [`RULE.md`](RULE.md) | 테스트 분류(1~6장) · 코드 규칙(7~12장) |
| [`abc-charge.md`](abc-charge.md) | ABC 충전 — 체인에서 입금을 확인하는 방법 · 대조 규칙 · 설정 |
| [`abc-withdrawal.md`](abc-withdrawal.md) | 정산금 출금 — 본인 확인 · 신청 때 금액을 묶는 장부 거래 · 정책 · 동시 신청 · 관리자 심사 · Swagger 심사 절차 · 설정 · 정산금을 쓸 수 있는 ABC 로 옮기기 |
| [`plans/`](plans/) | **아직 코드가 없는 것의 설계.** 구현되면 걷어낸다 |
| `diagrams/` | 개념도 · 구조도 |

---

## diagrams

| 그림 | 무엇을 그리나 |
| --- | --- |
| `domain-model` | **애그리거트와 그 안의 1급 개념, 애그리거트 사이의 관계** |
| 흐름도 | 버전 생성·실행처럼 여러 애그리거트를 거치는 시나리오 |

화면 설계와 API 명세는 두지 않는다. 그건 Notion 이 원본이다.

### 표기

[`example.png`](diagrams/example.png) 를 따른다.

- **바깥 상자 = 애그리거트**, 안의 둥근 상자 = 그 애그리거트의 1급 개념
- 화살표는 참조 방향. 되돌아오는 화살표가 보이면 그림이 아니라 코드를 의심한다
- **1급 개념만 그린다.** 딸린 값 객체, enum, 정책 객체는 그리지 않고 표로 적는다
- 계층·모듈·패키지는 그리지 않는다. 그건 `ARCHITECTURE.md` 와 `MODULES.md` 의 몫이다

**`*.drawio.svg` 로 저장한다.** draw.io 로 열어 편집하고, 그대로 GitLab 과 마크다운에서 렌더링된다.
PNG 로 두면 편집하려면 원본 파일이 따로 있어야 하고 diff 도 남지 않는다.

```bash
# 편집: diagrams.net 에서 File > Open 으로 .drawio.svg 를 열고, 저장할 때 Editable SVG 로 내보낸다
# 확인: rsvg-convert -o out.png graph.drawio.svg
```

### 파일명

**도메인 관계는 한 장에 담는다.** 애그리거트마다 쪼개면 애그리거트 사이의 관계가 보이지 않는다.

```
diagrams/domain-model.md  +  domain-model.drawio.svg
```

마크다운은 그림을 싣고 표와 근거를 함께 적는다. 여러 애그리거트를 거치는 흐름은
`agent-version-flow` 처럼 동작 이름으로 따로 만든다.

### 지켜야 할 것

- **코드와 어긋나면 코드가 맞다.** 그림은 코드를 따라간다.
- 구조를 바꾸는 MR 은 관련 그림도 같이 고친다. 못 고치면 MR 본문에 어느 그림이 낡았는지 적는다.
- 그림에 쓰는 말은 저장소 루트의 `GLOSSARY.md` 를 따른다. 새 말이 필요하면 거기부터 고친다.
- 계층·모듈을 그릴 때는 `ARCHITECTURE.md` 와 `MODULES.md` 가 원본이다. 그림이 그 문서와 달라지면 그림이 틀린 것이다.
