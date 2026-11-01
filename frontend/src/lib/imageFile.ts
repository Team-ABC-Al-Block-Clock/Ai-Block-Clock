/** 프로필·에이전트 등록이 같은 규칙을 쓴다 — 나누면 문구는 같고 판정만 갈린다. */
export const IMAGE_LIMITS = {
  maxBytes: 5 * 1024 * 1024,
  types: ['image/png', 'image/jpeg'],
  extensions: ['.png', '.jpg', '.jpeg'],
} as const

/** 고른 파일이 제약에 맞는지 — 어긋나면 띄울 문구, 맞으면 null. */
export function describeImageProblem(file: File): string | null {
  const { types, extensions, maxBytes } = IMAGE_LIMITS
  const name = file.name.toLowerCase()
  // File.type 은 빈 문자열이거나 image/jpg 로 오기도 한다. 최종 판단은 서버가 한다.
  const looksLikeImage =
    (types as readonly string[]).includes(file.type) || extensions.some((ext) => name.endsWith(ext))

  if (!looksLikeImage) return 'PNG 또는 JPG 만 올릴 수 있습니다'
  if (file.size > maxBytes) return '이미지는 5MB 이하여야 합니다'
  return null
}
