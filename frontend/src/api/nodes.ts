import { api } from '@/api/client'

/**
 * 작업 노드 API. 팔레트에서 놓는 항목의 원본이다.
 *
 * 캔버스에서 노드를 빼는 것과 여기서 지우는 것은 다른 일이다 — 캔버스는 배치만 풀고,
 * 이쪽은 원본을 지워 다른 워크플로우에서도 사라진다.
 */

/** 서버가 아는 모델. 비워 보내면 서버가 기본값을 고른다. */
export type AiModel = 'GEMINI_2_5_FLASH' | 'GEMINI_2_5_PRO'

/** 고르는 자리에 쓰는 이름. 서버 enum 이 늘어나면 여기만 고친다. */
export const AI_MODELS: { value: AiModel; label: string; hint?: string }[] = [
  { value: 'GEMINI_2_5_FLASH', label: 'Gemini 2.5 Flash', hint: '빠름 · 원가 낮음' },
  // 고르면 단가가 오른다 — 노드당 15~35초에 원가가 약 네 배다(AI 연동 가이드 13장).
  { value: 'GEMINI_2_5_PRO', label: 'Gemini 2.5 Pro', hint: '정밀 · 느리고 원가 4배' },
]

/** 서버 기본값과 같은 값. 만들기 자리의 첫 선택이다. */
export const DEFAULT_AI_MODEL: AiModel = 'GEMINI_2_5_FLASH'

/** 붙여 둔 참고 자료. */
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

/** 이미 만들어진 노드를 제자리에서 고친다. 배치는 `sourceId` 를 그대로 가리킨다. */
export interface NodeSaveBody {
  name: string
  prompt: string
  model?: AiModel | null
}

export const updateNode = (nodeId: string, body: NodeSaveBody) =>
  api.put<NodeDetail>(`/nodes/${nodeId}`, body)

/**
 * 서버 `RefFileFormat` 과 같은 값. 한 건이 아니라 노드에 붙은 것의 합계가 상한이다.
 *
 * 실행 엔진이 받는 형식이다(계약 v2.0 7.1). 엔진이 안 받는 것을 붙여 두면 실행할 때 그 노드가
 * `REF_FILE_REJECTED` · 공급자 탓으로 깨진다.
 */
export const REF_FILE_LIMITS = {
  maxTotalBytes: 10 * 1024 * 1024,
  extensions: ['.pdf', '.txt', '.md', '.csv'],
} as const

/**
 * 붙일 수 있는 파일인지 — 어긋나면 띄울 문구, 맞으면 null.
 *
 * 서버는 빈 파일과 형식 불가에 같은 코드(`NODE_006`)를 쓴다. 사유를 가르는 것은 여기뿐이다.
 */
export function describeRefFileProblem(
  file: File,
  attachedBytes: number,
  takenNames: readonly string[] = [],
): string | null {
  const { extensions, maxTotalBytes } = REF_FILE_LIMITS
  const name = file.name.toLowerCase()

  // 서버와 같은 순서로 본다 — 0바이트 zip 을 화면과 서버가 다른 사유로 거절하면 안 된다.
  if (file.size === 0) return '빈 파일은 업로드할 수 없습니다'
  if (!extensions.some((ext) => name.endsWith(ext)))
    return 'PDF · 텍스트 · 마크다운 · CSV 만 업로드 가능합니다'
  if (takenNames.some((taken) => taken.toLowerCase() === name)) return '이미 목록에 있는 파일입니다'
  if (attachedBytes + file.size > maxTotalBytes) return '업로드한 자료의 합계가 10MB 를 넘습니다'
  return null
}

/**
 * 참고 자료 한 건을 붙인다. 명세가 multipart 다 — `Content-Type` 은 손대지 않는다.
 *
 * 한 요청에 한 건이고, 합계 상한을 서버가 누적으로 재기 때문에 여러 건은 차례로 보낸다.
 */
export const attachRefFile = (nodeId: string, file: File) => {
  const form = new FormData()
  form.append('file', file)

  return api.post<RefFile>(`/nodes/${nodeId}/ref-files`, form)
}

/** 업로드한 자료를 삭제한다. 원본에서 사라져 다른 워크플로우에서도 빠진다. */
export const detachRefFile = (nodeId: string, refFileId: string) =>
  api.delete<void>(`/nodes/${nodeId}/ref-files/${refFileId}`)
