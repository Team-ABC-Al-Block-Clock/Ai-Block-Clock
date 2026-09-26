import { ApiError, NODE_ERROR } from '@/api/errors'

/**
 * 참고 자료를 붙이거나 뗄 때 서버가 거절한 이유의 화면 문구.
 *
 * 형식·용량은 제출 전 검사(`describeRefFileProblem`)가 먼저 막는다 — 여기까지 오는 것은
 * 다른 탭에서 먼저 올렸거나 지운 경우다.
 *
 * `NODE_002`(남의 노드)는 가르지 않는다. 자료 칸은 내 기본 노드에만 서고 팔레트에는 마켓
 * 에이전트만 실려서, 남의 노드에 올릴 길 자체가 없다.
 */
export function refFileErrorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isNetworkError) return '서버에 닿지 못했습니다. 연결을 확인하고 다시 시도해 주세요.'
    switch (error.code) {
      case NODE_ERROR.REF_FILE_TOO_LARGE:
        return '업로드한 자료의 합계가 10MB 를 넘습니다. 다른 자료를 먼저 삭제해 주세요.'
      case NODE_ERROR.REF_FILE_REJECTED:
        return 'PDF · 텍스트 · 마크다운 · CSV 만 업로드 가능합니다.'
      case NODE_ERROR.REF_FILE_NOT_FOUND:
        return '다른 곳에서 이미 삭제된 자료입니다. 목록을 새로 불러왔습니다.'
      case NODE_ERROR.NOT_FOUND:
        return '노드를 찾을 수 없습니다. 워크플로우를 다시 불러와 주세요.'
    }
  }
  return '자료를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}
