import { api } from '@/api/client'

/**
 * 작업 노드 API. 팔레트에서 놓는 항목의 원본이다.
 *
 * 캔버스에서 노드를 빼는 것과 여기서 지우는 것은 다른 일이다 — 캔버스는 배치만 풀고,
 * 이쪽은 원본을 지워 다른 워크플로우에서도 사라진다.
 */

/** 서버가 아는 모델. 지금은 하나뿐이고, 비워 보내면 서버가 기본값을 고른다. */
export type AiModel = 'GEMINI_2_5_FLASH'

/** 고르는 자리에 쓰는 이름. 서버 enum 이 늘어나면 여기만 고친다. */
export const AI_MODELS: { value: AiModel; label: string }[] = [
  { value: 'GEMINI_2_5_FLASH', label: 'Gemini 2.5 Flash' },
]

/** 서버 기본값과 같은 값. 만들기 자리의 첫 선택이다. */
export const DEFAULT_AI_MODEL: AiModel = 'GEMINI_2_5_FLASH'

/** 붙여 둔 참고 자료. 이 화면은 아직 다루지 않고 받기만 한다. */
export interface RefFile {
  refFileId: string
  filename: string
  /** 바이트 */
  size: number
}

export interface NodeDetail {
  nodeId: string
  name: string
  prompt: string
  model: AiModel
  /** 1회 실행 원가. 금액은 오차를 피해 문자열로 온다 */
  baseCost: string
  currency: string
  refFiles: RefFile[]
}

/** 서버 이름 상한. 넘겨 보내면 COMMON_001 로 돌아온다. */
export const NODE_NAME_MAX = 40

/** 저장된 워크플로우를 다시 열 때, 배치가 가리키는 원본의 이름·프롬프트를 읽는다. */
export const getNode = (nodeId: string) => api.get<NodeDetail>(`/nodes/${nodeId}`)
