import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'

import { ApiError, EXECUTION_ERROR } from '@/api/errors'
import { downloadResult, fetchResult, type ResultFormat } from '@/api/executions'
import { queryKeys } from '@/lib/queryKeys'
import { resultOf, resultProblem } from '@/lib/run/runResult'

/** 끝난 실행의 결과 — 패널에 펼쳐 보이고, 복사하고, 파일로 받는다. */
export function useResultActions(executionId: string | undefined, finished: boolean) {
  const result = useQuery({
    queryKey: queryKeys.executions.result(executionId ?? ''),
    queryFn: async () => {
      const { blob } = await fetchResult(executionId!, 'JSON')
      return resultOf(JSON.parse(await blob.text()))
    },
    enabled: !!executionId && finished,
    staleTime: Infinity,
    // 파일이 아직 없으면 서버가 그 자리에서 다시 거둬 온다(`ExecutionService.download`). 재시도는 공용 기본에 맡긴다.
  })

  const [copied, setCopied] = useState(false)
  const copyTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => () => clearTimeout(copyTimer.current), [])

  const handleCopy = () => {
    if (!result.data) return
    const txt = result.data.text
    const ta = document.createElement('textarea')
    ta.value = txt
    ta.setAttribute('readonly', '')
    ta.style.cssText =
      'position:fixed;top:0;left:0;width:1px;height:1px;opacity:0;pointer-events:none'
    document.body.appendChild(ta)
    ta.select()
    ta.setSelectionRange(0, txt.length)
    try {
      document.execCommand('copy')
    } catch {
      /* execCommand 미지원 — 아래 clipboard API 로 재시도 */
    }
    document.body.removeChild(ta)
    if (navigator.clipboard && navigator.clipboard.writeText) {
      try {
        navigator.clipboard.writeText(txt).catch(() => undefined)
      } catch {
        /* 권한 거부·비보안 컨텍스트 */
      }
    }
    setCopied(true)
    clearTimeout(copyTimer.current)
    copyTimer.current = setTimeout(() => setCopied(false), 1900)
  }

  const [downloading, setDownloading] = useState(false)
  const [downloadProblem, setDownloadProblem] = useState<'PDF_MISSING' | 'FAILED' | null>(null)

  const handleDownload = async (format: ResultFormat) => {
    if (!executionId || downloading) return
    setDownloading(true)
    setDownloadProblem(null)

    try {
      await downloadResult(executionId, format)
    } catch (error) {
      setDownloadProblem(
        format === 'PDF' &&
          error instanceof ApiError &&
          error.code === EXECUTION_ERROR.RESULT_NOT_FOUND
          ? 'PDF_MISSING'
          : 'FAILED',
      )
    } finally {
      setDownloading(false)
    }
  }

  return {
    blocks: result.data?.blocks,
    notice: resultProblem(result),
    copied,
    handleCopy,
    canDownload: !!executionId && !downloading,
    downloading,
    downloadProblem,
    handleDownload,
  }
}
