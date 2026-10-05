/** 입력이 제어 컴포넌트가 아니라 제출할 때 한 번 읽는다.
 *  JSX 도 이 맵으로 id 를 붙인다. 리터럴로 적어 두면 한쪽만 고쳐졌을 때 read() 가
 *  빈 문자열을 돌려주고, 설명은 조용히 사라지고 이름은 원인 모를 COMMON_001 이 된다. */
export const FIELD = {
  onboardingMessage: 'regOnboarding',
  name: 'regName',
  summary: 'regSummary',
  description: 'regDescription',
  usageExample: 'regUsageExample',
} as const

export function read(id: string) {
  const node = document.getElementById(id)
  return node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement
    ? node.value.trim()
    : ''
}
