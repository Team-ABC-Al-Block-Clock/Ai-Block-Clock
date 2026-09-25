/**
 * 받은 값 사이를 이 시간에 걸쳐 메운다. 조회 간격(2초)보다 조금 짧아, 다 굴러갈 때쯤
 * 다음 값이 온다 — 짧으면 도착하고 멈춰 서서 뚝뚝 끊겨 보이고, 길면 늘 뒤처진다.
 */
export const FILL_MS = 1700

/**
 * 끝났거나 멎었을 때 남은 거리를 좁히는 시간. 짧게 잡는다 — 이미 결과가 나와 있는데
 * 게이지만 느릿느릿 따라오면 화면이 덜 끝난 것처럼 보인다.
 */
export const SETTLE_MS = 400

/** 다음 눈금에 이만큼은 남겨 둔다. 닿아 버리면 서버가 알려 준 것보다 앞서 간 셈이 된다. */
const HEADROOM = 1

/** 1초에 남은 거리의 이만큼을 좁힌다. 닿지 않으므로 단계가 길든 짧든 계속 움직인다. */
const CREEP_PER_SEC = 0.45

/** 1밀리초에 나아갈 거리. 거리가 멀수록 빨라져 걸리는 시간은 늘 같다. */
export function rollRate(from: number, to: number, fillMs: number = FILL_MS): number {
  return Math.abs(to - from) / fillMs
}

/**
 * `dtMs` 동안 목표 쪽으로 나아간 값. 넘어서지 않는다.
 * 지수 감쇠 대신 일정한 속도를 쓴다 — 감쇠는 앞이 빠르고 뒤가 굼떠 숫자가 굴러가 보이지 않는다.
 */
export function rollTo(current: number, target: number, dtMs: number, perMs: number): number {
  // 흐른 시간이 없으면 제자리다. 여기서 목표를 돌려주면 첫 프레임에 그냥 건너뛴다.
  if (dtMs <= 0) return current
  if (perMs <= 0) return target

  const gap = target - current
  const step = perMs * dtMs

  return Math.abs(gap) <= step ? target : current + Math.sign(gap) * step
}

/**
 * 다음 눈금 앞까지 기어간다. 도는 단계가 끝나야 눈금이 오르는데, 그동안 게이지가 서 있으면
 * 멈춘 것처럼 보인다. 남은 거리의 일정 비율씩 좁혀 닿지는 않으므로, 단계가 얼마나 걸리든
 * 계속 움직이고 눈금 바로 앞에서 기다린다.
 */
export function creepTo(current: number, ceiling: number, dtMs: number): number {
  if (dtMs <= 0) return current

  const limit = ceiling - HEADROOM
  const gap = limit - current
  if (gap <= 0) return current

  return current + gap * (1 - Math.exp((-CREEP_PER_SEC * dtMs) / 1000))
}
