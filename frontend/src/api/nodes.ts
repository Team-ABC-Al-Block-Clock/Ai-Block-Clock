import { api } from '@/api/client'

/**
 * 작업 노드 API. 팔레트에서 놓는 항목의 원본이다.
 *
 * 캔버스에서 노드를 빼는 것과 여기서 지우는 것은 다른 일이다 — 캔버스는 배치만 풀고,
 * 이쪽은 원본을 지워 다른 워크플로우에서도 사라진다.
 */

/** 서버가 아는 모델. 비워 보내면 서버가 기본값을 고른다. */
export type ServerModel = 'GEMINI_2_5_FLASH' | 'GEMINI_2_5_PRO'

/** 실제로 도는 유일한 모델. 무엇을 골라도 이 값을 싣고, 만들기 자리의 첫 선택이다. */
export const DEFAULT_AI_MODEL: ServerModel = 'GEMINI_2_5_PRO'

/** 고르는 자리에 보이는 모델. GMS 텍스트 모델 목록으로, 보여 주기만 하고 실행은 기본 모델로 한다. */
export const AI_MODELS = [
  { value: 'GEMINI_2_5_PRO', label: 'Gemini 2.5 Pro', hint: '정밀 추론 · 긴 문서' },
  { value: 'GEMINI_2_5_FLASH', label: 'Gemini 2.5 Flash', hint: '빠른 범용' },
  { value: 'GPT_5_5', label: 'GPT-5.5', hint: '고급 추론 · 긴 맥락' },
  { value: 'GPT_5_5_PRO', label: 'GPT-5.5 Pro', hint: '최고 사양 추론 · 코딩' },
  { value: 'GPT_5_4', label: 'GPT-5.4', hint: '범용 플래그십' },
  { value: 'GPT_5_4_PRO', label: 'GPT-5.4 Pro', hint: '전문 분석 · 대형 코드' },
  { value: 'GPT_5_4_MINI', label: 'GPT-5.4 mini', hint: '실시간 · 대량 호출' },
  { value: 'GPT_5_4_NANO', label: 'GPT-5.4 nano', hint: '초고속 · 단순 반복' },
  { value: 'GPT_5_2', label: 'GPT-5.2', hint: '에이전트 · 코드 작업' },
  { value: 'GPT_5_2_PRO', label: 'GPT-5.2 Pro', hint: '깊은 사고 · 난제' },
  { value: 'GPT_5_1', label: 'GPT-5.1', hint: '코딩 · 에이전트' },
  { value: 'GPT_5', label: 'GPT-5', hint: '고급 추론' },
  { value: 'GPT_5_MINI', label: 'GPT-5 mini', hint: '균형 잡힌 범용' },
  { value: 'GPT_5_NANO', label: 'GPT-5 nano', hint: '초경량 · 대량 배치' },
  { value: 'GPT_4_1', label: 'GPT-4.1', hint: '긴 맥락 · 안정성' },
  { value: 'GPT_4_1_MINI', label: 'GPT-4.1 mini', hint: '가성비 · 프로토타입' },
  { value: 'GPT_4_1_NANO', label: 'GPT-4.1 nano', hint: '경량 · 챗봇' },
  { value: 'GPT_4O', label: 'GPT-4o', hint: '멀티모달 · 빠른 응답' },
  { value: 'GPT_4O_MINI', label: 'GPT-4o mini', hint: '경제적 멀티모달' },
  { value: 'O3_MINI', label: 'o3-mini', hint: '소형 추론 · 구조화 출력' },
  { value: 'GEMINI_3_5_FLASH', label: 'Gemini 3.5 Flash', hint: '초고속 · 긴 맥락' },
  { value: 'GEMINI_2_5_FLASH_LITE', label: 'Gemini 2.5 Flash-Lite', hint: '초경량 · 대량 작업' },
  { value: 'CLAUDE_OPUS_4_8', label: 'Claude Opus 4.8', hint: '전략 분석 · 다단계 추론' },
  { value: 'CLAUDE_OPUS_4_7', label: 'Claude Opus 4.7', hint: '정밀 논리 · 연구 분석' },
  { value: 'CLAUDE_OPUS_4_6', label: 'Claude Opus 4.6', hint: '고난도 종합 분석' },
  { value: 'CLAUDE_SONNET_4_6', label: 'Claude Sonnet 4.6', hint: '코딩 · 문서 추출' },
  { value: 'CLAUDE_SONNET_4_5', label: 'Claude Sonnet 4.5', hint: '에이전트 · 코딩' },
  { value: 'CLAUDE_HAIKU_4_5', label: 'Claude Haiku 4.5', hint: '가장 빠른 응답' },
  { value: 'CLAUDE_OPUS_4_5', label: 'Claude Opus 4.5', hint: '최대 지능 · 실용성' },
  { value: 'CLAUDE_OPUS_4_1', label: 'Claude Opus 4.1', hint: '고급 코딩 · 추론' },
] as const satisfies readonly { value: string; label: string; hint?: string }[]

export type AiModel = (typeof AI_MODELS)[number]['value']

/** 서버에 싣는 값. 목록은 보여 주기용이라 늘 기본 모델이다. */
export const serverModel = (model: AiModel): ServerModel => {
  void model
  return DEFAULT_AI_MODEL
}

/** 노드가 결과를 내는 형식. `JSON` 이면 결과가 `example` 과 같은 모양이고, 분기 조건은 그 칸만 읽는다. */
export type OutputKind = 'TEXT' | 'JSON'

export interface NodeOutput {
  kind: OutputKind
  /** `JSON` 일 때만. 키가 하나 이상인 객체이고 값이 null 인 키는 선택 항목이다 */
  example: Record<string, unknown> | null
}

/** 서버 기본값. 비워 보내면 이 값이 된다. */
export const TEXT_OUTPUT: NodeOutput = { kind: 'TEXT', example: null }

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
  model: ServerModel
  output: NodeOutput
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
  /** 빠지면 서버가 `TEXT` 로 되돌린다. 고칠 때마다 싣는다 */
  output: NodeOutput
}

export const updateNode = (nodeId: string, body: NodeSaveBody) =>
  api.put<NodeDetail>(`/nodes/${nodeId}`, {
    ...body,
    ...(body.model ? { model: serverModel(body.model) } : {}),
  })

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
