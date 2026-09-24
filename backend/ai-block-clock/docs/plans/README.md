# 설계 계획

**아직 코드가 없는 것의 설계를 둔다.** 무엇을 만들지, 왜 그렇게 나눌지, 어떤 순서로 할지를 적는다.

| 문서 | 범위 |
| --- | --- |
| [`agent-pipeline.md`](agent-pipeline.md) | 작업 노드 · 그래프 확장 · 에이전트 가격 · AI Runtime 실행 |
| [`file-upload.md`](file-upload.md) | **수요자**가 실행할 때 올리는 파일을 받아 검사 · 보관하고 Runtime 에 싣는다 |
| [`node-ref-files.md`](node-ref-files.md) | **공급자**가 노드에 붙여 두는 참고 자료. 굳힌 버전이 제 것을 들고 있다 |
| [`ai-runtime-contract-gap.md`](ai-runtime-contract-gap.md) | **AI Runtime 계약과 우리 코드가 어긋난 자리.** 저장 구조 · 실어 보내는 값 · 받는 값 |
| [`ai-runtime-questions.md`](ai-runtime-questions.md) | **AI 파트에 보내는 확인 요청.** 답이 오면 `agent-pipeline.md` 에 반영하고 걷어낸다 |
| [`completed/`](completed/) | **구현이 끝나 걷어낸 장.** 왜 그 모양인지 되짚을 때만 본다. 현재 규칙의 원본이 아니다 |

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
