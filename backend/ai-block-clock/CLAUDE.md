# CLAUDE.md

프로젝트 규칙은 `docs/RULE.md` 에 있다. 아래는 그 문서가 다루지 않는 서식 규칙이다.

## 메서드 본문은 의미 단위로 끊는다

한 단위가 끝나면 빈 줄을 넣는다. 블록(`if`·`for`·`while`·`try`)은 그 자체로 한 단위이므로
닫는 중괄호 다음에 빈 줄이 온다. 값 몇 개를 준비하는 줄들도 한 단위다.

```java
// 이렇게
private NodeKey soleStart() {
    Set<NodeKey> targeted = targetedNodes();
    List<NodeKey> starts = nodes.stream()
            .map(GraphNode::key)
            .filter(key -> !targeted.contains(key))
            .toList();

    if (starts.isEmpty()) {
        throw new CoreException(ErrorType.WORKFLOW_GRAPH_CYCLE);
    }

    if (starts.size() > 1) {
        throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_TREE,
                Map.of("startCount", starts.size()));
    }

    return starts.get(0);
}
```

```java
// 이렇게 말고 — 어디서 끊어 읽어야 하는지 눈이 스스로 찾아야 한다
private NodeKey soleStart() {
    Set<NodeKey> targeted = targetedNodes();
    List<NodeKey> starts = nodes.stream()
            .map(GraphNode::key)
            .filter(key -> !targeted.contains(key))
            .toList();
    if (starts.isEmpty()) {
        throw new CoreException(ErrorType.WORKFLOW_GRAPH_CYCLE);
    }
    if (starts.size() > 1) {
        throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_TREE,
                Map.of("startCount", starts.size()));
    }
    return starts.get(0);
}
```

두 가지는 예외다.

- 블록이 메서드의 마지막 문장이면 뒤에 빈 줄을 넣지 않는다. 닫는 중괄호가 이미 끊어준다.
- 본문이 서너 줄이고 흐름이 하나뿐이면 끊지 않는다. 빈 줄이 단위를 나누는 게 아니라 한 문장을 쪼개는 꼴이 된다.

빈 줄을 두 줄 이상 연달아 넣지 않는다.
