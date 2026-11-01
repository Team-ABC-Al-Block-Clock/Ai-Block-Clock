import { expectOk, type Api } from './auth'

export interface SavedWorkflow {
  id: string
  /** 실행 순서대로 놓인 LLM 노드 이름 */
  steps: string[]
}

/**
 * 시작 → LLM 노드들 → 끝(문서 · PDF) 한 줄짜리 워크플로우를 저장한다. 노드는 그 자리에서 프롬프트로 만든다.
 * 편집 화면을 거치지 않는다 — 이번 기능이 실행이라 준비는 API 로 한다.
 */
export async function saveLinearWorkflow(
  api: Api,
  steps: string[] = ['성적 분석', '가정통신문 작성'],
  name = '성적 리포트',
): Promise<SavedWorkflow> {
  const keys = steps.map((_, i) => `n${i + 1}`)
  const chain = ['s1', ...keys, 'e1']

  const response = await expectOk(
    await api.post('/api/v1/workflows', {
      name,
      nodes: [
        { nodeKey: 's1', sourceType: 'IO', ioType: 'START' },
        ...steps.map((step, i) => ({
          nodeKey: keys[i],
          sourceType: 'NODE',
          nodeType: 'LLM',
          name: step,
          prompt: `${step}을(를) 한다`,
        })),
        { nodeKey: 'e1', sourceType: 'IO', ioType: 'END', resultShape: 'DOCUMENT', resultFormat: 'PDF' },
      ],
      edges: chain.slice(1).map((target, i) => ({
        edgeKey: `g${i + 1}`,
        sourceNodeKey: chain[i],
        targetNodeKey: target,
      })),
    }),
  )

  return { id: (await response.json()).data.workflowId, steps }
}

export async function estimateOf(api: Api, workflowId: string): Promise<string> {
  const response = await expectOk(await api.post('/api/v1/executions/cost-estimates', { workflowId }))

  return (await response.json()).data.estimatedAmount
}
