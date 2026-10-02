/** 고른 그림이 받아들여지지 않은 까닭. 화면이 길어 고른 자리 옆 줄 끝에 띄운다. */
export default function ImageProblemLine({ text, top }: { text: string; top: number }) {
  return (
    <div
      className="absolute text-right text-[13px] tracking-[-.005em]"
      style={{ left: '272px', top: `${top}px`, width: '896px', color: 'rgba(255,196,214,.9)' }}
    >
      {text}
    </div>
  )
}
