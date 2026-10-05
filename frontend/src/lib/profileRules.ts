import { PROFILE_LIMITS } from '@/api/auth'

// 서버가 USER_002 로 막는 범위를 보내기 전에 여기서 먼저 잡는다.
const { nickname: nickLimit, introduction: introLimit } = PROFILE_LIMITS

export function nicknameProblem(trimmed: string) {
  return trimmed.length < nickLimit.min || trimmed.length > nickLimit.max
    ? `이름은 ${nickLimit.min}~${nickLimit.max}자여야 합니다`
    : null
}

export function introProblem(trimmed: string) {
  return trimmed.length > introLimit.max ? `한 줄 소개는 ${introLimit.max}자까지입니다` : null
}
