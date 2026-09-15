import { Component, type ErrorInfo, type ReactNode } from 'react'

import { paths } from '@/routes/paths'

interface ErrorBoundaryProps {
  children: ReactNode
  /**
   * 값이 바뀌면 에러 상태를 초기화한다.
   * 라우트 경로를 넘기면 다른 화면으로 이동했을 때 자동으로 복구된다.
   */
  resetKey?: unknown
  /** 폴백을 직접 지정하고 싶을 때. 없으면 기본 화면을 쓴다. */
  fallback?: (error: Error, reset: () => void) => ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * 렌더링 중 던져진 예외를 잡아 화면 전체가 하얘지는 것을 막는다.
 *
 * React 에는 훅 버전이 없어 클래스 컴포넌트로만 만들 수 있다.
 * 이벤트 핸들러·비동기 콜백에서 난 예외는 잡지 못한다 — 그쪽은
 * TanStack Query 의 에러 상태나 try/catch 로 다룬다.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // 개발 중에는 어느 컴포넌트에서 터졌는지가 스택보다 중요하다.
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  componentDidUpdate(prevProps: ErrorBoundaryProps) {
    if (this.state.error !== null && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null })
    }
  }

  private reset = () => this.setState({ error: null })

  render() {
    const { error } = this.state
    if (error === null) return this.props.children
    if (this.props.fallback) return this.props.fallback(error, this.reset)

    return (
      <div className="absolute inset-0 flex items-center justify-center p-10">
        <div className="glass-card bg-surface-deep shadow-card rounded-panel flex w-[420px] flex-col items-center gap-3 px-8 py-9 text-center">
          <h1 className="text-body text-[18px] font-semibold tracking-[-0.01em]">
            문제가 발생했어요
          </h1>
          <p className="text-muted text-[13px] leading-[1.6]">
            화면을 그리는 중 오류가 났습니다. 다시 시도하거나 홈으로 돌아가 주세요.
          </p>

          <pre className="bg-surface-inset text-faint scroll-thin mt-1 max-h-28 w-full rounded-[10px] px-3 py-2 text-left text-[11px] leading-[1.5] whitespace-pre-wrap">
            {error.message}
          </pre>

          <div className="mt-2 flex w-full gap-2">
            <button
              type="button"
              onClick={this.reset}
              className="bg-accent text-accent-ink h-11 flex-1 cursor-pointer rounded-xl text-[13px] font-bold transition-transform hover:-translate-y-px"
            >
              다시 시도
            </button>
            <a
              href={paths.home}
              className="bg-surface-quiet text-body flex h-11 flex-1 items-center justify-center rounded-xl text-[13px] font-semibold transition-transform hover:-translate-y-px"
            >
              홈으로
            </a>
          </div>
        </div>
      </div>
    )
  }
}
