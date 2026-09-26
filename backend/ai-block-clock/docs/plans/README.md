# 설계 계획

**아직 코드가 없는 것의 설계를 둔다.** 무엇을 만들지, 왜 그렇게 나눌지, 어떤 순서로 할지를 적는다.

| 문서 | 범위 |
| --- | --- |
| [`contract-v2.md`](contract-v2.md) | **09-26 계약 v2.0 과 FE 요청.** 처리 현황(MR !116 · !117 · !119 · !125 · !126), 노드별 출력 형식, `TRIGGER` · `TOOL`, AI 파트에 물을 것 |
| [`agent-pipeline.md`](agent-pipeline.md) | 작업 노드 · 그래프 확장 · 에이전트 가격 · AI Runtime 실행에서 **남은 것** — 정산 · 이력 목록 · 마켓 API · 실행 길이 상한 |
| [`io-nodes.md`](io-nodes.md) | **시작 · 끝 노드를 실행 요청으로 옮기기.** 저장 · 조회 · 결과 받기는 끝났다 |
| [`ai-runtime-contract-gap.md`](ai-runtime-contract-gap.md) | **AI Runtime 계약과 우리 코드가 어긋난 자리.** 09-22 기준이다 — v2.0 은 머리말과 `contract-v2.md` |
| [`ai-runtime-questions.md`](ai-runtime-questions.md) | **AI 파트에 보내는 확인 요청.** 답이 오면 `agent-pipeline.md` 에 반영하고 걷어낸다 |
| [`completed/`](completed/) | **구현이 끝나 걷어낸 장.** 왜 그 모양인지 되짚을 때만 본다. 현재 규칙의 원본이 아니다 |

`completed/` 에는 `agent-pipeline.md` · `io-nodes.md` 에서 걷어낸 장과, 통째로 끝난 `file-upload.md` ·
`node-ref-files.md` 가 있다.

## 다른 문서와의 경계

| | 무엇을 적나 |
| --- | --- |
| `plans/` | **앞으로 만들 것.** 구현되면 해당 내용을 걷어내고 아래로 옮긴다 |
| `ARCHITECTURE.md` · `MODULES.md` · `NAMING.md` · `RULE.md` | **이미 지켜야 하는 것** |
| `diagrams/` | 구현된 구조의 그림 |
| 루트 `GLOSSARY.md` | 말이 갈리는 자리 |

계획이 코드가 되면 문서는 **줄어야 한다.** 구현된 항목을 남겨 두면 어느 쪽이 현재인지 알 수 없다.

## 적는 방식

- **결정과 근거를 함께 적는다.** 근거 없는 결정은 다음 사람이 다시 뒤집는다.
- **미결은 미결이라고 적는다.** 정해진 척하는 것이 비어 있는 것보다 나쁘다.
- **외부 팀에 물어야 하는 것은 따로 모은다.** 답이 와야 진행되는 것과 지금 할 수 있는 것을 가른다.
