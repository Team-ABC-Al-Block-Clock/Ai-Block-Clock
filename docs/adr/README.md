# Architecture Decision Records

되돌리기 어려운 구조 결정을 한 건씩 남긴다. 무엇을 골랐는지보다 **왜 그것을 골랐고 무엇을 포기했는지**를 남기는 것이 목적이다.

| # | 결정 | 상태 | 날짜 |
| --- | --- | --- | --- |
| [0001](0001-execution-messaging-rabbitmq.md) | AI 실행 접수 · 결과 전달을 RabbitMQ 로 옮긴다 (vs DB 큐 + 콜백 + 대조) | Accepted | 2026-10-16 (구현 대조 10-21) |

## 쓰는 법

- 파일 이름은 `NNNN-짧은-제목.md`. 번호는 늘리기만 하고 다시 쓰지 않는다.
- 형식은 Status · Date · Deciders → Context → Decision → Options Considered → Trade-off Analysis → Consequences → Action Items.
- 결정이 바뀌면 원래 ADR 을 고치지 않는다. 새 ADR 을 쓰고, 원래 것의 상태를 `Superseded by NNNN` 으로 바꾼다.
- 결정은 그대로이고 구현하며 사실관계가 달라졌으면(이름 · 수치 · 세부 동작) 원래 ADR 을 고치고, 무엇을 고쳤는지 「변경 이력」에 남긴다.
- 다른 문서에 있던 결정을 뒤집으면 그 문서 자리를 `Supersedes` 에 적는다.
